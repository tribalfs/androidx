/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.lifecycle.service

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleService
import androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance

class TestService : LifecycleService() {
    private val binder = Binder()

    init {
        lifecycle.addObserver(
            LifecycleEventObserver { source, event ->
                val context: Context = source as TestService
                val intent = Intent(ACTION_LOG_EVENT)
                intent.putExtra(EXTRA_KEY_EVENT, event)
                getInstance(context).sendBroadcast(intent)
            }
        )
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    companion object {
        const val ACTION_LOG_EVENT = "ACTION_LOG_EVENT"
        const val EXTRA_KEY_EVENT = "EXTRA_KEY_EVENT"
    }
}
