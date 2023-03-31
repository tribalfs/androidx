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

package androidx.appactions.interaction.capabilities.core.entity

import androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters
import androidx.appactions.interaction.capabilities.core.values.Order

/**  Internal testing object for entity provider */
class OrderProvider internal constructor(
    private var id: String,
    private var response: EntityLookupResponse<Order>,
) : EntityProvider<Order>(TypeConverters.ORDER_TYPE_SPEC) {
    override fun getId(): String = id
    override suspend fun lookup(request: EntityLookupRequest<Order>):
        EntityLookupResponse<Order> = response
}