/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.appactions.interaction.capabilities.core.properties

/**
 * Base class for the property which describes a parameter for {@code ActionCapability}. This class
 * should not be used directly. Instead, use the typed property classes such as {@link
 * StringProperty}, etc.
 *
 */
sealed interface ParamProperty<V> {

    /** The current list of possible values for this parameter, can change over time. */
    val possibleValues: List<V>

    /** Indicates that a value for this property is required to be present for fulfillment. */
    val isRequired: Boolean

    /**
     * Indicates that a match of possible value for the given property must be present. Defaults to
     * false.
     *
     * <p>If true, Assistant skips the capability if there is no match.
     */
    val isValueMatchRequired: Boolean

    /**
     * If true, the {@code ActionCapability} will be rejected by assistant if corresponding param is
     * set in argument. And the value of |isRequired| and |entityMatchRequired| will also be ignored
     * by assistant.
     */
    val isProhibited: Boolean
}
