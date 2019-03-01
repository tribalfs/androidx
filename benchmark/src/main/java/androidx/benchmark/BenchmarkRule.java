/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.benchmark;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Use this rule to make sure we report the status after the test success.
 *
 * <pre>
 * {@literal @}Rule public BenchmarkRule benchmarkRule = new BenchmarkRule();
 * {@literal @}Test public void functionName() {
 *     ...
 *     BenchmarkState state = benchmarkRule.getBenchmarkState();
 *     while (state.keepRunning()) {
 *         // DO YOUR TEST HERE!
 *     }
 *     ...
 * }
 * </pre>
 *
 * When test succeeded, the status report will use the key as
 * "functionName[optional subTestName]_*"
 *
 * Notice that optional subTestName can't be just numbers, that means each sub test needs to have a
 * name when using parameterization.
 */

public class BenchmarkRule implements TestRule {
    private static final String TAG = "BenchmarkRule";
    @SuppressWarnings("WeakerAccess") // synthetic access
    final BenchmarkState mState = new BenchmarkState();
    @SuppressWarnings("WeakerAccess") // synthetic access
    boolean mApplied = false;

    @NonNull
    public BenchmarkState getState() {
        if (!mApplied) {
            throw new IllegalStateException("Cannot get state before BenchmarkRule is applied"
                    + " to a test. Check that your BenchmarkRule is annotated correctly"
                    + " (@Rule in Java, @get:Rule in Kotlin).");
        }
        return mState;
    }

    @NonNull
    @Override
    public Statement apply(@NonNull final Statement base, @NonNull final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                mApplied = true;
                String invokeMethodName = description.getMethodName();
                Log.i(TAG, "Running " + description.getClassName() + "#" + invokeMethodName);

                // validate and simplify the function name.
                // First, remove the "test" prefix which normally comes from CTS test.
                // Then make sure the [subTestName] is valid, not just numbers like [0].
                if (invokeMethodName.startsWith("test")) {
                    assertTrue("The test name " + invokeMethodName + " is too short",
                            invokeMethodName.length() > 5);
                    invokeMethodName = invokeMethodName.substring(4, 5).toLowerCase()
                            + invokeMethodName.substring(5);
                }

                int index = invokeMethodName.lastIndexOf('[');
                if (index > 0) {
                    boolean allDigits = true;
                    for (int i = index + 1; i < invokeMethodName.length() - 1; i++) {
                        if (!Character.isDigit(invokeMethodName.charAt(i))) {
                            allDigits = false;
                            break;
                        }
                    }
                    assertFalse("The name in [] can't contain only digits for " + invokeMethodName,
                            allDigits);
                }

                base.evaluate();

                InstrumentationRegistry.getInstrumentation().sendStatus(Activity.RESULT_OK,
                        mState.getFullStatusReport(invokeMethodName));
            }
        };
    }
}

