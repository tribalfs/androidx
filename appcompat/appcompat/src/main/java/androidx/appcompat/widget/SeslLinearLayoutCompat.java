/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.appcompat.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.R;
import androidx.appcompat.animation.SeslRecoilAnimator;
import androidx.appcompat.graphics.drawable.SeslRecoilDrawable;
import androidx.appcompat.util.SeslRoundedCorner;
import androidx.core.view.ViewCompat;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

//Added in sesl7
/**
 * SESL variant of LinearLayoutCompat that adds support for rounded corners and recoil animation.
 */
@RequiresApi(29)
public class SeslLinearLayoutCompat extends LinearLayoutCompat {
    private static final int MOTION_EVENT_ACTION_PEN_DOWN = 211;
    private static final int MOTION_EVENT_ACTION_PEN_UP = 212;
    private final ItemBackgroundHolder mItemBackgroundHolder;
    private final SeslRecoilAnimator.Holder mRecoilAnimatorHolder;
    private final SeslRoundedCorner mRoundedCorner;

    public static class ItemBackgroundHolder {
        Drawable activeBg = null;

        public ItemBackgroundHolder() {
        }

        public void setCancel() {
            if (activeBg != null) {
                if (activeBg instanceof SeslRecoilDrawable) {
                    ((SeslRecoilDrawable) activeBg).setCancel();
                } else {
                    activeBg.setState(new int[0]);
                }
                this.activeBg = null;
            }
        }

        public void setPress(@NonNull View view) {
            setRelease();
            Drawable background = view.getBackground();
            this.activeBg = background;
            if (background != null) {
                background.setState(new int[]{android.R.attr.state_pressed});
            }
        }

        public void setRelease() {
            if (activeBg != null) {
                activeBg.setState(new int[0]);
                activeBg = null;
            }
        }
    }

    public SeslLinearLayoutCompat(@NonNull Context context) {
        this(context, null);
    }

    public SeslLinearLayoutCompat(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SeslLinearLayoutCompat(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TintTypedArray a = TintTypedArray.obtainStyledAttributes(context, attrs, R.styleable.SeslLayout, defStyleAttr, 0);
        ViewCompat.saveAttributeDataForStyleable(this, context, R.styleable.SeslLayout, attrs, a.getWrappedTypeArray(), defStyleAttr, 0);
        int roundedCorner = a.getInt(R.styleable.SeslLayout_seslLayoutRoundedCorner, 0);
        a.recycle();

        mRoundedCorner =  new SeslRoundedCorner(context);
        mRoundedCorner.setRoundedCorners(roundedCorner);
        mItemBackgroundHolder = new ItemBackgroundHolder();
        mRecoilAnimatorHolder = new SeslRecoilAnimator.Holder(context);
    }

    private View findChildViewUnder(View view, int x, int y) {
        View foundView = null;

        if (view instanceof ViewGroup viewGroup) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);

                Rect childRect = new Rect();
                Rect parentRect = new Rect();
                child.getGlobalVisibleRect(childRect);
                getGlobalVisibleRect(parentRect);

                if (childRect.contains(parentRect.left + x, parentRect.top + y)) {
                    foundView = findChildViewUnder(child, x, y);
                    if (foundView != null) {
                        break;
                    }
                }
            }
        }

        if (foundView == null && view.isClickable() && view.getVisibility() == View.VISIBLE && view.isEnabled()) {
            return view;
        }

        return foundView;
    }


    private View findClickableChildUnder(MotionEvent event) {
        View clickableChild = null;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            Rect childRect = new Rect();
            Rect parentRect = new Rect();
            child.getGlobalVisibleRect(childRect);
            getGlobalVisibleRect(parentRect);

            if (childRect.contains(parentRect.left + (int) event.getX(), parentRect.top + (int) event.getY())) {
                clickableChild = child;
                break;
            }
        }

        if (clickableChild == null) {
            return null;
        }

        View childUnder = findChildViewUnder(clickableChild, (int) event.getX(), (int) event.getY());
        if (childUnder != null && childUnder != clickableChild) {
            if (childUnder.getHeight() * childUnder.getWidth() < clickableChild.getHeight() * clickableChild.getWidth() * 0.5d) {
                return null;
            }
        }

        return childUnder;
    }

    @Override
    public void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        this.mRoundedCorner.drawRoundedCorner(canvas);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent keyEvent) {
        if (keyEvent.getKeyCode() == 66) {
            if (keyEvent.getAction() == 0) {
                View focusedChild = getFocusedChild();
                if (focusedChild != null) {
                    this.mRecoilAnimatorHolder.setPress(focusedChild);
                }
            } else {
                this.mRecoilAnimatorHolder.setRelease();
            }
        }
        return super.dispatchKeyEvent(keyEvent);
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        int action = motionEvent.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MOTION_EVENT_ACTION_PEN_DOWN:
                View clickableChild = findClickableChildUnder(motionEvent);
                if (clickableChild != null) {
                    mItemBackgroundHolder.setPress(clickableChild);
                    mRecoilAnimatorHolder.setPress(clickableChild);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MOTION_EVENT_ACTION_PEN_UP:
                mItemBackgroundHolder.setRelease();
                mRecoilAnimatorHolder.setRelease();
                break;
            case MotionEvent.ACTION_CANCEL:
                mItemBackgroundHolder.setCancel();
                mRecoilAnimatorHolder.setRelease();
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(motionEvent);
    }

    @NonNull
    public SeslRoundedCorner getRoundedCorner() {
        return this.mRoundedCorner;
    }


}