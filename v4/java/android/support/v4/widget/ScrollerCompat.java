/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.support.v4.widget;

import android.content.Context;
import android.os.Build;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.Scroller;

/**
 * Provides access to new {@link android.widget.Scroller Scroller} APIs when available.
 *
 * <p>This class provides a platform version-independent mechanism for obeying the
 * current device's preferred scroll physics and fling behavior. It offers a subset of
 * the APIs from Scroller or OverScroller.</p>
 */
public class ScrollerCompat {
    private static final String TAG = "ScrollerCompat";

    Object mScroller;
    ScrollerCompatImpl mImpl;

    interface ScrollerCompatImpl {
        Object createScroller(Context context, Interpolator interpolator);
        boolean isFinished(Object scroller);
        int getCurrX(Object scroller);
        int getCurrY(Object scroller);
        float getCurrVelocity(Object scroller);
        boolean computeScrollOffset(Object scroller);
        void startScroll(Object scroller, int startX, int startY, int dx, int dy);
        void startScroll(Object scroller, int startX, int startY, int dx, int dy, int duration);
        void fling(Object scroller, int startX, int startY, int velX, int velY,
                int minX, int maxX, int minY, int maxY);
        void fling(Object scroller, int startX, int startY, int velX, int velY,
                int minX, int maxX, int minY, int maxY, int overX, int overY);
        void abortAnimation(Object scroller);
        void notifyHorizontalEdgeReached(Object scroller, int startX, int finalX, int overX);
        void notifyVerticalEdgeReached(Object scroller, int startY, int finalY, int overY);
        boolean isOverScrolled(Object scroller);
        int getFinalX(Object scroller);
        int getFinalY(Object scroller);
    }

    static final int CHASE_FRAME_TIME = 16; // ms per target frame

    static class Chaser {
        private int mX;
        private int mY;
        private int mTargetX;
        private int mTargetY;
        private float mTranslateSmoothing = 2;
        private long mLastTime;
        private boolean mFinished = true;

        @Override
        public String toString() {
            return "{x=" + mX + " y=" + mY + " targetX=" + mTargetX + " targetY=" + mTargetY +
                    " smoothing=" + mTranslateSmoothing + " lastTime=" + mLastTime + "}";
        }

        public int getCurrX() {
            return mX;
        }

        public int getCurrY() {
            return mY;
        }

        public int getFinalX() {
            return mTargetX;
        }

        public int getFinalY() {
            return mTargetY;
        }

        public void setCurrentPosition(int x, int y) {
            mX = x;
            mY = y;
            mFinished = false;
        }

        public void setSmoothing(float smoothing) {
            if (smoothing < 0) {
                throw new IllegalArgumentException("smoothing value must be positive");
            }
            mTranslateSmoothing = smoothing;
        }

        public boolean isSmoothingEnabled() {
            return mTranslateSmoothing > 0;
        }

        public void setTarget(int targetX, int targetY) {
            mTargetX = targetX;
            mTargetY = targetY;
        }

        public void abort() {
            mX = mTargetX;
            mY = mTargetY;
            mLastTime = AnimationUtils.currentAnimationTimeMillis();
            mFinished = true;
        }

        public boolean isFinished() {
            return mFinished || (mX == mTargetX && mY == mTargetY);
        }

        public boolean computeScrollOffset() {
            if (isSmoothingEnabled() && !isFinished()) {
                final long now = AnimationUtils.currentAnimationTimeMillis();
                final long dt = now - mLastTime;
                final float framesElapsed = (float) dt / CHASE_FRAME_TIME;

                if (framesElapsed > 0) {
                    for (int i = 0; i < framesElapsed; i++) {
                        final int totalDx = mTargetX - mX;
                        final int totalDy = mTargetY - mY;

                        final int dx = (int) (totalDx / mTranslateSmoothing);
                        final int dy = (int) (totalDy / mTranslateSmoothing);

                        mX += dx;
                        mY += dy;

                        // Handle cropping at the end
                        if (mX != mTargetX && dx == 0) {
                            mX = mTargetX;
                        }
                        if (mY != mTargetY && dy == 0) {
                            mY = mTargetY;
                        }
                    }

                    mLastTime = now;
                }
                mFinished = mX == mTargetX && mY == mTargetY;
                return true;
            }
            return false;
        }
    }

    static class ScrollerCompatImplBase implements ScrollerCompatImpl {
        private Chaser mChaser;

        public ScrollerCompatImplBase() {
            mChaser = createChaser();
        }

        protected Chaser createChaser() {
            // Override if running on a platform version where this isn't needed
            return new Chaser();
        }

        @Override
        public Object createScroller(Context context, Interpolator interpolator) {
            return interpolator != null ?
                    new Scroller(context, interpolator) : new Scroller(context);
        }

        @Override
        public boolean isFinished(Object scroller) {
            return (!isSmoothingEnabled() || mChaser.isFinished()) &&
                    ((Scroller) scroller).isFinished();
        }

        @Override
        public int getCurrX(Object scroller) {
            if (isSmoothingEnabled()) {
                return mChaser.getCurrX();
            }
            return ((Scroller) scroller).getCurrX();
        }

        @Override
        public int getCurrY(Object scroller) {
            if (isSmoothingEnabled()) {
                return mChaser.getCurrY();
            }
            return ((Scroller) scroller).getCurrY();
        }

        @Override
        public float getCurrVelocity(Object scroller) {
            return 0;
        }

        @Override
        public boolean computeScrollOffset(Object scroller) {
            final Scroller s = (Scroller) scroller;
            final boolean result = s.computeScrollOffset();
            if (isSmoothingEnabled()) {
                mChaser.setTarget(s.getCurrX(), s.getCurrY());
                if (isSmoothingEnabled() && !mChaser.isFinished()) {
                    return mChaser.computeScrollOffset() || result;
                }
            }
            return result;
        }

        private boolean isSmoothingEnabled() {
            return mChaser != null && mChaser.isSmoothingEnabled();
        }

        @Override
        public void startScroll(Object scroller, int startX, int startY, int dx, int dy) {
            if (isSmoothingEnabled()) {
                mChaser.abort();
                mChaser.setCurrentPosition(startX, startY);
            }
            ((Scroller) scroller).startScroll(startX, startY, dx, dy);
        }

        @Override
        public void startScroll(Object scroller, int startX, int startY, int dx, int dy,
                int duration) {
            if (isSmoothingEnabled()) {
                mChaser.abort();
                mChaser.setCurrentPosition(startX, startY);
            }
            ((Scroller) scroller).startScroll(startX, startY, dx, dy, duration);
        }

        @Override
        public void fling(Object scroller, int startX, int startY, int velX, int velY,
                int minX, int maxX, int minY, int maxY) {
            if (isSmoothingEnabled()) {
                mChaser.abort();
                mChaser.setCurrentPosition(startX, startY);
            }
            ((Scroller) scroller).fling(startX, startY, velX, velY, minX, maxX, minY, maxY);
        }

        @Override
        public void fling(Object scroller, int startX, int startY, int velX, int velY,
                int minX, int maxX, int minY, int maxY, int overX, int overY) {
            if (isSmoothingEnabled()) {
                mChaser.abort();
                mChaser.setCurrentPosition(startX, startY);
            }
            ((Scroller) scroller).fling(startX, startY, velX, velY, minX, maxX, minY, maxY);
        }

        @Override
        public void abortAnimation(Object scroller) {
            if (mChaser != null) {
                mChaser.abort();
            }
            ((Scroller) scroller).abortAnimation();
        }

        @Override
        public void notifyHorizontalEdgeReached(Object scroller, int startX, int finalX,
                int overX) {
            // No-op
        }

        @Override
        public void notifyVerticalEdgeReached(Object scroller, int startY, int finalY, int overY) {
            // No-op
        }

        @Override
        public boolean isOverScrolled(Object scroller) {
            // Always false
            return false;
        }

        @Override
        public int getFinalX(Object scroller) {
            return ((Scroller) scroller).getFinalX();
        }

        @Override
        public int getFinalY(Object scroller) {
            return ((Scroller) scroller).getFinalY();
        }
    }

    static class ScrollerCompatImplGingerbread implements ScrollerCompatImpl {
        private Chaser mChaser;

        public ScrollerCompatImplGingerbread() {
            mChaser = createChaser();
        }

        @Override
        public Object createScroller(Context context, Interpolator interpolator) {
            return ScrollerCompatGingerbread.createScroller(context, interpolator);
        }

        protected Chaser createChaser() {
            return new Chaser();
        }

        @Override
        public boolean isFinished(Object scroller) {
            return (!isSmoothingEnabled() || mChaser.isFinished()) &&
                    ScrollerCompatGingerbread.isFinished(scroller);
        }

        @Override
        public int getCurrX(Object scroller) {
            if (isSmoothingEnabled()) {
                return mChaser.getCurrX();
            }
            return ScrollerCompatGingerbread.getCurrX(scroller);
        }

        @Override
        public int getCurrY(Object scroller) {
            if (isSmoothingEnabled()) {
                return mChaser.getCurrY();
            }
            return ScrollerCompatGingerbread.getCurrY(scroller);
        }

        @Override
        public float getCurrVelocity(Object scroller) {
            return 0;
        }

        @Override
        public boolean computeScrollOffset(Object scroller) {
            final boolean result = ScrollerCompatGingerbread.computeScrollOffset(scroller);
            if (isSmoothingEnabled()) {
                mChaser.setTarget(ScrollerCompatGingerbread.getCurrX(scroller),
                        ScrollerCompatGingerbread.getCurrY(scroller));
                if (!mChaser.isFinished()) {
                    return mChaser.computeScrollOffset() || result;
                }
            }
            return result;
        }

        private boolean isSmoothingEnabled() {
            return mChaser != null && mChaser.isSmoothingEnabled();
        }

        @Override
        public void startScroll(Object scroller, int startX, int startY, int dx, int dy) {
            if (isSmoothingEnabled()) {
                mChaser.abort();
                mChaser.setCurrentPosition(startX, startY);
            }
            ScrollerCompatGingerbread.startScroll(scroller, startX, startY, dx, dy);
        }

        @Override
        public void startScroll(Object scroller, int startX, int startY, int dx, int dy,
                int duration) {
            if (isSmoothingEnabled()) {
                mChaser.abort();
                mChaser.setCurrentPosition(startX, startY);
            }
            ScrollerCompatGingerbread.startScroll(scroller, startX, startY, dx, dy, duration);
        }

        @Override
        public void fling(Object scroller, int startX, int startY, int velX, int velY,
                int minX, int maxX, int minY, int maxY) {
            if (isSmoothingEnabled()) {
                mChaser.abort();
                mChaser.setCurrentPosition(startX, startY);
            }
            ScrollerCompatGingerbread.fling(scroller, startX, startY, velX, velY,
                    minX, maxX, minY, maxY);
        }

        @Override
        public void fling(Object scroller, int startX, int startY, int velX, int velY,
                int minX, int maxX, int minY, int maxY, int overX, int overY) {
            if (isSmoothingEnabled()) {
                mChaser.abort();
                mChaser.setCurrentPosition(startX, startY);
            }
            ScrollerCompatGingerbread.fling(scroller, startX, startY, velX, velY,
                    minX, maxX, minY, maxY, overX, overY);
        }

        @Override
        public void abortAnimation(Object scroller) {
            if (mChaser != null) {
                mChaser.abort();
            }
            ScrollerCompatGingerbread.abortAnimation(scroller);
        }

        @Override
        public void notifyHorizontalEdgeReached(Object scroller, int startX, int finalX,
                int overX) {
            ScrollerCompatGingerbread.notifyHorizontalEdgeReached(scroller, startX, finalX, overX);
        }

        @Override
        public void notifyVerticalEdgeReached(Object scroller, int startY, int finalY, int overY) {
            ScrollerCompatGingerbread.notifyVerticalEdgeReached(scroller, startY, finalY, overY);
        }

        @Override
        public boolean isOverScrolled(Object scroller) {
            return ScrollerCompatGingerbread.isOverScrolled(scroller);
        }

        @Override
        public int getFinalX(Object scroller) {
            return ScrollerCompatGingerbread.getFinalX(scroller);
        }

        @Override
        public int getFinalY(Object scroller) {
            return ScrollerCompatGingerbread.getFinalY(scroller);
        }
    }

    static class ScrollerCompatImplIcs extends ScrollerCompatImplGingerbread {
        @Override
        public float getCurrVelocity(Object scroller) {
            return ScrollerCompatIcs.getCurrVelocity(scroller);
        }
    }

    public static ScrollerCompat create(Context context) {
        return create(context, null);
    }

    public static ScrollerCompat create(Context context, Interpolator interpolator) {
        return new ScrollerCompat(context, interpolator);
    }

    ScrollerCompat(Context context, Interpolator interpolator) {
        final int version = Build.VERSION.SDK_INT;
        if (version >= 14) { // ICS
            mImpl = new ScrollerCompatImplIcs();
        } else if (version >= 9) { // Gingerbread
            mImpl = new ScrollerCompatImplGingerbread();
        } else {
            mImpl = new ScrollerCompatImplBase();
        }
        mScroller = mImpl.createScroller(context, interpolator);
    }

    /**
     * Returns whether the scroller has finished scrolling.
     *
     * @return True if the scroller has finished scrolling, false otherwise.
     */
    public boolean isFinished() {
        return mImpl.isFinished(mScroller);
    }

    /**
     * Returns the current X offset in the scroll.
     *
     * @return The new X offset as an absolute distance from the origin.
     */
    public int getCurrX() {
        return mImpl.getCurrX(mScroller);
    }

    /**
     * Returns the current Y offset in the scroll.
     *
     * @return The new Y offset as an absolute distance from the origin.
     */
    public int getCurrY() {
        return mImpl.getCurrY(mScroller);
    }

    /**
     * @return The final X position for the scroll in progress, if known.
     */
    public int getFinalX() {
        return mImpl.getFinalX(mScroller);
    }

    /**
     * @return The final Y position for the scroll in progress, if known.
     */
    public int getFinalY() {
        return mImpl.getFinalY(mScroller);
    }

    /**
     * Returns the current velocity on platform versions that support it.
     *
     * <p>The device must support at least API level 14 (Ice Cream Sandwich).
     * On older platform versions this method will return 0. This method should
     * only be used as input for nonessential visual effects such as {@link EdgeEffectCompat}.</p>
     *
     * @return The original velocity less the deceleration. Result may be
     * negative.
     */
    public float getCurrVelocity() {
        return mImpl.getCurrVelocity(mScroller);
    }

    /**
     * Call this when you want to know the new location.  If it returns true,
     * the animation is not yet finished.  loc will be altered to provide the
     * new location.
     */
    public boolean computeScrollOffset() {
        return mImpl.computeScrollOffset(mScroller);
    }

    /**
     * Start scrolling by providing a starting point and the distance to travel.
     * The scroll will use the default value of 250 milliseconds for the
     * duration.
     *
     * @param startX Starting horizontal scroll offset in pixels. Positive
     *        numbers will scroll the content to the left.
     * @param startY Starting vertical scroll offset in pixels. Positive numbers
     *        will scroll the content up.
     * @param dx Horizontal distance to travel. Positive numbers will scroll the
     *        content to the left.
     * @param dy Vertical distance to travel. Positive numbers will scroll the
     *        content up.
     */
    public void startScroll(int startX, int startY, int dx, int dy) {
        mImpl.startScroll(mScroller, startX, startY, dx, dy);
    }

    /**
     * Start scrolling by providing a starting point and the distance to travel.
     *
     * @param startX Starting horizontal scroll offset in pixels. Positive
     *        numbers will scroll the content to the left.
     * @param startY Starting vertical scroll offset in pixels. Positive numbers
     *        will scroll the content up.
     * @param dx Horizontal distance to travel. Positive numbers will scroll the
     *        content to the left.
     * @param dy Vertical distance to travel. Positive numbers will scroll the
     *        content up.
     * @param duration Duration of the scroll in milliseconds.
     */
    public void startScroll(int startX, int startY, int dx, int dy, int duration) {
        mImpl.startScroll(mScroller, startX, startY, dx, dy, duration);
    }

    /**
     * Start scrolling based on a fling gesture. The distance travelled will
     * depend on the initial velocity of the fling.
     *
     * @param startX Starting point of the scroll (X)
     * @param startY Starting point of the scroll (Y)
     * @param velocityX Initial velocity of the fling (X) measured in pixels per
     *        second.
     * @param velocityY Initial velocity of the fling (Y) measured in pixels per
     *        second
     * @param minX Minimum X value. The scroller will not scroll past this
     *        point.
     * @param maxX Maximum X value. The scroller will not scroll past this
     *        point.
     * @param minY Minimum Y value. The scroller will not scroll past this
     *        point.
     * @param maxY Maximum Y value. The scroller will not scroll past this
     *        point.
     */
    public void fling(int startX, int startY, int velocityX, int velocityY,
            int minX, int maxX, int minY, int maxY) {
        mImpl.fling(mScroller, startX, startY, velocityX, velocityY, minX, maxX, minY, maxY);
    }

    /**
     * Start scrolling based on a fling gesture. The distance travelled will
     * depend on the initial velocity of the fling.
     *
     * @param startX Starting point of the scroll (X)
     * @param startY Starting point of the scroll (Y)
     * @param velocityX Initial velocity of the fling (X) measured in pixels per
     *        second.
     * @param velocityY Initial velocity of the fling (Y) measured in pixels per
     *        second
     * @param minX Minimum X value. The scroller will not scroll past this
     *        point.
     * @param maxX Maximum X value. The scroller will not scroll past this
     *        point.
     * @param minY Minimum Y value. The scroller will not scroll past this
     *        point.
     * @param maxY Maximum Y value. The scroller will not scroll past this
     *        point.
     * @param overX Overfling range. If > 0, horizontal overfling in either
     *            direction will be possible.
     * @param overY Overfling range. If > 0, vertical overfling in either
     *            direction will be possible.
     */
    public void fling(int startX, int startY, int velocityX, int velocityY,
            int minX, int maxX, int minY, int maxY, int overX, int overY) {
        mImpl.fling(mScroller, startX, startY, velocityX, velocityY,
                minX, maxX, minY, maxY, overX, overY);
    }

    /**
     * Stops the animation. Aborting the animation causes the scroller to move to the final x and y
     * position.
     */
    public void abortAnimation() {
        mImpl.abortAnimation(mScroller);
    }


    /**
     * Notify the scroller that we've reached a horizontal boundary.
     * Normally the information to handle this will already be known
     * when the animation is started, such as in a call to one of the
     * fling functions. However there are cases where this cannot be known
     * in advance. This function will transition the current motion and
     * animate from startX to finalX as appropriate.
     *
     * @param startX Starting/current X position
     * @param finalX Desired final X position
     * @param overX Magnitude of overscroll allowed. This should be the maximum
     *              desired distance from finalX. Absolute value - must be positive.
     */
    public void notifyHorizontalEdgeReached(int startX, int finalX, int overX) {
        mImpl.notifyHorizontalEdgeReached(mScroller, startX, finalX, overX);
    }

    /**
     * Notify the scroller that we've reached a vertical boundary.
     * Normally the information to handle this will already be known
     * when the animation is started, such as in a call to one of the
     * fling functions. However there are cases where this cannot be known
     * in advance. This function will animate a parabolic motion from
     * startY to finalY.
     *
     * @param startY Starting/current Y position
     * @param finalY Desired final Y position
     * @param overY Magnitude of overscroll allowed. This should be the maximum
     *              desired distance from finalY. Absolute value - must be positive.
     */
    public void notifyVerticalEdgeReached(int startY, int finalY, int overY) {
        mImpl.notifyVerticalEdgeReached(mScroller, startY, finalY, overY);
    }

    /**
     * Returns whether the current Scroller is currently returning to a valid position.
     * Valid bounds were provided by the
     * {@link #fling(int, int, int, int, int, int, int, int, int, int)} method.
     *
     * One should check this value before calling
     * {@link #startScroll(int, int, int, int)} as the interpolation currently in progress
     * to restore a valid position will then be stopped. The caller has to take into account
     * the fact that the started scroll will start from an overscrolled position.
     *
     * @return true when the current position is overscrolled and in the process of
     *         interpolating back to a valid value.
     */
    public boolean isOverScrolled() {
        return mImpl.isOverScrolled(mScroller);
    }
}
