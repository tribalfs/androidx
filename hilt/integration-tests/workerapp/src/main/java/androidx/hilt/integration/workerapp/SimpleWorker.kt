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

package androidx.hilt.integration.workerapp

import android.content.Context
import android.util.Log
import androidx.hilt.work.WorkerInject
import androidx.work.Worker
import androidx.work.WorkerParameters
import javax.inject.Inject

class SimpleWorker @WorkerInject constructor(
    context: Context,
    params: WorkerParameters,
    private val logger: MyLogger
) : Worker(context, params) {
    override fun doWork(): Result {
        logger.log("Hi")
        return Result.success()
    }
}

class MyLogger @Inject constructor() {
    fun log(s: String) {
        Log.i("MyLogger", s)
    }
}