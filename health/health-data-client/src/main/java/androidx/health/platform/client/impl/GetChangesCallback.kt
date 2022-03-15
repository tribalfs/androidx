/*
 * Copyright (C) 2022 The Android Open Source Project
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
package androidx.health.platform.client.impl

import androidx.health.platform.client.error.ErrorStatus
import androidx.health.platform.client.impl.error.toException
import androidx.health.platform.client.proto.ResponseProto
import androidx.health.platform.client.response.GetChangesResponse
import androidx.health.platform.client.service.IGetChangesCallback
import androidx.health.platform.client.service.IReadDataCallback
import com.google.common.util.concurrent.SettableFuture

/** Wrapper to convert [IReadDataCallback] to listenable futures. */
internal class GetChangesCallback(
    private val resultFuture: SettableFuture<ResponseProto.GetChangesResponse>
) : IGetChangesCallback.Stub() {

    override fun onSuccess(response: GetChangesResponse) {
        resultFuture.set(response.proto)
    }

    override fun onError(error: ErrorStatus) {
        resultFuture.setException(error.toException())
    }
}
