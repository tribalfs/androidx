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

package androidx.wear.protolayout.expression;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInt32;
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicFloat;

@RunWith(RobolectricTestRunner.class)
public final class DynamicInt32Test {
    private static final String STATE_KEY = "state-key";
    private static final int CONSTANT_VALUE = 42;

    @Test
    public void constantInt32() {
        DynamicInt32 constantInt32 = DynamicInt32.constant(CONSTANT_VALUE);

        assertThat(constantInt32.toDynamicInt32Proto().getFixed().getValue())
                .isEqualTo(CONSTANT_VALUE);
    }

    @Test
    public void stateEntryValueInt32() {
        DynamicInt32 stateInt32 = DynamicInt32.fromState(STATE_KEY);

        assertThat(stateInt32.toDynamicInt32Proto().getStateSource().getSourceKey()).isEqualTo(
                STATE_KEY);
    }

    @Test
    public void constantInt32_asFloat() {
        DynamicInt32 constantInt32 = DynamicInt32.constant(CONSTANT_VALUE);

        DynamicFloat dynamicFloat = constantInt32.asFloat();

        assertThat(dynamicFloat.toDynamicFloatProto().getInt32ToFloatOperation()
                .getInput().getFixed().getValue()).isEqualTo(CONSTANT_VALUE);
    }
}
