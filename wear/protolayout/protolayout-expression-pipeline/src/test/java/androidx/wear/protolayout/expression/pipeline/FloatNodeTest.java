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

import static org.robolectric.Shadows.shadowOf;

import static java.lang.Integer.MAX_VALUE;

import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.wear.protolayout.expression.pipeline.FloatNodes.AnimatableFixedFloatNode;
import androidx.wear.protolayout.expression.pipeline.FloatNodes.ArithmeticFloatNode;
import androidx.wear.protolayout.expression.pipeline.FloatNodes.DynamicAnimatedFloatNode;
import androidx.wear.protolayout.expression.pipeline.FloatNodes.FixedFloatNode;
import androidx.wear.protolayout.expression.pipeline.FloatNodes.Int32ToFloatNode;
import androidx.wear.protolayout.expression.pipeline.FloatNodes.StateFloatSourceNode;
import androidx.wear.protolayout.expression.pipeline.Int32Nodes.StateInt32SourceNode;
import androidx.wear.protolayout.expression.proto.AnimationParameterProto.AnimationSpec;
import androidx.wear.protolayout.expression.proto.DynamicProto.AnimatableFixedFloat;
import androidx.wear.protolayout.expression.proto.DynamicProto.ArithmeticFloatOp;
import androidx.wear.protolayout.expression.proto.DynamicProto.ArithmeticOpType;
import androidx.wear.protolayout.expression.proto.DynamicProto.StateFloatSource;
import androidx.wear.protolayout.expression.proto.DynamicProto.StateInt32Source;
import androidx.wear.protolayout.expression.proto.FixedProto.FixedFloat;
import androidx.wear.protolayout.expression.proto.FixedProto.FixedInt32;
import androidx.wear.protolayout.expression.proto.StateEntryProto.StateEntryValue;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class FloatNodeTest {

    @Test
    public void fixedFloatNodesTest() {
        List<Float> results = new ArrayList<>();
        float testValue = 6.6f;

        FixedFloat protoNode = FixedFloat.newBuilder().setValue(testValue).build();
        FixedFloatNode node = new FixedFloatNode(protoNode, new AddToListCallback<>(results));

        node.preInit();
        node.init();

        assertThat(results).containsExactly(testValue);
    }

    @Test
    public void stateFloatSourceNodeTest() {
        List<Float> results = new ArrayList<>();
        float testValue = 6.6f;
        StateStore oss =
                new StateStore(
                        ImmutableMap.of(
                                "foo",
                                StateEntryValue.newBuilder()
                                        .setFloatVal(FixedFloat.newBuilder().setValue(testValue))
                                        .build()));

        StateFloatSource protoNode = StateFloatSource.newBuilder().setSourceKey("foo").build();
        StateFloatSourceNode node =
                new StateFloatSourceNode(oss, protoNode, new AddToListCallback<>(results));

        node.preInit();
        node.init();

        assertThat(results).containsExactly(testValue);
    }

    @Test
    public void stateFloatSourceNode_updatesWithStateChanges() {
        List<Float> results = new ArrayList<>();
        float oldValue = 6.5f;
        float newValue = 7.8f;

        StateStore oss =
                new StateStore(
                        ImmutableMap.of(
                                "foo",
                                StateEntryValue.newBuilder()
                                        .setFloatVal(FixedFloat.newBuilder().setValue(oldValue))
                                        .build()));

        StateFloatSource protoNode = StateFloatSource.newBuilder().setSourceKey("foo").build();
        StateFloatSourceNode node =
                new StateFloatSourceNode(oss, protoNode, new AddToListCallback<>(results));

        node.preInit();
        node.init();
        assertThat(results).containsExactly(oldValue);

        oss.setStateEntryValuesProto(
                ImmutableMap.of(
                        "foo",
                        StateEntryValue.newBuilder()
                                .setFloatVal(FixedFloat.newBuilder().setValue(newValue))
                                .build()));

        assertThat(results).containsExactly(oldValue, newValue).inOrder();
    }

    @Test
    public void stateFloatSourceNode_noUpdatesAfterDestroy() {
        List<Float> results = new ArrayList<>();
        float oldValue = 6.5f;
        float newValue = 7.8f;

        StateStore oss =
                new StateStore(
                        ImmutableMap.of(
                                "foo",
                                StateEntryValue.newBuilder()
                                        .setFloatVal(FixedFloat.newBuilder().setValue(oldValue))
                                        .build()));

        StateFloatSource protoNode = StateFloatSource.newBuilder().setSourceKey("foo").build();
        StateFloatSourceNode node =
                new StateFloatSourceNode(oss, protoNode, new AddToListCallback<>(results));

        node.preInit();
        node.init();
        assertThat(results).containsExactly(oldValue);

        results.clear();
        node.destroy();

        oss.setStateEntryValuesProto(
                ImmutableMap.of(
                        "foo",
                        StateEntryValue.newBuilder()
                                .setFloatVal(FixedFloat.newBuilder().setValue(newValue))
                                .build()));

        assertThat(results).isEmpty();
    }

    @Test
    public void arithmeticFloat_add() {
        List<Float> results = new ArrayList<>();
        ArithmeticFloatOp protoNode =
                ArithmeticFloatOp.newBuilder()
                        .setOperationType(ArithmeticOpType.ARITHMETIC_OP_TYPE_ADD)
                        .build();

        ArithmeticFloatNode node = new ArithmeticFloatNode(protoNode,
                new AddToListCallback<>(results));

        float lhsValue = 6.6f;
        FixedFloat lhsProtoNode = FixedFloat.newBuilder().setValue(lhsValue).build();
        FixedFloatNode lhsNode = new FixedFloatNode(lhsProtoNode, node.getLhsIncomingCallback());
        lhsNode.init();

        float oldRhsValue = 6.5f;
        StateStore oss =
                new StateStore(
                        ImmutableMap.of(
                                "foo",
                                StateEntryValue.newBuilder()
                                        .setFloatVal(FixedFloat.newBuilder().setValue(oldRhsValue))
                                        .build()));
        StateFloatSource rhsProtoNode = StateFloatSource.newBuilder().setSourceKey("foo").build();
        StateFloatSourceNode rhsNode =
                new StateFloatSourceNode(oss, rhsProtoNode, node.getRhsIncomingCallback());

        rhsNode.preInit();
        rhsNode.init();

        assertThat(results).containsExactly(lhsValue + oldRhsValue);

        float newRhsValue = 7.8f;
        oss.setStateEntryValuesProto(
                ImmutableMap.of(
                        "foo",
                        StateEntryValue.newBuilder()
                                .setFloatVal(FixedFloat.newBuilder().setValue(newRhsValue))
                                .build()));
        assertThat(results).containsExactly(lhsValue + oldRhsValue,
                lhsValue + newRhsValue).inOrder();
    }

    @Test
    public void int32ToFloatTest() {
        List<Float> results = new ArrayList<>();
        Int32ToFloatNode node = new Int32ToFloatNode(new AddToListCallback<>(results));

        int oldIntValue = 65;
        StateStore oss =
                new StateStore(
                        ImmutableMap.of(
                                "foo",
                                StateEntryValue.newBuilder()
                                        .setInt32Val(FixedInt32.newBuilder().setValue(oldIntValue))
                                        .build()));

        StateInt32Source protoNode = StateInt32Source.newBuilder().setSourceKey("foo").build();
        StateInt32SourceNode intNode =
                new StateInt32SourceNode(oss, protoNode, node.getIncomingCallback());

        intNode.preInit();
        intNode.init();

        assertThat(results).containsExactly((float) oldIntValue);

        int newIntValue = 12;
        oss.setStateEntryValuesProto(
                ImmutableMap.of(
                        "foo",
                        StateEntryValue.newBuilder()
                                .setInt32Val(FixedInt32.newBuilder().setValue(newIntValue))
                                .build()));

        assertThat(results).containsExactly((float) oldIntValue, (float) newIntValue).inOrder();
    }

    @Test
    public void animatableFixedFloat_animates() {
        float startValue = 3.0f;
        float endValue = 33.0f;
        List<Float> results = new ArrayList<>();
        QuotaManager quotaManager = new FixedQuotaManagerImpl(MAX_VALUE);
        AnimatableFixedFloat protoNode =
                AnimatableFixedFloat.newBuilder().setFromValue(startValue).setToValue(
                        endValue).build();
        AnimatableFixedFloatNode node =
                new AnimatableFixedFloatNode(protoNode, new AddToListCallback<>(results),
                        quotaManager);
        node.setVisibility(true);

        node.preInit();
        node.init();
        shadowOf(Looper.getMainLooper()).idle();

        assertThat(results.size()).isGreaterThan(2);
        assertThat(results.get(0)).isEqualTo(startValue);
        assertThat(Iterables.getLast(results)).isEqualTo(endValue);
    }

    @Test
    public void animatableFixedFloat_whenInvisible_skipToEnd() {
        float startValue = 3.0f;
        float endValue = 33.0f;
        List<Float> results = new ArrayList<>();
        QuotaManager quotaManager = new FixedQuotaManagerImpl(MAX_VALUE);
        AnimatableFixedFloat protoNode =
                AnimatableFixedFloat.newBuilder().setFromValue(startValue).setToValue(
                        endValue).build();
        AnimatableFixedFloatNode node =
                new AnimatableFixedFloatNode(protoNode, new AddToListCallback<>(results),
                        quotaManager);
        node.setVisibility(false);

        node.preInit();
        node.init();
        shadowOf(Looper.getMainLooper()).idle();

        assertThat(results).hasSize(1);
        assertThat(results).containsExactly(endValue);
    }

    @Test
    public void animatableFixedFloat_whenNoQuota_skip() {
        float startValue = 3.0f;
        float endValue = 33.0f;
        List<Float> results = new ArrayList<>();
        QuotaManager quotaManager = new FixedQuotaManagerImpl(0);
        AnimatableFixedFloat protoNode =
                AnimatableFixedFloat.newBuilder().setFromValue(startValue).setToValue(
                        endValue).build();
        AnimatableFixedFloatNode node =
                new AnimatableFixedFloatNode(protoNode, new AddToListCallback<>(results),
                        quotaManager);
        node.setVisibility(true);

        node.preInit();
        node.init();
        shadowOf(Looper.getMainLooper()).idle();

        assertThat(results).hasSize(1);
        assertThat(results).containsExactly(endValue);
    }

    @Test
    public void dynamicAnimatedFloat_onlyAnimateWhenVisible() {
        float value1 = 3.0f;
        float value2 = 11.0f;
        float value3 = 17.0f;
        List<Float> results = new ArrayList<>();
        QuotaManager quotaManager = new FixedQuotaManagerImpl(MAX_VALUE);
        StateStore oss =
                new StateStore(
                        ImmutableMap.of(
                                "foo",
                                StateEntryValue.newBuilder()
                                        .setFloatVal(
                                                FixedFloat.newBuilder().setValue(value1).build())
                                        .build()));
        DynamicAnimatedFloatNode floatNode =
                new DynamicAnimatedFloatNode(
                        new AddToListCallback<>(results), AnimationSpec.getDefaultInstance(),
                        quotaManager);
        floatNode.setVisibility(false);
        StateFloatSourceNode stateNode =
                new StateFloatSourceNode(
                        oss,
                        StateFloatSource.newBuilder().setSourceKey("foo").build(),
                        floatNode.getInputCallback());

        stateNode.preInit();
        stateNode.init();

        results.clear();
        oss.setStateEntryValuesProto(
                ImmutableMap.of(
                        "foo",
                        StateEntryValue.newBuilder()
                                .setFloatVal(FixedFloat.newBuilder().setValue(value2))
                                .build()));
        shadowOf(Looper.getMainLooper()).idle();

        // Only contains last value.
        assertThat(results).hasSize(1);
        assertThat(results).containsExactly(value2);

        floatNode.setVisibility(true);
        results.clear();
        oss.setStateEntryValuesProto(
                ImmutableMap.of(
                        "foo",
                        StateEntryValue.newBuilder()
                                .setFloatVal(FixedFloat.newBuilder().setValue(value3))
                                .build()));
        shadowOf(Looper.getMainLooper()).idle();

        // Contains intermediate values besides the initial and last.
        assertThat(results.size()).isGreaterThan(2);
        assertThat(results.get(0)).isEqualTo(value2);
        assertThat(Iterables.getLast(results)).isEqualTo(value3);
        assertThat(results).isInOrder();
    }
}
