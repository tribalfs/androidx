/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.plugins.kotlin.compiler.lower

import androidx.compose.plugins.kotlin.KtxNameConventions
import androidx.compose.plugins.kotlin.analysis.ComposeWritableSlices
import androidx.compose.plugins.kotlin.frames.findTopLevel
import androidx.compose.plugins.kotlin.generateSymbols
import androidx.compose.plugins.kotlin.hasComposableAnnotation
import androidx.compose.plugins.kotlin.irTrace
import androidx.compose.plugins.kotlin.isEmitInline
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.ir.copyTypeParametersFrom
import org.jetbrains.kotlin.backend.jvm.ir.isInlineParameter
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PropertyGetterDescriptor
import org.jetbrains.kotlin.descriptors.PropertySetterDescriptor
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrOverridableDeclaration
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.copyAttributes
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.descriptors.WrappedFunctionDescriptorWithContainerSource
import org.jetbrains.kotlin.ir.descriptors.WrappedPropertyGetterDescriptor
import org.jetbrains.kotlin.ir.descriptors.WrappedPropertySetterDescriptor
import org.jetbrains.kotlin.ir.descriptors.WrappedSimpleFunctionDescriptor
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.copyTypeArgumentsFrom
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.DeepCopySymbolRemapper
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.explicitParameters
import org.jetbrains.kotlin.ir.util.findAnnotation
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi2ir.findFirstFunction
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DescriptorWithContainerSource
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections
import org.jetbrains.kotlin.util.OperatorNameConventions
import kotlin.math.min

class ComposerParamTransformer(
    context: IrPluginContext,
    symbolRemapper: DeepCopySymbolRemapper,
    bindingTrace: BindingTrace,
    val functionBodySkipping: Boolean
) :
    AbstractComposeLowering(context, symbolRemapper, bindingTrace),
    ModuleLoweringPass {

    override fun lower(module: IrModuleFragment) {
        module.transformChildrenVoid(this)

        module.acceptVoid(symbolRemapper)

        val typeRemapper = ComposerTypeRemapper(
            context,
            symbolRemapper,
            typeTranslator,
            composerTypeDescriptor,
            functionBodySkipping
        )
        // for each declaration, we create a deepCopy transformer It is important here that we
        // use the "preserving metadata" variant since we are using this copy to *replace* the
        // originals, or else the module we would produce wouldn't have any metadata in it.
        val transformer = DeepCopyIrTreeWithSymbolsPreservingMetadata(
            context,
            symbolRemapper,
            typeRemapper,
            typeTranslator
        ).also { typeRemapper.deepCopy = it }
        module.transformChildren(
            transformer,
            null
        )
        // just go through and patch all of the parents to make sure things are properly wired
        // up.
        module.patchDeclarationParents()
    }

    private val transformedFunctions: MutableMap<IrFunction, IrFunction> = mutableMapOf()

    private val transformedFunctionSet = mutableSetOf<IrFunction>()

    private val composerType = composerTypeDescriptor
        .defaultType
        .replaceArgumentsWithStarProjections()
        .toIrType()

    override fun visitFunction(declaration: IrFunction): IrStatement {
        return super.visitFunction(declaration.withComposerParamIfNeeded())
    }

    fun IrCall.withComposerParamIfNeeded(composerParam: IrValueParameter): IrCall {
        val isComposableLambda = isComposableLambdaInvoke()
        if (!symbol.descriptor.isComposable() && !isComposableLambda)
            return this
        val ownerFn = when {
            isComposableLambda -> {
                if (!symbol.isBound) context.irProviders.getDeclaration(symbol)
                (symbol.owner as IrSimpleFunction).lambdaInvokeWithComposerParamIfNeeded()
            }
            else -> (symbol.owner as IrSimpleFunction).withComposerParamIfNeeded()
        }
        if (!transformedFunctionSet.contains(ownerFn))
            return this
        if (symbol.owner == ownerFn)
            return this
        return IrCallImpl(
            startOffset,
            endOffset,
            type,
            ownerFn.symbol,
            typeArgumentsCount,
            ownerFn.valueParameters.size,
            origin,
            superQualifierSymbol
        ).also {
            it.copyAttributes(this)
            context.irTrace.record(
                ComposeWritableSlices.IS_COMPOSABLE_CALL,
                it,
                true
            )
            it.copyTypeArgumentsFrom(this)
            it.dispatchReceiver = dispatchReceiver
            it.extensionReceiver = extensionReceiver
            val argumentsMissing = mutableListOf<Boolean>()
            for (i in 0 until valueArgumentsCount) {
                val arg = getValueArgument(i)
                argumentsMissing.add(arg == null)
                if (arg != null) {
                    it.putValueArgument(i, arg)
                } else if (functionBodySkipping) {
                    it.putValueArgument(i, defaultArgumentFor(ownerFn.valueParameters[i]))
                }
            }
            val realParams = valueArgumentsCount
            var argIndex = valueArgumentsCount
            it.putValueArgument(
                argIndex++,
                IrGetValueImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    composerParam.symbol
                )
            )

            if (functionBodySkipping) {
                for (i in 0 until changedParamCount(realParams)) {
                    if (argIndex < ownerFn.valueParameters.size) {
                        it.putValueArgument(
                            argIndex++,
                            irConst(0)
                        )
                    } else {
                        error("expected value parameter count to be higher")
                    }
                }

                for (i in 0 until defaultParamCount(realParams)) {
                    val start = i * BITS_PER_INT
                    val end = min(start + BITS_PER_INT, realParams)
                    if (argIndex < ownerFn.valueParameters.size) {
                        val bits = argumentsMissing
                            .toBooleanArray()
                            .sliceArray(start until end)
                        it.putValueArgument(
                            argIndex++,
                            irConst(bitMask(*bits))
                        )
                    } else if (argumentsMissing.any { it }) {
                        error("expected value parameter count to be higher")
                    }
                }
            }
        }
    }

    private fun defaultArgumentFor(param: IrValueParameter): IrExpression {
        assert(functionBodySkipping)
        return when {
            param.type.isInt() -> irConst(0)
            // TODO(lmr): deal with all primitive types
            else -> IrConstImpl(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                param.type,
                IrConstKind.Null,
                null
            )
        }
    }

    // Transform `@Composable fun foo(params): RetType` into `fun foo(params, $composer: Composer): RetType`
    fun IrFunction.withComposerParamIfNeeded(): IrFunction {
        // don't transform functions that themselves were produced by this function. (ie, if we
        // call this with a function that has the synthetic composer parameter, we don't want to
        // transform it further).
        if (transformedFunctionSet.contains(this)) return this

        // if not a composable fn, nothing we need to do
        if (!descriptor.isComposable()) return this

        // emit children lambdas are marked composable, but technically they are unit lambdas... so
        // we don't want to transform them
        if (isEmitInlineChildrenLambda()) return this

        // if this function is an inlined lambda passed as an argument to an inline function (and
        // is NOT a composable lambda), then we don't want to transform it. Ideally, this
        // wouldn't have gotten this far because the `isComposable()` check above should return
        // false, but right now the composable annotation checker seems to produce a
        // false-positive here. It is important that we *DO NOT* transform this, but we should
        // probably fix the annotation checker instead.
        // TODO(b/147250515)
        if (isNonComposableInlinedLambda()) return this

        // we don't bother transforming expect functions. They exist only for type resolution and
        // don't need to be transformed to have a composer parameter
        if (isExpect) return this

        // cache the transformed function with composer parameter
        return transformedFunctions[this] ?: copyWithComposerParam()
    }

    fun IrFunction.lambdaInvokeWithComposerParamIfNeeded(): IrFunction {
        if (transformedFunctionSet.contains(this)) return this
        return transformedFunctions.getOrPut(this) {
            lambdaInvokeWithComposerParam().also { transformedFunctionSet.add(it) }
        }
    }

    fun IrFunction.lambdaInvokeWithComposerParam(): IrFunction {
        val descriptor = descriptor
        val argCount = descriptor.valueParameters.size
        val extraParams = if (functionBodySkipping)
            composeSyntheticParamCount(argCount, hasDefaults = false)
        else
            1
        val newFnClass = context.symbolTable
            .referenceClass(context.builtIns.getFunction(argCount + extraParams))
        val newDescriptor = newFnClass.descriptor.unsubstitutedMemberScope.findFirstFunction(
            OperatorNameConventions.INVOKE.identifier
        ) { true }

        return IrFunctionImpl(
            startOffset,
            endOffset,
            origin,
            newDescriptor,
            newDescriptor.returnType?.toIrType()!!
        ).also { fn ->
            if (!newFnClass.isBound) context.irProviders.getDeclaration(newFnClass)
            fn.parent = newFnClass.owner

            fn.copyTypeParametersFrom(this)
            dispatchReceiverParameter?.type?.let { context.bindIfNeeded(it) }
            fn.dispatchReceiverParameter = dispatchReceiverParameter?.copyTo(fn)
            extensionReceiverParameter?.type?.let { context.bindIfNeeded(it) }
            fn.extensionReceiverParameter = extensionReceiverParameter?.copyTo(fn)
            newDescriptor.valueParameters.forEach { p ->
                fn.addValueParameter(p.name.identifier, p.type.toIrType())
            }
            assert(fn.body == null) { "expected body to be null" }
        }
    }

    private fun wrapDescriptor(descriptor: FunctionDescriptor): WrappedSimpleFunctionDescriptor {
        return when (descriptor) {
            is PropertyGetterDescriptor ->
                WrappedPropertyGetterDescriptor(
                    descriptor.annotations,
                    descriptor.source
                )
            is PropertySetterDescriptor ->
                WrappedPropertySetterDescriptor(
                    descriptor.annotations,
                    descriptor.source
                )
            is DescriptorWithContainerSource ->
                WrappedFunctionDescriptorWithContainerSource(descriptor.containerSource)
            else ->
                WrappedSimpleFunctionDescriptor(sourceElement = descriptor.source)
        }
    }

    private fun IrFunction.copy(
        isInline: Boolean = this.isInline,
        modality: Modality = descriptor.modality
    ): IrSimpleFunction {
        // TODO(lmr): use deepCopy instead?
        val descriptor = descriptor
        val newDescriptor = wrapDescriptor(descriptor)

        return IrFunctionImpl(
            startOffset,
            endOffset,
            origin,
            IrSimpleFunctionSymbolImpl(newDescriptor),
            name,
            visibility,
            modality,
            returnType,
            isInline,
            isExternal,
            descriptor.isTailrec,
            descriptor.isSuspend,
            descriptor.isOperator,
            isExpect,
            isFakeOverride
        ).also { fn ->
            newDescriptor.bind(fn)
            if (this is IrSimpleFunction) {
                fn.correspondingPropertySymbol = correspondingPropertySymbol
            }
            fn.parent = parent
            this.typeParameters.forEach { it.superTypes.forEach {
                if (it is IrSimpleType && !it.classifier.isBound) context.irProviders
                .getDeclaration(it
                .classifier)
            } }
            fn.copyTypeParametersFrom(this)
            generateSymbols(context)
            fn.dispatchReceiverParameter = dispatchReceiverParameter?.copyTo(fn)
            fn.extensionReceiverParameter = extensionReceiverParameter?.copyTo(fn)
            valueParameters.mapTo(fn.valueParameters) { p ->
                // Composable lambdas will always have `IrGet`s of all of their parameters
                // generated, since they are passed into the restart lambda. This causes an
                // interesting corner case with "anonymous parameters" of composable functions.
                // If a parameter is anonymous (using the name `_`) in user code, you can usually
                // make the assumption that it is never used, but this is technically not the
                // case in composable lambdas. The synthetic name that kotlin generates for
                // anonymous parameters has an issue where it is not safe to dex, so we sanitize
                // the names here to ensure that dex is always safe.
                p.type.let { if (it is IrSimpleType && !it.classifier.isBound) context.irProviders
                    .getDeclaration(it
                        .classifier) }
                p.type.let {
                    if (it is IrSimpleType) it.arguments.forEach {
                        if (it is IrTypeProjection) {
                            val tp = it.type
                            if (tp is IrSimpleType && !tp.classifier.isBound) context.irProviders
                                .getDeclaration(tp.classifier)
                        }
                    }
                }
                p.copyTo(fn, name = dexSafeName(p.name))
            }
            annotations.mapTo(fn.annotations) { a -> a }
            fn.metadata = metadata
            fn.body = body?.deepCopyWithSymbols(this)
        }
    }

    private fun dexSafeName(name: Name): Name {
        return if (name.isSpecial && name.asString().contains(' ')) {
            val sanitized = name
                .asString()
                .replace(' ', '$')
                .replace('<', '$')
                .replace('>', '$')
            Name.identifier(sanitized)
        } else name
    }

    private fun jvmNameAnnotation(name: String): IrConstructorCall {
        val jvmName = getTopLevelClass(DescriptorUtils.JVM_NAME)
        val cd = context.moduleDescriptor.findTopLevel(DescriptorUtils.JVM_NAME)
            .unsubstitutedPrimaryConstructor!!
        val type = jvmName.createType(false, emptyList())
        val ctor = context.symbolTable.referenceConstructor(cd)
        return IrConstructorCallImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            type,
            ctor,
            0, 0, 1
        ).also {
            it.putValueArgument(0, IrConstImpl.string(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                builtIns.stringType,
                name
            ))
        }
    }

    private fun IrFunction.copyWithComposerParam(): IrFunction {
        assert(explicitParameters.lastOrNull()?.name != KtxNameConventions.COMPOSER_PARAMETER) {
            "Attempted to add composer param to $this, but it has already been added."
        }
        return copy().also { fn ->
            val oldFn = this

            // NOTE: it's important to add these here before we recurse into the body in
            // order to avoid an infinite loop on circular/recursive calls
            transformedFunctionSet.add(fn)
            transformedFunctions[oldFn] = fn

            // The overridden symbols might also be composable functions, so we want to make sure
            // and transform them as well
            if (this is IrOverridableDeclaration<*>) {
                overriddenSymbols.mapTo(fn.overriddenSymbols) {
                    it as IrSimpleFunctionSymbol
                    val owner = it.owner
                    val newOwner = owner.withComposerParamIfNeeded()
                    newOwner.symbol as IrSimpleFunctionSymbol
                }
            }

            // if we are transforming a composable property, the jvm signature of the
            // corresponding getters and setters have a composer parameter. Since Kotlin uses the
            // lack of a parameter to determine if it is a getter, this breaks inlining for
            // composable property getters since it ends up looking for the wrong jvmSignature.
            // In this case, we manually add the appropriate "@JvmName" annotation so that the
            // inliner doesn't get confused.
            val descriptor = descriptor
            if (descriptor is PropertyGetterDescriptor &&
                fn.annotations.findAnnotation(DescriptorUtils.JVM_NAME) == null
            ) {
                val name = JvmAbi.getterName(descriptor.correspondingProperty.name.identifier)
                fn.annotations.add(jvmNameAnnotation(name))
                fn.correspondingPropertySymbol?.owner?.getter = fn
            }

            // same thing for the setter
            if (descriptor is PropertySetterDescriptor &&
                fn.annotations.findAnnotation(DescriptorUtils.JVM_NAME) == null
            ) {
                val name = JvmAbi.setterName(descriptor.correspondingProperty.name.identifier)
                fn.annotations.add(jvmNameAnnotation(name))
                fn.correspondingPropertySymbol?.owner?.setter = fn
            }

            val valueParametersMapping = explicitParameters
                .zip(fn.explicitParameters)
                .toMap()

            val realParams = fn.valueParameters.size

            val composerParam = fn.addValueParameter(
                KtxNameConventions.COMPOSER_PARAMETER.identifier,
                composerType.makeNullable()
            )

            if (functionBodySkipping) {
                val name = KtxNameConventions.CHANGED_PARAMETER.identifier
                for (i in 0 until changedParamCount(realParams)) {
                    fn.addValueParameter(
                        if (i == 0) name else "$name$i",
                        context.irBuiltIns.intType
                    )
                }
            }

            if (
                functionBodySkipping &&
                // we only add a default mask parameter if one of the parameters has a default
                // expression
                fn.valueParameters.any {
                    it.defaultValue != null
                }
            ) {
                val name = KtxNameConventions.DEFAULT_PARAMETER.identifier
                for (i in 0 until defaultParamCount(realParams)) {
                    fn.addValueParameter(
                        if (i == 0) name else "$name$i",
                        context.irBuiltIns.intType
                    )
                }
            }

            fn.transformChildrenVoid(object : IrElementTransformerVoid() {
                var isNestedScope = false
                override fun visitGetValue(expression: IrGetValue): IrGetValue {
                    val newParam = valueParametersMapping[expression.symbol.owner]
                    return if (newParam != null) {
                        IrGetValueImpl(
                            expression.startOffset,
                            expression.endOffset,
                            expression.type,
                            newParam.symbol,
                            expression.origin
                        )
                    } else expression
                }

                override fun visitReturn(expression: IrReturn): IrExpression {
                    if (expression.returnTargetSymbol == oldFn.symbol) {
                        // update the return statement to point to the new function, or else
                        // it will be interpreted as a non-local return
                        return super.visitReturn(IrReturnImpl(
                            expression.startOffset,
                            expression.endOffset,
                            expression.type,
                            fn.symbol,
                            expression.value
                        ))
                    }
                    return super.visitReturn(expression)
                }

                override fun visitFunction(declaration: IrFunction): IrStatement {
                    val wasNested = isNestedScope
                    try {
                        // we don't want to pass the composer parameter in to composable calls
                        // inside of nested scopes.... *unless* the scope was inlined.
                        isNestedScope = if (declaration.isInlinedLambda()) wasNested else true
                        return super.visitFunction(declaration)
                    } finally {
                        isNestedScope = wasNested
                    }
                }

                override fun visitCall(expression: IrCall): IrExpression {
                    val expr = if (!isNestedScope) {
                        expression.withComposerParamIfNeeded(composerParam).also { call ->
                            if (
                                fn.isInline &&
                                call !== expression &&
                                call.isInlineParameterLambdaInvoke()
                            ) {
                                context.irTrace.record(
                                    ComposeWritableSlices.IS_INLINE_COMPOSABLE_CALL,
                                    call,
                                    true
                                )
                            }
                        }
                    } else
                        expression
                    return super.visitCall(expr)
                }
            })
        }
    }

    fun IrCall.isInlineParameterLambdaInvoke(): Boolean {
        if (origin != IrStatementOrigin.INVOKE) return false
        val lambda = dispatchReceiver as? IrGetValue
        val valueParameter = lambda?.symbol?.owner as? IrValueParameter
        return valueParameter?.isInlineParameter() == true
    }

    fun IrCall.isComposableLambdaInvoke(): Boolean {
        return origin == IrStatementOrigin.INVOKE &&
                dispatchReceiver?.type?.hasComposableAnnotation() == true
    }

    fun IrFunction.isNonComposableInlinedLambda(): Boolean {
        descriptor.findPsi()?.let { psi ->
            (psi as? KtFunctionLiteral)?.let {
                val arg = InlineUtil.getInlineArgumentDescriptor(
                    it,
                    context.bindingContext
                ) ?: return false

                return !arg.type.hasComposableAnnotation()
            }
        }
        return false
    }

    fun IrFunction.isInlinedLambda(): Boolean {
        descriptor.findPsi()?.let { psi ->
            (psi as? KtFunctionLiteral)?.let {
                if (InlineUtil.isInlinedArgument(
                        it,
                        context.bindingContext,
                        false
                    )
                )
                    return true
                if (it.isEmitInline(context.bindingContext)) {
                    return true
                }
            }
        }
        return false
    }

    fun IrFunction.isEmitInlineChildrenLambda(): Boolean {
        descriptor.findPsi()?.let { psi ->
            (psi as? KtFunctionLiteral)?.let {
                if (it.isEmitInline(context.bindingContext)) {
                    return true
                }
            }
        }
        return false
    }

    fun IrFile.remapComposableTypesWithComposerParam() {
        // NOTE(lmr): this operation is somewhat fragile, and the order things are done here is
        // important.
        val originalDeclarations = declarations.toList()

        // The symbolRemapper needs to traverse everything to gather symbols, so we run this first.
        acceptVoid(symbolRemapper)

        // Now that we have all of the symbols, we can clear the existing declarations, since
        // we are going to be putting new versions of them into the file.
        declarations.clear()

        originalDeclarations.mapTo(declarations) { d ->
            val typeRemapper = ComposerTypeRemapper(
                context,
                symbolRemapper,
                typeTranslator,
                composerTypeDescriptor,
                functionBodySkipping
            )
            // for each declaration, we create a deepCopy transformer It is important here that we
            // use the "preserving metadata" variant since we are using this copy to *replace* the
            // originals, or else the module we would produce wouldn't have any metadata in it.
            val transformer = DeepCopyIrTreeWithSymbolsPreservingMetadata(
                context,
                symbolRemapper,
                typeRemapper,
                typeTranslator
            ).also { typeRemapper.deepCopy = it }
            val result = d.transform(
                transformer,
                null
            ) as IrDeclaration
            // just go through and patch all of the parents to make sure things are properly wired
            // up.
            result.patchDeclarationParents(this)
            result
        }
    }
}
