/*
 * Copyright 2024 The Android Open Source Project
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

@file:JvmName("KClassUtil")
@file:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)

package androidx.room.util

import androidx.annotation.RestrictTo
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

/**
 * Determines if the class or interface represented by this object is the same as, or is a
 * superclass or superinterface of the class or interface represented by the specified [KClass]
 * parameter.
 */
internal actual fun KClass<*>.isAssignableFrom(other: KClass<*>): Boolean {
    return this.java.isAssignableFrom(other.java)
}

/**
 * Finds and instantiates via reflection the implementation class generated by Room of an
 * `@Database` annotated type.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
fun <T, C> findAndInstantiateDatabaseImpl(klass: Class<C>, suffix: String = "_Impl"): T {
    val fullPackage: String = klass.getPackage()?.name ?: ""
    val name: String = klass.canonicalName!!
    val postPackageName =
        if (fullPackage.isEmpty()) name else name.substring(fullPackage.length + 1)
    val implName = postPackageName.replace('.', '_') + suffix
    return try {
        val fullClassName =
            if (fullPackage.isEmpty()) {
                implName
            } else {
                "$fullPackage.$implName"
            }
        @Suppress("UNCHECKED_CAST")
        val aClass = Class.forName(fullClassName, true, klass.classLoader) as Class<T>
        aClass.getDeclaredConstructor().newInstance()
    } catch (e: ClassNotFoundException) {
        throw RuntimeException(
            "Cannot find implementation for ${klass.canonicalName}. $implName does not " +
                "exist. Is Room annotation processor correctly configured?",
            e
        )
    } catch (e: IllegalAccessException) {
        throw RuntimeException("Cannot access the constructor ${klass.canonicalName}", e)
    } catch (e: InstantiationException) {
        throw RuntimeException("Failed to create an instance of ${klass.canonicalName}", e)
    }
}
