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

package android.support.v7.app;

import android.content.Context;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.AnimationDrawable;
import android.support.v4.content.ContextCompat;
import android.support.v7.mediarouter.R;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

/**
 * Chevron/Caret button to expand/collapse group volume list with animation.
 */
class MediaRouteExpandCollapseButton extends ImageButton {
    private final AnimationDrawable mExpandAnimationDrawable;
    private final AnimationDrawable mCollapseAnimationDrawable;
    private final String mExpandGroupDescription;
    private final String mCollapseGroupDescription;
    private boolean mIsGroupExpanded;
    private OnClickListener mListener;

    public MediaRouteExpandCollapseButton(Context context) {
        this(context, null);
    }

    public MediaRouteExpandCollapseButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MediaRouteExpandCollapseButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mExpandAnimationDrawable = (AnimationDrawable) ContextCompat.getDrawable(
                context, R.drawable.mr_group_expand);
        mCollapseAnimationDrawable = (AnimationDrawable) ContextCompat.getDrawable(
                context, R.drawable.mr_group_collapse);

        ColorFilter filter = new PorterDuffColorFilter(
                MediaRouterThemeHelper.getControllerColor(context, defStyleAttr),
                PorterDuff.Mode.SRC_IN);
        mExpandAnimationDrawable.setColorFilter(filter);
        mCollapseAnimationDrawable.setColorFilter(filter);

        mExpandGroupDescription = context.getString(R.string.mr_controller_expand_group);
        mCollapseGroupDescription = context.getString(R.string.mr_controller_collapse_group);

        setImageDrawable(mExpandAnimationDrawable.getFrame(0));
        setContentDescription(mExpandGroupDescription);

        super.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mIsGroupExpanded = !mIsGroupExpanded;
                if (mIsGroupExpanded) {
                    setImageDrawable(mExpandAnimationDrawable);
                    mExpandAnimationDrawable.start();
                    setContentDescription(mCollapseGroupDescription);
                } else {
                    setImageDrawable(mCollapseAnimationDrawable);
                    mCollapseAnimationDrawable.start();
                    setContentDescription(mExpandGroupDescription);
                }
                if (mListener != null) {
                    mListener.onClick(view);
                }
            }
        });
    }

    @Override
    public void setOnClickListener(OnClickListener listener) {
        mListener = listener;
    }
}
