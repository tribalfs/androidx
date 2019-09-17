/*
 * Copyright 2018 The Android Open Source Project
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

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.AnonymousFunctionDescriptor
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionReferenceImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.ConstantValueGenerator
import org.jetbrains.kotlin.ir.util.TypeTranslator
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import androidx.compose.plugins.kotlin.ComposableAnnotationChecker
import androidx.compose.plugins.kotlin.ComposeUtils.generateComposePackageName
import androidx.compose.plugins.kotlin.KtxNameConventions.UPDATE_SCOPE
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import androidx.compose.plugins.kotlin.analysis.ComposeWritableSlices
import androidx.compose.plugins.kotlin.getKeyValue
import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.backend.common.lower.irNot
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irEqeqeq
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTryImpl
import org.jetbrains.kotlin.ir.util.referenceFunction
import org.jetbrains.kotlin.resolve.DelegatingBindingTrace
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isUnit

class ComposeObservePatcher(val context: JvmBackendContext) :
    IrElementTransformerVoid(),
    FileLoweringPass {

    private val typeTranslator =
        TypeTranslator(
            context.ir.symbols.externalSymbolTable,
            context.state.languageVersionSettings,
            context.builtIns
        ).apply {
            constantValueGenerator = ConstantValueGenerator(
                context.state.module,
                context.ir .symbols.externalSymbolTable
            )
            constantValueGenerator.typeTranslator = this
        }

    private fun KotlinType.toIrType(): IrType = typeTranslator.translateType(this)

    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid(this)
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {

        super.visitFunction(declaration)

        // Only insert observe scopes in non-empty composable function
        if (declaration.body == null) return declaration
        if (!isComposable(declaration)) return declaration

        val descriptor = declaration.descriptor

        // Do not insert observe scope in an inline  function
        if (descriptor.isInline) return declaration

        // Do not insert an observe scope in an inline composable lambda
        descriptor.findPsi()?.let { psi ->
            (psi as? KtFunctionLiteral)?.let {
                if (InlineUtil.isInlinedArgument(
                        it,
                        context.state.bindingContext,
                        true
                    )
                )
                    return declaration
            }
        }

        // Do not insert an observe scope if the funciton has a return result
        if (descriptor.returnType.let { it == null || !it.isUnit() }) return declaration

        // Check if the descriptor has restart scope calls resolved
        val bindingContext = context.state.bindingContext
        if (descriptor is SimpleFunctionDescriptor) {
            val restartCalls = bindingContext.get(ComposeWritableSlices.RESTART_CALLS, descriptor)

            val oldBody = declaration.body
            if (restartCalls != null && oldBody != null) {
                val composerResolvedCall = restartCalls.composer
                val symbols = context.ir.symbols
                val symbolTable = symbols.externalSymbolTable

                // Create call to get the composer
                val unitType = context.irBuiltIns.unitType
                val composer = irGetProperty(composerResolvedCall)

                // Create call to startRestartGroup
                val startRestartGroup = irMethodCall(composer, restartCalls.startRestartGroup)
                    .apply {
                        putValueArgument(0,
                            keyExpression(
                                descriptor,
                                declaration.startOffset,
                                context.builtIns.intType.toIrType()
                            )
                        )
                    }

                // Create call to endRestartGroup
                val endRestartGroup = irMethodCall(composer, restartCalls.endRestartGroup)

                // Create self-invoke lambda
                val endRestartGroupResolvedCall = restartCalls.endRestartGroup
                val endRestartGroupDescriptor = (
                        endRestartGroupResolvedCall.resultingDescriptor as? FunctionDescriptor
                    ) ?: error("Expected function descriptor")
                val updateScopeDescriptor =
                    endRestartGroupDescriptor.returnType?.memberScope?.getContributedFunctions(
                        UPDATE_SCOPE,
                        NoLookupLocation.FROM_BACKEND
                    )?.singleOrNull()
                        ?: error("updateScope not found in result type of endRestartGroup")
                val blockParameterDescriptor = updateScopeDescriptor.valueParameters.singleOrNull()
                    ?: error("expected a single block parameter for updateScope")
                val blockParameterType = blockParameterDescriptor.type
                val selfSymbol = declaration.symbol

                val lambdaDescriptor = AnonymousFunctionDescriptor(
                    declaration.descriptor,
                    Annotations.EMPTY,
                    CallableMemberDescriptor.Kind.DECLARATION,
                    SourceElement.NO_SOURCE,
                    false
                ).apply {
                    initialize(
                        null,
                        null,
                        emptyList(),
                        emptyList(),
                        blockParameterType,
                        Modality.FINAL,
                        Visibilities.LOCAL
                    )
                }

                val fn = IrFunctionImpl(
                    UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                    IrDeclarationOrigin.LOCAL_FUNCTION_NO_CLOSURE,
                    IrSimpleFunctionSymbolImpl(lambdaDescriptor),
                    context.irBuiltIns.unitType
                ).also {
                    val irBuilder = context.createIrBuilder(it.symbol)
                    it.body = irBuilder.irBlockBody {
                        // Call the function again with the same parameters
                        +irReturn(irCall(selfSymbol).apply {
                            descriptor.valueParameters.forEachIndexed { index, valueParameter ->
                                val value = declaration.valueParameters[index].symbol
                                putValueArgument(
                                    index, IrGetValueImpl(
                                        UNDEFINED_OFFSET,
                                        UNDEFINED_OFFSET,
                                        valueParameter.type.toIrType(),
                                        value
                                    )
                                )
                            }
                            descriptor.dispatchReceiverParameter?.let { receiverDescriptor ->
                                dispatchReceiver = irGet(receiverDescriptor.type.toIrType(),
                                    declaration.dispatchReceiverParameter?.symbol
                                        ?: error("Expected dispatch receiver on declaration")
                                )
                            }
                            descriptor.extensionReceiverParameter?.let { receiverDescriptor ->
                                extensionReceiver = irGet(receiverDescriptor.type.toIrType(),
                                    declaration.extensionReceiverParameter?.symbol
                                        ?: error("Expected extension receiver on declaration")
                                )
                            }
                            descriptor.typeParameters.forEachIndexed { index, descriptor ->
                                putTypeArgument(index, descriptor.defaultType.toIrType())
                            }
                        })
                    }
                }

                val irBuilder = context.createIrBuilder(declaration.symbol)
                val endRestartGroupCallBlock = irBuilder.irBlock(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET
                ) {
                    val result = irTemporary(endRestartGroup)
                    val updateScopeSymbol =
                        symbolTable.referenceSimpleFunction(updateScopeDescriptor)
                    +fn
                    +irIfThen(irNot(irEqeqeq(irGet(result.type, result.symbol), irNull())),
                        irCall(updateScopeSymbol).apply {
                            dispatchReceiver = irGet(result.type, result.symbol)
                            putValueArgument(0,
                                IrFunctionReferenceImpl(
                                    UNDEFINED_OFFSET,
                                    UNDEFINED_OFFSET,
                                    blockParameterType.toIrType(),
                                    fn.symbol,
                                    fn.descriptor,
                                    0,
                                    IrStatementOrigin.IR_TRANSFORM
                                )
                            )
                        }
                    )
                }

                when (oldBody) {
                    is IrBlockBody -> {
                        // Transform the block into
                        // composer.startRestartGroup()
                        // try {
                        //   ... old statements ...
                        // } finally {
                        //    composer.endRestartGroup()
                        // }
                        declaration.body = irBuilder.irBlockBody {
                            +IrTryImpl(
                                oldBody.startOffset, oldBody.endOffset, unitType,
                                IrBlockImpl(
                                    UNDEFINED_OFFSET,
                                    UNDEFINED_OFFSET,
                                    unitType
                                ).apply {
                                    statements.add(startRestartGroup)
                                    statements.addAll(oldBody.statements)
                                },
                                catches = emptyList(),
                                finallyExpression = endRestartGroupCallBlock
                            )
                        }
                        return declaration
                    }
                    else -> {
                        // Composable function do not use IrExpressionBody as they are converted
                        // by the call lowering to IrBlockBody to introduce the call temporaries.
                    }
                }
            }
        }

        // Otherwise, fallback to wrapping the code block in a call to Observe()
        val module = descriptor.module
        val observeFunctionDescriptor = module
            .getPackage(FqName(generateComposePackageName()))
            .memberScope
            .getContributedFunctions(
                Name.identifier("Observe"),
                NoLookupLocation.FROM_BACKEND
            ).single()

        val symbolTable = context.ir.symbols.externalSymbolTable

        val observeFunctionSymbol = symbolTable.referenceSimpleFunction(observeFunctionDescriptor)

        val type = observeFunctionDescriptor.valueParameters[0].type

        val lambdaDescriptor = AnonymousFunctionDescriptor(
            declaration.descriptor,
            Annotations.EMPTY,
            CallableMemberDescriptor.Kind.DECLARATION,
            SourceElement.NO_SOURCE,
            false
        ).apply {
            initialize(
                null,
                null,
                emptyList(),
                emptyList(),
                type,
                Modality.FINAL,
                Visibilities.LOCAL
            )
        }

        val irBuilder = context.createIrBuilder(declaration.symbol)

        return declaration.apply {
            body = irBuilder.irBlockBody {
                val fn = IrFunctionImpl(
                    UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                    IrDeclarationOrigin.LOCAL_FUNCTION_NO_CLOSURE,
                    IrSimpleFunctionSymbolImpl(lambdaDescriptor),
                    context.irBuiltIns.unitType
                ).also { fn ->
                    fn.body = declaration.body.apply {
                        // Move the target for the returns to avoid introducing a non-local return.
                        transformChildrenVoid(object : IrElementTransformerVoid() {
                            override fun visitReturn(expression: IrReturn): IrExpression {
                                if (expression.returnTargetSymbol === declaration.symbol) {
                                    return IrReturnImpl(
                                        startOffset = expression.startOffset,
                                        endOffset = expression.endOffset,
                                        type = expression.type,
                                        returnTargetSymbol = fn.symbol,
                                        value = expression.value
                                    )
                                }
                                return expression
                            }
                        })
                    }
                }
                +fn
                +irCall(
                    observeFunctionSymbol,
                    observeFunctionDescriptor,
                    context.irBuiltIns.unitType
                ).also {
                    it.putValueArgument(
                        0, IrFunctionReferenceImpl(
                            UNDEFINED_OFFSET,
                            UNDEFINED_OFFSET,
                            type.toIrType(),
                            fn.symbol,
                            fn.descriptor,
                            0,
                            IrStatementOrigin.IR_TRANSFORM
                        )
                    )
                }
            }
        }
    }

    fun irCall(descriptor: FunctionDescriptor): IrCall {
        val type = descriptor.returnType?.toIrType() ?: error("Expected a return type")
        val symbol = context.ir.symbols.externalSymbolTable.referenceFunction(descriptor)
        return IrCallImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            type,
            symbol,
            descriptor
        )
    }

    fun irGetProperty(resolvedCall: ResolvedCall<*>): IrCall {
        val descriptor = (resolvedCall.resultingDescriptor as? PropertyDescriptor)?.getter
            ?: error("Expected property")
        return irCall(descriptor)
    }

    fun irMethodCall(target: IrExpression, resolvedCall: ResolvedCall<*>): IrCall {
        val descriptor = (resolvedCall.resultingDescriptor as?
                FunctionDescriptor) ?: error("Expected function descriptor")
        return irCall(descriptor).apply {
            dispatchReceiver = target
        }
    }

    private fun isComposable(declaration: IrFunction): Boolean {
        val tmpTrace =
            DelegatingBindingTrace(
                context.state.bindingContext, "tmp for composable analysis"
            )
        val composability =
            ComposableAnnotationChecker(ComposableAnnotationChecker.Mode.KTX_CHECKED)
                .analyze(
                tmpTrace,
                declaration.descriptor
            )
        return when (composability) {
            ComposableAnnotationChecker.Composability.NOT_COMPOSABLE -> false
            ComposableAnnotationChecker.Composability.MARKED -> true
            ComposableAnnotationChecker.Composability.INFERRED -> true
        }
    }

    fun keyExpression(
        descriptor: CallableMemberDescriptor,
        sourceOffset: Int,
        intType: IrType
    ): IrExpression {
        val sourceKey = getKeyValue(descriptor, sourceOffset)
        return IrConstImpl.int(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            intType,
            sourceKey
        )
    }
}
