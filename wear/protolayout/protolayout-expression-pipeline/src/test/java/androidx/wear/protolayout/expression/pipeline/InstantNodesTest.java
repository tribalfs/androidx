/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.wear.protolayout.expression.pipeline;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.wear.protolayout.expression.pipeline.InstantNodes.FixedInstantNode;
import androidx.wear.protolayout.expression.pipeline.InstantNodes.PlatformTimeSourceNode;
import androidx.wear.protolayout.expression.proto.FixedProto.FixedInstant;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class InstantNodesTest {

    @Test
    public void testFixedInstant() {
        List<Instant> results = new ArrayList<>();

        FixedInstant protoNode = FixedInstant.newBuilder().setEpochSeconds(1234567L).build();
        FixedInstantNode node = new FixedInstantNode(protoNode, new AddToListCallback<>(results));

        node.preInit();
        node.init();

        assertThat(results).containsExactly(Instant.ofEpochSecond(1234567L));
    }

    @Test
    public void testPlatformTimeSourceNodeDestroy() {
        EpochTimePlatformDataSource timeSource =
                new EpochTimePlatformDataSource(mock(PlatformTimeUpdateNotifier.class));
        List<Instant> results = new ArrayList<>();

        PlatformTimeSourceNode node =
                new PlatformTimeSourceNode(timeSource, new AddToListCallback<>(results));
        node.preInit();
        node.init();
        assertThat(timeSource.getRegisterConsumersCount()).isEqualTo(1);

        node.destroy();
        assertThat(timeSource.getRegisterConsumersCount()).isEqualTo(0);
    }
}
