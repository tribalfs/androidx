/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.appsearch.localstorage;

import static androidx.appsearch.localstorage.FrameworkOptimizeStrategy.BYTES_OPTIMIZE_THRESHOLD;
import static androidx.appsearch.localstorage.FrameworkOptimizeStrategy.DOC_COUNT_OPTIMIZE_THRESHOLD;
import static androidx.appsearch.localstorage.FrameworkOptimizeStrategy.TIME_OPTIMIZE_THRESHOLD_MILLIS;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.icing.proto.GetOptimizeInfoResultProto;
import com.google.android.icing.proto.StatusProto;

import org.junit.Test;

public class FrameworkOptimizeStrategyTest {
    FrameworkOptimizeStrategy mFrameworkOptimizeStrategy = new FrameworkOptimizeStrategy();

    @Test
    public void testShouldOptimize_docCountThreshold() {
        GetOptimizeInfoResultProto optimizeInfo = GetOptimizeInfoResultProto.newBuilder()
                .setTimeSinceLastOptimizeMs(0)
                .setEstimatedOptimizableBytes(BYTES_OPTIMIZE_THRESHOLD)
                .setOptimizableDocs(0)
                .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.OK).build())
                .build();
        assertThat(mFrameworkOptimizeStrategy.shouldOptimize(optimizeInfo)).isTrue();
    }

    @Test
    public void testShouldOptimize_byteThreshold() {
        GetOptimizeInfoResultProto optimizeInfo = GetOptimizeInfoResultProto.newBuilder()
                .setTimeSinceLastOptimizeMs(TIME_OPTIMIZE_THRESHOLD_MILLIS)
                .setEstimatedOptimizableBytes(0)
                .setOptimizableDocs(0)
                .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.OK).build())
                .build();
        assertThat(mFrameworkOptimizeStrategy.shouldOptimize(optimizeInfo)).isTrue();
    }

    @Test
    public void testShouldNotOptimize_timeThreshold() {
        GetOptimizeInfoResultProto optimizeInfo = GetOptimizeInfoResultProto.newBuilder()
                .setTimeSinceLastOptimizeMs(0)
                .setEstimatedOptimizableBytes(0)
                .setOptimizableDocs(DOC_COUNT_OPTIMIZE_THRESHOLD)
                .setStatus(StatusProto.newBuilder().setCode(StatusProto.Code.OK).build())
                .build();
        assertThat(mFrameworkOptimizeStrategy.shouldOptimize(optimizeInfo)).isTrue();
    }
}
