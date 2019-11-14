/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.work.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestFile

object Stubs {
    val WORK_MANAGER_CONFIGURATION_PROVIDER: TestFile = kotlin(
        "androidx/work/Configuration.kt",
        """
                 package androidx.work
                 class Configuration {
                   interface Provider {
                     fun getWorkManagerConfiguration(): Configuration
                   }
                 }
            """
    )
        .indented().within("src")

    val ANDROID_APPLICATION: TestFile = kotlin(
        "android/app/Application.kt",
        """
                package android.app
                open class Application {
                  fun onCreate() {

                  }
                }
            """
    )
        .indented().within("src")

    val WORK_REQUEST: TestFile = kotlin(
        "androidx/work/WorkRequest.kt",
        """
            package androidx.work

            open class WorkRequest
        """
    ).indented().within("src")

    val ONE_TIME_WORK_REQUEST: TestFile = kotlin(
        "androidx/work/OneTimeWorkRequest.kt",
        """
            package androidx.work

            class OneTimeWorkRequest: WorkRequest()
        """
    ).indented().within("src")

    val PERIODIC_WORK_REQUEST: TestFile = kotlin(
        "androidx/work/PeriodicWorkRequest.kt",
        """
            package androidx.work

            class PeriodicWorkRequest: WorkRequest()
        """
    ).indented().within("src")

    val WORK_MANAGER: TestFile = kotlin(
        "androidx/work/WorkManager.kt",
        """
                 package androidx.work

                 interface WorkManager {
                    fun enqueue(request: WorkRequest)
                    fun enqueue(requests: List<WorkRequest>)
                    fun enqueueUniqueWork(name: String, request: PeriodicWorkRequest)
                 }
            """
    )
        .indented().within("src")
}
