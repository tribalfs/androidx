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

package androidx.appactions.interaction.capabilities.communication

import androidx.appactions.interaction.capabilities.core.impl.converters.ParamValueConverter
import androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters
import androidx.appactions.interaction.capabilities.core.impl.converters.UnionTypeSpec
import androidx.appactions.interaction.capabilities.core.values.SearchAction
import androidx.appactions.interaction.capabilities.core.values.properties.Recipient

class RecipientValue private constructor(
    val asRecipient: Recipient?,
    val asRecipientFilter: SearchAction<Recipient>?,
) {
    constructor(recipient: Recipient) : this(recipient, null)

    // TODO(b/268071906) add RecipientFilter type to SearchAction
    constructor(recipientFilter: SearchAction<Recipient>) : this(null, recipientFilter)

    companion object {
        private val TYPE_SPEC = UnionTypeSpec.Builder<RecipientValue>()
            .bindMemberType(
                memberGetter = RecipientValue::asRecipient,
                ctor = { RecipientValue(it) },
                typeSpec = TypeConverters.RecipientTypeSpec(),
            )
            .bindMemberType(
                memberGetter = RecipientValue::asRecipientFilter,
                ctor = { RecipientValue(it) },
                typeSpec = TypeConverters.createSearchActionTypeSpec(
                    TypeConverters.RecipientTypeSpec(),
                ),
            )
            .build()

        internal val FROM_PARAM_VALUE = ParamValueConverter {
            TYPE_SPEC.fromStruct(it.getStructValue())
        }
    }
}
