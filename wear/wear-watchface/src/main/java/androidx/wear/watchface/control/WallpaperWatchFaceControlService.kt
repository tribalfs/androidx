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

package androidx.wear.watchface.control

import android.os.Handler
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.control.data.WallpaperInteractiveWatchFaceInstanceParams
import androidx.wear.watchface.runOnHandler

internal class WallpaperWatchFaceControlService(
    private val engine: WatchFaceService.EngineWrapper,
    private val uiThreadHandler: Handler
) : IWallpaperWatchFaceControlService.Stub() {
    override fun getApiVersion() = IWallpaperWatchFaceControlService.API_VERSION

    override fun createInteractiveWatchFaceInstance(
        params: WallpaperInteractiveWatchFaceInstanceParams
    ) = uiThreadHandler.runOnHandler { engine.createInteractiveInstance(params)?.wcsApi }
}
