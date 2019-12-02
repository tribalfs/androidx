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

package androidx.camera.core;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.internal.DoNotInstrument;

import java.util.concurrent.TimeUnit;

@SmallTest
@RunWith(RobolectricTestRunner.class)
@DoNotInstrument
public class FocusMeteringActionTest {
    private SurfaceOrientedMeteringPointFactory mPointFactory =
            new SurfaceOrientedMeteringPointFactory(1.0f, 1.0f);

    MeteringPoint mPoint1 = mPointFactory.createPoint(0, 0);
    MeteringPoint mPoint2 = mPointFactory.createPoint(1, 1);
    MeteringPoint mPoint3 = mPointFactory.createPoint(1, 0);

    @Test
    public void defaultBuilder_valueIsDefault() {
        FocusMeteringAction action = FocusMeteringAction.Builder.from(mPoint1).build();

        assertThat(action.getMeteringPointsAf()).containsExactly(mPoint1);
        assertThat(action.getMeteringPointsAe()).containsExactly(mPoint1);
        assertThat(action.getMeteringPointsAwb()).containsExactly(mPoint1);
        assertThat(action.getAutoCancelDurationInMillis()).isEqualTo(
                FocusMeteringAction.DEFAULT_AUTOCANCEL_DURATION);
        assertThat(action.isAutoCancelEnabled()).isTrue();
    }

    @Test
    public void fromPointWithAFAEAWB() {
        FocusMeteringAction action = FocusMeteringAction.Builder
                .from(mPoint1, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE
                        | FocusMeteringAction.FLAG_AWB).build();
        assertThat(action.getMeteringPointsAf()).containsExactly(mPoint1);
        assertThat(action.getMeteringPointsAe()).containsExactly(mPoint1);
        assertThat(action.getMeteringPointsAwb()).containsExactly(mPoint1);
    }

    @Test
    public void fromPointWithAFAE() {
        FocusMeteringAction action = FocusMeteringAction.Builder
                .from(mPoint1, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE).build();
        assertThat(action.getMeteringPointsAf()).containsExactly(mPoint1);
        assertThat(action.getMeteringPointsAe()).containsExactly(mPoint1);
        assertThat(action.getMeteringPointsAwb()).isEmpty();
    }

    @Test
    public void fromPointWithAFAWB() {
        FocusMeteringAction action = FocusMeteringAction.Builder
                .from(mPoint1, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AWB).build();
        assertThat(action.getMeteringPointsAf()).containsExactly(mPoint1);
        assertThat(action.getMeteringPointsAe()).isEmpty();
        assertThat(action.getMeteringPointsAwb()).containsExactly(mPoint1);
    }

    @Test
    public void fromPointWithAEAWB() {
        FocusMeteringAction action = FocusMeteringAction.Builder
                .from(mPoint1, FocusMeteringAction.FLAG_AE | FocusMeteringAction.FLAG_AWB).build();
        assertThat(action.getMeteringPointsAf()).isEmpty();
        assertThat(action.getMeteringPointsAe()).containsExactly(mPoint1);
        assertThat(action.getMeteringPointsAwb()).containsExactly(mPoint1);
    }

    @Test
    public void fromPointWithAF() {
        FocusMeteringAction action = FocusMeteringAction.Builder
                .from(mPoint1, FocusMeteringAction.FLAG_AF).build();
        assertThat(action.getMeteringPointsAf()).containsExactly(mPoint1);
        assertThat(action.getMeteringPointsAe()).isEmpty();
        assertThat(action.getMeteringPointsAwb()).isEmpty();
    }

    @Test
    public void fromPointWithAE() {
        FocusMeteringAction action = FocusMeteringAction.Builder
                .from(mPoint1, FocusMeteringAction.FLAG_AE).build();
        assertThat(action.getMeteringPointsAf()).isEmpty();
        assertThat(action.getMeteringPointsAe()).containsExactly(mPoint1);
        assertThat(action.getMeteringPointsAwb()).isEmpty();
    }

    @Test
    public void fromPointWithAWB() {
        FocusMeteringAction action = FocusMeteringAction.Builder
                .from(mPoint1, FocusMeteringAction.FLAG_AWB).build();
        assertThat(action.getMeteringPointsAf()).isEmpty();
        assertThat(action.getMeteringPointsAe()).isEmpty();
        assertThat(action.getMeteringPointsAwb()).containsExactly(mPoint1);
    }

    @Test
    public void multiplePointsWithDefaultMeteringMode() {
        FocusMeteringAction action = FocusMeteringAction.Builder.from(mPoint1)
                .addPoint(mPoint2)
                .addPoint(mPoint3)
                .build();
        assertThat(action.getMeteringPointsAf()).containsExactly(mPoint1, mPoint2, mPoint3);
        assertThat(action.getMeteringPointsAe()).containsExactly(mPoint1, mPoint2, mPoint3);
        assertThat(action.getMeteringPointsAwb()).containsExactly(mPoint1, mPoint2, mPoint3);
    }

    @Test
    public void multiplePointsWithSameAF_AE_AWB() {
        FocusMeteringAction action = FocusMeteringAction.Builder
                .from(mPoint1,
                        FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE
                                | FocusMeteringAction.FLAG_AWB)
                .addPoint(mPoint2,
                        FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE
                                | FocusMeteringAction.FLAG_AWB)
                .addPoint(mPoint3,
                        FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE
                                | FocusMeteringAction.FLAG_AWB)
                .build();
        assertThat(action.getMeteringPointsAf()).containsExactly(mPoint1, mPoint2, mPoint3);
        assertThat(action.getMeteringPointsAe()).containsExactly(mPoint1, mPoint2, mPoint3);
        assertThat(action.getMeteringPointsAwb()).containsExactly(mPoint1, mPoint2, mPoint3);
    }

    @Test
    public void multiplePointsWithSameAF_AE() {
        FocusMeteringAction action = FocusMeteringAction.Builder
                .from(mPoint1, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE)
                .addPoint(mPoint2, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE)
                .addPoint(mPoint3, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE)
                .build();
        assertThat(action.getMeteringPointsAf()).containsExactly(mPoint1, mPoint2, mPoint3);
        assertThat(action.getMeteringPointsAe()).containsExactly(mPoint1, mPoint2, mPoint3);
        assertThat(action.getMeteringPointsAwb()).isEmpty();
    }

    @Test
    public void multiplePointsWithSameAE_AWB() {
        FocusMeteringAction action = FocusMeteringAction.Builder
                .from(mPoint1, FocusMeteringAction.FLAG_AE | FocusMeteringAction.FLAG_AWB)
                .addPoint(mPoint2, FocusMeteringAction.FLAG_AE | FocusMeteringAction.FLAG_AWB)
                .addPoint(mPoint3, FocusMeteringAction.FLAG_AE | FocusMeteringAction.FLAG_AWB)
                .build();
        assertThat(action.getMeteringPointsAf()).isEmpty();
        assertThat(action.getMeteringPointsAe()).containsExactly(mPoint1, mPoint2, mPoint3);
        assertThat(action.getMeteringPointsAwb()).containsExactly(mPoint1, mPoint2, mPoint3);
    }

    @Test
    public void multiplePointsWithSameAF_AWB() {
        FocusMeteringAction action = FocusMeteringAction.Builder
                .from(mPoint1, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AWB)
                .addPoint(mPoint2, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AWB)
                .addPoint(mPoint3, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AWB)
                .build();
        assertThat(action.getMeteringPointsAf()).containsExactly(mPoint1, mPoint2, mPoint3);
        assertThat(action.getMeteringPointsAe()).isEmpty();
        assertThat(action.getMeteringPointsAwb()).containsExactly(mPoint1, mPoint2, mPoint3);
    }

    @Test
    public void multiplePointsWithSameAWBOnly() {
        FocusMeteringAction action = FocusMeteringAction.Builder.from(mPoint1,
                FocusMeteringAction.FLAG_AWB)
                .addPoint(mPoint2, FocusMeteringAction.FLAG_AWB)
                .addPoint(mPoint3, FocusMeteringAction.FLAG_AWB)
                .build();
        assertThat(action.getMeteringPointsAf()).isEmpty();
        assertThat(action.getMeteringPointsAe()).isEmpty();
        assertThat(action.getMeteringPointsAwb()).containsExactly(mPoint1, mPoint2, mPoint3);
    }

    @Test
    public void multiplePointsWithSameAEOnly() {
        FocusMeteringAction action = FocusMeteringAction.Builder.from(mPoint1,
                FocusMeteringAction.FLAG_AE)
                .addPoint(mPoint2, FocusMeteringAction.FLAG_AE)
                .addPoint(mPoint3, FocusMeteringAction.FLAG_AE)
                .build();
        assertThat(action.getMeteringPointsAf()).isEmpty();
        assertThat(action.getMeteringPointsAe()).containsExactly(mPoint1, mPoint2, mPoint3);
        assertThat(action.getMeteringPointsAwb()).isEmpty();
    }

    @Test
    public void multiplePointsWithSameAFOnly() {
        FocusMeteringAction action = FocusMeteringAction.Builder.from(mPoint1,
                FocusMeteringAction.FLAG_AF)
                .addPoint(mPoint2, FocusMeteringAction.FLAG_AF)
                .addPoint(mPoint3, FocusMeteringAction.FLAG_AF)
                .build();
        assertThat(action.getMeteringPointsAf()).containsExactly(mPoint1, mPoint2, mPoint3);
        assertThat(action.getMeteringPointsAe()).isEmpty();
        assertThat(action.getMeteringPointsAwb()).isEmpty();
    }

    @Test
    public void multiplePointsWithAFOnly_AEOnly_AWBOnly() {
        FocusMeteringAction action = FocusMeteringAction.Builder.from(mPoint1,
                FocusMeteringAction.FLAG_AF)
                .addPoint(mPoint2, FocusMeteringAction.FLAG_AE)
                .addPoint(mPoint3, FocusMeteringAction.FLAG_AWB)
                .build();
        assertThat(action.getMeteringPointsAf()).containsExactly(mPoint1);
        assertThat(action.getMeteringPointsAe()).containsExactly(mPoint2);
        assertThat(action.getMeteringPointsAwb()).containsExactly(mPoint3);
    }

    @Test
    public void multiplePointsWithAFAE_AEAWB_AFAWB() {
        FocusMeteringAction action = FocusMeteringAction.Builder.from(mPoint1,
                FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE)
                .addPoint(mPoint2, FocusMeteringAction.FLAG_AE | FocusMeteringAction.FLAG_AWB)
                .addPoint(mPoint3, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AWB)
                .build();
        assertThat(action.getMeteringPointsAf()).containsExactly(mPoint1, mPoint3);
        assertThat(action.getMeteringPointsAe()).containsExactly(mPoint2, mPoint1);
        assertThat(action.getMeteringPointsAwb()).containsExactly(mPoint3, mPoint2);
    }

    @Test
    public void multiplePointsWithAFAEAWB_AEAWB_AFOnly() {
        FocusMeteringAction action = FocusMeteringAction.Builder.from(mPoint1,
                FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE
                        | FocusMeteringAction.FLAG_AWB)
                .addPoint(mPoint2, FocusMeteringAction.FLAG_AE | FocusMeteringAction.FLAG_AWB)
                .addPoint(mPoint3, FocusMeteringAction.FLAG_AF)
                .build();
        assertThat(action.getMeteringPointsAf()).containsExactly(mPoint1, mPoint3);
        assertThat(action.getMeteringPointsAe()).containsExactly(mPoint1, mPoint2);
        assertThat(action.getMeteringPointsAwb()).containsExactly(mPoint1, mPoint2);
    }

    @Test
    public void multiplePointsWithAEOnly_AFAWAEB_AEOnly() {
        FocusMeteringAction action = FocusMeteringAction.Builder.from(mPoint1,
                FocusMeteringAction.FLAG_AE)
                .addPoint(mPoint2,
                        FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE
                                | FocusMeteringAction.FLAG_AWB)
                .addPoint(mPoint3, FocusMeteringAction.FLAG_AE)
                .build();
        assertThat(action.getMeteringPointsAf()).containsExactly(mPoint2);
        assertThat(action.getMeteringPointsAe()).containsExactly(mPoint1, mPoint2, mPoint3);
        assertThat(action.getMeteringPointsAwb()).containsExactly(mPoint2);
    }

    @Test
    public void setAutoCancelDurationBySeconds() {
        FocusMeteringAction action = FocusMeteringAction.Builder.from(mPoint1)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build();
        assertThat(action.getAutoCancelDurationInMillis()).isEqualTo(3000);
    }

    @Test
    public void setAutoCancelDurationByMinutes() {
        FocusMeteringAction action = FocusMeteringAction.Builder.from(mPoint1)
                .setAutoCancelDuration(2, TimeUnit.MINUTES)
                .build();
        assertThat(action.getAutoCancelDurationInMillis()).isEqualTo(120000);
    }

    @Test
    public void setAutoCancelDurationByMilliseconds() {
        FocusMeteringAction action = FocusMeteringAction.Builder.from(mPoint1)
                .setAutoCancelDuration(1500, TimeUnit.MILLISECONDS)
                .build();
        assertThat(action.getAutoCancelDurationInMillis()).isEqualTo(1500);
    }

    @Test
    public void setAutoCancelDurationLargerThan0_shouldEnableAutoCancel() {
        FocusMeteringAction action = FocusMeteringAction.Builder.from(mPoint1)
                .setAutoCancelDuration(1, TimeUnit.MILLISECONDS)
                .build();

        assertThat(action.isAutoCancelEnabled()).isTrue();
    }

    @Test(expected = IllegalArgumentException.class)
    public void setAutoCancelDuration0_shouldThrowException() {
        FocusMeteringAction action = FocusMeteringAction.Builder.from(mPoint1)
                .setAutoCancelDuration(0, TimeUnit.MILLISECONDS)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void setAutoCancelDurationSmallerThan0_shouldThrowException() {
        FocusMeteringAction action = FocusMeteringAction.Builder.from(mPoint1)
                .setAutoCancelDuration(-1, TimeUnit.MILLISECONDS)
                .build();
    }
}
