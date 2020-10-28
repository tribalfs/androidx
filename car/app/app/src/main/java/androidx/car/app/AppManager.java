/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.car.app;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.annotation.SuppressLint;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.car.app.utils.RemoteUtils;
import androidx.car.app.utils.ThreadUtils;

import java.util.Objects;

// TODO(rampara): Uncomment on commit of model modules.
//import androidx.car.app.model.TemplateWrapper;

/** Manages the communication between the app and the host. */
public class AppManager {
    @NonNull
    @SuppressWarnings("UnusedVariable")
    // TODO(rampara): Remove suppress annotation on commit of model modules.
    private final CarContext mCarContext;
    @NonNull
    private final IAppManager.Stub mAppManager;
    @NonNull
    private final HostDispatcher mHostDispatcher;

    /**
     * Sets the {@link SurfaceListener} to get changes and updates to the surface on which the
     * app can draw custom content, or {@code null} to reset the listener.
     *
     * <p>This call requires the {@code androidx.car.app.ACCESS_SURFACE}
     * permission to be declared.
     *
     * <p>The {@link Surface} can be used to draw custom content such as a navigation app's map.
     *
     * @throws SecurityException if the app does not have the required permissions to access the
     *                           surface.
     * @throws HostException     if the remote call fails.
     */
    // TODO(rampara): Add Executor parameter.
    @SuppressLint("ExecutorRegistration")
    public void setSurfaceListener(@Nullable SurfaceListener surfaceListener) {
        mHostDispatcher.dispatch(
                CarContext.APP_SERVICE,
                (IAppHost host) -> {
                    host.setSurfaceListener(RemoteUtils.stubSurfaceListener(surfaceListener));
                    return null;
                },
                "setSurfaceListener");
    }

    // TODO(rampara): Change code tags to link after commit of model module.
    /**
     * Requests the current template to be invalidated, which eventually triggers a call to {@code
     * Screen#getTemplate} to get the new template to display.
     *
     * @throws HostException if the remote call fails.
     */
    public void invalidate() {
        mHostDispatcher.dispatch(
                CarContext.APP_SERVICE,
                (IAppHost host) -> {
                    host.invalidate();
                    return null;
                },
                "invalidate");
    }

    /**
     * Shows a toast on the car screen.
     *
     * @param text     the text to show.
     * @param duration how long to display the message.
     * @throws HostException if the remote call fails.
     */
    public void showToast(@NonNull CharSequence text, int duration) {
        mHostDispatcher.dispatch(
                CarContext.APP_SERVICE,
                (IAppHost host) -> {
                    host.showToast(text, duration);
                    return null;
                },
                "showToast");
    }

    /** Returns the {@code IAppManager.Stub} binder. */
    IAppManager.Stub getIInterface() {
        return mAppManager;
    }

    /** Creates an instance of {@link AppManager}. */
    static AppManager create(@NonNull CarContext carContext,
            @NonNull HostDispatcher hostDispatcher) {
        Objects.requireNonNull(carContext);
        Objects.requireNonNull(hostDispatcher);

        return new AppManager(carContext, hostDispatcher);
    }

    /** @hide */
    @RestrictTo(LIBRARY_GROUP) // Restrict to testing library
    protected AppManager(@NonNull CarContext carContext, @NonNull HostDispatcher hostDispatcher) {
        this.mCarContext = carContext;
        this.mHostDispatcher = hostDispatcher;
        mAppManager =
                new IAppManager.Stub() {
                    @Override
                    public void getTemplate(IOnDoneCallback callback) {
                        ThreadUtils.runOnMain(
                                () -> {
                                    // TODO(rampara): Uncomment on commit of model modules.
//                                    TemplateWrapper templateWrapper;
//                                    try {
//                                        templateWrapper =
//                                                AppManager.this
//                                                        .mCarContext
//                                                        .getCarService(ScreenManager.class)
//                                                        .getTopTemplate();
//                                    } catch (RuntimeException e) {
//                                        RemoteUtils.sendFailureResponse(callback,
//                                        "getTemplate", e);
//                                        throw new WrappedRuntimeException(e);
//                                    }
//
//                                    RemoteUtils.sendSuccessResponse(callback, "getTemplate",
//                                            templateWrapper);
                                });
                    }

                    @Override
                    public void onBackPressed(IOnDoneCallback callback) {
                        RemoteUtils.dispatchHostCall(
                                carContext.getOnBackPressedDispatcher()::onBackPressed, callback,
                                "onBackPressed");
                    }
                };
    }
}
