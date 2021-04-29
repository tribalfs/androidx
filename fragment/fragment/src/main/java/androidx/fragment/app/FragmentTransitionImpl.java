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

package androidx.fragment.app;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.os.CancellationSignal;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * @hide
 */
@RestrictTo(LIBRARY_GROUP_PREFIX)
@SuppressLint("UnknownNullness")
public abstract class FragmentTransitionImpl {

    /**
     * Returns {@code true} if this implementation can handle the specified {@link transition}.
     */
    public abstract boolean canHandle(Object transition);

    /**
     * Returns a clone of a transition or null if it is null
     */
    public abstract Object cloneTransition(Object transition);

    /**
     * Wraps a transition in a TransitionSet and returns the set. If transition is null, null is
     * returned.
     */
    public abstract Object wrapTransitionInSet(Object transition);

    /**
     * Finds all children of the shared elements and sets the wrapping TransitionSet
     * targets to point to those. It also limits transitions that have no targets to the
     * specific shared elements. This allows developers to target child views of the
     * shared elements specifically, but this doesn't happen by default.
     */
    public abstract void setSharedElementTargets(Object transitionObj,
            View nonExistentView, ArrayList<View> sharedViews);

    /**
     * Sets a transition epicenter to the rectangle of a given View.
     */
    public abstract void setEpicenter(Object transitionObj, View view);

    /**
     * Replacement for view.getBoundsOnScreen because that is not public. This returns a rect
     * containing the bounds relative to the screen that the view is in.
     */
    protected void getBoundsOnScreen(View view, Rect epicenter) {
        if (!ViewCompat.isAttachedToWindow(view)) {
            return;
        }

        final RectF rect = new RectF();
        rect.set(0, 0, view.getWidth(), view.getHeight());

        view.getMatrix().mapRect(rect);
        rect.offset(view.getLeft(), view.getTop());

        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            View parentView = (View) parent;

            rect.offset(-parentView.getScrollX(), -parentView.getScrollY());
            parentView.getMatrix().mapRect(rect);
            rect.offset(parentView.getLeft(), parentView.getTop());

            parent = parentView.getParent();
        }

        final int[] loc = new int[2];
        view.getRootView().getLocationOnScreen(loc);
        rect.offset(loc[0], loc[1]);
        epicenter.set(Math.round(rect.left), Math.round(rect.top), Math.round(rect.right),
                Math.round(rect.bottom));
    }

    /**
     * This method adds views as targets to the transition, but only if the transition
     * doesn't already have a target. It is best for views to contain one View object
     * that does not exist in the view hierarchy (state.nonExistentView) so that
     * when they are removed later, a list match will suffice to remove the targets.
     * Otherwise, if you happened to have targeted the exact views for the transition,
     * the replaceTargets call will remove them unexpectedly.
     */
    public abstract void addTargets(Object transitionObj, ArrayList<View> views);

    /**
     * Creates a TransitionSet that plays all passed transitions together. Any null
     * transitions passed will not be added to the set. If all are null, then an empty
     * TransitionSet will be returned.
     */
    public abstract Object mergeTransitionsTogether(Object transition1, Object transition2,
            Object transition3);

    /**
     * After the transition completes, the fragment's view is set to GONE and the exiting
     * views are set to VISIBLE.
     */
    public abstract void scheduleHideFragmentView(Object exitTransitionObj, View fragmentView,
            ArrayList<View> exitingViews);

    /**
     * Combines enter, exit, and shared element transition so that they play in the proper
     * sequence. First the exit transition plays along with the shared element transition.
     * When the exit transition completes, the enter transition starts. The shared element
     * transition can continue running while the enter transition plays.
     *
     * @return A TransitionSet with all of enter, exit, and shared element transitions in
     * it (modulo null values), ordered such that they play in the proper sequence.
     */
    public abstract Object mergeTransitionsInSequence(Object exitTransitionObj,
            Object enterTransitionObj, Object sharedElementTransitionObj);

    /**
     * Calls {@code TransitionManager#beginDelayedTransition(ViewGroup, Transition)}.
     */
    public abstract void beginDelayedTransition(ViewGroup sceneRoot, Object transition);

    /**
     * Prepares for setting the shared element names by gathering the names of the incoming
     * shared elements and clearing them. {@link #setNameOverridesReordered(View, ArrayList,
     * ArrayList, ArrayList, Map)} must be called after this to complete setting the shared element
     * name overrides. This must be called before
     * {@link #beginDelayedTransition(ViewGroup, Object)}.
     */
    ArrayList<String> prepareSetNameOverridesReordered(ArrayList<View> sharedElementsIn) {
        final ArrayList<String> names = new ArrayList<>();
        final int numSharedElements = sharedElementsIn.size();
        for (int i = 0; i < numSharedElements; i++) {
            final View view = sharedElementsIn.get(i);
            names.add(ViewCompat.getTransitionName(view));
            ViewCompat.setTransitionName(view, null);
        }
        return names;
    }

    /**
     * Changes the shared element names for the incoming shared elements to match those of the
     * outgoing shared elements. This also temporarily clears the shared element names of the
     * outgoing shared elements. Must be called after
     * {@link #beginDelayedTransition(ViewGroup, Object)}.
     */
    void setNameOverridesReordered(final View sceneRoot,
            final ArrayList<View> sharedElementsOut, final ArrayList<View> sharedElementsIn,
            final ArrayList<String> inNames, final Map<String, String> nameOverrides) {
        final int numSharedElements = sharedElementsIn.size();
        final ArrayList<String> outNames = new ArrayList<>();

        for (int i = 0; i < numSharedElements; i++) {
            final View view = sharedElementsOut.get(i);
            final String name = ViewCompat.getTransitionName(view);
            outNames.add(name);
            if (name == null) {
                continue;
            }
            ViewCompat.setTransitionName(view, null);
            final String inName = nameOverrides.get(name);
            for (int j = 0; j < numSharedElements; j++) {
                if (inName.equals(inNames.get(j))) {
                    ViewCompat.setTransitionName(sharedElementsIn.get(j), name);
                    break;
                }
            }
        }

        OneShotPreDrawListener.add(sceneRoot, new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < numSharedElements; i++) {
                    ViewCompat.setTransitionName(sharedElementsIn.get(i), inNames.get(i));
                    ViewCompat.setTransitionName(sharedElementsOut.get(i), outNames.get(i));
                }
            }
        });
    }

    /**
     * After the transition has started, remove all targets that we added to the transitions
     * so that the transitions are left in a clean state.
     */
    public abstract void scheduleRemoveTargets(Object overallTransitionObj,
            Object enterTransition, ArrayList<View> enteringViews,
            Object exitTransition, ArrayList<View> exitingViews,
            Object sharedElementTransition, ArrayList<View> sharedElementsIn);


    /**
     * Set a listener for Transition end events. The default behavior immediately completes the
     * transition.
     *
     * @param outFragment The first fragment that is exiting
     * @param transition all transitions to be executed on a single container
     * @param signal used indicate the desired behavior on transition cancellation
     * @param transitionCompleteRunnable used to notify the FragmentManager when a transition is
     *                                   complete
     */
    public void setListenerForTransitionEnd(@NonNull final Fragment outFragment,
            @NonNull Object transition, @NonNull CancellationSignal signal,
            @NonNull Runnable transitionCompleteRunnable) {
        transitionCompleteRunnable.run();
    }

    /**
     * Swap the targets for the shared element transition from those Views in sharedElementsOut
     * to those in sharedElementsIn
     */
    public abstract void swapSharedElementTargets(Object sharedElementTransitionObj,
            ArrayList<View> sharedElementsOut, ArrayList<View> sharedElementsIn);

    /**
     * This method removes the views from transitions that target ONLY those views and
     * replaces them with the new targets list.
     * The views list should match those added in addTargets and should contain
     * one view that is not in the view hierarchy (state.nonExistentView).
     */
    public abstract void replaceTargets(Object transitionObj, ArrayList<View> oldTargets,
            ArrayList<View> newTargets);

    /**
     * Adds a View target to a transition. If transitionObj is null, nothing is done.
     */
    public abstract void addTarget(Object transitionObj, View view);

    /**
     * Remove a View target to a transition. If transitionObj is null, nothing is done.
     */
    public abstract void removeTarget(Object transitionObj, View view);

    /**
     * Sets the epicenter of a transition to a rect object. The object can be modified until
     * the transition runs.
     */
    public abstract void setEpicenter(Object transitionObj, Rect epicenter);

    /**
     * Uses a breadth-first scheme to add startView and all of its children to views.
     * It won't add a child if it is already in views or if it has a transition name.
     */
    protected static void bfsAddViewChildren(final List<View> views, final View startView) {
        final int startIndex = views.size();
        if (containedBeforeIndex(views, startView, startIndex)) {
            return; // This child is already in the list, so all its children are also.
        }
        if (ViewCompat.getTransitionName(startView) != null) {
            views.add(startView);
        }
        for (int index = startIndex; index < views.size(); index++) {
            final View view = views.get(index);
            if (view instanceof ViewGroup) {
                ViewGroup viewGroup = (ViewGroup) view;
                final int childCount =  viewGroup.getChildCount();
                for (int childIndex = 0; childIndex < childCount; childIndex++) {
                    final View child = viewGroup.getChildAt(childIndex);
                    if (!containedBeforeIndex(views, child, startIndex)
                            && ViewCompat.getTransitionName(child) != null) {
                        views.add(child);
                    }
                }
            }
        }
    }

    /**
     * Does a linear search through views for view, limited to maxIndex.
     */
    private static boolean containedBeforeIndex(final List<View> views, final View view,
            final int maxIndex) {
        for (int i = 0; i < maxIndex; i++) {
            if (views.get(i) == view) {
                return true;
            }
        }
        return false;
    }

    /**
     * Simple utility to detect if a list is null or has no elements.
     */
    protected static boolean isNullOrEmpty(List list) {
        return list == null || list.isEmpty();
    }

}
