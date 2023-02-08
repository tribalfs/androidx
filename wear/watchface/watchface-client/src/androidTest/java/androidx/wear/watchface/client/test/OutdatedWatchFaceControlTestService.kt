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

package androidx.wear.watchface.client.test

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Test WatchFaceControlService which has obsolete XML version in manifest. */
public class OutdatedWatchFaceControlTestService : Service() {
    override fun onBind(p0: Intent?): IBinder? {
        // It is not assumed to be called
        throw NotImplementedError()
    }
}
