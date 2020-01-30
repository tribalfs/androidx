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
import androidx.compose.plugins.kotlin.hasComposableAnnotation
import androidx.compose.plugins.kotlin.irTrace
import androidx.compose.plugins.kotlin.isEmitInline
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.descriptors.WrappedFunctionDescriptorWithContainerSource
import org.jetbrains.kotlin.backend.common.descriptors.WrappedPropertyGetterDescriptor
import org.jetbrains.kotlin.backend.common.descriptors.WrappedPropertySetterDescriptor
import org.jetbrains.kotlin.backend.common.descriptors.WrappedSimpleFunctionDescriptor
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.ir.copyTypeParametersFrom
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PropertyAccessorDescriptor
import org.jetbrains.kotlin.descriptors.PropertyGetterDescriptor
import org.jetbrains.kotlin.descriptors.PropertySetterDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptorImpl
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrOverridableDeclaration
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.copyAttributes
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
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
import org.jetbrains.kotlin.ir.types.createType
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.DeepCopySymbolRemapper
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.explicitParameters
import org.jetbrains.kotlin.ir.util.findAnnotation
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi2ir.findFirstFunction
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DescriptorWithContainerSource
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.util.OperatorNameConventions

private const val DEBUG_LOG = false

class ComposerParamTransformer(
    context: JvmBackendContext,
    symbolRemapper: DeepCopySymbolRemapper
) :
    AbstractComposeLowering(context, symbolRemapper),
    FileLoweringPass,
    ModuleLoweringPass {

    override fun lower(module: IrModuleFragment) {
        module.transformChildrenVoid(this)

        module.acceptVoid(symbolRemapper)

        val typeRemapper = ComposerTypeRemapper(
            context,
            symbolRemapper,
            typeTranslator,
            composerTypeDescriptor
        )
        // for each declaration, we create a deepCopy transformer It is important here that we
        // use the "preserving metadata" variant since we are using this copy to *replace* the
        // originals, or else the module we would produce wouldn't have any metadata in it.
        val transformer = DeepCopyIrTreeWithSymbolsPreservingMetadata(
            context,
            symbolRemapper,
            typeRemapper
        ).also { typeRemapper.deepCopy = it }
        module.transformChildren(
            transformer,
            null
        )
        // just go through and patch all of the parents to make sure things are properly wired
        // up.
        module.patchDeclarationParents()
    }

    private val transformedFunctions: MutableMap<IrFunction, IrFunction>
        get() = context.suspendFunctionViews

    private val transformedFunctionSet = mutableSetOf<IrFunction>()

    private val composerType = composerTypeDescriptor.defaultType.toIrType()

    override fun lower(irFile: IrFile) {
        if (DEBUG_LOG) {
            println("""
                =========
                BEFORE
                =========
            """.trimIndent())
            println(irFile.dump())
        }
        irFile.transform(this, null)
        if (DEBUG_LOG) {
            println("""
                =========
                AFTER TRANSFORM
                =========
            """.trimIndent())
            println(irFile.dump())
        }
        irFile.remapComposableTypesWithComposerParam()
        if (DEBUG_LOG) {
            println("""
                =========
                AFTER TYPE REMAPPING
                =========
            """.trimIndent())
            println(irFile.dump())
        }
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        return super.visitFunction(declaration.withComposerParamIfNeeded())
    }

    override fun visitFile(declaration: IrFile): IrFile {
        val originalFunctions = mutableListOf<IrFunction>()
        val originalProperties = mutableListOf<Pair<IrProperty, IrSimpleFunction>>()
        loop@for (child in declaration.declarations) {
            when (child) {
                is IrFunction -> originalFunctions.add(child)
                is IrProperty -> {
                    val getter = child.getter ?: continue@loop
                    originalProperties.add(child to getter)
                }
            }
        }
        val result = super.visitFile(declaration)
        result.patchWithSyntheticComposableDecoys(originalFunctions, originalProperties)
        return result
    }

    override fun visitClass(declaration: IrClass): IrStatement {
        val originalFunctions = declaration.functions.toList()
        val originalProperties = declaration
            .properties
            .mapNotNull { p -> p.getter?.let { p to it } }
            .toList()
        val result = super.visitClass(declaration)
        if (result !is IrClass) error("expected IrClass")
        result.patchWithSyntheticComposableDecoys(originalFunctions, originalProperties)
        return result
    }

    fun IrDeclarationContainer.patchWithSyntheticComposableDecoys(
        originalFunctions: List<IrFunction>,
        originalProperties: List<Pair<IrProperty, IrSimpleFunction>>
    ) {
        for (function in originalFunctions) {
            if (transformedFunctions.containsKey(function) && function.isComposable()) {
                declarations.add(function.copyAsComposableDecoy())
            }
        }
        for ((property, getter) in originalProperties) {
            if (transformedFunctions.containsKey(getter) && property.hasComposableAnnotation()) {
                val newGetter = property.getter
                assert(getter !== newGetter)
                assert(newGetter != null)
                // NOTE(lmr): the compiler seems to turn a getter with a single parameter into a
                // setter, even though it's in the "getter" position. As a result, we will put
                // the original parameter-less getter in the "getter" position, and add the
                // single-parameter getter to the class itself.
                property.getter = getter.copyAsComposableDecoy().also { it.parent = this }
                declarations.add(newGetter!!)
                newGetter.parent = this
            }
        }
    }

    fun IrCall.withComposerParamIfNeeded(composerParam: IrValueParameter): IrCall {
        val isComposableLambda = isComposableLambdaInvoke()
        if (!descriptor.isComposable() && !isComposableLambda)
            return this
        val ownerFn = when {
            isComposableLambda ->
                (symbol.owner as IrSimpleFunction).lambdaInvokeWithComposerParamIfNeeded()
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
            ownerFn.symbol.descriptor,
            typeArgumentsCount,
            valueArgumentsCount + 1, // +1 for the composer param
            origin,
            superQualifierSymbol
        ).also {
            it.copyAttributes(this)
            context.state.irTrace.record(
                ComposeWritableSlices.IS_COMPOSABLE_CALL,
                it,
                true
            )
            it.copyTypeArgumentsFrom(this)
            it.dispatchReceiver = dispatchReceiver
            it.extensionReceiver = extensionReceiver
            for (i in 0 until valueArgumentsCount) {
                it.putValueArgument(i, getValueArgument(i))
            }
            it.putValueArgument(
                valueArgumentsCount,
                IrGetValueImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    composerParam.symbol
                )
            )
        }
    }

    // Transform `@Composable fun foo(params): RetType` into `fun foo(params, $composer: Composer): RetType`
    fun IrFunction.withComposerParamIfNeeded(): IrFunction {
        // don't transform functions that themselves were produced by this function. (ie, if we
        // call this with a function that has the synthetic composer parameter, we don't want to
        // transform it further).
        if (transformedFunctionSet.contains(this)) return this

        if (origin == COMPOSABLE_DECOY_IMPL) return this

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
        val newFnClass = context.irIntrinsics.symbols.getFunction(argCount + 1)
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
            fn.parent = newFnClass.owner

            fn.copyTypeParametersFrom(this)
            fn.dispatchReceiverParameter = dispatchReceiverParameter?.copyTo(fn)
            fn.extensionReceiverParameter = extensionReceiverParameter?.copyTo(fn)
            newDescriptor.valueParameters.forEach { p ->
                fn.addValueParameter(p.name.identifier, p.type.toIrType())
            }
            assert(fn.body == null) { "expected body to be null" }
        }
    }

    private fun IrFunction.copyAsComposableDecoy(): IrSimpleFunction {
        return copy().also { fn ->
            fn.origin = COMPOSABLE_DECOY_IMPL
            (fn as IrFunctionImpl).metadata = metadata
            val errorClass = getTopLevelClass(FqName("kotlin.NotImplementedError"))
            val errorCtor = errorClass.owner.constructors.single {
                it.valueParameters.size == 1 &&
                        it.valueParameters.single().type.isString()
            }
            // the decoy cannot have default expressions in its parameters, since they might be
            // composable and if they are, it wouldn't have a composer param to use
            fn.valueParameters.clear()
            valueParameters.mapTo(fn.valueParameters) { p -> p.copyTo(fn, defaultValue = null) }
            fn.body = context.createIrBuilder(fn.symbol).irBlockBody {
                +irThrow(
                    irCall(errorCtor).apply {
                        putValueArgument(
                            0,
                            irString(
                                "Composable functions cannot be called without a " +
                                        "composer. If you are getting this error, it " +
                                        "is likely because of a misconfigured compiler"
                            )
                        )
                    }
                )
            }
        }
    }

    private fun wrapDescriptor(descriptor: FunctionDescriptor) : WrappedSimpleFunctionDescriptor {
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
            descriptor.isSuspend
        ).also { fn ->
            newDescriptor.bind(fn)
            if (this is IrSimpleFunction) {
                fn.correspondingPropertySymbol = correspondingPropertySymbol
            }
            fn.parent = parent
            fn.copyTypeParametersFrom(this)
            fn.dispatchReceiverParameter = dispatchReceiverParameter?.copyTo(fn)
            fn.extensionReceiverParameter = extensionReceiverParameter?.copyTo(fn)
            valueParameters.mapTo(fn.valueParameters) { p -> p.copyTo(fn) }
            annotations.mapTo(fn.annotations) { a -> a }
            fn.body = body?.deepCopyWithSymbols(this)
        }
    }

    private fun jvmNameAnnotation(name: String): IrConstructorCall {
        val jvmName = getTopLevelClass(DescriptorUtils.JVM_NAME)
        val type = jvmName.createType(false, emptyList())
        val ctor = jvmName.constructors.first()
        return IrConstructorCallImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            type,
            ctor,
            ctor.descriptor,
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
            }

            // same thing for the setter
            if (descriptor is PropertySetterDescriptor &&
                fn.annotations.findAnnotation(DescriptorUtils.JVM_NAME) == null
            ) {
                val name = JvmAbi.setterName(descriptor.correspondingProperty.name.identifier)
                fn.annotations.add(jvmNameAnnotation(name))
            }

            val valueParametersMapping = explicitParameters
                .zip(fn.explicitParameters)
                .toMap()

            val composerParam = fn.addValueParameter(
                KtxNameConventions.COMPOSER_PARAMETER.identifier,
                composerType
            )
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
                    val expr = if (!isNestedScope)
                        expression.withComposerParamIfNeeded(composerParam)
                    else
                        expression
                    return super.visitCall(expr)
                }
            })
        }
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
                    context.state.bindingContext
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
                        context.state.bindingContext,
                        false
                    )
                )
                    return true
                if (it.isEmitInline(context.state.bindingContext)) {
                    return true
                }
            }
        }
        return false
    }

    fun IrFunction.isEmitInlineChildrenLambda(): Boolean {
        descriptor.findPsi()?.let { psi ->
            (psi as? KtFunctionLiteral)?.let {
                if (it.isEmitInline(context.state.bindingContext)) {
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
                composerTypeDescriptor
            )
            // for each declaration, we create a deepCopy transformer It is important here that we
            // use the "preserving metadata" variant since we are using this copy to *replace* the
            // originals, or else the module we would produce wouldn't have any metadata in it.
            val transformer = DeepCopyIrTreeWithSymbolsPreservingMetadata(
                context,
                symbolRemapper,
                typeRemapper
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

internal val COMPOSABLE_DECOY_IMPL =
    object : IrDeclarationOriginImpl("COMPOSABLE_DECOY_IMPL", isSynthetic = true) {}