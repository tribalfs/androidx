/*
 * Copyright (C) 2022 The Android Open Source Project
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

package androidx.core.view;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.util.ArrayMap;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.core.util.Consumer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */
public class SeslTouchTargetDelegate extends TouchDelegate {
    static boolean DEBUG = false;
    private static final String TAG = "SeslTouchTargetDelegate";
    @NonNull
    final View mAnchorView;
    @NonNull
    final HashSet<CapturedTouchDelegate> mTouchDelegateSet;


    public static final class Builder {
        private final View mAnchorView;
        private final Queue<Consumer<SeslTouchTargetDelegate>> mQueue = new LinkedList<>();

        public Builder(@NonNull View view) {
            this.mAnchorView = view;
        }

        @NonNull
        public Builder addDelegateView(@NonNull final View view, @Nullable final ExtraInsets extraInsets) {
            this.mQueue.add(value -> value.addTouchDelegate(view, extraInsets));
            return this;
        }

        public void apply() {
            this.mAnchorView.post(() -> {
                SeslTouchTargetDelegate seslTouchTargetDelegate = new SeslTouchTargetDelegate(this.mAnchorView);
                for (Consumer<SeslTouchTargetDelegate> queueConsumer : this.mQueue) {
                    queueConsumer.accept(seslTouchTargetDelegate);
                }
                this.mAnchorView.setTouchDelegate(seslTouchTargetDelegate);
                if (SeslTouchTargetDelegate.DEBUG) {
                    SeslTouchTargetDelegate.drawTouchBounds(this.mAnchorView, seslTouchTargetDelegate.mTouchDelegateSet);
                }
            });
        }
    }

    public static class CapturedTouchDelegate extends TouchDelegate {
        @NonNull
        protected final Rect mBounds;
        @NonNull
        protected final View mView;

        public CapturedTouchDelegate(@NonNull Rect rect, @NonNull View view) {
            super(rect, view);
            this.mBounds = rect;
            this.mView = view;
        }
    }

    public static class InvalidDelegateViewException extends RuntimeException {
        public InvalidDelegateViewException() {
            super("TouchTargetDelegate's delegateView must be child of anchorView");
        }
    }

    public static class TouchBoundsPainter {

        public static class TouchBoundsBitmapDrawable extends BitmapDrawable {
            public TouchBoundsBitmapDrawable(@NonNull Resources resources, @NonNull Bitmap bitmap) {
                super(resources, bitmap);
            }
        }

        @RequiresApi(api = 23)
        public static void drawTouchBounds(@NonNull View view, @NonNull List<Rect> list) {
            if (view.getMeasuredWidth() <= 0 || view.getMeasuredHeight() <= 0) {
                return;
            }
            Bitmap createBitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(createBitmap);
            canvas.drawColor(0);
            Paint paint = new Paint();
            paint.setStrokeWidth(3.0f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.parseColor("#34C3EF"));
            for (Rect rect : list) {
                canvas.drawRect(rect, paint);
            }
            TouchBoundsBitmapDrawable touchBoundsBitmapDrawable = new TouchBoundsBitmapDrawable(view.getResources(), createBitmap);
            Drawable foreground = view.getForeground();
            if (foreground instanceof LayerDrawable) {
                LayerDrawable layerDrawable = (LayerDrawable) foreground;
                int numberOfLayers = layerDrawable.getNumberOfLayers() - 1;
                if (layerDrawable.getDrawable(numberOfLayers) instanceof TouchBoundsBitmapDrawable) {
                    layerDrawable.setDrawable(numberOfLayers, touchBoundsBitmapDrawable);
                    return;
                }
            }
            view.setForeground(new LayerDrawable((Drawable[]) Arrays.asList(foreground, touchBoundsBitmapDrawable).toArray(new Drawable[2])));
        }
    }

    public SeslTouchTargetDelegate(@NonNull View view) {
        super(new Rect(), view);
        this.mTouchDelegateSet = new HashSet<>();
        this.mAnchorView = view;
    }

    @NonNull
    @RestrictTo({RestrictTo.Scope.LIBRARY_GROUP_PREFIX})
    public static Rect calculateViewBounds(@NonNull View anchorView, @NonNull View delegateView) throws InvalidDelegateViewException {
        Rect viewBounds = new Rect(0, 0, delegateView.getWidth(), delegateView.getHeight());
        Rect r = new Rect();
        while (!delegateView.equals(anchorView)) {
            delegateView.getHitRect(r);
            viewBounds.left += r.left;
            viewBounds.right += r.left;
            viewBounds.top += r.top;
            viewBounds.bottom += r.top;

            ViewParent parent = delegateView.getParent();
            if (!(parent instanceof View)) {
                break;
            }

            delegateView = (View) parent;
        }

        if (delegateView == anchorView) {
            return viewBounds;
        }
        throw new InvalidDelegateViewException();
    }

    static void drawTouchBounds(@NonNull View mAnchorView, @NonNull HashSet<CapturedTouchDelegate> hashSet) {
        if (DEBUG) {
            ArrayList<Rect> arrayList = new ArrayList<>();
            Iterator<CapturedTouchDelegate> it = hashSet.iterator();
            while (it.hasNext()) {
                CapturedTouchDelegate next = it.next();
                if (next.mView.getVisibility() != View.GONE) {
                    arrayList.add(next.mBounds);
                }
            }
            if (arrayList.size() > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    TouchBoundsPainter.drawTouchBounds(mAnchorView, arrayList);
                }
            }
        }
    }

    @Nullable
    public TouchDelegate addTouchDelegate(@NonNull Rect rect, @NonNull View view) {
        CapturedTouchDelegate capturedTouchDelegate = new CapturedTouchDelegate(rect, view);
        this.mTouchDelegateSet.add(capturedTouchDelegate);
        return capturedTouchDelegate;
    }

    @Override
    @NonNull
    @RequiresApi(api = 29)
    public AccessibilityNodeInfo.TouchDelegateInfo getTouchDelegateInfo() {
        Log.i(TAG, "SeslTouchTargetDelegate does not support accessibility because it cannot support multi-touch delegation with AOSP View");
        ArrayMap<Region, View> map = new ArrayMap<>(1);
        map.put(new Region(new Rect()), mAnchorView);
        return new AccessibilityNodeInfo.TouchDelegateInfo(map);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent motionEvent) {
        Iterator<CapturedTouchDelegate> it = this.mTouchDelegateSet.iterator();
        while (it.hasNext()) {
            CapturedTouchDelegate next = it.next();
            if (next.mView.getParent() == null) {
                Log.w(TAG, "delegate view(" + next.mView + ")'s getParent() is null");
            } else if (next.onTouchEvent(motionEvent)) {
                if (DEBUG) {
                    Log.i(TAG, "touchEvent was consumed on touchDelegate " + next.mView);
                    return true;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    @RequiresApi(api = 29)
    public boolean onTouchExplorationHoverEvent(@NonNull MotionEvent motionEvent) {
        Log.i(TAG, "SeslTouchTargetDelegate does not support accessibility because it cannot support multi-touch delegation with AOSP View");
        return false;
    }

    public boolean removeTouchDelegate(@NonNull TouchDelegate touchDelegate) {
        if (DEBUG) {
            Log.i(TAG, "removeTouchDelegate touchDelegate : " + touchDelegate);
        }
        if (touchDelegate instanceof CapturedTouchDelegate) {
            return this.mTouchDelegateSet.remove(touchDelegate);
        }
        return false;
    }

    public static class ExtraInsets {
        @NonNull
        public static final ExtraInsets NONE = new ExtraInsets(0, 0, 0, 0);
        int bottom;
        int left;
        int right;
        int top;

        private ExtraInsets(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        @NonNull
        public static ExtraInsets of(int left, int top, int right, int bottom) {
            if (left == 0 && top == 0 && right == 0 && bottom == 0) {
                return NONE;
            }
            return new ExtraInsets(left, top, right, bottom);
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            ExtraInsets extraInsets = (ExtraInsets) obj;
            return this.bottom == extraInsets.bottom
                    && this.left == extraInsets.left
                    && this.right == extraInsets.right
                    && this.top == extraInsets.top;
        }

        public int hashCode() {
            return (((((this.left * 31) + this.top) * 31) + this.right) * 31) + this.bottom;
        }

        @NonNull
        public Rect toRect() {
            return new Rect(this.left, this.top, this.right, this.bottom);
        }

        @NonNull
        public String toString() {
            return "ExtraInsets{left=" + this.left + ", top=" + this.top + ", right=" + this.right + ", bottom=" + this.bottom + '}';
        }

        @NonNull
        public static ExtraInsets of(int leftRight, int topBottom) {
            if (leftRight == 0 && topBottom == 0) {
                return NONE;
            }
            return new ExtraInsets(leftRight, topBottom, leftRight, topBottom);
        }

        @NonNull
        public static ExtraInsets of(@Nullable Rect rect) {
            return rect == null ? NONE : of(rect.left, rect.top, rect.right, rect.bottom);
        }
    }

    @Nullable
    public TouchDelegate addTouchDelegate(@NonNull View view) {
        return addTouchDelegate(view, (ExtraInsets) null);
    }

    @Nullable
    public TouchDelegate addTouchDelegate(@NonNull View delegateView, @Nullable ExtraInsets extraInsets) {
        try {
            Rect viewBounds = calculateViewBounds(this.mAnchorView, delegateView);
            if (extraInsets != null) {
                viewBounds.left -= extraInsets.left;
                viewBounds.top -= extraInsets.top;
                viewBounds.right += extraInsets.right;
                viewBounds.bottom += extraInsets.bottom;
            }
            return addTouchDelegate(viewBounds, delegateView);
        } catch (InvalidDelegateViewException e) {
            Log.w(TAG, "delegateView must be child of anchorView");
            e.printStackTrace();
            return null;
        }
    }
}