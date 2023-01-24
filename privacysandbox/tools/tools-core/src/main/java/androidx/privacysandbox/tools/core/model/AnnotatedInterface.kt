/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.privacysandbox.tools.core.model

/** Result of parsing a Kotlin interface. */
data class AnnotatedInterface(
    val type: Type,
    /**
     * Direct super types of this interface.
     *
     * When there are no explicit parents, the list should be empty (ie. not containing kotlin.Any).
     */
    val superTypes: List<Type> = emptyList(),
    val methods: List<Method> = emptyList(),
)