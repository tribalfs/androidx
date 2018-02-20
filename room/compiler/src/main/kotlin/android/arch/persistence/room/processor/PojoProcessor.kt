/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.arch.persistence.room.processor

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Embedded
import android.arch.persistence.room.Ignore
import android.arch.persistence.room.Relation
import android.arch.persistence.room.ext.getAllFieldsIncludingPrivateSupers
import android.arch.persistence.room.ext.getAnnotationValue
import android.arch.persistence.room.ext.getAsString
import android.arch.persistence.room.ext.getAsStringList
import android.arch.persistence.room.ext.hasAnnotation
import android.arch.persistence.room.ext.hasAnyOf
import android.arch.persistence.room.ext.isAssignableWithoutVariance
import android.arch.persistence.room.ext.isCollection
import android.arch.persistence.room.ext.toClassType
import android.arch.persistence.room.ext.typeName
import android.arch.persistence.room.processor.ProcessorErrors.CANNOT_FIND_GETTER_FOR_FIELD
import android.arch.persistence.room.processor.ProcessorErrors.CANNOT_FIND_SETTER_FOR_FIELD
import android.arch.persistence.room.processor.ProcessorErrors.CANNOT_FIND_TYPE
import android.arch.persistence.room.processor.ProcessorErrors.POJO_FIELD_HAS_DUPLICATE_COLUMN_NAME
import android.arch.persistence.room.processor.cache.Cache
import android.arch.persistence.room.vo.CallType
import android.arch.persistence.room.vo.Constructor
import android.arch.persistence.room.vo.EmbeddedField
import android.arch.persistence.room.vo.Entity
import android.arch.persistence.room.vo.Field
import android.arch.persistence.room.vo.FieldGetter
import android.arch.persistence.room.vo.FieldSetter
import android.arch.persistence.room.vo.Pojo
import android.arch.persistence.room.vo.PojoMethod
import android.arch.persistence.room.vo.Warning
import com.google.auto.common.AnnotationMirrors
import com.google.auto.common.MoreElements
import com.google.auto.common.MoreTypes
import me.eugeniomarletti.kotlin.metadata.KotlinClassMetadata
import me.eugeniomarletti.kotlin.metadata.KotlinMetadataUtils
import me.eugeniomarletti.kotlin.metadata.jvm.getJvmConstructorSignature
import me.eugeniomarletti.kotlin.metadata.kotlinMetadata
import org.jetbrains.kotlin.serialization.ProtoBuf
import org.jetbrains.kotlin.serialization.deserialization.NameResolver
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier.ABSTRACT
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PROTECTED
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC
import javax.lang.model.element.Modifier.TRANSIENT
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter

/**
 * Processes any class as if it is a Pojo.
 */
class PojoProcessor(
        baseContext: Context,
        val element: TypeElement,
        val bindingScope: FieldProcessor.BindingScope,
        val parent: EmbeddedField?,
        val referenceStack: LinkedHashSet<Name> = LinkedHashSet())
    : KotlinMetadataUtils {
    val context = baseContext.fork(element)

    // for KotlinMetadataUtils
    override val processingEnv: ProcessingEnvironment
        get() = context.processingEnv

    // opportunistic kotlin metadata
    private val kotlinMetadata by lazy {
        try {
            element.kotlinMetadata
        } catch (throwable: Throwable) {
            context.logger.d(element, "failed to read get kotlin metadata from %s", element)
        }
    }

    companion object {
        val PROCESSED_ANNOTATIONS = listOf(ColumnInfo::class, Embedded::class,
                Relation::class)
    }

    fun process(): Pojo {
        return context.cache.pojos.get(Cache.PojoKey(element, bindingScope, parent), {
            referenceStack.add(element.qualifiedName)
            try {
                doProcess()
            } finally {
                referenceStack.remove(element.qualifiedName)
            }
        })
    }

    private fun doProcess(): Pojo {
        val declaredType = MoreTypes.asDeclared(element.asType())
        // TODO handle conflicts with super: b/35568142
        val allFields = element.getAllFieldsIncludingPrivateSupers(context.processingEnv)
                .filter {
                    !it.hasAnnotation(Ignore::class)
                            && !it.hasAnyOf(STATIC)
                            && (!it.hasAnyOf(TRANSIENT)
                            || it.hasAnnotation(ColumnInfo::class)
                            || it.hasAnnotation(Embedded::class)
                            || it.hasAnnotation(Relation::class))
                }
                .groupBy { field ->
                    context.checker.check(
                            PROCESSED_ANNOTATIONS.count { field.hasAnnotation(it) } < 2, field,
                            ProcessorErrors.CANNOT_USE_MORE_THAN_ONE_POJO_FIELD_ANNOTATION
                    )
                    if (field.hasAnnotation(Embedded::class)) {
                        Embedded::class
                    } else if (field.hasAnnotation(Relation::class)) {
                        Relation::class
                    } else {
                        null
                    }
                }

        val myFields = allFields[null]
                ?.map {
                    FieldProcessor(
                            baseContext = context,
                            containing = declaredType,
                            element = it,
                            bindingScope = bindingScope,
                            fieldParent = parent).process()
                } ?: emptyList()

        val embeddedFields =
                allFields[Embedded::class]
                        ?.map {
                            processEmbeddedField(declaredType, it)
                        }
                        ?.filterNotNull()
                        ?: emptyList()

        val subFields = embeddedFields.flatMap { it.pojo.fields }
        val fields = myFields + subFields

        val myRelationsList = allFields[Relation::class]
                ?.map {
                    processRelationField(fields, declaredType, it)
                }
                ?.filterNotNull()
                ?: emptyList()

        val subRelations = embeddedFields.flatMap { it.pojo.relations }
        val relations = myRelationsList + subRelations

        fields.groupBy { it.columnName }
                .filter { it.value.size > 1 }
                .forEach {
                    context.logger.e(element, ProcessorErrors.pojoDuplicateFieldNames(
                            it.key, it.value.map(Field::getPath)
                    ))
                    it.value.forEach {
                        context.logger.e(it.element, POJO_FIELD_HAS_DUPLICATE_COLUMN_NAME)
                    }
                }

        val methods = MoreElements.getLocalAndInheritedMethods(element,
                context.processingEnv.elementUtils)
                .filter {
                    !it.hasAnyOf(PRIVATE, ABSTRACT, STATIC)
                            && !it.hasAnnotation(Ignore::class)
                }
                .map { MoreElements.asExecutable(it) }
                .map {
                    PojoMethodProcessor(
                            context = context,
                            element = it,
                            owner = declaredType
                    ).process()
                }

        val getterCandidates = methods.filter {
            it.element.parameters.size == 0 && it.resolvedType.returnType.kind != TypeKind.VOID
        }

        val setterCandidates = methods.filter {
            it.element.parameters.size == 1 && it.resolvedType.returnType.kind == TypeKind.VOID
        }

        // don't try to find a constructor for binding to statement.
        val constructor = if (bindingScope == FieldProcessor.BindingScope.BIND_TO_STMT) {
            // we don't need to construct this POJO.
            null
        } else {
            chooseConstructor(myFields, embeddedFields, relations)
        }

        assignGetters(myFields, getterCandidates)
        assignSetters(myFields, setterCandidates, constructor)

        embeddedFields.forEach {
            assignGetter(it.field, getterCandidates)
            assignSetter(it.field, setterCandidates, constructor)
        }

        myRelationsList.forEach {
            assignGetter(it.field, getterCandidates)
            assignSetter(it.field, setterCandidates, constructor)
        }

        return Pojo(element = element,
                type = declaredType,
                fields = fields,
                embeddedFields = embeddedFields,
                relations = relations,
                constructor = constructor)
    }

    /**
     * Retrieves the parameter names of a method. If the method is inherited from a dependency
     * module, the parameter name is not available (not in java spec). For kotlin, since parameter
     * names are part of the API, we can read them via the kotlin metadata annotation.
     * <p>
     * Since we are using an unofficial library to read the metadata, all access to that code
     * is safe guarded to avoid unexpected failures. In other words, it is a best effort but
     * better than not supporting these until JB provides a proper API.
     */
    private fun getParamNames(method: ExecutableElement): List<String> {
        val paramNames = method.parameters.map { it.simpleName.toString() }
        if (paramNames.isEmpty()) {
            return emptyList()
        }
        (kotlinMetadata as? KotlinClassMetadata)?.let {
            try {
                val kotlinParams = it
                        .findConstructor(method)
                        ?.tryGetParameterNames(it.data.nameResolver)
                if (kotlinParams != null) {
                    return kotlinParams
                }
            } catch (throwable: Throwable) {
                context.logger.d(
                        method,
                        "Cannot read kotlin metadata, falling back to jvm signature. %s",
                        throwable.message as Any)
            }
        }
        // either it is java or something went wrong w/ kotlin metadata. default to whatever
        // we can read.
        return paramNames
    }

    private fun chooseConstructor(
            myFields: List<Field>,
            embedded: List<EmbeddedField>,
            relations: List<android.arch.persistence.room.vo.Relation>): Constructor? {
        val constructors = ElementFilter.constructorsIn(element.enclosedElements)
                .filterNot { it.hasAnnotation(Ignore::class) || it.hasAnyOf(PRIVATE) }
        val fieldMap = myFields.associateBy { it.name }
        val embeddedMap = embedded.associateBy { it.field.name }
        val typeUtils = context.processingEnv.typeUtils
        // list of param names -> matched params pairs for each failed constructor
        val failedConstructors = arrayListOf<FailedConstructor>()
        // if developer puts a relation into a constructor, it is usually an error but if there
        // is another constructor that is good, we can ignore the error. b/72884434
        val relationsInConstructor = arrayListOf<VariableElement>()
        val goodConstructors = constructors.map { constructor ->
            val parameterNames = getParamNames(constructor)
            val params = constructor.parameters.mapIndexed param@ { index, param ->
                val paramName = parameterNames[index]
                val paramType = param.asType()

                val matches = fun(field: Field?): Boolean {
                    return if (field == null) {
                        false
                    } else if (!field.nameWithVariations.contains(paramName)) {
                        false
                    } else {
                        // see: b/69164099
                        typeUtils.isAssignableWithoutVariance(paramType, field.type)
                    }
                }

                val exactFieldMatch = fieldMap[paramName]

                if (matches(exactFieldMatch)) {
                    return@param Constructor.FieldParam(exactFieldMatch!!)
                }
                val exactEmbeddedMatch = embeddedMap[paramName]
                if (matches(exactEmbeddedMatch?.field)) {
                    return@param Constructor.EmbeddedParam(exactEmbeddedMatch!!)
                }

                val matchingFields = myFields.filter {
                    matches(it)
                }
                val embeddedMatches = embedded.filter {
                    matches(it.field)
                }
                if (matchingFields.isEmpty() && embeddedMatches.isEmpty()) {
                    // if it didn't match a proper field, a common mistake is to have a relation
                    // so check to see if it is a relation
                    val matchedRelation = relations.any {
                        it.field.nameWithVariations.contains(paramName)
                    }
                    if (matchedRelation) {
                        relationsInConstructor.add(param)
                    }
                    null
                } else if (matchingFields.size + embeddedMatches.size == 1) {
                    if (matchingFields.isNotEmpty()) {
                        Constructor.FieldParam(matchingFields.first())
                    } else {
                        Constructor.EmbeddedParam(embeddedMatches.first())
                    }
                } else {
                    context.logger.e(param, ProcessorErrors.ambigiousConstructor(
                            pojo = element.qualifiedName.toString(),
                            paramName = paramName,
                            matchingFields = matchingFields.map { it.getPath() }
                                    + embedded.map { it.field.getPath() }
                    ))
                    null
                }
            }
            if (params.any { it == null }) {
                failedConstructors.add(FailedConstructor(constructor, parameterNames, params))
                null
            } else {
                @Suppress("UNCHECKED_CAST")
                Constructor(constructor, params as List<Constructor.Param>)
            }
        }.filterNotNull()
        when {
            goodConstructors.isEmpty() -> {
                relationsInConstructor.forEach {
                    context.logger.e(it,
                            ProcessorErrors.RELATION_CANNOT_BE_CONSTRUCTOR_PARAMETER)
                }
                if (failedConstructors.isNotEmpty()) {
                    val failureMsg = failedConstructors.joinToString("\n") { entry ->
                        entry.log()
                    }
                    context.logger.e(element, ProcessorErrors.MISSING_POJO_CONSTRUCTOR +
                            "\nTried the following constructors but they failed to match:" +
                            "\n$failureMsg")
                }
                context.logger.e(element, ProcessorErrors.MISSING_POJO_CONSTRUCTOR)
                return null
            }
            goodConstructors.size > 1 -> {
                // if there is a no-arg constructor, pick it. Even though it is weird, easily happens
                // with kotlin data classes.
                val noArg = goodConstructors.firstOrNull { it.params.isEmpty() }
                if (noArg != null) {
                    context.logger.w(Warning.DEFAULT_CONSTRUCTOR, element,
                            ProcessorErrors.TOO_MANY_POJO_CONSTRUCTORS_CHOOSING_NO_ARG)
                    return noArg
                }
                goodConstructors.forEach {
                    context.logger.e(it.element, ProcessorErrors.TOO_MANY_POJO_CONSTRUCTORS)
                }
                return null
            }
            else -> return goodConstructors.first()
        }
    }

    private fun processEmbeddedField(
            declaredType: DeclaredType?, variableElement: VariableElement): EmbeddedField? {

        val asTypeElement = MoreTypes.asTypeElement(variableElement.asType())

        if (detectReferenceRecursion(asTypeElement)) {
            return null
        }

        val fieldPrefix = variableElement
                .getAnnotationValue(Embedded::class.java, "prefix")
                ?.toString()
                ?: ""
        val inheritedPrefix = parent?.prefix ?: ""
        val embeddedField = Field(
                variableElement,
                variableElement.simpleName.toString(),
                type = context
                        .processingEnv
                        .typeUtils
                        .asMemberOf(declaredType, variableElement),
                affinity = null,
                parent = parent)
        val subParent = EmbeddedField(
                field = embeddedField,
                prefix = inheritedPrefix + fieldPrefix,
                parent = parent)
        subParent.pojo = PojoProcessor(
                baseContext = context.fork(variableElement),
                element = asTypeElement,
                bindingScope = bindingScope,
                parent = subParent,
                referenceStack = referenceStack).process()
        return subParent
    }

    private fun processRelationField(
            myFields: List<Field>, container: DeclaredType?,
            relationElement: VariableElement
    ): android.arch.persistence.room.vo.Relation? {
        val asTypeElement = MoreTypes.asTypeElement(
                MoreElements.asVariable(relationElement).asType())

        if (detectReferenceRecursion(asTypeElement)) {
            return null
        }

        val annotation = MoreElements.getAnnotationMirror(relationElement, Relation::class.java)
                .orNull()!!
        val parentColumnInput = AnnotationMirrors.getAnnotationValue(annotation, "parentColumn")
                .getAsString("") ?: ""

        val parentField = myFields.firstOrNull {
            it.columnName == parentColumnInput
        }
        if (parentField == null) {
            context.logger.e(relationElement,
                    ProcessorErrors.relationCannotFindParentEntityField(
                            entityName = element.qualifiedName.toString(),
                            columnName = parentColumnInput,
                            availableColumns = myFields.map { it.columnName }))
            return null
        }
        // parse it as an entity.
        val asMember = MoreTypes
                .asMemberOf(context.processingEnv.typeUtils, container, relationElement)
        if (asMember.kind == TypeKind.ERROR) {
            context.logger.e(ProcessorErrors.CANNOT_FIND_TYPE, element)
            return null
        }
        val declared = MoreTypes.asDeclared(asMember)
        if (!declared.isCollection()) {
            context.logger.e(relationElement, ProcessorErrors.RELATION_NOT_COLLECTION)
            return null
        }
        val typeArg = declared.typeArguments.first()
        if (typeArg.kind == TypeKind.ERROR) {
            context.logger.e(MoreTypes.asTypeElement(typeArg), CANNOT_FIND_TYPE)
            return null
        }
        val typeArgElement = MoreTypes.asTypeElement(typeArg)
        val entityClassInput = AnnotationMirrors
                .getAnnotationValue(annotation, "entity").toClassType()

        // do we need to decide on the entity?
        val inferEntity = (entityClassInput == null
                || MoreTypes.isTypeOf(Any::class.java, entityClassInput))

        val entity = if (inferEntity) {
            EntityProcessor(context, typeArgElement, referenceStack).process()
        } else {
            EntityProcessor(context, MoreTypes.asTypeElement(entityClassInput),
                    referenceStack).process()
        }

        // now find the field in the entity.
        val entityColumnInput = AnnotationMirrors.getAnnotationValue(annotation, "entityColumn")
                .getAsString() ?: ""
        val entityField = entity.fields.firstOrNull {
            it.columnName == entityColumnInput
        }

        if (entityField == null) {
            context.logger.e(relationElement,
                    ProcessorErrors.relationCannotFindEntityField(
                            entityName = entity.typeName.toString(),
                            columnName = entityColumnInput,
                            availableColumns = entity.fields.map { it.columnName }))
            return null
        }

        val field = Field(
                element = relationElement,
                name = relationElement.simpleName.toString(),
                type = context.processingEnv.typeUtils.asMemberOf(container, relationElement),
                affinity = null,
                parent = parent)

        val projectionInput = AnnotationMirrors.getAnnotationValue(annotation, "projection")
                .getAsStringList()
        val projection = if (projectionInput.isEmpty()) {
            // we need to infer the projection from inputs.
            createRelationshipProjection(inferEntity, typeArg, entity, entityField, typeArgElement)
        } else {
            // make sure projection makes sense
            validateRelationshipProjection(projectionInput, entity, relationElement)
            projectionInput
        }
        // if types don't match, row adapter prints a warning
        return android.arch.persistence.room.vo.Relation(
                entity = entity,
                pojoType = typeArg,
                field = field,
                parentField = parentField,
                entityField = entityField,
                projection = projection
        )
    }

    private fun validateRelationshipProjection(
            projectionInput: List<String>,
            entity: Entity,
            relationElement: VariableElement) {
        val missingColumns = projectionInput.filterNot { columnName ->
            entity.fields.any { columnName == it.columnName }
        }
        if (missingColumns.isNotEmpty()) {
            context.logger.e(relationElement,
                    ProcessorErrors.relationBadProject(entity.typeName.toString(),
                            missingColumns, entity.fields.map { it.columnName }))
        }
    }

    /**
     * Create the projection column list based on the relationship args.
     *
     *  if entity field in the annotation is not specified, it is the method return type
     *  if it is specified in the annotation:
     *       still check the method return type, if the same, use it
     *       if not, check to see if we can find a column Adapter, if so use the childField
     *       last resort, try to parse it as a pojo to infer it.
     */
    private fun createRelationshipProjection(
            inferEntity: Boolean,
            typeArg: TypeMirror,
            entity: Entity,
            entityField: Field,
            typeArgElement: TypeElement): List<String> {
        return if (inferEntity || typeArg.typeName() == entity.typeName) {
            entity.fields.map { it.columnName }
        } else {
            val columnAdapter = context.typeAdapterStore.findCursorValueReader(typeArg, null)
            if (columnAdapter != null) {
                // nice, there is a column adapter for this, assume single column response
                listOf(entityField.name)
            } else {
                // last resort, it needs to be a pojo
                val pojo = PojoProcessor(
                        baseContext = context,
                        element = typeArgElement,
                        bindingScope = FieldProcessor.BindingScope.READ_FROM_CURSOR,
                        parent = parent,
                        referenceStack = referenceStack).process()
                pojo.fields.map { it.columnName }
            }
        }
    }

    private fun detectReferenceRecursion(typeElement: TypeElement): Boolean {
        if (referenceStack.contains(typeElement.qualifiedName)) {
            context.logger.e(
                    typeElement,
                    ProcessorErrors
                            .RECURSIVE_REFERENCE_DETECTED
                            .format(computeReferenceRecursionString(typeElement)))
            return true
        }
        return false
    }

    private fun computeReferenceRecursionString(typeElement: TypeElement): String {
        val recursiveTailTypeName = typeElement.qualifiedName

        val referenceRecursionList = mutableListOf<Name>()
        with(referenceRecursionList) {
            add(recursiveTailTypeName)
            addAll(referenceStack.toList().takeLastWhile { it != recursiveTailTypeName })
            add(recursiveTailTypeName)
        }

        return referenceRecursionList.joinToString(" -> ")
    }

    private fun assignGetters(fields: List<Field>, getterCandidates: List<PojoMethod>) {
        fields.forEach { field ->
            assignGetter(field, getterCandidates)
        }
    }

    private fun assignGetter(field: Field, getterCandidates: List<PojoMethod>) {
        val success = chooseAssignment(field = field,
                candidates = getterCandidates,
                nameVariations = field.getterNameWithVariations,
                getType = { method ->
                    method.resolvedType.returnType
                },
                assignFromField = {
                    field.getter = FieldGetter(
                            name = field.name,
                            type = field.type,
                            callType = CallType.FIELD)
                },
                assignFromMethod = { match ->
                    field.getter = FieldGetter(
                            name = match.name,
                            type = match.resolvedType.returnType,
                            callType = CallType.METHOD)
                },
                reportAmbiguity = { matching ->
                    context.logger.e(field.element,
                            ProcessorErrors.tooManyMatchingGetters(field, matching))
                })
        context.checker.check(success, field.element, CANNOT_FIND_GETTER_FOR_FIELD)
    }

    private fun assignSetters(
            fields: List<Field>,
            setterCandidates: List<PojoMethod>,
            constructor: Constructor?) {
        fields.forEach { field ->
            assignSetter(field, setterCandidates, constructor)
        }
    }

    private fun assignSetter(
            field: Field,
            setterCandidates: List<PojoMethod>,
            constructor: Constructor?) {
        if (constructor != null && constructor.hasField(field)) {
            field.setter = FieldSetter(field.name, field.type, CallType.CONSTRUCTOR)
            return
        }
        val success = chooseAssignment(field = field,
                candidates = setterCandidates,
                nameVariations = field.setterNameWithVariations,
                getType = { method ->
                    method.resolvedType.parameterTypes.first()
                },
                assignFromField = {
                    field.setter = FieldSetter(
                            name = field.name,
                            type = field.type,
                            callType = CallType.FIELD)
                },
                assignFromMethod = { match ->
                    val paramType = match.resolvedType.parameterTypes.first()
                    field.setter = FieldSetter(
                            name = match.name,
                            type = paramType,
                            callType = CallType.METHOD)
                },
                reportAmbiguity = { matching ->
                    context.logger.e(field.element,
                            ProcessorErrors.tooManyMatchingSetter(field, matching))
                })
        context.checker.check(success, field.element, CANNOT_FIND_SETTER_FOR_FIELD)
    }

    /**
     * Finds a setter/getter from available list of methods.
     * It returns true if assignment is successful, false otherwise.
     * At worst case, it sets to the field as if it is accessible so that the rest of the
     * compilation can continue.
     */
    private fun chooseAssignment(
            field: Field,
            candidates: List<PojoMethod>,
            nameVariations: List<String>,
            getType: (PojoMethod) -> TypeMirror,
            assignFromField: () -> Unit,
            assignFromMethod: (PojoMethod) -> Unit,
            reportAmbiguity: (List<String>) -> Unit
    ): Boolean {
        if (field.element.hasAnyOf(PUBLIC)) {
            assignFromField()
            return true
        }
        val types = context.processingEnv.typeUtils

        val matching = candidates
                .filter {
                    // b/69164099
                    types.isAssignableWithoutVariance(getType(it), field.type)
                            && (field.nameWithVariations.contains(it.name)
                            || nameVariations.contains(it.name))
                }
                .groupBy {
                    if (it.element.hasAnyOf(PUBLIC)) PUBLIC else PROTECTED
                }
        if (matching.isEmpty()) {
            // we always assign to avoid NPEs in the rest of the compilation.
            assignFromField()
            // if field is not private, assume it works (if we are on the same package).
            // if not, compiler will tell, we didn't have any better alternative anyways.
            return !field.element.hasAnyOf(PRIVATE)
        }
        val match = verifyAndChooseOneFrom(matching[PUBLIC], reportAmbiguity)
                ?: verifyAndChooseOneFrom(matching[PROTECTED], reportAmbiguity)
        if (match == null) {
            assignFromField()
            return false
        } else {
            assignFromMethod(match)
            return true
        }
    }

    private fun verifyAndChooseOneFrom(
            candidates: List<PojoMethod>?,
            reportAmbiguity: (List<String>) -> Unit
    ): PojoMethod? {
        if (candidates == null) {
            return null
        }
        if (candidates.size > 1) {
            reportAmbiguity(candidates.map { it.name })
        }
        return candidates.first()
    }

    /**
     * Finds the kotlin meteadata for a constructor.
     */
    private fun KotlinClassMetadata.findConstructor(
            executableElement: ExecutableElement
    ): ProtoBuf.Constructor? {
        val (nameResolver, classProto) = data
        val jvmSignature = executableElement.jvmMethodSignature
        // find constructor
        return classProto.constructorList.singleOrNull {
            it.getJvmConstructorSignature(nameResolver, classProto.typeTable) == jvmSignature
        }
    }

    /**
     * Tries to get the parameter names of a kotlin method
     */
    private fun ProtoBuf.Constructor.tryGetParameterNames(
            nameResolver: NameResolver
    ): List<String>? {
        return valueParameterList.map {
            if (it.hasName()) {
                nameResolver.getName(it.name)
                        .asString()
                        .replace("`", "")
                        .removeSuffix("?")
                        .trim()
            } else {
                // early return bad parameter
                return null
            }
        }
    }

    private data class FailedConstructor(
            val method: ExecutableElement,
            val params: List<String>,
            val matches: List<Constructor.Param?>
    ) {
        fun log(): String {
            val logPerParam = params.withIndex().joinToString(", ") {
                "param:${it.value} -> matched field:" + (matches[it.index]?.log() ?: "unmatched")
            }
            return "$method -> [$logPerParam]"
        }
    }
}
