/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.support.transition;

import static android.support.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.v4.content.res.TypedArrayUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This transition tracks changes to the visibility of target views in the
 * start and end scenes. Visibility is determined not just by the
 * {@link View#setVisibility(int)} state of views, but also whether
 * views exist in the current view hierarchy. The class is intended to be a
 * utility for subclasses such as {@link Fade}, which use this visibility
 * information to determine the specific animations to run when visibility
 * changes occur. Subclasses should implement one or both of the methods
 * {@link #onAppear(ViewGroup, TransitionValues, int, TransitionValues, int)},
 * {@link #onDisappear(ViewGroup, TransitionValues, int, TransitionValues, int)} or
 * {@link #onAppear(ViewGroup, View, TransitionValues, TransitionValues)},
 * {@link #onDisappear(ViewGroup, View, TransitionValues, TransitionValues)}.
 */
public abstract class Visibility extends Transition {

    static final String PROPNAME_VISIBILITY = "android:visibility:visibility";
    private static final String PROPNAME_PARENT = "android:visibility:parent";
    private static final String PROPNAME_SCREEN_LOCATION = "android:visibility:screenLocation";

    /**
     * Mode used in {@link #setMode(int)} to make the transition
     * operate on targets that are appearing. Maybe be combined with
     * {@link #MODE_OUT} to target Visibility changes both in and out.
     */
    public static final int MODE_IN = 0x1;

    /**
     * Mode used in {@link #setMode(int)} to make the transition
     * operate on targets that are disappearing. Maybe be combined with
     * {@link #MODE_IN} to target Visibility changes both in and out.
     */
    public static final int MODE_OUT = 0x2;

    /** @hide */
    @RestrictTo(LIBRARY_GROUP)
    @IntDef(flag = true, value = {MODE_IN, MODE_OUT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {
    }

    private static final String[] sTransitionProperties = {
            PROPNAME_VISIBILITY,
            PROPNAME_PARENT,
    };

    private static class VisibilityInfo {
        boolean mVisibilityChange;
        boolean mFadeIn;
        int mStartVisibility;
        int mEndVisibility;
        ViewGroup mStartParent;
        ViewGroup mEndParent;
    }

    private int mMode = MODE_IN | MODE_OUT;

    public Visibility() {
    }

    public Visibility(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, Styleable.VISIBILITY_TRANSITION);
        @Mode
        int mode = TypedArrayUtils.getNamedInt(a, (XmlResourceParser) attrs,
                "transitionVisibilityMode",
                Styleable.VisibilityTransition.TRANSITION_VISIBILITY_MODE, 0);
        a.recycle();
        if (mode != 0) {
            setMode(mode);
        }
    }

    /**
     * Changes the transition to support appearing and/or disappearing Views, depending
     * on <code>mode</code>.
     *
     * @param mode The behavior supported by this transition, a combination of
     *             {@link #MODE_IN} and {@link #MODE_OUT}.
     */
    public void setMode(@Mode int mode) {
        if ((mode & ~(MODE_IN | MODE_OUT)) != 0) {
            throw new IllegalArgumentException("Only MODE_IN and MODE_OUT flags are allowed");
        }
        mMode = mode;
    }

    /**
     * Returns whether appearing and/or disappearing Views are supported.
     *
     * @return whether appearing and/or disappearing Views are supported. A combination of
     * {@link #MODE_IN} and {@link #MODE_OUT}.
     */
    @Mode
    public int getMode() {
        return mMode;
    }

    @Nullable
    @Override
    public String[] getTransitionProperties() {
        return sTransitionProperties;
    }

    private void captureValues(TransitionValues transitionValues) {
        int visibility = transitionValues.view.getVisibility();
        transitionValues.values.put(PROPNAME_VISIBILITY, visibility);
        transitionValues.values.put(PROPNAME_PARENT, transitionValues.view.getParent());
        int[] loc = new int[2];
        transitionValues.view.getLocationOnScreen(loc);
        transitionValues.values.put(PROPNAME_SCREEN_LOCATION, loc);
    }

    @Override
    public void captureStartValues(@NonNull TransitionValues transitionValues) {
        captureValues(transitionValues);
    }

    @Override
    public void captureEndValues(@NonNull TransitionValues transitionValues) {
        captureValues(transitionValues);
    }

    /**
     * Returns whether the view is 'visible' according to the given values
     * object. This is determined by testing the same properties in the values
     * object that are used to determine whether the object is appearing or
     * disappearing in the {@link
     * Transition#createAnimator(ViewGroup, TransitionValues, TransitionValues)}
     * method. This method can be called by, for example, subclasses that want
     * to know whether the object is visible in the same way that Visibility
     * determines it for the actual animation.
     *
     * @param values The TransitionValues object that holds the information by
     *               which visibility is determined.
     * @return True if the view reference by <code>values</code> is visible,
     * false otherwise.
     */
    public boolean isVisible(TransitionValues values) {
        if (values == null) {
            return false;
        }
        int visibility = (Integer) values.values.get(PROPNAME_VISIBILITY);
        View parent = (View) values.values.get(PROPNAME_PARENT);

        return visibility == View.VISIBLE && parent != null;
    }

    private VisibilityInfo getVisibilityChangeInfo(TransitionValues startValues,
            TransitionValues endValues) {
        final VisibilityInfo visInfo = new VisibilityInfo();
        visInfo.mVisibilityChange = false;
        visInfo.mFadeIn = false;
        if (startValues != null && startValues.values.containsKey(PROPNAME_VISIBILITY)) {
            visInfo.mStartVisibility = (Integer) startValues.values.get(PROPNAME_VISIBILITY);
            visInfo.mStartParent = (ViewGroup) startValues.values.get(PROPNAME_PARENT);
        } else {
            visInfo.mStartVisibility = -1;
            visInfo.mStartParent = null;
        }
        if (endValues != null && endValues.values.containsKey(PROPNAME_VISIBILITY)) {
            visInfo.mEndVisibility = (Integer) endValues.values.get(PROPNAME_VISIBILITY);
            visInfo.mEndParent = (ViewGroup) endValues.values.get(PROPNAME_PARENT);
        } else {
            visInfo.mEndVisibility = -1;
            visInfo.mEndParent = null;
        }
        if (startValues != null && endValues != null) {
            if (visInfo.mStartVisibility == visInfo.mEndVisibility
                    && visInfo.mStartParent == visInfo.mEndParent) {
                return visInfo;
            } else {
                if (visInfo.mStartVisibility != visInfo.mEndVisibility) {
                    if (visInfo.mStartVisibility == View.VISIBLE) {
                        visInfo.mFadeIn = false;
                        visInfo.mVisibilityChange = true;
                    } else if (visInfo.mEndVisibility == View.VISIBLE) {
                        visInfo.mFadeIn = true;
                        visInfo.mVisibilityChange = true;
                    }
                    // no visibilityChange if going between INVISIBLE and GONE
                } else /* if (visInfo.mStartParent != visInfo.mEndParent) */ {
                    if (visInfo.mEndParent == null) {
                        visInfo.mFadeIn = false;
                        visInfo.mVisibilityChange = true;
                    } else if (visInfo.mStartParent == null) {
                        visInfo.mFadeIn = true;
                        visInfo.mVisibilityChange = true;
                    }
                }
            }
        } else if (startValues == null && visInfo.mEndVisibility == View.VISIBLE) {
            visInfo.mFadeIn = true;
            visInfo.mVisibilityChange = true;
        } else if (endValues == null && visInfo.mStartVisibility == View.VISIBLE) {
            visInfo.mFadeIn = false;
            visInfo.mVisibilityChange = true;
        }
        return visInfo;
    }

    @Nullable
    @Override
    public Animator createAnimator(@NonNull ViewGroup sceneRoot,
            @Nullable TransitionValues startValues, @Nullable TransitionValues endValues) {
        VisibilityInfo visInfo = getVisibilityChangeInfo(startValues, endValues);
        if (visInfo.mVisibilityChange
                && (visInfo.mStartParent != null || visInfo.mEndParent != null)) {
            if (visInfo.mFadeIn) {
                return onAppear(sceneRoot, startValues, visInfo.mStartVisibility,
                        endValues, visInfo.mEndVisibility);
            } else {
                return onDisappear(sceneRoot, startValues, visInfo.mStartVisibility,
                        endValues, visInfo.mEndVisibility
                );
            }
        }
        return null;
    }

    /**
     * The default implementation of this method does nothing. Subclasses
     * should override if they need to create an Animator when targets appear.
     * The method should only be called by the Visibility class; it is
     * not intended to be called from external classes.
     *
     * @param sceneRoot       The root of the transition hierarchy
     * @param startValues     The target values in the start scene
     * @param startVisibility The target visibility in the start scene
     * @param endValues       The target values in the end scene
     * @param endVisibility   The target visibility in the end scene
     * @return An Animator to be started at the appropriate time in the
     * overall transition for this scene change. A null value means no animation
     * should be run.
     */
    @SuppressWarnings("UnusedParameters")
    public Animator onAppear(ViewGroup sceneRoot, TransitionValues startValues, int startVisibility,
            TransitionValues endValues, int endVisibility) {
        if ((mMode & MODE_IN) != MODE_IN || endValues == null) {
            return null;
        }
        if (startValues == null) {
            View endParent = (View) endValues.view.getParent();
            TransitionValues startParentValues = getMatchedTransitionValues(endParent,
                    false);
            TransitionValues endParentValues = getTransitionValues(endParent, false);
            VisibilityInfo parentVisibilityInfo =
                    getVisibilityChangeInfo(startParentValues, endParentValues);
            if (parentVisibilityInfo.mVisibilityChange) {
                return null;
            }
        }
        return onAppear(sceneRoot, endValues.view, startValues, endValues);
    }

    /**
     * The default implementation of this method returns a null Animator. Subclasses should
     * override this method to make targets appear with the desired transition. The
     * method should only be called from
     * {@link #onAppear(ViewGroup, TransitionValues, int, TransitionValues, int)}.
     *
     * @param sceneRoot   The root of the transition hierarchy
     * @param view        The View to make appear. This will be in the target scene's View
     *                    hierarchy
     *                    and
     *                    will be VISIBLE.
     * @param startValues The target values in the start scene
     * @param endValues   The target values in the end scene
     * @return An Animator to be started at the appropriate time in the
     * overall transition for this scene change. A null value means no animation
     * should be run.
     */
    public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues,
            TransitionValues endValues) {
        return null;
    }

    /**
     * The default implementation of this method does nothing. Subclasses
     * should override if they need to create an Animator when targets disappear.
     * The method should only be called by the Visibility class; it is
     * not intended to be called from external classes.
     *
     * @param sceneRoot       The root of the transition hierarchy
     * @param startValues     The target values in the start scene
     * @param startVisibility The target visibility in the start scene
     * @param endValues       The target values in the end scene
     * @param endVisibility   The target visibility in the end scene
     * @return An Animator to be started at the appropriate time in the
     * overall transition for this scene change. A null value means no animation
     * should be run.
     */
    @SuppressWarnings("UnusedParameters")
    public Animator onDisappear(ViewGroup sceneRoot, TransitionValues startValues,
            int startVisibility, TransitionValues endValues, int endVisibility) {
        if ((mMode & MODE_OUT) != MODE_OUT) {
            return null;
        }

        View startView = (startValues != null) ? startValues.view : null;
        View endView = (endValues != null) ? endValues.view : null;
        View overlayView = null;
        View viewToKeep = null;
        if (endView == null || endView.getParent() == null) {
            if (endView != null) {
                // endView was removed from its parent - add it to the overlay
                overlayView = endView;
            } else if (startView != null) {
                // endView does not exist. Use startView only under certain
                // conditions, because placing a view in an overlay necessitates
                // it being removed from its current parent
                if (startView.getParent() == null) {
                    // no parent - safe to use
                    overlayView = startView;
                } else if (startView.getParent() instanceof View) {
                    View startParent = (View) startView.getParent();
                    TransitionValues startParentValues = getTransitionValues(startParent, true);
                    TransitionValues endParentValues = getMatchedTransitionValues(startParent,
                            true);
                    VisibilityInfo parentVisibilityInfo =
                            getVisibilityChangeInfo(startParentValues, endParentValues);
                    if (!parentVisibilityInfo.mVisibilityChange) {
                        overlayView = TransitionUtils.copyViewImage(sceneRoot, startView,
                                startParent);
                    } else if (startParent.getParent() == null) {
                        int id = startParent.getId();
                        if (id != View.NO_ID && sceneRoot.findViewById(id) != null
                                && mCanRemoveViews) {
                            // no parent, but its parent is unparented  but the parent
                            // hierarchy has been replaced by a new hierarchy with the same id
                            // and it is safe to un-parent startView
                            overlayView = startView;
                        }
                    }
                }
            }
        } else {
            // visibility change
            if (endVisibility == View.INVISIBLE) {
                viewToKeep = endView;
            } else {
                // Becoming GONE
                if (startView == endView) {
                    viewToKeep = endView;
                } else {
                    overlayView = startView;
                }
            }
        }
        final int finalVisibility = endVisibility;

        if (overlayView != null && startValues != null) {
            // TODO: Need to do this for general case of adding to overlay
            int[] screenLoc = (int[]) startValues.values.get(PROPNAME_SCREEN_LOCATION);
            int screenX = screenLoc[0];
            int screenY = screenLoc[1];
            int[] loc = new int[2];
            sceneRoot.getLocationOnScreen(loc);
            overlayView.offsetLeftAndRight((screenX - loc[0]) - overlayView.getLeft());
            overlayView.offsetTopAndBottom((screenY - loc[1]) - overlayView.getTop());
            final ViewGroupOverlayImpl overlay = ViewGroupUtils.getOverlay(sceneRoot);
            overlay.add(overlayView);
            Animator animator = onDisappear(sceneRoot, overlayView, startValues, endValues);
            if (animator == null) {
                overlay.remove(overlayView);
            } else {
                final View finalOverlayView = overlayView;
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        overlay.remove(finalOverlayView);
                    }
                });
            }
            return animator;
        }

        if (viewToKeep != null) {
            int originalVisibility = -1;
            originalVisibility = viewToKeep.getVisibility();
            viewToKeep.setVisibility(View.VISIBLE);
            Animator animator = onDisappear(sceneRoot, viewToKeep, startValues, endValues);
            if (animator != null) {
                final View finalViewToKeep = viewToKeep;
                animator.addListener(new AnimatorListenerAdapter() {
                    boolean mCanceled = false;

                    @Override
                    public void onAnimationPause(Animator animation) {
                        if (!mCanceled) {
                            //noinspection WrongConstant
                            finalViewToKeep.setVisibility(finalVisibility);
                        }
                    }

                    @Override
                    public void onAnimationResume(Animator animation) {
                        if (!mCanceled) {
                            finalViewToKeep.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        mCanceled = true;
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!mCanceled) {
                            //noinspection WrongConstant
                            finalViewToKeep.setVisibility(finalVisibility);
                        }
                    }
                });
            } else {
                viewToKeep.setVisibility(originalVisibility);
            }
            return animator;
        }
        return null;


    }

    /**
     * The default implementation of this method returns a null Animator. Subclasses should
     * override this method to make targets disappear with the desired transition. The
     * method should only be called from
     * {@link #onDisappear(ViewGroup, TransitionValues, int, TransitionValues, int)}.
     *
     * @param sceneRoot   The root of the transition hierarchy
     * @param view        The View to make disappear. This will be in the target scene's View
     *                    hierarchy or in an {@link android.view.ViewGroupOverlay} and will be
     *                    VISIBLE.
     * @param startValues The target values in the start scene
     * @param endValues   The target values in the end scene
     * @return An Animator to be started at the appropriate time in the
     * overall transition for this scene change. A null value means no animation
     * should be run.
     */
    public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues,
            TransitionValues endValues) {
        return null;
    }

    @Override
    boolean areValuesChanged(TransitionValues oldValues, TransitionValues newValues) {
        if (oldValues == null && newValues == null) {
            return false;
        }
        VisibilityInfo changeInfo = getVisibilityChangeInfo(oldValues, newValues);
        return changeInfo.mVisibilityChange && (changeInfo.mStartVisibility == View.VISIBLE
                || changeInfo.mEndVisibility == View.VISIBLE);
    }

    // TODO: Implement API 23; isTransitionRequired

}
