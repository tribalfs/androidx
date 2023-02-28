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
package androidx.appactions.interaction.capabilities.core.task.impl

import androidx.annotation.RestrictTo
import androidx.appactions.interaction.capabilities.core.impl.converters.DisambigEntityConverter
import androidx.appactions.interaction.capabilities.core.impl.converters.ParamValueConverter
import androidx.appactions.interaction.capabilities.core.impl.converters.SearchActionConverter
import androidx.appactions.interaction.proto.ParamValue

/**
 * A binding between a parameter and its Property converter / Argument setter.
 *
 *
</ValueTypeT> */
@RestrictTo(RestrictTo.Scope.LIBRARY)
data class TaskParamBinding<ValueTypeT> internal constructor(
    val name: String,
    val groundingPredicate: (ParamValue) -> Boolean,
    val resolver: GenericResolverInternal<ValueTypeT>,
    val converter: ParamValueConverter<ValueTypeT>,
    val entityConverter: DisambigEntityConverter<ValueTypeT>?,
    val searchActionConverter: SearchActionConverter<ValueTypeT>?,
)