/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.support.design.widget;

import android.content.Context;
import android.graphics.Rect;
import android.support.design.widget.CoordinatorLayout.Behavior;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

/**
 * The {@link Behavior} for a scrolling view that is positioned vertically below another view.
 * See {@link HeaderBehavior}.
 */
abstract class HeaderScrollingViewBehavior extends ViewOffsetBehavior<View> {

    private final Rect mTempRect1 = new Rect();
    private final Rect mTempRect2 = new Rect();

    private int mVerticalLayoutGap = 0;

    public HeaderScrollingViewBehavior() {}

    public HeaderScrollingViewBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onMeasureChild(CoordinatorLayout parent, View child,
            int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec,
            int heightUsed) {
        final int childLpHeight = child.getLayoutParams().height;
        if (childLpHeight == ViewGroup.LayoutParams.MATCH_PARENT
                || childLpHeight == ViewGroup.LayoutParams.WRAP_CONTENT) {
            // If the menu's height is set to match_parent/wrap_content then measure it
            // with the maximum visible height

            final List<View> dependencies = parent.getDependencies(child);
            final View header = findFirstDependency(dependencies);
            if (header != null) {
                if (ViewCompat.getFitsSystemWindows(header)
                        && !ViewCompat.getFitsSystemWindows(child)) {
                    // If the header is fitting system windows then we need to also,
                    // otherwise we'll get CoL's compatible measuring
                    ViewCompat.setFitsSystemWindows(child, true);

                    if (ViewCompat.getFitsSystemWindows(child)) {
                        // If the set succeeded, trigger a new layout and return true
                        child.requestLayout();
                        return true;
                    }
                }

                if (ViewCompat.isLaidOut(header)) {
                    int availableHeight = View.MeasureSpec.getSize(parentHeightMeasureSpec);
                    if (availableHeight == 0) {
                        // If the measure spec doesn't specify a size, use the current height
                        availableHeight = parent.getHeight();
                    }

                    final int height = availableHeight - header.getMeasuredHeight()
                            + getScrollRange(header);
                    final int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(height,
                            childLpHeight == ViewGroup.LayoutParams.MATCH_PARENT
                                    ? View.MeasureSpec.EXACTLY
                                    : View.MeasureSpec.AT_MOST);

                    // Now measure the scrolling view with the correct height
                    parent.onMeasureChild(child, parentWidthMeasureSpec,
                            widthUsed, heightMeasureSpec, heightUsed);

                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void layoutChild(final CoordinatorLayout parent, final View child,
            final int layoutDirection) {
        final List<View> dependencies = parent.getDependencies(child);
        final View header = findFirstDependency(dependencies);

        if (header != null) {
            final CoordinatorLayout.LayoutParams lp =
                    (CoordinatorLayout.LayoutParams) child.getLayoutParams();
            final Rect available = mTempRect1;
            available.set(parent.getPaddingLeft() + lp.leftMargin,
                    header.getBottom() + lp.topMargin,
                    parent.getWidth() - parent.getPaddingRight() - lp.rightMargin,
                    parent.getHeight() + header.getBottom()
                            - parent.getPaddingBottom() - lp.bottomMargin);

            final Rect out = mTempRect2;
            GravityCompat.apply(resolveGravity(lp.gravity), child.getMeasuredWidth(),
                    child.getMeasuredHeight(), available, out, layoutDirection);

            child.layout(out.left, out.top, out.right, out.bottom);
            mVerticalLayoutGap = out.top - header.getBottom();
        } else {
            // If we don't have a dependency, let super handle it
            super.layoutChild(parent, child, layoutDirection);
            mVerticalLayoutGap = 0;
        }
    }

    private static int resolveGravity(int gravity) {
        return gravity == Gravity.NO_GRAVITY ? GravityCompat.START | Gravity.TOP : gravity;
    }

    abstract View findFirstDependency(List<View> views);

    int getScrollRange(View v) {
        return v.getMeasuredHeight();
    }

    /**
     * The gap between the top of the scrolling view and the bottom of the header layout in pixels.
     */
    int getVerticalLayoutGap() {
        return mVerticalLayoutGap;
    }
}