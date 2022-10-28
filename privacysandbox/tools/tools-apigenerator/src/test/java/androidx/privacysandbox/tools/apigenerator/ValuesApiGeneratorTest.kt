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

package androidx.privacysandbox.tools.apigenerator

import java.io.File
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ValuesApiGeneratorTest : BaseApiGeneratorTest() {
    override val inputDirectory = File("src/test/test-data/values/input")
    override val outputDirectory = File("src/test/test-data/values/output")
    override val relativePathsToExpectedAidlClasses = listOf(
        "com/sdkwithvalues/IMyInterface.java",
        "com/sdkwithvalues/ISdkInterface.java",
        "com/sdkwithvalues/ISdkResponseTransactionCallback.java",
        "com/sdkwithvalues/ParcelableInnerSdkValue.java",
        "com/sdkwithvalues/ParcelableSdkRequest.java",
        "com/sdkwithvalues/ParcelableSdkResponse.java",
        "com/sdkwithvalues/ICancellationSignal.java",
        "com/sdkwithvalues/ParcelableStackFrame.java",
        "com/sdkwithvalues/PrivacySandboxThrowableParcel.java",
    )
}
