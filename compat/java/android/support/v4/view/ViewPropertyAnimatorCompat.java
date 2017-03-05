/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.support.v4.view;

import android.support.annotation.RequiresApi;
import android.os.Build;
import android.view.View;
import android.view.animation.Interpolator;

import java.lang.ref.WeakReference;

public final class ViewPropertyAnimatorCompat {
    private static final String TAG = "ViewAnimatorCompat";
    private WeakReference<View> mView;
    Runnable mStartAction = null;
    Runnable mEndAction = null;
    int mOldLayerType = -1;
    // HACK ALERT! Choosing this id knowing that the framework does not use it anywhere
    // internally and apps should use ids higher than it
    static final int LISTENER_TAG_ID = 0x7e000000;

    ViewPropertyAnimatorCompat(View view) {
        mView = new WeakReference<View>(view);
    }

    interface ViewPropertyAnimatorCompatImpl {
        Interpolator getInterpolator(ViewPropertyAnimatorCompat vpa, View view);
        void z(ViewPropertyAnimatorCompat vpa, View view, float value);
        void zBy(ViewPropertyAnimatorCompat vpa, View view, float value);
        void translationZ(ViewPropertyAnimatorCompat vpa, View view, float value);
        void translationZBy(ViewPropertyAnimatorCompat vpa, View view, float value);
        void withLayer(ViewPropertyAnimatorCompat vpa, View view);
        void withStartAction(ViewPropertyAnimatorCompat vpa, View view, Runnable runnable);
        void withEndAction(ViewPropertyAnimatorCompat vpa, View view, Runnable runnable);
        void setListener(ViewPropertyAnimatorCompat vpa, View view,
                ViewPropertyAnimatorListener listener);
        void setUpdateListener(ViewPropertyAnimatorCompat vpa, View view,
                ViewPropertyAnimatorUpdateListener listener);
    }

    static class ViewPropertyAnimatorCompatBaseImpl implements ViewPropertyAnimatorCompatImpl {
        @Override
        public void setListener(ViewPropertyAnimatorCompat vpa, View view,
                ViewPropertyAnimatorListener listener) {
            view.setTag(LISTENER_TAG_ID, listener);
            ViewPropertyAnimatorCompatICS.setListener(view, new MyVpaListener(vpa));
        }

        @Override
        public void withEndAction(ViewPropertyAnimatorCompat vpa, View view,
                final Runnable runnable) {
            ViewPropertyAnimatorCompatICS.setListener(view, new MyVpaListener(vpa));
            vpa.mEndAction = runnable;
        }

        @Override
        public void withStartAction(ViewPropertyAnimatorCompat vpa, View view,
                final Runnable runnable) {
            ViewPropertyAnimatorCompatICS.setListener(view, new MyVpaListener(vpa));
            vpa.mStartAction = runnable;
        }

        @Override
        public void withLayer(ViewPropertyAnimatorCompat vpa, View view) {
            vpa.mOldLayerType = view.getLayerType();
            ViewPropertyAnimatorCompatICS.setListener(view, new MyVpaListener(vpa));
        }

        @Override
        public Interpolator getInterpolator(ViewPropertyAnimatorCompat vpa, View view) {
            return null;
        }

        @Override
        public void z(ViewPropertyAnimatorCompat vpa, View view, float value) {
            // noop on versions prior to Lollipop
        }

        @Override
        public void zBy(ViewPropertyAnimatorCompat vpa, View view, float value) {
            // noop on versions prior to Lollipop
        }

        @Override
        public void translationZ(ViewPropertyAnimatorCompat vpa, View view, float value) {
            // noop on versions prior to Lollipop
        }

        @Override
        public void translationZBy(ViewPropertyAnimatorCompat vpa, View view, float value) {
            // noop on versions prior to Lollipop
        }

        @Override
        public void setUpdateListener(ViewPropertyAnimatorCompat vpa, View view,
                ViewPropertyAnimatorUpdateListener listener) {
            // noop
        }

        static class MyVpaListener implements ViewPropertyAnimatorListener {
            ViewPropertyAnimatorCompat mVpa;
            boolean mAnimEndCalled;

            MyVpaListener(ViewPropertyAnimatorCompat vpa) {
                mVpa = vpa;
            }

            @Override
            public void onAnimationStart(View view) {
                // Reset our end called flag, since this is a new animation...
                mAnimEndCalled = false;

                if (mVpa.mOldLayerType >= 0) {
                    view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                }
                if (mVpa.mStartAction != null) {
                    Runnable startAction = mVpa.mStartAction;
                    mVpa.mStartAction = null;
                    startAction.run();
                }
                Object listenerTag = view.getTag(LISTENER_TAG_ID);
                ViewPropertyAnimatorListener listener = null;
                if (listenerTag instanceof ViewPropertyAnimatorListener) {
                    listener = (ViewPropertyAnimatorListener) listenerTag;
                }
                if (listener != null) {
                    listener.onAnimationStart(view);
                }
            }

            @Override
            public void onAnimationEnd(View view) {
                if (mVpa.mOldLayerType >= 0) {
                    view.setLayerType(mVpa.mOldLayerType, null);
                    mVpa.mOldLayerType = -1;
                }
                if (Build.VERSION.SDK_INT >= 16 || !mAnimEndCalled) {
                    // Pre-v16 seems to have a bug where onAnimationEnd is called
                    // twice, therefore we only dispatch on the first call
                    if (mVpa.mEndAction != null) {
                        Runnable endAction = mVpa.mEndAction;
                        mVpa.mEndAction = null;
                        endAction.run();
                    }
                    Object listenerTag = view.getTag(LISTENER_TAG_ID);
                    ViewPropertyAnimatorListener listener = null;
                    if (listenerTag instanceof ViewPropertyAnimatorListener) {
                        listener = (ViewPropertyAnimatorListener) listenerTag;
                    }
                    if (listener != null) {
                        listener.onAnimationEnd(view);
                    }
                    mAnimEndCalled = true;
                }
            }

            @Override
            public void onAnimationCancel(View view) {
                Object listenerTag = view.getTag(LISTENER_TAG_ID);
                ViewPropertyAnimatorListener listener = null;
                if (listenerTag instanceof ViewPropertyAnimatorListener) {
                    listener = (ViewPropertyAnimatorListener) listenerTag;
                }
                if (listener != null) {
                    listener.onAnimationCancel(view);
                }
            }
        }
    }

    @RequiresApi(16)
    static class ViewPropertyAnimatorCompatApi16Impl extends ViewPropertyAnimatorCompatBaseImpl {

        @Override
        public void setListener(ViewPropertyAnimatorCompat vpa, View view, ViewPropertyAnimatorListener listener) {
            ViewPropertyAnimatorCompatJB.setListener(view, listener);
        }

        @Override
        public void withStartAction(ViewPropertyAnimatorCompat vpa, View view, Runnable runnable) {
            view.animate().withStartAction(runnable);
        }

        @Override
        public void withEndAction(ViewPropertyAnimatorCompat vpa, View view, Runnable runnable) {
            view.animate().withEndAction(runnable);
        }

        @Override
        public void withLayer(ViewPropertyAnimatorCompat vpa, View view) {
            view.animate().withLayer();
        }
    }

    @RequiresApi(18)
    static class ViewPropertyAnimatorCompatApi18Impl extends ViewPropertyAnimatorCompatApi16Impl {

        @Override
        public Interpolator getInterpolator(ViewPropertyAnimatorCompat vpa, View view) {
            return (Interpolator) view.animate().getInterpolator();
        }
    }

    @RequiresApi(19)
    static class ViewPropertyAnimatorCompatApi19Impl extends ViewPropertyAnimatorCompatApi18Impl {
        @Override
        public void setUpdateListener(ViewPropertyAnimatorCompat vpa, View view,
                ViewPropertyAnimatorUpdateListener listener) {
            ViewPropertyAnimatorCompatKK.setUpdateListener(view, listener);
        }
    }

    @RequiresApi(21)
    static class ViewPropertyAnimatorCompatApi21Impl extends
            ViewPropertyAnimatorCompatApi19Impl {
        @Override
        public void translationZ(ViewPropertyAnimatorCompat vpa, View view, float value) {
            view.animate().translationZ(value);
        }

        @Override
        public void translationZBy(ViewPropertyAnimatorCompat vpa, View view, float value) {
            view.animate().translationZBy(value);
        }

        @Override
        public void z(ViewPropertyAnimatorCompat vpa, View view, float value) {
            view.animate().z(value);
        }

        @Override
        public void zBy(ViewPropertyAnimatorCompat vpa, View view, float value) {
            view.animate().zBy(value);
        }
    }

    static final ViewPropertyAnimatorCompatImpl IMPL;
    static {
        if (Build.VERSION.SDK_INT >= 21) {
            IMPL = new ViewPropertyAnimatorCompatApi21Impl();
        } else if (Build.VERSION.SDK_INT >= 19) {
            IMPL = new ViewPropertyAnimatorCompatApi19Impl();
        } else if (Build.VERSION.SDK_INT >= 18) {
            IMPL = new ViewPropertyAnimatorCompatApi18Impl();
        } else if (Build.VERSION.SDK_INT >= 16) {
            IMPL = new ViewPropertyAnimatorCompatApi16Impl();
        } else {
            IMPL = new ViewPropertyAnimatorCompatBaseImpl();
        }
    }

    /**
     * Sets the duration for the underlying animator that animates the requested properties.
     * By default, the animator uses the default value for ValueAnimator. Calling this method
     * will cause the declared value to be used instead.
     *
     * @param value The length of ensuing property animations, in milliseconds. The value
     * cannot be negative.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat setDuration(long value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().setDuration(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>alpha</code> property to be animated to the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The value to be animated to.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat alpha(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().alpha(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>alpha</code> property to be animated by the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The amount to be animated by, as an offset from the current value.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat alphaBy(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().alphaBy(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>translationX</code> property to be animated to the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The value to be animated to.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat translationX(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().translationX(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>translationY</code> property to be animated to the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The value to be animated to.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat translationY(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().translationY(value);
        }
        return this;
    }

    /**
     * Specifies an action to take place when the next animation ends. The action is only
     * run if the animation ends normally; if the ViewPropertyAnimator is canceled during
     * that animation, the runnable will not run.
     * This method, along with {@link #withStartAction(Runnable)}, is intended to help facilitate
     * choreographing ViewPropertyAnimator animations with other animations or actions
     * in the application.
     *
     * <p>For example, the following code animates a view to x=200 and then back to 0:</p>
     * <pre>
     *     Runnable endAction = new Runnable() {
     *         public void run() {
     *             view.animate().x(0);
     *         }
     *     };
     *     view.animate().x(200).withEndAction(endAction);
     * </pre>
     *
     * <p>Prior to API 14, this method will run the action immediately.</p>
     *
     * <p>For API 14 and 15, this method will run by setting
     * a listener on the ViewPropertyAnimatorCompat object and running the action
     * in that listener's {@link ViewPropertyAnimatorListener#onAnimationEnd(View)} method.</p>
     *
     * @param runnable The action to run when the next animation ends.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat withEndAction(Runnable runnable) {
        View view;
        if ((view = mView.get()) != null) {
            IMPL.withEndAction(this, view, runnable);
        }
        return this;
    }

    /**
     * Returns the current duration of property animations. If the duration was set on this
     * object, that value is returned. Otherwise, the default value of the underlying Animator
     * is returned.
     *
     * @see #setDuration(long)
     * @return The duration of animations, in milliseconds.
     */
    public long getDuration() {
        View view;
        if ((view = mView.get()) != null) {
            return view.animate().getDuration();
        } else {
            return 0;
        }
    }

    /**
     * Sets the interpolator for the underlying animator that animates the requested properties.
     * By default, the animator uses the default interpolator for ValueAnimator. Calling this method
     * will cause the declared object to be used instead.
     *
     * @param value The TimeInterpolator to be used for ensuing property animations.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat setInterpolator(Interpolator value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().setInterpolator(value);
        }
        return this;
    }

    /**
     * Returns the timing interpolator that this animation uses.
     *
     * @return The timing interpolator for this animation.
     */
    public Interpolator getInterpolator() {
        View view;
        if ((view = mView.get()) != null) {
            return IMPL.getInterpolator(this, view);
        }
        else return null;
    }

    /**
     * Sets the startDelay for the underlying animator that animates the requested properties.
     * By default, the animator uses the default value for ValueAnimator. Calling this method
     * will cause the declared value to be used instead.
     *
     * @param value The delay of ensuing property animations, in milliseconds. The value
     * cannot be negative.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat setStartDelay(long value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().setStartDelay(value);
        }
        return this;
    }

    /**
     * Returns the current startDelay of property animations. If the startDelay was set on this
     * object, that value is returned. Otherwise, the default value of the underlying Animator
     * is returned.
     *
     * @see #setStartDelay(long)
     * @return The startDelay of animations, in milliseconds.
     */
    public long getStartDelay() {
        View view;
        if ((view = mView.get()) != null) {
            return view.animate().getStartDelay();
        } else {
            return 0;
        }
    }

    /**
     * This method will cause the View's <code>rotation</code> property to be animated to the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The value to be animated to.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat rotation(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().rotation(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>rotation</code> property to be animated by the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The amount to be animated by, as an offset from the current value.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat rotationBy(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().rotationBy(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>rotationX</code> property to be animated to the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The value to be animated to.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat rotationX(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().rotationX(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>rotationX</code> property to be animated by the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The amount to be animated by, as an offset from the current value.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat rotationXBy(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().rotationXBy(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>rotationY</code> property to be animated to the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The value to be animated to.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat rotationY(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().rotationY(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>rotationY</code> property to be animated by the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The amount to be animated by, as an offset from the current value.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat rotationYBy(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().rotationYBy(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>scaleX</code> property to be animated to the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The value to be animated to.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat scaleX(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().scaleX(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>scaleX</code> property to be animated by the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The amount to be animated by, as an offset from the current value.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat scaleXBy(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().scaleXBy(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>scaleY</code> property to be animated to the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The value to be animated to.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat scaleY(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().scaleY(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>scaleY</code> property to be animated by the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The amount to be animated by, as an offset from the current value.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat scaleYBy(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().scaleYBy(value);
        }
        return this;
    }

    /**
     * Cancels all property animations that are currently running or pending.
     */
    public void cancel() {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().cancel();
        }
    }

    /**
     * This method will cause the View's <code>x</code> property to be animated to the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The value to be animated to.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat x(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().x(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>x</code> property to be animated by the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The amount to be animated by, as an offset from the current value.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat xBy(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().xBy(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>y</code> property to be animated to the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The value to be animated to.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat y(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().y(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>y</code> property to be animated by the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The amount to be animated by, as an offset from the current value.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat yBy(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().yBy(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>translationX</code> property to be animated by the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The amount to be animated by, as an offset from the current value.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat translationXBy(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().translationXBy(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>translationY</code> property to be animated by the
     * specified value. Animations already running on the property will be canceled.
     *
     * @param value The amount to be animated by, as an offset from the current value.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat translationYBy(float value) {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().translationYBy(value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>translationZ</code> property to be animated by the
     * specified value. Animations already running on the property will be canceled.
     *
     * <p>Prior to API 21, this method will do nothing.</p>
     *
     * @param value The amount to be animated by, as an offset from the current value.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat translationZBy(float value) {
        View view;
        if ((view = mView.get()) != null) {
            IMPL.translationZBy(this, view, value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>translationZ</code> property to be animated to the
     * specified value. Animations already running on the property will be canceled.
     *
     * <p>Prior to API 21, this method will do nothing.</p>
     *
     * @param value The amount to be animated by, as an offset from the current value.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat translationZ(float value) {
        View view;
        if ((view = mView.get()) != null) {
            IMPL.translationZ(this, view, value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>z</code> property to be animated to the
     * specified value. Animations already running on the property will be canceled.
     *
     * <p>Prior to API 21, this method will do nothing.</p>
     *
     * @param value The amount to be animated by, as an offset from the current value.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat z(float value) {
        View view;
        if ((view = mView.get()) != null) {
            IMPL.z(this, view, value);
        }
        return this;
    }

    /**
     * This method will cause the View's <code>z</code> property to be animated by the
     * specified value. Animations already running on the property will be canceled.
     *
     * <p>Prior to API 21, this method will do nothing.</p>
     *
     * @param value The amount to be animated by, as an offset from the current value.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat zBy(float value) {
        View view;
        if ((view = mView.get()) != null) {
            IMPL.zBy(this, view, value);
        }
        return this;
    }

    /**
     * Starts the currently pending property animations immediately. Calling <code>start()</code>
     * is optional because all animations start automatically at the next opportunity. However,
     * if the animations are needed to start immediately and synchronously (not at the time when
     * the next event is processed by the hierarchy, which is when the animations would begin
     * otherwise), then this method can be used.
     */
    public void start() {
        View view;
        if ((view = mView.get()) != null) {
            view.animate().start();
        }
    }

    /**
     * The View associated with this ViewPropertyAnimator will have its
     * {@link View#setLayerType(int, android.graphics.Paint) layer type} set to
     * {@link View#LAYER_TYPE_HARDWARE} for the duration of the next animation.
     * As stated in the documentation for {@link View#LAYER_TYPE_HARDWARE},
     * the actual type of layer used internally depends on the runtime situation of the
     * view. If the activity and this view are hardware-accelerated, then the layer will be
     * accelerated as well. If the activity or the view is not accelerated, then the layer will
     * effectively be the same as {@link View#LAYER_TYPE_SOFTWARE}.
     *
     * <p>This state is not persistent, either on the View or on this ViewPropertyAnimator: the
     * layer type of the View will be restored when the animation ends to what it was when this
     * method was called, and this setting on ViewPropertyAnimator is only valid for the next
     * animation. Note that calling this method and then independently setting the layer type of
     * the View (by a direct call to
     * {@link View#setLayerType(int, android.graphics.Paint)}) will result in some
     * inconsistency, including having the layer type restored to its pre-withLayer()
     * value when the animation ends.</p>
     *
     * <p>Prior to API 14, this method will do nothing.</p>
     *
     * <p>For API 14 and 15, this method will run by setting
     * a listener on the ViewPropertyAnimatorCompat object, setting a hardware layer in
     * the listener's {@link ViewPropertyAnimatorListener#onAnimationStart(View)} method,
     * and then restoring the orignal layer type in the listener's
     * {@link ViewPropertyAnimatorListener#onAnimationEnd(View)} method.</p>
     *
     * @see View#setLayerType(int, android.graphics.Paint)
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat withLayer() {
        View view;
        if ((view = mView.get()) != null) {
            IMPL.withLayer(this, view);
        }
        return this;
    }

    /**
     * Specifies an action to take place when the next animation runs. If there is a
     * {@link #setStartDelay(long) startDelay} set on this ViewPropertyAnimator, then the
     * action will run after that startDelay expires, when the actual animation begins.
     * This method, along with {@link #withEndAction(Runnable)}, is intended to help facilitate
     * choreographing ViewPropertyAnimator animations with other animations or actions
     * in the application.
     *
     * <p>Prior to API 14, this method will run the action immediately.</p>
     *
     * <p>For API 14 and 15, this method will run by setting
     * a listener on the ViewPropertyAnimatorCompat object and running the action
     * in that listener's {@link ViewPropertyAnimatorListener#onAnimationStart(View)} method.</p>
     *
     * @param runnable The action to run when the next animation starts.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat withStartAction(Runnable runnable) {
        View view;
        if ((view = mView.get()) != null) {
            IMPL.withStartAction(this, view, runnable);
        }
        return this;
    }

    /**
     * Sets a listener for events in the underlying Animators that run the property
     * animations.
     *
     * <p>Prior to API 14, this method will do nothing.</p>
     *
     * @param listener The listener to be called with AnimatorListener events. A value of
     * <code>null</code> removes any existing listener.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat setListener(ViewPropertyAnimatorListener listener) {
        View view;
        if ((view = mView.get()) != null) {
            IMPL.setListener(this, view, listener);
        }
        return this;
    }

    /**
     * Sets a listener for update events in the underlying Animator that runs
     * the property animations.
     *
     * <p>Prior to API 19, this method will do nothing.</p>
     *
     * @param listener The listener to be called with update events. A value of
     * <code>null</code> removes any existing listener.
     * @return This object, allowing calls to methods in this class to be chained.
     */
    public ViewPropertyAnimatorCompat setUpdateListener(
            ViewPropertyAnimatorUpdateListener listener) {
        View view;
        if ((view = mView.get()) != null) {
            IMPL.setUpdateListener(this, view, listener);
        }
        return this;
    }
}
