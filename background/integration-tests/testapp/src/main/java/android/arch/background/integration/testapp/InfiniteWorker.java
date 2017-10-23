/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.arch.background.integration.testapp;

import android.arch.background.workmanager.Worker;
import android.util.Log;

/**
 * Created by sumir on 10/23/17.
 */

public class InfiniteWorker extends Worker {

    @Override
    public void doWork() throws Exception {
        while (true) {
            try {
                Thread.sleep(5000L);
            } catch (InterruptedException e) {
                // Do nothing.
            } finally {
                Log.e("InfiniteWorker", "work work");
            }
        }
    }
}
