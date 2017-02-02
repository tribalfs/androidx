/*
 * Copyright (C) 2016 The Android Open Source Project
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

@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.android.support.room.ext

import com.google.auto.common.MoreElements
import com.google.auto.common.MoreTypes
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.Modifier
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.SimpleAnnotationValueVisitor6
import kotlin.reflect.KClass

fun Element.hasAnyOf(vararg modifiers: Modifier) : Boolean {
    return this.modifiers.any { modifiers.contains(it) }
}

fun Element.hasAnnotation(klass : KClass<out Annotation>) : Boolean {
    return MoreElements.isAnnotationPresent(this, klass.java)
}

/**
 * Checks if it has all of the annotations
 */
fun Element.hasAllOf(vararg klasses : KClass<out Annotation>) : Boolean {
    return !klasses.any { !hasAnnotation(it) }
}

// code below taken from dagger2
// compiler/src/main/java/dagger/internal/codegen/ConfigurationAnnotations.java
private val TO_LIST_OF_TYPES = object
    : SimpleAnnotationValueVisitor6<List<TypeMirror>, Void?>() {
    override fun visitArray(values: MutableList<out AnnotationValue>?, p: Void?)
            : List<TypeMirror> {
        return values?.map {
            val tmp = TO_TYPE.visit(it)
            tmp
        }?.filterNotNull() ?: emptyList()
    }


    override fun defaultAction(o: Any?, p: Void?): List<TypeMirror>? {
        return emptyList()
    }
}

private val TO_TYPE = object : SimpleAnnotationValueVisitor6<TypeMirror, Void>() {

    override fun visitType(t: TypeMirror, p: Void?): TypeMirror {
        return t
    }

    override fun defaultAction(o: Any?, p: Void?): TypeMirror {
        throw TypeNotPresentException(o!!.toString(), null)
    }
}

fun AnnotationValue.toListOfClassTypes(): List<TypeMirror> {
    return TO_LIST_OF_TYPES.visit(this)
}

fun AnnotationValue.toClassType(): TypeMirror? {
    return TO_TYPE.visit(this)
}

fun TypeMirror.isCollection() : Boolean {
    return MoreTypes.isType(this)
            && (MoreTypes.isTypeOf(java.util.List::class.java, this)
            || MoreTypes.isTypeOf(java.util.Set::class.java, this))
}
