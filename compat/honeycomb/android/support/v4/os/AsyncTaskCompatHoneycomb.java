/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.support.v4.os;

import android.os.AsyncTask;
import android.support.annotation.RequiresApi;

/**
 * Implementation of AsyncTask compatibility that can call Honeycomb APIs.
 */

@RequiresApi(11)
class AsyncTaskCompatHoneycomb {

    static <Params, Progress, Result> void executeParallel(
            AsyncTask<Params, Progress, Result> task,
            Params... params) {
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
    }

}
