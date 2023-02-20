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

package androidx.wear.protolayout.renderer.dynamicdata;

import static androidx.core.util.Preconditions.checkNotNull;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.annotation.SuppressLint;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.icu.util.ULocale;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.collection.ArrayMap;
import androidx.collection.ArraySet;
import androidx.vectordrawable.graphics.drawable.SeekableAnimatedVectorDrawable;
import androidx.wear.protolayout.expression.pipeline.BoundDynamicType;
import androidx.wear.protolayout.expression.pipeline.DynamicTypeEvaluator;
import androidx.wear.protolayout.expression.pipeline.DynamicTypeValueReceiver;
import androidx.wear.protolayout.expression.pipeline.FixedQuotaManagerImpl;
import androidx.wear.protolayout.expression.pipeline.ObservableStateStore;
import androidx.wear.protolayout.expression.pipeline.QuotaManager;
import androidx.wear.protolayout.expression.pipeline.sensor.SensorGateway;
import androidx.wear.protolayout.expression.proto.DynamicProto.DynamicBool;
import androidx.wear.protolayout.expression.proto.DynamicProto.DynamicColor;
import androidx.wear.protolayout.expression.proto.DynamicProto.DynamicFloat;
import androidx.wear.protolayout.expression.proto.DynamicProto.DynamicInt32;
import androidx.wear.protolayout.expression.proto.DynamicProto.DynamicString;
import androidx.wear.protolayout.proto.ColorProto.ColorProp;
import androidx.wear.protolayout.proto.DimensionProto.DegreesProp;
import androidx.wear.protolayout.proto.DimensionProto.DpProp;
import androidx.wear.protolayout.proto.ModifiersProto.AnimatedVisibility;
import androidx.wear.protolayout.proto.ModifiersProto.EnterTransition;
import androidx.wear.protolayout.proto.ModifiersProto.ExitTransition;
import androidx.wear.protolayout.proto.TriggerProto.Trigger;
import androidx.wear.protolayout.renderer.dynamicdata.NodeInfo.ResolvedAvd;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Pipeline for dynamic data.
 *
 * <p>Given a dynamic ProtoLayout data source, this builds up a {@link BoundDynamicType}, which can
 * source the required data, and transform it into its final form.
 *
 * @hide
 */
@RestrictTo(Scope.LIBRARY_GROUP)
public class ProtoLayoutDynamicDataPipeline {
    @NonNull private static final String TAG = "DynamicDataPipeline";

    @NonNull
    private static final QuotaManager DISABLED_ANIMATIONS_QUOTA_MANAGER =
            new FixedQuotaManagerImpl(/* quotaCap= */ 0);

    @NonNull final PositionIdTree<NodeInfo> mPositionIdTree = new PositionIdTree<>();
    @NonNull final List<QuotaAwareAnimationSet> mEnterAnimations = new ArrayList<>();
    @NonNull final List<QuotaAwareAnimationSet> mExitAnimations = new ArrayList<>();
    final boolean mEnableAnimations;
    boolean mFullyVisible;
    @NonNull final QuotaManager mAnimationQuotaManager;
    @NonNull private final DynamicTypeEvaluator mEvaluator;

    /**
     * Creates a {@link ProtoLayoutDynamicDataPipeline} without animation support.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public ProtoLayoutDynamicDataPipeline(
            boolean canUpdateGateways,
            @Nullable SensorGateway sensorGateway,
            @NonNull ObservableStateStore stateStore) {
        // Build pipeline with quota that doesn't allow any animations.
        this(
                canUpdateGateways,
                sensorGateway,
                stateStore,
                /* enableAnimations= */ false,
                DISABLED_ANIMATIONS_QUOTA_MANAGER);
    }

    /**
     * Creates a {@link ProtoLayoutDynamicDataPipeline} with animation support. Maximum number of
     * concurrently running animations is defined in the given {@link QuotaManager}.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public ProtoLayoutDynamicDataPipeline(
            boolean canUpdateGateways,
            @Nullable SensorGateway sensorGateway,
            @NonNull ObservableStateStore stateStore,
            @NonNull QuotaManager animationQuotaManager) {
        this(
                canUpdateGateways,
                sensorGateway,
                stateStore,
                /* enableAnimations= */ true,
                animationQuotaManager);
    }

    /** Creates a {@link ProtoLayoutDynamicDataPipeline}. */
    private ProtoLayoutDynamicDataPipeline(
            boolean canUpdateGateways,
            @Nullable SensorGateway sensorGateway,
            @NonNull ObservableStateStore stateStore,
            boolean enableAnimations,
            @NonNull QuotaManager animationQuotaManager) {
        this.mEnableAnimations = enableAnimations;
        this.mAnimationQuotaManager = animationQuotaManager;
        if (enableAnimations) {
            this.mEvaluator =
                    new DynamicTypeEvaluator(
                            canUpdateGateways, sensorGateway, stateStore, animationQuotaManager);
        } else {
            this.mEvaluator =
                    new DynamicTypeEvaluator(canUpdateGateways, sensorGateway, stateStore);
        }
    }

    /**
     * Returns the number of active dynamic types in this pipeline.
     *
     * @hide
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @RestrictTo(Scope.TESTS)
    public int size() {
        return mPositionIdTree.getAllNodes().stream().mapToInt(NodeInfo::size).sum();
    }

    @UiThread
    void clear() {
        mPositionIdTree.clear();
    }

    /** Removes all nodes that are descendants of {@code posId}. */
    @UiThread
    void removeChildNodesFor(@NonNull String posId) {
        mPositionIdTree.removeChildNodesFor(posId);
    }

    /**
     * Build {@link PipelineMaker}.
     *
     * @hide
     */
    @NonNull
    @RestrictTo(Scope.LIBRARY_GROUP)
    public PipelineMaker newPipelineMaker(
            @NonNull BiFunction<EnterTransition, View, AnimationSet> enterAnimationInflator,
            @NonNull BiFunction<ExitTransition, View, AnimationSet> exitAnimationInflator) {
        return new PipelineMaker(this, enterAnimationInflator, exitAnimationInflator, mEvaluator);
    }

    /**
     * Test version of the {@link #newPipelineMaker(BiFunction, BiFunction)} without animation
     * inflators.
     *
     * @hide
     */
    @VisibleForTesting
    @NonNull
    @RestrictTo(Scope.TESTS)
    public PipelineMaker newPipelineMaker() {
        return newPipelineMaker(
                (enterTransition, view) -> new AnimationSet(/* shareInterpolator= */ false),
                (exitTransition, view) -> new AnimationSet(/* shareInterpolator= */ false));
    }

    /**
     * Sets whether this proto layout can perform updates. If the proto layout cannot update, then
     * updates through the data pipeline (e.g. health updates) will be suppressed.
     *
     * @hide
     */
    @UiThread
    @SuppressWarnings("RestrictTo")
    @RestrictTo(Scope.LIBRARY_GROUP)
    public void setUpdatesEnabled(boolean canUpdate) {
        if (canUpdate) {
            mEvaluator.enablePlatformDataSources();
        } else {
            mEvaluator.disablePlatformDataSources();
        }
    }

    /**
     * Closes existing gateways.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @SuppressWarnings("RestrictTo")
    public void close() {
        mEvaluator.close();
    }

    /**
     * PipelineMaker for a dynamic data pipeline.
     *
     * <p>Given a dynamic ProtoLayout data source, this creates a sequence of
     * {@link BoundDynamicType} instances, which can source the required data, and transform it
     * into its final form.
     *
     * <p>The nodes are accumulated and can be committed to the pipeline.
     *
     * <p>Note that this class is not thread-safe.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static final class PipelineMaker {
        @NonNull private final ProtoLayoutDynamicDataPipeline mPipeline;

        @NonNull
        private final BiFunction<EnterTransition, View, AnimationSet> mEnterAnimationInflator;

        @NonNull
        private final BiFunction<ExitTransition, View, AnimationSet> mExitAnimationInflator;

        // Stores pending nodes that are committed to the pipeline after a successful layout update.
        @NonNull private final Map<String, NodeInfo> mPosIdToNodeInfo = new ArrayMap<>();
        @NonNull private final List<String> mNodesPendingChildrenRemoval = new ArrayList<>();
        @NonNull private final Set<String> mChangedNodes = new ArraySet<>();
        @NonNull private final Set<String> mParentsOfChangedNodes = new ArraySet<>();
        @NonNull private final DynamicTypeEvaluator mEvaluator;
        private int mExitAnimationsCounter = 0;

        PipelineMaker(
                @NonNull ProtoLayoutDynamicDataPipeline pipeline,
                @NonNull BiFunction<EnterTransition, View, AnimationSet> enterAnimationInflator,
                @NonNull BiFunction<ExitTransition, View, AnimationSet> exitAnimationInflator,
                @NonNull DynamicTypeEvaluator evaluator) {
            this.mPipeline = pipeline;
            this.mEnterAnimationInflator = enterAnimationInflator;
            this.mExitAnimationInflator = exitAnimationInflator;
            this.mEvaluator = evaluator;
        }

        /**
         * Clears the current data in the {@link ProtoLayoutDynamicDataPipeline} instance that was
         * used to create this and then commits any stored changes.
         *
         * @param parentView The parent view these nodes are being inflated into. This will be used
         *     for content transition animations.
         * @param isReattaching if True, this layout is being reattached and will skip content
         *     transition animations.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @UiThread
        public void clearDataPipelineAndCommit(
                @NonNull ViewGroup parentView, boolean isReattaching) {
            this.mPipeline.clear();
            this.commit(parentView, isReattaching);
        }

        /**
         * Plays Exit animations. This method should be called while {@code parentView} still
         * corresponds to the previous layout. Any subsequent change to the layout should be
         * schedule through the {@code onEnd} callback.
         *
         * @param parentView The parent view these nodes are being inflated into. Note that it
         *     should be attached to a window (and has gone through its layout passes).
         * @param isReattaching if True, this layout is being reattached and will skip content
         *     transition animations.
         * @param onEnd the callback to execute after all Exit animations have finished.
         * @hide
         */
        @UiThread
        @RestrictTo(Scope.LIBRARY_GROUP)
        public void playExitAnimations(
                @NonNull ViewGroup parentView, boolean isReattaching, @Nullable Runnable onEnd) {
            mPipeline.cancelContentTransitionAnimations();

            if (!isReattaching && mPipeline.mFullyVisible && mPipeline.mEnableAnimations) {
                Map<String, ExitTransition> animatingNodes = new ArrayMap<>();
                for (String parentOfChangedNodes : mParentsOfChangedNodes) {
                    mPipeline
                            .mPositionIdTree
                            .findChildrenFor(parentOfChangedNodes)
                            .forEach(
                                    node ->
                                            addAffectedExitAnimations(
                                                    node.getPosId(), animatingNodes));
                }
                for (String changedNode : mChangedNodes) {
                    addAffectedExitAnimations(changedNode, animatingNodes);
                }
                mExitAnimationsCounter = 0;
                for (Map.Entry<String, ExitTransition> animatingNode : animatingNodes.entrySet()) {
                    View associatedView = parentView.findViewWithTag(animatingNode.getKey());
                    if (associatedView != null) {
                        AnimationSet animationSet =
                                mExitAnimationInflator.apply(
                                        checkNotNull(animatingNode.getValue()), associatedView);
                        if (animationSet != null && !animationSet.getAnimations().isEmpty()) {
                            QuotaAwareAnimationSet quotaAwareAnimationSet =
                                    new QuotaAwareAnimationSet(
                                            animationSet,
                                            mPipeline.mAnimationQuotaManager,
                                            associatedView,
                                            () -> {
                                                if (onEnd != null) {
                                                    mExitAnimationsCounter--;
                                                    if (mExitAnimationsCounter == 0) {
                                                        mPipeline.mExitAnimations.clear();
                                                        onEnd.run();
                                                    }
                                                }
                                            });
                            quotaAwareAnimationSet.tryStartAnimation(
                                    () -> {
                                        mExitAnimationsCounter++;
                                        mPipeline.mExitAnimations.add(quotaAwareAnimationSet);
                                    });
                        }
                    }
                }
            }
            if (mPipeline.mExitAnimations.isEmpty() && onEnd != null) {
                // No exit animations.
                onEnd.run();
            }
        }

        private void addAffectedExitAnimations(
                @NonNull String changedNode, @NonNull Map<String, ExitTransition> animatingNodes) {
            List<NodeInfo> nodesAffectedBy =
                    mPipeline.getNodesAffectedBy(
                            changedNode,
                            node -> {
                                AnimatedVisibility animatedVisibility =
                                        node.getAnimatedVisibility();
                                return animatedVisibility != null && animatedVisibility.hasExit();
                            });
            for (NodeInfo affectedNode : nodesAffectedBy) {
                animatingNodes.putIfAbsent(
                        affectedNode.getPosId(),
                        checkNotNull(affectedNode.getAnimatedVisibility()).getExit());
            }
        }

        /**
         * Commits any stored changes into the {@link ProtoLayoutDynamicDataPipeline} instance that
         * was used to create this. This replaces any already available node and should be called
         * only once per layout update.
         *
         * @param parentView The parent view these nodes are being inflated into. This will be used
         *     for Enter animations. If this view is not attached to a window, the animations (and
         *     the rest of pipeline init) will be scheduled to run when the view attaches to a
         *     window later
         * @param isReattaching if True, this layout is being reattached and will skip content
         *     transition animations.
         * @hide
         */
        @UiThread
        @RestrictTo(Scope.LIBRARY_GROUP)
        public void commit(@NonNull ViewGroup parentView, boolean isReattaching) {
            for (String nodePosId : mNodesPendingChildrenRemoval) {
                mPipeline.removeChildNodesFor(nodePosId);
            }
            mNodesPendingChildrenRemoval.clear();
            for (Entry<String, NodeInfo> entry : mPosIdToNodeInfo.entrySet()) {
                String key = entry.getKey();
                if (key.isEmpty()) {
                    Log.e(TAG, "Ignoring empty posId.");
                    continue;
                }
                mPipeline.mPositionIdTree.addOrReplace(key, entry.getValue());
            }

            // in the modified levels.
            if (isReattaching || !mPipeline.mFullyVisible) {
                // Skip content transition animations.
                mChangedNodes.clear();
            }
            parentView.post(
                    () -> {
                        mPipeline.initNewLayout();
                        playEnterAnimations(parentView, isReattaching);
                    });
        }

        @UiThread
        private void playEnterAnimations(@NonNull ViewGroup parentView, boolean isReattaching) {
            // Cancel any already running Enter animation.
            mPipeline.mEnterAnimations.forEach(QuotaAwareAnimationSet::cancelAnimations);
            mPipeline.mEnterAnimations.clear();

            if (isReattaching || !mPipeline.mFullyVisible || !mPipeline.mEnableAnimations) {
                return;
            }
            Map<String, EnterTransition> animatingNodes = new ArrayMap<>();
            for (String changedNode : mChangedNodes) {
                List<NodeInfo> nodesAffectedBy =
                        mPipeline.getNodesAffectedBy(
                                changedNode,
                                node -> {
                                    AnimatedVisibility animatedVisibility =
                                            node.getAnimatedVisibility();
                                    return animatedVisibility != null
                                            && animatedVisibility.hasEnter();
                                });
                for (NodeInfo affectedNode : nodesAffectedBy) {
                    animatingNodes.putIfAbsent(
                            affectedNode.getPosId(),
                            checkNotNull(affectedNode.getAnimatedVisibility()).getEnter());
                }
            }
            for (Map.Entry<String, EnterTransition> animatingNode : animatingNodes.entrySet()) {
                View associatedView = parentView.findViewWithTag(animatingNode.getKey());
                if (associatedView != null) {
                    AnimationSet animationSet =
                            mEnterAnimationInflator.apply(
                                    checkNotNull(animatingNode.getValue()), associatedView);

                    if (animationSet != null && !animationSet.getAnimations().isEmpty()) {
                        QuotaAwareAnimationSet quotaAwareAnimationSet =
                                new QuotaAwareAnimationSet(
                                        animationSet,
                                        mPipeline.mAnimationQuotaManager,
                                        associatedView);
                        quotaAwareAnimationSet.tryStartAnimation(
                                () -> mPipeline.mEnterAnimations.add(quotaAwareAnimationSet));
                    }
                }
            }
        }

        @NonNull
        private NodeInfo getNodeInfo(@NonNull String posId) {
            return mPosIdToNodeInfo.computeIfAbsent(
                    posId, k -> new NodeInfo(posId, mPipeline.mAnimationQuotaManager));
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @SuppressWarnings("RestrictTo")
        @NonNull
        public PipelineMaker addPipelineFor(
                @NonNull DynamicString stringSource,
                @NonNull Locale locale,
                @NonNull String posId,
                @NonNull DynamicTypeValueReceiver<String> consumer) {
            BoundDynamicType node =
                    mEvaluator.bind(stringSource, ULocale.forLocale(locale), consumer);
            getNodeInfo(posId).addBoundType(node);
            return this;
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public PipelineMaker addPipelineFor(
                @NonNull DynamicInt32 int32Source,
                int invalidData,
                @NonNull String posId,
                @NonNull Consumer<Integer> consumer) {
            return addPipelineFor(
                    int32Source, posId, buildStateUpdateCallback(invalidData, consumer));
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @SuppressWarnings("RestrictTo")
        @NonNull
        public PipelineMaker addPipelineFor(
                @NonNull DynamicInt32 int32Source,
                @NonNull String posId,
                @NonNull DynamicTypeValueReceiver<Integer> consumer) {
            BoundDynamicType node = mEvaluator.bind(int32Source, consumer);
            getNodeInfo(posId).addBoundType(node);
            return this;
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public PipelineMaker addPipelineFor(
                @NonNull DynamicString stringSource,
                @NonNull String invalidData,
                @NonNull Locale locale,
                @NonNull String posId,
                @NonNull Consumer<String> consumer) {
            return addPipelineFor(
                    stringSource, locale, posId, buildStateUpdateCallback(invalidData, consumer));
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @SuppressWarnings("RestrictTo")
        @NonNull
        public PipelineMaker addPipelineFor(
                @NonNull DynamicFloat floatSource,
                @NonNull String posId,
                @NonNull DynamicTypeValueReceiver<Float> consumer) {
            BoundDynamicType node = mEvaluator.bind(floatSource, consumer);
            getNodeInfo(posId).addBoundType(node);
            return this;
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public PipelineMaker addPipelineFor(
                @NonNull DynamicFloat floatSource,
                float invalidData,
                @NonNull String posId,
                @NonNull Consumer<Float> consumer) {
            return addPipelineFor(
                    floatSource, posId, buildStateUpdateCallback(invalidData, consumer));
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @SuppressWarnings("RestrictTo")
        @NonNull
        public PipelineMaker addPipelineFor(
                @NonNull DynamicColor colorSource,
                @NonNull String posId,
                @NonNull DynamicTypeValueReceiver<Integer> consumer) {
            BoundDynamicType node = mEvaluator.bind(colorSource, consumer);
            getNodeInfo(posId).addBoundType(node);
            return this;
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public PipelineMaker addPipelineFor(
                @NonNull DynamicColor colorSource,
                int invalidData,
                @NonNull String posId,
                @NonNull Consumer<Integer> consumer) {
            return addPipelineFor(
                    colorSource, posId, buildStateUpdateCallback(invalidData, consumer));
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public PipelineMaker addPipelineFor(
                @NonNull DynamicBool boolSource,
                @NonNull String posId,
                @NonNull Runnable triggerAnimationRunnable) {
            DynamicTypeValueReceiver<Boolean> consumer =
                    buildBooleanConditionTriggerCallback(
                            triggerAnimationRunnable, mPipeline.mAnimationQuotaManager);
            return addPipelineFor(boolSource, posId, consumer);
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @SuppressWarnings("RestrictTo")
        @NonNull
        public PipelineMaker addPipelineFor(
                @NonNull DynamicBool boolSource,
                @NonNull String posId,
                @NonNull DynamicTypeValueReceiver<Boolean> consumer) {
            BoundDynamicType node = mEvaluator.bind(boolSource, consumer);
            getNodeInfo(posId).addBoundType(node);
            return this;
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public PipelineMaker addPipelineFor(
                @NonNull DynamicBool boolSource,
                boolean invalidData,
                @NonNull String posId,
                @NonNull Consumer<Boolean> consumer) {
            return addPipelineFor(
                    boolSource, posId, buildStateUpdateCallback(invalidData, consumer));
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @SuppressWarnings("RestrictTo")
        @NonNull
        @RestrictTo(Scope.LIBRARY_GROUP)
        public PipelineMaker addPipelineFor(
                @NonNull DpProp dpProp,
                @NonNull String posId,
                @NonNull DynamicTypeValueReceiver<Float> consumer) {
            BoundDynamicType node;
            if (dpProp.hasValue()) {
                node = mEvaluator.bind(dpProp.getDynamicValue(), consumer, dpProp.getValue());
            } else {
                node = mEvaluator.bind(dpProp.getDynamicValue(), consumer);
            }
            getNodeInfo(posId).addBoundType(node);
            return this;
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @SuppressWarnings("RestrictTo")
        @NonNull
        public PipelineMaker addPipelineFor(
                @NonNull DegreesProp degreesProp,
                @NonNull String posId,
                @NonNull DynamicTypeValueReceiver<Float> consumer) {
            BoundDynamicType node;
            if (degreesProp.hasValue()) {
                node =
                        mEvaluator.bind(
                                degreesProp.getDynamicValue(), consumer, degreesProp.getValue());
            } else {
                node = mEvaluator.bind(degreesProp.getDynamicValue(), consumer);
            }
            getNodeInfo(posId).addBoundType(node);
            return this;
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @SuppressWarnings("RestrictTo")
        @NonNull
        public PipelineMaker addPipelineFor(
                @NonNull ColorProp colorProp,
                @NonNull String posId,
                @NonNull DynamicTypeValueReceiver<Integer> consumer) {
            BoundDynamicType node;
            if (colorProp.hasArgb()) {
                node = mEvaluator.bind(colorProp.getDynamicValue(), consumer, colorProp.getArgb());
            } else {
                node = mEvaluator.bind(colorProp.getDynamicValue(), consumer);
            }
            getNodeInfo(posId).addBoundType(node);
            return this;
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @SuppressWarnings("RestrictTo")
        @NonNull
        public PipelineMaker addPipelineFor(
                @NonNull DpProp dpProp,
                float invalidData,
                @NonNull String posId,
                @NonNull Consumer<Float> consumer) {
            return addPipelineFor(dpProp, posId, buildStateUpdateCallback(invalidData, consumer));
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @SuppressWarnings("RestrictTo")
        @NonNull
        public PipelineMaker addPipelineFor(
                @NonNull DegreesProp degreesProp,
                float invalidData,
                @NonNull String posId,
                @NonNull Consumer<Float> consumer) {
            return addPipelineFor(
                    degreesProp, posId, buildStateUpdateCallback(invalidData, consumer));
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @SuppressWarnings("RestrictTo")
        @NonNull
        public PipelineMaker addPipelineFor(
                @NonNull ColorProp colorProp,
                int invalidData,
                @NonNull String posId,
                @NonNull Consumer<Integer> consumer) {
            return addPipelineFor(
                    colorProp, posId, buildStateUpdateCallback(invalidData, consumer));
        }

        /**
         * This store method shall be called during the layout inflation in a background thread.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @SuppressLint("CheckReturnValue") // (b/247804720)
        @NonNull
        public PipelineMaker addResolvedAnimatedImage(
                @NonNull AnimatedVectorDrawable drawable,
                @NonNull Trigger trigger,
                @NonNull String posId) {
            if (!this.mPipeline.mEnableAnimations) {
                Log.w(TAG, "Cannot use ResolvedAnimationImage; animations are disabled.");
                return this;
            }

            getNodeInfo(posId).addResolvedAvd(drawable, trigger);
            return this;
        }

        /**
         * This store method shall be called during the layout inflation in a background thread. It
         * adds given {@link DynamicBool} to the pipeline too.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public PipelineMaker addResolvedAnimatedImageWithBoolTrigger(
                @NonNull AnimatedVectorDrawable drawable,
                @NonNull Trigger trigger,
                @NonNull String posId,
                @NonNull DynamicBool boolTrigger) {
            if (!this.mPipeline.mEnableAnimations) {
                Log.w(TAG, "Cannot use ResolvedAnimationImage; animations are disabled.");
                return this;
            }

            if (trigger.getInnerCase() != Trigger.InnerCase.ON_CONDITION_MET_TRIGGER) {
                Log.w(TAG, "Wrong trigger type.");
                return this;
            }

            ResolvedAvd avd = getNodeInfo(posId).addResolvedAvd(drawable, trigger);
            addPipelineFor(boolTrigger, posId, avd::startAnimation);
            return this;
        }

        /**
         * This store method shall be called during the layout inflation in a background thread.
         *
         * @hide
         */
        @NonNull
        @RestrictTo(Scope.LIBRARY_GROUP)
        public PipelineMaker addResolvedSeekableAnimatedImage(
                @NonNull SeekableAnimatedVectorDrawable seekableDrawable,
                @NonNull DynamicFloat boundProgress,
                @NonNull String posId) {
            if (!this.mPipeline.mEnableAnimations) {
                Log.w(TAG, "Cannot use ResolveSeekableAvd; animations are disabled.");
                return this;
            }

            // Register the bound progress to the seekable animated drawable.
            addPipelineFor(
                    boundProgress,
                    0.0f,
                    posId,
                    aFloat -> {
                        float progress = max(0.0f, min(aFloat, 1.0f));
                        seekableDrawable.setCurrentPlayTime(
                                (long) (progress * seekableDrawable.getTotalDuration()));
                    });
            getNodeInfo(posId)
                    .addResolvedSeekableAvd(
                            new NodeInfo.ResolvedSeekableAvd(seekableDrawable, boundProgress));
            return this;
        }

        /**
         * Stores the {@link AnimatedVisibility} associated with the {@code posId}.
         *
         * @hide
         */
        @NonNull
        @RestrictTo(Scope.LIBRARY_GROUP)
        public PipelineMaker storeAnimatedVisibilityFor(
                @NonNull String posId, @NonNull AnimatedVisibility animatedVisibility) {
            if (!mPipeline.mEnableAnimations) {
                Log.w(TAG, "Can't use AnimatedVisibility; animations are disabled.");
                return this;
            }
            getNodeInfo(posId).setAnimatedVisibility(animatedVisibility);
            return this;
        }

        /**
         * Mark the node {@code posId} as changed. Content transition animations affected by this
         * node will be triggered when the pipeline is committed.
         *
         * @param posId positionId of the node
         * @param includePreviousChildren if True, the previous children of this node will be marked
         *     as changed too. This is used for triggering Exit animations.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public PipelineMaker markNodeAsChanged(
                @NonNull String posId, boolean includePreviousChildren) {
            if (mPipeline.mEnableAnimations) {
                mChangedNodes.add(posId);
                mParentsOfChangedNodes.add(posId);
            }
            return this;
        }

        @NonNull
        private static DynamicTypeValueReceiver<Boolean> buildBooleanConditionTriggerCallback(
                @NonNull Runnable triggerAnimationRunnable, @NonNull QuotaManager quotaManager) {
            return new DynamicTypeValueReceiver<Boolean>() {
                private boolean mCurrent;

                @Override
                @SuppressWarnings("RestrictTo")
                public void onPreUpdate() {}

                @Override
                public void onData(@NonNull Boolean newData) {

                    if (newData && !mCurrent && quotaManager.tryAcquireQuota(1)) {
                        triggerAnimationRunnable.run();
                    }
                    mCurrent = newData;
                }

                @Override
                public void onInvalidated() {}
            };
        }

        @NonNull
        private <T> DynamicTypeValueReceiver<T> buildStateUpdateCallback(
                @NonNull T invalidData, @NonNull Consumer<T> consumer) {
            return new DynamicTypeValueReceiver<T>() {
                @Override
                @SuppressWarnings("RestrictTo")
                public void onPreUpdate() {}

                @Override
                public void onData(@NonNull T newData) {
                    consumer.accept(newData);
                }

                @Override
                public void onInvalidated() {
                    consumer.accept(invalidData);
                }
            };
        }

        /**
         * Add the given source to the pipeline for future evaluation. Evaluation will start when
         * {@link PipelineMaker} is committed with {@link PipelineMaker#commit}.
         *
         * @hide
         */
        @NonNull
        @RestrictTo(Scope.LIBRARY_GROUP)
        public PipelineMaker markForChildRemoval(@NonNull String nodePosId) {
            mNodesPendingChildrenRemoval.add(nodePosId);
            return this;
        }

        /**
         * Stores a node if doesn't exist. Otherwise does nothing.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public PipelineMaker rememberNode(@NonNull String nodePosId) {
            NodeInfo ignored = getNodeInfo(nodePosId);
            return this;
        }
    }

    /**
     * Initialize the data pipeline without playing content transition animations. Normally this is
     * called automatically when the parent {@link ViewGroup} associated with this pipeline is
     * attached to a {@link View} hierarchy. This is so that the content transition animations can
     * be executed before this (if needed).
     *
     * <p>This method can be called directly in screenshot tests and when the renderer output is
     * never supposed to be attached to a window.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @UiThread
    public void initWithoutContentTransition() {
        initNewLayout();
    }

    /** Initialize the data pipeline after a new layout is pushed. */
    @UiThread
    @SuppressWarnings("RestrictTo")
    void initNewLayout() {
        if (mFullyVisible) {
            playAvdAnimations(Trigger.InnerCase.ON_VISIBLE_TRIGGER);
            playAvdAnimations(Trigger.InnerCase.ON_VISIBLE_ONCE_TRIGGER);
        }
        playAvdAnimations(Trigger.InnerCase.ON_LOAD_TRIGGER);
        setAnimationVisibility(mFullyVisible);
        mPositionIdTree.forEach(NodeInfo::initPendingBoundTypes);
    }

    /**
     * Play the animation with the given trigger type.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public void playAvdAnimations(@NonNull Trigger.InnerCase triggerCase) {
        mPositionIdTree.forEach(info -> info.playAvdAnimations(triggerCase));
    }

    /** Sets visibility of animations. Also pauses or resumes any animators. */
    @UiThread
    private void setAnimationVisibility(boolean visible) {
        mPositionIdTree.forEach(info -> info.setVisibility(visible));
    }

    /**
     * Reset the avd animations with the given trigger type.
     *
     * @hide
     */
    @UiThread
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    @RestrictTo(Scope.LIBRARY_GROUP)
    public void resetAvdAnimations(@NonNull Trigger.InnerCase triggerCase) {
        mPositionIdTree.forEach(info -> info.resetAvdAnimations(triggerCase));
    }

    /**
     * Stops running avd animations and releases their quota.
     *
     * @hide
     */
    @UiThread
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    @RestrictTo(Scope.LIBRARY_GROUP)
    public void stopAvdAnimations() {
        mPositionIdTree.forEach(NodeInfo::stopAvdAnimations);
    }

    /** Cancel any already running content transition animations. */
    @UiThread
    void cancelContentTransitionAnimations() {
        mExitAnimations.forEach(QuotaAwareAnimationSet::cancelAnimations);
        mExitAnimations.clear();
        mEnterAnimations.forEach(QuotaAwareAnimationSet::cancelAnimations);
        mEnterAnimations.clear();
    }

    /**
     * Sets visibility for resources tracked by the pipeline and plays / stops any affected
     * animations.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @UiThread
    public void setFullyVisible(boolean fullyVisible) {
        if (this.mFullyVisible == fullyVisible) {
            return;
        }

        this.mFullyVisible = fullyVisible;
        setAnimationVisibility(fullyVisible);
        if (fullyVisible) {
            playAvdAnimations(Trigger.InnerCase.ON_VISIBLE_TRIGGER);
            playAvdAnimations(Trigger.InnerCase.ON_VISIBLE_ONCE_TRIGGER);
        } else {
            cancelContentTransitionAnimations();
            stopAvdAnimations();
            resetAvdAnimations(Trigger.InnerCase.ON_VISIBLE_TRIGGER);
        }
    }

    /**
     * Returns the total duration in milliseconds of the animated drawable associated with a
     * StateSource with the given key name; or null if no such SourceKey exists.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public Long getSeekableAnimationTotalDurationMillis(@NonNull String sourceKey) {
        NodeInfo node =
                mPositionIdTree.findFirst(
                        nodeInfo ->
                                nodeInfo.getSeekableAnimationTotalDurationMillis(sourceKey)
                                        != null);
        if (node != null) {
            return node.getSeekableAnimationTotalDurationMillis(sourceKey);
        }
        return null;
    }

    /**
     * Returns the list of nodes with matching {@code predicate} affected by a change to the node
     * {@code posId}
     */
    @UiThread
    @NonNull
    List<NodeInfo> getNodesAffectedBy(
            @NonNull String posId, @NonNull Predicate<NodeInfo> predicate) {
        List<NodeInfo> affectedNodes = mPositionIdTree.findAncestorsFor(posId, predicate);
        NodeInfo currentNode = mPositionIdTree.get(posId);
        if (currentNode != null && predicate.test(currentNode)) {
            affectedNodes.add(currentNode);
        }

        return affectedNodes;
    }

    /**
     * Returns how many animations are running.
     *
     * @hide
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @RestrictTo(Scope.TESTS)
    public int getRunningAnimationsCount() {
        return mPositionIdTree.getAllNodes().stream()
                        .mapToInt(NodeInfo::getRunningAnimationCount)
                        .sum()
                + mEnterAnimations.stream()
                        .mapToInt(QuotaAwareAnimationSet::getRunningAnimationCount)
                        .sum()
                + mExitAnimations.stream()
                        .mapToInt(QuotaAwareAnimationSet::getRunningAnimationCount)
                        .sum();
    }

    /**
     * Returns whether all quota has been released.
     *
     * @hide
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @RestrictTo(Scope.TESTS)
    public boolean isAllQuotaReleased() {
        return mAnimationQuotaManager instanceof FixedQuotaManagerImpl
                && ((FixedQuotaManagerImpl) mAnimationQuotaManager).isAllQuotaReleased();
    }
}
