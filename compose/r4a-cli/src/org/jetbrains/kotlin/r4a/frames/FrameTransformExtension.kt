package org.jetbrains.kotlin.r4a.frames

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ClassConstructorDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.LazyClassReceiverParameterDescriptor
import org.jetbrains.kotlin.descriptors.impl.PackageFragmentDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrKtxStatement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.toIrType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtxElement
import org.jetbrains.kotlin.psi2ir.extensions.SyntheticIrExtension
import org.jetbrains.kotlin.psi2ir.generators.GeneratorContext
import org.jetbrains.kotlin.psi2ir.generators.StatementGenerator
import org.jetbrains.kotlin.r4a.frames.analysis.FrameMetadata
import org.jetbrains.kotlin.r4a.frames.analysis.FrameWritableSlices.FRAMED_DESCRIPTOR
import org.jetbrains.kotlin.r4a.frames.analysis.FrameWritableSlices.RECORD_CLASS
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.scopes.MemberScopeImpl
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.utils.Printer
import javax.rmi.CORBA.ClassDesc

/**
 * The frame transform extension transforms a "framed" classes properties into the form expected by the frames runtime.
 * The transformation is:
 *   - Move the backing fields for public properties from the class itself to a value record.
 *   - Change the property initializers to initialize the value record.
 *   - Change the public property getters and setters to get the current frame record and set or get the value from that
 *     record.
 *
 * The frame runtime will which value record is current for the frame.
 */
class FrameTransformExtension : SyntheticIrExtension {
    override fun interceptModuleFragment(context: GeneratorContext, ktFiles: Collection<KtFile>, irModuleFragment: IrModuleFragment) {
        // Transform any class derived from Component into a framed object

        fun processFramedClass(irClass: IrClass): IrClass {
            val className = irClass.descriptor.fqNameSafe
            val recordDescriptor = context.bindingContext.get(RECORD_CLASS, className) ?: return irClass
            // If there are no properties, skip the class
            if (!irClass.anyChild { it is IrProperty }) return irClass

            val file = irModuleFragment.find { it is IrFile && it.anyChild { it == irClass } }.single() as IrFile

            val recordClassInfo = addFramedStateRecord(context, file, recordDescriptor)
            return augmentFramedClass(context, className, irClass, recordDescriptor, recordClassInfo)
        }

        val transformer = object : IrElementTransformer<Nothing?> {
            override fun visitClass(declaration: IrClass, data: Nothing?): IrStatement =
                super.visitClass(processFramedClass(declaration), data)
        }

        irModuleFragment.find { it is IrFile }.forEach { irFile ->
            irFile.transformChildren(transformer, null)
        }
    }
}

class RecordClassInfo(
    val irClass: IrClass,
    val fields: List<IrField>,
    val constructorSymbol: IrConstructorSymbol,
    recordTypeDescriptor: ClassDescriptor
)

fun addFramedStateRecord(context: GeneratorContext, file: IrFile, recordClassDescriptor: FrameRecordClassDescriptor): RecordClassInfo {
    val recordTypeDescriptor = context.moduleDescriptor.findClassAcrossModuleDependencies(ClassId.topLevel(recordClassName))
            ?: error("Cannot find the Record class")


    val fields = mutableListOf<IrField>()
    var constructor: IrConstructor? = null

    // Declare the state record
    val recordClass = context.symbolTable.declareSyntheticClass(recordClassDescriptor).buildWithScope(context) { recordClass ->

        recordClass.thisReceiver = context.symbolTable.declareValueParameter(
            -1, -1,
            IrDeclarationOrigin.INSTANCE_RECEIVER,
            recordClassDescriptor.thisAsReceiverParameter,
            recordClassDescriptor.thisAsReceiverParameter.type.toIrType()!!
        )

        // Create the state record constructor
        val recordConstructorDescriptor =
            recordClassDescriptor.unsubstitutedPrimaryConstructor ?: error("Record class constructor not found")
        constructor = context.symbolTable.declareSyntheticConstructor(recordConstructorDescriptor).buildWithScope(context) { constructor ->
            constructor.createParameterDeclarations()

            // Call the ancestor constructor
            val superConstructor =
                context.symbolTable.referenceConstructor(recordClassDescriptor.getSuperClassNotAny()!!.constructors.single())
            val superCall = syntheticConstructorDelegatingCall(superConstructor, superConstructor.descriptor)

            // Fields are left uninitialized as they will be set either by the framed object's constructor or by a call to assign()

            constructor.body = syntheticBlockBody(listOf(superCall))
        }
        recordClass.declarations.add(constructor!!)

        fun toRecord(expr: IrExpression) = syntheticImplicitCast(
            recordTypeDescriptor.defaultType,
            recordClassDescriptor.defaultType.toIrType()!!,
            context.symbolTable.referenceClassifier(recordClassDescriptor), expr
        )

        // Create fields corresponding to the attributes
        val attributes = recordClassDescriptor.unsubstitutedMemberScope.getVariableNames()
        for (attribute in attributes) {
            val member = recordClassDescriptor.unsubstitutedMemberScope.getContributedVariables(
                attribute,
                NoLookupLocation.FROM_BACKEND
            ).single()
            val field = context.symbolTable.declareSyntheticField(member)
            recordClass.declarations.add(field)
            fields.add(field)
        }

        // Create the create method
        createMethod(Name.identifier("create"), context, recordClassDescriptor, recordClass) {
            val recordConstructorSymbol = constructor!!.symbol
            val callConstructor = syntheticCall(
                recordClassDescriptor.defaultType.toIrType()!!,
                recordConstructorSymbol,
                recordConstructorDescriptor
            )
            syntheticExpressionBody(callConstructor)
        }


        // Create the apply method
        createMethod(Name.identifier("assign"), context, recordClassDescriptor, recordClass) { irFunction ->
            val statements = mutableListOf<IrStatement>()
            val thisParameterSymbol = irFunction.dispatchReceiverParameter!!.symbol
            val valueParameterSymbol = irFunction.valueParameters[0].symbol
            for (i in 0 until attributes.count()) {
                val field = fields[i]
                val fieldSymbol = field.symbol
                val thisParameter = syntheticGetValue(thisParameterSymbol)
                val valueParameter = toRecord(syntheticGetValue(valueParameterSymbol))
                val otherField = syntheticGetField(fieldSymbol, valueParameter)
                statements.add(syntheticSetField(fieldSymbol, thisParameter, otherField))
            }
            syntheticBlockBody(statements)
        }
        file.declarations.add(recordClass)

    }
    return RecordClassInfo(recordClass, fields, constructor!!.symbol, recordTypeDescriptor)
}

fun addField(
    context: GeneratorContext,
    framedClass: IrClass,
    framedDescriptor: ClassDescriptor,
    recordDescriptor: ClassDescriptor
): IrFieldSymbol {
    val recordType = recordDescriptor.defaultType
    val name = Name.identifier("\$record")
    val descriptor = PropertyDescriptorImpl.create(
        /* containingDeclaration = */ framedDescriptor,
        /* annotations           = */ Annotations.EMPTY,
        /* modality              = */ Modality.FINAL,
        /* visibility            = */ Visibilities.PRIVATE,
        /* isVar                 = */ true,
        /* name                  = */ name,
        /* kind                  = */ CallableMemberDescriptor.Kind.DECLARATION,
        /* source                = */ SourceElement.NO_SOURCE,
        /* lateInit              = */ false,
        /* isConst               = */ false,
        /* isExpect              = */ false,
        /* isActual                = */ false,
        /* isExternal            = */ false,
        /* isDelegated           = */ false
    ).apply {
        initialize(null, null)

        setType(recordType, emptyList(), recordDescriptor.thisAsReceiverParameter, null)
    }
    val field = context.symbolTable.declareSyntheticField(descriptor)
    framedClass.declarations.add(field)
    return context.symbolTable.referenceField(descriptor)
}

fun addFramedImpl(
    context: GeneratorContext,
    framedClass: IrClass,
    framedDescriptor: ClassDescriptor,
    recordTypeDescriptor: ClassDescriptor,
    framedType: ClassDescriptor,
    metadata: FrameMetadata
): IrFieldSymbol {
    // Declare the $record field to store the record
    val fieldReference = addField(context, framedClass, framedDescriptor, recordTypeDescriptor)


    // Declare function getFirstFrameRecord
    createMethod(metadata.firstFrameDescriptor(recordTypeDescriptor), context, framedClass) { irFunction ->
        val dispatchReceiver = syntheticGetValue(irFunction.dispatchReceiverParameter!!.symbol)

        syntheticExpressionBody(syntheticGetField(fieldReference, dispatchReceiver))
    }

    // Declare the prependFrameRecord method
    createMethod(metadata.prependFrameRecordDescriptor(recordTypeDescriptor), context, framedClass) { irFunction ->
        val dispatchReceiver = syntheticGetValue(irFunction.dispatchReceiverParameter!!.symbol)


        val nextField = recordTypeDescriptor.unsubstitutedMemberScope.getContributedVariables(
            Name.identifier("next"),
            NoLookupLocation.FROM_BACKEND
        ).singleOrNull() ?: error("Could not find Record.next field")
        val setNextSymbol = context.symbolTable.referenceFunction(nextField.setter ?: error("Expected setter for Record.next"))
        val valueParameter = irFunction.valueParameters[0]
        val valueParameterSymbol = valueParameter.symbol
        val value = syntheticGetValue(valueParameterSymbol)


        syntheticBlockBody(
            listOf(
                syntheticSetterCall(
                    setNextSymbol,
                    nextField.setter!!,
                    value,
                    syntheticGetField(fieldReference, dispatchReceiver)
                ),
                syntheticSetField(
                    fieldReference,
                    dispatchReceiver,
                    value
                )
            )
        )
    }

    // Add the framed interface to the list of the framed classes interfaces
    framedClass.superTypes.add(framedType.defaultType.toIrType()!!)

    return fieldReference
}

fun augmentInterfaceList(original: ClassDescriptor, addedInterface: ClassDescriptor): ClassDescriptor =
    object : ClassDescriptor by original {
        override fun getTypeConstructor(): TypeConstructor = object : TypeConstructor by original.typeConstructor {
            override fun getSupertypes(): Collection<KotlinType> =
                original.typeConstructor.supertypes + addedInterface.defaultType
        }
    }

fun augmentClassWithInterface(irClass: IrClass, interfaceDescriptor: ClassDescriptor): IrClass = object : IrClass by irClass {
    val newDescriptor by lazy {
        augmentInterfaceList(irClass.descriptor, interfaceDescriptor)
    }

    override val descriptor: ClassDescriptor get() = newDescriptor

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R {
        val result = irClass.accept(visitor, data)
        @Suppress("UNCHECKED_CAST")
        return if (result === irClass) this as R else result
    }

    override fun <D> transform(transformer: IrElementTransformer<D>, data: D): IrStatement {
        val result = irClass.transform(transformer, data)
        if (result == irClass) return this
        return result
    }
}

fun augmentFramedClass(
    context: GeneratorContext,
    name: FqName,
    framedClass: IrClass,
    recordDescriptor: ClassDescriptor,
    recordClassInfo: RecordClassInfo
): IrClass {
    val framedDescriptor = context.bindingContext.get(FRAMED_DESCRIPTOR, name) ?: error("Could not find framed class descriptor")
    val metadata = FrameMetadata(framedDescriptor)
    val recordTypeDescriptor = context.moduleDescriptor.findClassAcrossModuleDependencies(ClassId.topLevel(recordClassName))
            ?: error("Cannot find the Record class")

    val thisSymbol = framedClass.thisReceiver?.symbol ?: error("No this receiver found for class ${framedClass.name}")
    val thisValue = syntheticGetValue(thisSymbol)

    var result = framedClass

    val framedType = context.moduleDescriptor.findClassAcrossModuleDependencies(ClassId.topLevel(framedTypeName))
            ?: error("Cannot find the Framed interface")

    val field = addFramedImpl(context, framedClass, framedDescriptor, recordTypeDescriptor, framedType, metadata)
    result = augmentClassWithInterface(result, framedType)
    val recordGetter = syntheticGetField(field, thisValue)
    val recordSetter = { value: IrExpression -> syntheticSetField(field, thisValue, value) }

    fun toRecord(expr: IrExpression) = syntheticImplicitCast(
        recordTypeDescriptor.defaultType,
        recordDescriptor.defaultType.toIrType()!!,
        context.symbolTable.referenceClassifier(recordDescriptor), expr
    )

    fun getRecord() = toRecord(recordGetter)
    val framesPackageDescriptor = context.moduleDescriptor.getPackage(framesPackageName)
    val createdDescriptor =
        framesPackageDescriptor.memberScope.getContributedFunctions(
            Name.identifier("_created"),
            NoLookupLocation.FROM_BACKEND
        ).single()
    val createdSymbol = context.symbolTable.referenceSimpleFunction(createdDescriptor)

    // Move property initializer to an anonymous initializer as the backing field is moved to the record
    context.symbolTable.declareAnonymousInitializer(UNDEFINED_OFFSET, UNDEFINED_OFFSET, IrDeclarationOrigin.DEFINED, framedDescriptor)
        .buildWithScope(context) { irInitializer ->
            val statements = mutableListOf<IrStatement>()

            // Create the initial state record
            statements.add(
                recordSetter(
                    syntheticCall(
                        recordDescriptor.defaultType.toIrType()!!,
                        recordClassInfo.constructorSymbol,
                        // Non-null was already validated when the record class was constructed
                        recordDescriptor.unsubstitutedPrimaryConstructor!!
                    )
                )
            )

            // Assign the fields
            metadata.getFramedProperties(context.bindingContext).forEach { propertyDescriptor ->
                // Move backing field initializer to an anonymous initializer of the record field
                val irFramedProperty = framedClass.declarations.find { it.descriptor.name == propertyDescriptor.name } as? IrProperty
                        ?: error("Could not find ir representation of ${propertyDescriptor.name}")
                val irRecordField =
                    recordClassInfo.fields.find { it.name == propertyDescriptor.name } ?: error("Could not find record field for $name")
                val backingField = irFramedProperty.backingField ?: TODO("Properties without a backing field are not supported yet")
                backingField.initializer?.let { initializer ->
                    // (this.next as <record>).<field> = <initializer>
                    statements.add(syntheticSetField(irRecordField.symbol, getRecord(), initializer.expression))
                }
            }

            // Notify the runtime a new framed object was created
            statements.add(
                syntheticCall(
                    context.irBuiltIns.unitType,
                    createdSymbol,
                    createdDescriptor
                ).apply {
                    putValueArgument(0, thisValue)
                }
            )

            irInitializer.body = syntheticBlockBody(statements)

            // TODO(http://b/79588393): Determine if the order is important here. Should this be added before, all other initializers, after, be before the property
            framedClass.declarations.add(irInitializer)
        }

    // Replace property getter/setters with _readable/_writable calls (this, indirectly, removes the backing field)
    val readableDescriptor =
        framesPackageDescriptor.memberScope.getContributedFunctions(
            Name.identifier("_readable"),
            NoLookupLocation.FROM_BACKEND
        ).single()
    val writableDescriptor =
        framesPackageDescriptor.memberScope.getContributedFunctions(
            Name.identifier("_writable"),
            NoLookupLocation.FROM_BACKEND
        ).single()
    val readableSymbol = context.symbolTable.referenceSimpleFunction(readableDescriptor)
    val writableSymbol = context.symbolTable.referenceSimpleFunction(writableDescriptor)
    metadata.getFramedProperties(context.bindingContext).forEach { propertyDescriptor ->
        val irFramedProperty = framedClass.declarations.find { it.descriptor.name == propertyDescriptor.name } as? IrProperty
                ?: error("Could not find ir representation of ${propertyDescriptor.name}")
        val irRecordField = recordClassInfo.fields.find { it.name == propertyDescriptor.name }
                ?: error("Could not find record field of ${propertyDescriptor.name}")
        irFramedProperty.backingField = null
        irFramedProperty.getter?.let { getter ->
            // replace this.field with (_readable(this.next) as <record>).<field>
            getter.body?.transform(object : IrElementTransformer<Nothing?> {
                override fun visitGetField(expression: IrGetField, data: Nothing?): IrExpression {
                    val newExpression = if (expression.descriptor == irFramedProperty.descriptor) {
                        syntheticGetField(irRecordField.symbol,
                                          toRecord(syntheticCall(
                                              recordClassInfo.irClass.defaultType,
                                              readableSymbol,
                                              readableDescriptor
                                          ).also {
                                              it.putValueArgument(
                                                  0,
                                                  recordGetter
                                              )
                                              it.putValueArgument(1, syntheticGetValue(thisSymbol))
                                          }
                                          ))

                    } else expression
                    return super.visitGetField(newExpression, data)
                }
            }, null)
        }

        irFramedProperty.setter?.let { setter ->
            // replace "this.field = value" with "(_writable(this.next) as <record>).<field> = value"
            setter.body?.transform(object : IrElementTransformer<Nothing?> {
                override fun visitSetField(expression: IrSetField, data: Nothing?): IrExpression {
                    val newExpression = if (expression.descriptor == irFramedProperty.descriptor) {
                        syntheticSetField(
                            irRecordField.symbol,
                            toRecord(syntheticCall(
                                recordClassInfo.irClass.defaultType,
                                writableSymbol,
                                writableDescriptor
                            ).also {
                                it.putValueArgument(
                                    0, recordGetter
                                )
                                it.putValueArgument(1, syntheticGetValue(thisSymbol))
                            }),
                            expression.value
                        )
                    } else expression
                    return super.visitSetField(newExpression, data)
                }
            }, null)
        }
    }

    return result
}

fun createMethod(
    methodDescriptor: FunctionDescriptor,
    context: GeneratorContext,
    irClass: IrClass,
    block: (IrSimpleFunction) -> IrBody
): IrSimpleFunction {
    val irMethod = context.symbolTable.declareSimpleFunctionWithOverrides(
        UNDEFINED_OFFSET, UNDEFINED_OFFSET, IrDeclarationOrigin.DEFINED,
        methodDescriptor
    ).buildWithScope(context) {
        it.createParameterDeclarations()
        it.body = block(it)
    }
    irClass.declarations.add(irMethod)
    return irMethod
}

fun createMethod(
    name: Name,
    context: GeneratorContext,
    classDescriptor: ClassDescriptor,
    irClass: IrClass,
    block: (IrSimpleFunction) -> IrBody
): IrSimpleFunction {
    val methodDescriptor = classDescriptor.unsubstitutedMemberScope.getContributedFunctions(name, NoLookupLocation.FROM_BACKEND).single()
    return createMethod(methodDescriptor, context, irClass, block)
}

// TODO(chuckj): This is copied from R4ASyntheticExtension. Consider moving it to a location that can be shared.
fun IrElement.find(filter: (descriptor: IrElement) -> Boolean): Collection<IrElement> {
    val elements = mutableListOf<IrElement>()
    accept(object : IrElementVisitorVoid {
        override fun visitElement(element: IrElement) {
            if (filter(element)) elements.add(element)
            element.acceptChildren(this, null)
        }

        override fun visitKtxStatement(expression: IrKtxStatement, data: Nothing?) {
            expression.acceptChildren(this, null)
        }
    }, null)
    return elements
}

fun IrElement.anyChild(filter: (descriptor: IrElement) -> Boolean): Boolean {
    var result = false
    accept(object : IrElementVisitorVoid {
        override fun visitElement(element: IrElement) {
            if (!result) {
                if (filter(element)) {
                    result = true
                } else {
                    element.acceptChildren(this, null)
                }
            }
        }

        override fun visitKtxStatement(expression: IrKtxStatement, data: Nothing?) {
            if (!result)
                expression.acceptChildren(this, null)
        }
    }, null)
    return result
}

fun SymbolTable.declareSyntheticClass(descriptor: ClassDescriptor) =
    declareClass(UNDEFINED_OFFSET, UNDEFINED_OFFSET, IrDeclarationOrigin.DEFINED, descriptor)

fun SymbolTable.declareSyntheticConstructor(descriptor: ClassConstructorDescriptor) =
    declareConstructor(UNDEFINED_OFFSET, UNDEFINED_OFFSET, IrDeclarationOrigin.DEFINED, descriptor)

fun SymbolTable.declareSyntheticField(descriptor: PropertyDescriptor) =
    declareField(UNDEFINED_OFFSET, UNDEFINED_OFFSET, IrDeclarationOrigin.DEFINED, descriptor, descriptor.type.toIrType()!!)

fun syntheticConstructorDelegatingCall(symbol: IrConstructorSymbol, descriptor: ClassConstructorDescriptor) =
    IrDelegatingConstructorCallImpl(
        UNDEFINED_OFFSET,
        UNDEFINED_OFFSET,
        descriptor.returnType.toIrType()!!,
        symbol,
        descriptor,
        typeArgumentsCount = 0
    )

fun syntheticBlockBody(statements: List<IrStatement>) =
    IrBlockBodyImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, statements)

fun syntheticCall(kotlinType: IrType, symbol: IrFunctionSymbol, descriptor: FunctionDescriptor) =
    IrCallImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, kotlinType, symbol, descriptor, typeArgumentsCount = 0)

fun syntheticGetterCall(symbol: IrFunctionSymbol, descriptor: FunctionDescriptor, dispatchReceiver: IrExpression?) =
    IrGetterCallImpl(
        UNDEFINED_OFFSET, UNDEFINED_OFFSET, descriptor.returnType!!.toIrType()!!, symbol, descriptor,
        typeArgumentsCount = 0,
        dispatchReceiver = dispatchReceiver,
        extensionReceiver = null
    )

fun syntheticSetterCall(
    symbol: IrFunctionSymbol,
    descriptor: FunctionDescriptor,
    dispatchReceiver: IrExpression?,
    argument: IrExpression
) =
    IrSetterCallImpl(
        UNDEFINED_OFFSET, UNDEFINED_OFFSET, descriptor.returnType!!.toIrType()!!, symbol, descriptor,
        typeArgumentsCount = 0,
        dispatchReceiver = dispatchReceiver,
        extensionReceiver = null,
        argument = argument
    )

fun syntheticExpressionBody(expression: IrExpression) =
    IrExpressionBodyImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, expression)

fun syntheticGetValue(symbol: IrValueSymbol) =
    IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, symbol)

fun syntheticGetField(symbol: IrFieldSymbol, receiver: IrExpression) =
    IrGetFieldImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, symbol, symbol.descriptor.type.toIrType()!!, receiver)

fun syntheticSetField(symbol: IrFieldSymbol, receiver: IrExpression, expression: IrExpression) =
    IrSetFieldImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, symbol, receiver, expression, symbol.descriptor.type.toIrType()!!)

fun syntheticImplicitCast(fromType: KotlinType, toType: IrType, classifier: IrClassifierSymbol, argument: IrExpression) =
    IrTypeOperatorCallImpl(
        UNDEFINED_OFFSET, UNDEFINED_OFFSET, fromType.toIrType()!!, IrTypeOperator.IMPLICIT_CAST,
        toType, classifier, argument
    )

// TODO(chuckj): Move to a shared location
inline fun <T : IrDeclaration> T.buildWithScope(context: GeneratorContext, crossinline builder: (T) -> Unit): T =
    also { irDeclaration ->
        context.symbolTable.withScope(irDeclaration.descriptor) {
            builder(irDeclaration)
        }
    }

class SyntheticFramePackageDescriptor(module: ModuleDescriptor, fqName: FqName) : PackageFragmentDescriptorImpl(module, fqName) {
    private lateinit var classDescriptor: ClassDescriptor
    private val scope: MemberScope = object : MemberScopeImpl() {
        override fun getContributedDescriptors(
            kindFilter: DescriptorKindFilter,
            nameFilter: (Name) -> Boolean
        ): List<DeclarationDescriptor> =
            if (nameFilter(classDescriptor.name)) listOf(classDescriptor) else emptyList()

        override fun getContributedClassifier(name: Name, location: LookupLocation): ClassifierDescriptor? =
            if (classDescriptor.fqNameSafe == FqName(fqName.toString() + "" + name.identifier)) classDescriptor else null

        override fun printScopeStructure(p: Printer) {
            p.println(this::class.java.simpleName)
        }
    }

    override fun getMemberScope() = scope

    fun setClassDescriptor(value: ClassDescriptor) {
        classDescriptor = value
    }
}

// TODO(chuckj): Consider refactoring to have a shared synthetic base with other synthetic classes
class FrameRecordClassDescriptor(
    private val myName: Name,
    private val myContainingDeclaration: DeclarationDescriptor,
    val recordDescriptor: ClassDescriptor,
    myFramedClassDescriptor: ClassDescriptor,
    mySuperTypes: Collection<KotlinType>,
    bindingContext: BindingContext
) : ClassDescriptor {
    override fun getKind() = ClassKind.CLASS
    override fun getModality() = Modality.FINAL
    override fun getName() = myName
    override fun getSource() = SourceElement.NO_SOURCE!!
    override fun getMemberScope(typeArguments: MutableList<out TypeProjection>): MemberScope = myScope
    override fun getMemberScope(typeSubstitution: TypeSubstitution): MemberScope = myScope
    override fun getUnsubstitutedMemberScope(): MemberScope = myScope
    override fun getUnsubstitutedInnerClassesScope(): MemberScope = myScope
    override fun getStaticScope(): MemberScope = myScope
    override fun getConstructors(): Collection<ClassConstructorDescriptor> = listOf(myUnsubstitutedPrimaryConstructor)
    override fun getContainingDeclaration() = myContainingDeclaration
    override fun getDefaultType() = myDefaultType
    override fun getCompanionObjectDescriptor(): ClassDescriptor? = null
    override fun getVisibility() = Visibilities.PUBLIC
    override fun isCompanionObject() = false
    override fun isData(): Boolean = false
    override fun isInline() = false
    override fun getThisAsReceiverParameter() = thisAsReceiverParameter
    override fun getUnsubstitutedPrimaryConstructor(): ClassConstructorDescriptor? = myUnsubstitutedPrimaryConstructor
    override fun getSealedSubclasses(): Collection<ClassDescriptor> = emptyList()
    override fun getOriginal(): ClassDescriptor = this
    override fun isExpect() = false
    override fun substitute(substitutor: TypeSubstitutor): ClassifierDescriptorWithTypeParameters =
        error("Class $this can't be substituted")

    override fun isActual(): Boolean = false
    override fun getTypeConstructor() = myTypeConstructor
    override fun isInner() = false

    override fun <R : Any?, D : Any?> accept(visitor: DeclarationDescriptorVisitor<R, D>?, data: D): R {
        return visitor!!.visitClassDescriptor(this, data)
    }

    override fun getDeclaredTypeParameters() = emptyList<TypeParameterDescriptor>()

    override fun isExternal() = false

    override fun acceptVoid(visitor: DeclarationDescriptorVisitor<Void, Void>?) {
        visitor?.visitClassDescriptor(this, null)
    }

    override val annotations = Annotations.EMPTY

    private val myUnsubstitutedPrimaryConstructor: ClassConstructorDescriptor by lazy {
        val constructor = ClassConstructorDescriptorImpl.create(
            this,
            Annotations.EMPTY,
            false,
            SourceElement.NO_SOURCE
        )
        constructor.initialize(
            emptyList(),
            Visibilities.PUBLIC
        )

        constructor.apply {
            returnType = containingDeclaration.defaultType
        }
        constructor as ClassConstructorDescriptor
    }

    private val myScope = genScope(bindingContext)

    private val myDefaultType: SimpleType by lazy {
        TypeUtils.makeUnsubstitutedType(this, unsubstitutedMemberScope)
    }

    private val thisAsReceiverParameter = LazyClassReceiverParameterDescriptor(this)
    private val myTypeConstructor = ClassTypeConstructorImpl(this, declaredTypeParameters, mySuperTypes, LockBasedStorageManager.NO_LOCKS)
    private val myMetadata = FrameMetadata(myFramedClassDescriptor)

    private fun genScope(bindingContext: BindingContext): MemberScope {
        return object : MemberScopeImpl() {

            override fun printScopeStructure(p: Printer) {
                p.println(this::class.java.simpleName)
            }

            override fun getVariableNames() = myVariableNames
            override fun getContributedVariables(name: Name, location: LookupLocation): Collection<PropertyDescriptor> =
                myContributedVariables[name]?.let { listOf(it) } ?: emptyList()

            override fun getFunctionNames(): Set<Name> = myFunctionNames
            override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<SimpleFunctionDescriptor> =
                myContributedFunctions[name]?.let { listOf(it) } ?: emptyList()

            val myVariableNames by lazy { myContributedVariables.keys }
            val myContributedVariables by lazy {
                myMetadata.getRecordPropertyDescriptors(this@FrameRecordClassDescriptor, bindingContext).map {
                    it.name to it
                }.toMap()
            }
            val myFunctionNames by lazy { myContributedFunctions.keys }
            val myContributedFunctions: Map<Name, SimpleFunctionDescriptor> by lazy {
                myMetadata.getRecordMethodDescriptors(this@FrameRecordClassDescriptor, recordDescriptor).map {
                    it.name to it
                }.toMap()
            }
        }
    }
}
