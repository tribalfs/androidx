/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.contentaccess

import android.content.ContentResolver
import kotlin.reflect.KClass

class ContentAccess {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T : Any> getAccessor(
            contentAccessObject: KClass<T>,
            contentResolver: ContentResolver
        ): T {
            val generatedClassName = "_${contentAccessObject.simpleName}Impl"
            val packageName = contentAccessObject.java.`package`!!.name
            try {
                val cl = Class.forName("$packageName.$generatedClassName")
                val constructor = cl.getConstructor(ContentResolver::class.java)
                return constructor.newInstance(contentResolver) as T
            } catch (e: ClassNotFoundException) {
                error("Cannot find generated class for content accessor ${contentAccessObject
                    .qualifiedName}, this is most likely because the class is not annotated " +
                        "with @ContentAccessObject or because the contentaccess-compiler " +
                        "annotation processor was not ran properly.")
            } catch (e: InstantiationException) {
                error("Unable to instantiate implementation $packageName.$generatedClassName of " +
                        "${contentAccessObject.qualifiedName}.")
            }
        }
    }
}
