/*
 * Copyright (C) 2019 The Android Open Source Project
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

package androidx.camera.core;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.util.Size;

import androidx.annotation.GuardedBy;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.camera.core.impl.utils.Threads;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.camera.core.impl.utils.futures.FutureCallback;
import androidx.camera.core.impl.utils.futures.FutureChain;
import androidx.camera.core.impl.utils.futures.Futures;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.core.util.Preconditions;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Main interface for accessing CameraX library.
 *
 * <p>This is a singleton class responsible for managing the set of camera instances and
 * attached use cases (such as {@link Preview}, {@link ImageAnalysis}, or {@link ImageCapture}.
 * Use cases are bound to a {@link LifecycleOwner} by calling
 * {@link #bindToLifecycle(LifecycleOwner, UseCase...)}   Once bound, the lifecycle of the
 * {@link LifecycleOwner} determines when the camera is started and stopped, and when camera data
 * is available to the use case.
 *
 * <p>It is often sufficient to just bind the use cases once when the activity is created, and
 * let the lifecycle handle the rest, so application code generally does not need to call
 * {@link #unbind(UseCase...)} nor call {@link #bindToLifecycle} more than once.
 *
 * <p>A lifecycle transition from {@link Lifecycle.State#CREATED} to {@link Lifecycle.State#STARTED}
 * state (via {@link Lifecycle.Event#ON_START}) initializes the camera asynchronously on a
 * CameraX managed thread. After initialization, the camera is opened and a camera capture
 * session is created.   If a {@link Preview} or {@link ImageAnalysis} is bound, those use cases
 * will begin to receive camera data after initialization completes. {@link ImageCapture} can
 * receive data via specific calls (such as {@link ImageCapture#takePicture}) after initialization
 * completes. Calling {@link #bindToLifecycle} with no Use Cases does nothing.
 *
 * <p>Binding to a {@link LifecycleOwner} when the state is {@link Lifecycle.State#STARTED} or
 * greater will also initialize and start data capture as though an
 * {@link Lifecycle.Event#ON_START} transition had occurred.  If the camera was already running
 * this may cause a new initialization to occur, temporarily stopping data from the camera before
 * restarting it.
 *
 * <p>After a lifecycle transition from {@link Lifecycle.State#STARTED} to
 * {@link Lifecycle.State#CREATED} state (via {@link Lifecycle.Event#ON_STOP}), use cases will no
 * longer receive camera data.  The camera capture session is destroyed and the camera device is
 * closed.  Use cases can remain bound and will become active again on the next
 * {@link Lifecycle.Event#ON_START} transition.
 *
 * <p>When the lifecycle transitions from {@link Lifecycle.State#CREATED} to the
 * {@link Lifecycle.State#DESTROYED} state (via {@link Lifecycle.Event#ON_DESTROY}) any
 * bound use cases are unbound and use case resources are freed.  Calls to {@link #bindToLifecycle}
 * when the lifecycle is in the {@link Lifecycle.State#DESTROYED} state will fail.
 * A call to {@link #bindToLifecycle} will need to be made with another lifecycle to rebind the
 * UseCase that has been unbound.
 *
 * <p>If the camera is not already closed, unbinding all use cases will cause the camera to close
 * asynchronously.
 *
 * <pre>{@code
 * public void setup() {
 *   // Initialize UseCase
 *   useCase = ...;
 *
 *   // UseCase binding event
 *   CameraX.bindToLifecycle(lifecycleOwner, useCase);
 *
 *   // Make calls on useCase
 * }
 *
 * public void operateOnUseCase() {
 *   if (CameraX.isBound(useCase)) {
 *     // Make calls on useCase
 *   }
 * }
 *
 * public void prematureTearDown() {
 *   // Not required since the lifecycle automatically stops the use case.  Can be used to
 *   // disassociate use cases from the lifecycle to move a use case to a different lifecycle.
 *   CameraX.unbindAll();
 * }
 * }</pre>
 *
 * <p>All operations on a use case, including binding and unbinding, should be done on the main
 * thread.  This is because lifecycle events are triggered on main thread and so accessing the use
 * case on the main thread guarantees that lifecycle state changes will not occur during execution
 * of a method call or binding/unbinding.
 */
@MainThread
public final class CameraX {
    private static final String TAG = "CameraX";
    private static final long WAIT_INITIALIZED_TIMEOUT = 3L;

    static final Object sInitDeinitLock = new Object();

    @GuardedBy("sInitDeinitLock")
    @Nullable
    static CameraX sInstance = null;

    @GuardedBy("sInitDeinitLock")
    private static boolean sTargetInitialized = false;

    @GuardedBy("sInitDeinitLock")
    @NonNull
    private static ListenableFuture<Void> sInitFuture = Futures.immediateFailedFuture(
            new IllegalStateException("CameraX is not initialized."));

    @GuardedBy("sInitDeinitLock")
    @NonNull
    private static ListenableFuture<Void> sDeinitFuture = Futures.immediateFuture(null);

    final CameraRepository mCameraRepository = new CameraRepository();
    private final Object mInitDeinitLock = new Object();
    private final UseCaseGroupRepository mUseCaseGroupRepository = new UseCaseGroupRepository();
    private final ErrorHandler mErrorHandler = new ErrorHandler();
    private final Executor mCameraExecutor;
    private CameraFactory mCameraFactory;
    private CameraDeviceSurfaceManager mSurfaceManager;
    private UseCaseConfigFactory mDefaultConfigFactory;
    private Context mContext;
    @GuardedBy("mInitDeinitLock")
    private InternalInitState mInitState = InternalInitState.UNINITIALIZED;
    @GuardedBy("mInitDeinitLock")
    private ListenableFuture<Void> mDeinitInternalFuture = Futures.immediateFuture(null);

    /** Prevents construction. */
    CameraX(@NonNull Executor executor) {
        Preconditions.checkNotNull(executor);
        mCameraExecutor = executor;
    }

    /**
     * Binds the collection of {@link UseCase} to a {@link LifecycleOwner}.
     *
     * <p>The state of the lifecycle will determine when the cameras are open, started, stopped
     * and closed.  When started, the use cases receive camera data.
     *
     * <p>Binding to a lifecycleOwner in state currently in {@link Lifecycle.State#STARTED} or
     * greater will also initialize and start data capture. If the camera was already running
     * this may cause a new initialization to occur temporarily stopping data from the camera
     * before restarting it.
     *
     * <p>Multiple use cases can be bound via adding them all to a single bindToLifecycle call, or
     * by using multiple bindToLifecycle calls.  Using a single call that includes all the use
     * cases helps to set up a camera session correctly for all uses cases, such as by allowing
     * determination of resolutions depending on all the use cases bound being bound.
     * If the use cases are bound separately, it will find the supported resolution with the
     * priority depending on the binding sequence. If the use cases are bound with a single call,
     * it will find the supported resolution with the priority in sequence of {@link ImageCapture},
     * {@link Preview} and then {@link ImageAnalysis}. The resolutions that can be supported depends
     * on the camera device hardware level that there are some default guaranteed resolutions
     * listed in {@link android.hardware.camera2.CameraDevice#createCaptureSession(List,
     * CameraCaptureSession.StateCallback, Handler)}.
     *
     * <p>Currently up to 3 use cases may be bound to a {@link Lifecycle} at any time. Exceeding
     * capability of target camera device will throw an IllegalArgumentException.
     *
     * <p>A UseCase should only be bound to a single lifecycle at a time.  Attempting to bind a
     * UseCase to a Lifecycle when it is already bound to another Lifecycle is an error, and the
     * UseCase binding will not change.
     *
     * <p>Only {@link UseCase} bound to latest active {@link Lifecycle} can keep alive.
     * {@link UseCase} bound to other {@link Lifecycle} will be stopped.
     *
     * @param lifecycleOwner The lifecycleOwner which controls the lifecycle transitions of the use
     *                       cases.
     * @param useCases       The use cases to bind to a lifecycle.
     * @throws IllegalStateException If the use case has already been bound to another lifecycle
     *                               or method is not called on main thread.
     */
    public static void bindToLifecycle(LifecycleOwner lifecycleOwner, UseCase... useCases) {
        Threads.checkMainThread();
        CameraX cameraX = checkInitialized();

        UseCaseGroupLifecycleController useCaseGroupLifecycleController =
                cameraX.getOrCreateUseCaseGroup(lifecycleOwner);
        UseCaseGroup useCaseGroupToBind = useCaseGroupLifecycleController.getUseCaseGroup();

        Collection<UseCaseGroupLifecycleController> controllers =
                cameraX.mUseCaseGroupRepository.getUseCaseGroups();
        for (UseCase useCase : useCases) {
            for (UseCaseGroupLifecycleController controller : controllers) {
                UseCaseGroup useCaseGroup = controller.getUseCaseGroup();
                if (useCaseGroup.contains(useCase) && useCaseGroup != useCaseGroupToBind) {
                    throw new IllegalStateException(
                            String.format(
                                    "Use case %s already bound to a different lifecycle.",
                                    useCase));
                }
            }
        }

        for (UseCase useCase : useCases) {
            useCase.onBind();
        }

        calculateSuggestedResolutions(lifecycleOwner, useCases);

        for (UseCase useCase : useCases) {
            useCaseGroupToBind.addUseCase(useCase);
            for (String cameraId : useCase.getAttachedCameraIds()) {
                attach(cameraId, useCase);
            }
        }

        useCaseGroupLifecycleController.notifyState();
    }

    /**
     * Returns true if the {@link UseCase} is bound to a lifecycle. Otherwise returns false.
     *
     * <p>After binding a use case with {@link #bindToLifecycle}, use cases remain bound until the
     * lifecycle reaches a {@link Lifecycle.State#DESTROYED} state or if is unbound by calls to
     * {@link #unbind(UseCase...)} or {@link #unbindAll()}.
     */
    public static boolean isBound(UseCase useCase) {
        CameraX cameraX = checkInitialized();

        Collection<UseCaseGroupLifecycleController> controllers =
                cameraX.mUseCaseGroupRepository.getUseCaseGroups();

        for (UseCaseGroupLifecycleController controller : controllers) {
            UseCaseGroup useCaseGroup = controller.getUseCaseGroup();
            if (useCaseGroup.contains(useCase)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Unbinds all specified use cases from the lifecycle.
     *
     * <p>This will initiate a close of every open camera which has zero {@link UseCase}
     * associated with it at the end of this call.
     *
     * <p>If a use case in the argument list is not bound, then it is simply ignored.
     *
     * <p>After unbinding a UseCase, the UseCase can be and bound to another {@link Lifecycle}
     * however listeners and settings should be reset by the application.
     *
     * @param useCases The collection of use cases to remove.
     * @throws IllegalStateException If not called on main thread.
     */
    public static void unbind(UseCase... useCases) {
        Threads.checkMainThread();
        CameraX cameraX = checkInitialized();

        Collection<UseCaseGroupLifecycleController> useCaseGroups =
                cameraX.mUseCaseGroupRepository.getUseCaseGroups();

        Map<String, List<UseCase>> detachingUseCaseMap = new HashMap<>();

        for (UseCase useCase : useCases) {
            for (UseCaseGroupLifecycleController useCaseGroupLifecycleController : useCaseGroups) {
                UseCaseGroup useCaseGroup = useCaseGroupLifecycleController.getUseCaseGroup();
                if (useCaseGroup.removeUseCase(useCase)) {
                    // Saves all detaching use cases and detach them at once.
                    for (String cameraId : useCase.getAttachedCameraIds()) {
                        List<UseCase> useCasesOnCameraId = detachingUseCaseMap.get(cameraId);
                        if (useCasesOnCameraId == null) {
                            useCasesOnCameraId = new ArrayList<>();
                            detachingUseCaseMap.put(cameraId, useCasesOnCameraId);
                        }
                        useCasesOnCameraId.add(useCase);
                    }
                }
            }
        }

        for (String cameraId : detachingUseCaseMap.keySet()) {
            detach(cameraId, detachingUseCaseMap.get(cameraId));
        }

        for (UseCase useCase : useCases) {
            useCase.clear();
        }
    }

    /**
     * Unbinds all use cases from the lifecycle and removes them from CameraX.
     *
     * <p>This will initiate a close of every currently open camera.
     *
     * @throws IllegalStateException If not called on main thread.
     */
    public static void unbindAll() {
        Threads.checkMainThread();
        CameraX cameraX = checkInitialized();

        Collection<UseCaseGroupLifecycleController> useCaseGroups =
                cameraX.mUseCaseGroupRepository.getUseCaseGroups();

        List<UseCase> useCases = new ArrayList<>();
        for (UseCaseGroupLifecycleController useCaseGroupLifecycleController : useCaseGroups) {
            UseCaseGroup useCaseGroup = useCaseGroupLifecycleController.getUseCaseGroup();
            useCases.addAll(useCaseGroup.getUseCases());
        }

        unbind(useCases.toArray(new UseCase[0]));
    }

    /**
     * Checks if the device supports specified lens facing.
     *
     * @param lensFacing the lens facing
     * @return true if the device has at least one camera with the specified lens facing,
     * otherwise false.
     * @throws CameraInfoUnavailableException if unable to access cameras, perhaps due to
     *                                        insufficient permissions
     */
    public static boolean hasCameraWithLensFacing(LensFacing lensFacing)
            throws CameraInfoUnavailableException {
        checkInitialized();

        return getCameraFactory().cameraIdForLensFacing(lensFacing) != null;
    }

    /**
     * Returns the camera id for a camera with the specified lens facing.
     *
     * <p>This only gives the first (primary) camera found with the specified facing.
     *
     * @param lensFacing the lens facing of the camera
     * @return the cameraId if camera exists or {@code null} if no camera with specified facing
     * exists
     * @throws CameraInfoUnavailableException if unable to access cameras, perhaps due to
     *                                        insufficient permissions.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public static String getCameraWithLensFacing(LensFacing lensFacing)
            throws CameraInfoUnavailableException {
        checkInitialized();
        return getCameraFactory().cameraIdForLensFacing(lensFacing);
    }

    /**
     * Returns the camera id for a camera defined by the CameraDeviceConfig.
     *
     * <p>This will first selects the cameras with lens facing specified in the config. Then
     * filter those with camera id filters if there's any.
     *
     * @param config the config of the camera device
     * @return the cameraId if camera exists or {@code null} if no camera found with the config
     * @throws CameraInfoUnavailableException if unable to access cameras, perhaps due to
     *                                        insufficient permissions.
     * @throws IllegalArgumentException       if there's no lens facing set in the config.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public static String getCameraWithCameraDeviceConfig(CameraDeviceConfig config)
            throws CameraInfoUnavailableException {
        checkInitialized();

        Set<String> availableCameraIds = getCameraFactory().getAvailableCameraIds();
        LensFacing lensFacing = config.getLensFacing(null);
        if (lensFacing != null) {
            // Filters camera ids with lens facing.
            availableCameraIds =
                    LensFacingCameraIdFilter.createLensFacingCameraIdFilter(lensFacing)
                            .filter(availableCameraIds);
        } else {
            throw new IllegalArgumentException("Lens facing isn't set in the config.");
        }

        CameraIdFilter cameraIdFilter = config.getCameraIdFilter(null);
        if (cameraIdFilter != null) {
            // Filters camera ids with other filters.
            availableCameraIds = cameraIdFilter.filter(availableCameraIds);
        }

        if (!availableCameraIds.isEmpty()) {
            return availableCameraIds.iterator().next();
        } else {
            return null;
        }
    }

    /**
     * Gets the default lens facing or {@code null} if there is no available camera.
     *
     * @return The default lens facing or {@code null}.
     * @throws CameraInfoUnavailableException if unable to access cameras, perhaps due to
     *                                        insufficient permissions.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public static LensFacing getDefaultLensFacing() throws CameraInfoUnavailableException {
        CameraX cameraX = checkInitialized();

        LensFacing lensFacingCandidate = null;
        List<LensFacing> lensFacingList = Arrays.asList(LensFacing.BACK, LensFacing.FRONT);
        for (LensFacing lensFacing : lensFacingList) {
            String cameraId = cameraX.getCameraFactory().cameraIdForLensFacing(lensFacing);
            if (cameraId != null) {
                lensFacingCandidate = lensFacing;
                break;
            }
        }
        return lensFacingCandidate;
    }

    /**
     * Returns the camera info for the camera with the given camera id.
     *
     * @param cameraId the internal id of the camera
     * @return the camera info if it can be retrieved for the given id.
     * @throws CameraInfoUnavailableException if unable to access cameras, perhaps due to
     *                                        insufficient permissions.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public static CameraInfoInternal getCameraInfo(String cameraId)
            throws CameraInfoUnavailableException {
        CameraX cameraX = checkInitialized();

        return cameraX.getCameraRepository().getCamera(cameraId).getCameraInfoInternal();
    }

    /**
     * Returns the camera info for the camera with the given lens facing.
     *
     * @param lensFacing the lens facing of the camera
     * @return the camera info if it can be retrieved for the given lens facing.
     * @throws CameraInfoUnavailableException if unable to access cameras, perhaps due to
     *                                        insufficient permissions.
     */
    @NonNull
    public static CameraInfo getCameraInfo(@NonNull LensFacing lensFacing)
            throws CameraInfoUnavailableException {
        CameraX cameraX = checkInitialized();
        try {
            String cameraId = getCameraWithLensFacing(lensFacing);
            return cameraX.getCameraRepository().getCamera(cameraId).getCameraInfoInternal();
        } catch (IllegalArgumentException e) {
            throw new CameraInfoUnavailableException("Unable to retrieve info for camera with "
                    + "lens facing: " + lensFacing, e);
        }
    }

    /**
     * Returns the camera control for the camera with the given lens facing.
     *
     * @param lensFacing the lens facing of the camera
     * @return the {@link CameraControl}.
     * @throws CameraInfoUnavailableException if unable to access cameras, perhaps due to
     *                                        insufficient permissions.
     */
    public static CameraControl getCameraControl(LensFacing lensFacing)
            throws CameraInfoUnavailableException {
        CameraX cameraX = checkInitialized();

        String cameraId = getCameraWithLensFacing(lensFacing);
        return (CameraControl) cameraX.getCameraRepository().getCamera(
                cameraId).getCameraControlInternal();
    }

    /**
     * Returns the {@link CameraDeviceSurfaceManager} which can be used to query for valid surface
     * configurations.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static CameraDeviceSurfaceManager getSurfaceManager() {
        CameraX cameraX = checkInitialized();

        return cameraX.getCameraDeviceSurfaceManager();
    }

    /**
     * Returns the default configuration for the given use case configuration type.
     *
     * <p>The options contained in this configuration serve as fallbacks if they are not included in
     * the user-provided configuration used to create a use case.
     *
     * @param configType the configuration type
     * @param lensFacing The {@link LensFacing} that the default configuration will target to.
     * @return the default configuration for the given configuration type
     * @throws IllegalStateException if Camerax has not yet been initialized.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public static <C extends UseCaseConfig<?>> C getDefaultUseCaseConfig(
            Class<C> configType, LensFacing lensFacing) {
        CameraX cameraX = checkInitialized();

        return cameraX.getDefaultConfigFactory().getConfig(configType, lensFacing);
    }

    /**
     * Sets an {@link ErrorListener} which will get called any time a CameraX specific error is
     * encountered.
     *
     * @param errorListener the listener which will get all the error messages. If this is set to
     *                      {@code null} then the default error listener will be set.
     * @param handler       the handler for the thread to run the error handling on. If this is
     *                      set to
     *                      {@code null} then it will default to run on the main thread.
     */
    public static void setErrorListener(ErrorListener errorListener, Handler handler) {
        CameraX cameraX = checkInitialized();

        cameraX.mErrorHandler.setErrorListener(errorListener, handler);
    }

    /**
     * Posts an error which can be handled by the {@link ErrorListener}.
     *
     * @param errorCode the type of error that occurred
     * @param message   the associated message with more details of the error
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static void postError(ErrorCode errorCode, String message) {
        CameraX cameraX = checkInitialized();

        cameraX.mErrorHandler.postError(errorCode, message);
    }

    /**
     * Initializes CameraX with the given context and application configuration.
     *
     * <p>The context enables CameraX to obtain access to necessary services, including the camera
     * service. For example, the context can be provided by the application.
     *
     * @param context   to attach
     * @param appConfig configuration options for this application session.
     * @return A {@link ListenableFuture} representing the initialization task.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public static ListenableFuture<Void> init(@NonNull Context context,
            @NonNull AppConfig appConfig) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(appConfig);

        synchronized (sInitDeinitLock) {
            // TODO(b/141155037): Remove auto deinit() call when Camera2Initializer is removed.
            // Currently this is for test purpose. Because CameraX.init() will be invoked by
            // Camera2Initializer on android test. If invoke CameraX.init() in test's setUp()
            // method, then it causes continuously invoke init() and leads to precondition check
            // fail.
            if (sTargetInitialized) {
                deinit();
            }

            Preconditions.checkState(!sTargetInitialized, "Must call CameraX.deinit() first.");
            sTargetInitialized = true;

            Executor executor = appConfig.getCameraExecutor(null);
            // Set a default camera executor if not set.
            if (executor == null) {
                executor = new CameraExecutor();
            }

            CameraX cameraX = new CameraX(executor);
            sInstance = cameraX;

            sInitFuture = CallbackToFutureAdapter.getFuture(completer -> {
                synchronized (sInitDeinitLock) {
                    // The deinitFuture should always be successful, otherwise it will not
                    // propagate to transformAsync() due to the behavior of FutureChain.
                    ListenableFuture<Void> future = FutureChain.from(sDeinitFuture)
                            .transformAsync(input -> cameraX.initInternal(context, appConfig),
                                    CameraXExecutors.directExecutor());

                    Futures.addCallback(future, new FutureCallback<Void>() {
                        @Override
                        public void onSuccess(@Nullable Void result) {
                            completer.set(null);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            Log.w(TAG, "CameraX init() failed", t);
                            // Call deinit() automatically, if initialization fails.
                            synchronized (sInitDeinitLock) {
                                // Make sure it is the same instance to prevent reinitialization
                                // during initialization.
                                if (sInstance == cameraX) {
                                    deinit();
                                }
                            }
                            completer.setException(t);
                        }
                    }, CameraXExecutors.directExecutor());
                    return "CameraX-init";
                }
            });

            return sInitFuture;
        }
    }

    /**
     * Deinitializes CameraX so that it can be initialized again.
     *
     * @return A {@link ListenableFuture} representing the deinitialization task.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public static ListenableFuture<Void> deinit() {
        synchronized (sInitDeinitLock) {
            if (!sTargetInitialized) {
                // If it is already or will be deinitialized, return the future directly.
                return sDeinitFuture;
            }
            sTargetInitialized = false;

            CameraX cameraX = sInstance;
            sInstance = null;

            // Do not use FutureChain to chain the initFuture, because FutureChain.transformAsync()
            // will not propagate if the input initFuture is failed. We want to always
            // deinitialize the CameraX instance to ensure that resources are freed.
            sDeinitFuture = CallbackToFutureAdapter.getFuture(
                    completer -> {
                        synchronized (sInitDeinitLock) {
                            // Wait init complete
                            sInitFuture.addListener(() -> {
                                // Wait deinitInternal complete
                                Futures.propagate(cameraX.deinitInternal(), completer);
                            }, CameraXExecutors.directExecutor());
                            return "CameraX deinit";
                        }
                    });
            return sDeinitFuture;
        }
    }

    /**
     * Returns the context used for CameraX.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public static Context getContext() {
        CameraX cameraX = checkInitialized();
        return cameraX.mContext;
    }

    /**
     * Returns true if CameraX is initialized.
     *
     * <p>Any previous call to {@link #init(Context, AppConfig)} would have initialized
     * CameraX.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static boolean isInitialized() {
        synchronized (sInitDeinitLock) {
            return sInstance != null && sInstance.isInitializedInternal();
        }
    }

    /**
     * Returns currently active {@link UseCase}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public static Collection<UseCase> getActiveUseCases() {
        CameraX cameraX = checkInitialized();

        Collection<UseCase> activeUseCases = null;

        Collection<UseCaseGroupLifecycleController> controllers =
                cameraX.mUseCaseGroupRepository.getUseCaseGroups();

        for (UseCaseGroupLifecycleController controller : controllers) {
            if (controller.getUseCaseGroup().isActive()) {
                activeUseCases = controller.getUseCaseGroup().getUseCases();
                break;
            }
        }

        return activeUseCases;
    }

    /**
     * Returns the {@link CameraFactory} instance.
     *
     * @throws IllegalStateException if the {@link CameraFactory} has not been set, due to being
     *                               uninitialized.
     * @hide
     */
    @NonNull
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static CameraFactory getCameraFactory() {
        CameraX cameraX = checkInitialized();

        if (cameraX.mCameraFactory == null) {
            throw new IllegalStateException("CameraX not initialized yet.");
        }

        return cameraX.mCameraFactory;
    }

    /**
     * Wait for the initialize or deinitialize task finished and then check if it is initialized.
     *
     * @return CameraX instance
     * @throws IllegalStateException if it is not initialized
     */
    @NonNull
    private static CameraX checkInitialized() {
        CameraX cameraX = waitInitialized();
        Preconditions.checkState(cameraX != null && cameraX.isInitializedInternal(),
                "Must call CameraX.init() first");
        return cameraX;
    }

    /**
     * Wait for the initialize or deinitialize task finished.
     *
     * @throws IllegalStateException if the initialization is fail or timeout
     */
    @Nullable
    private static CameraX waitInitialized() {
        ListenableFuture<Void> future;
        CameraX cameraX;
        synchronized (sInitDeinitLock) {
            if (!sTargetInitialized) {
                return null;
            }
            future = sInitFuture;
            cameraX = sInstance;
        }
        if (!future.isDone()) {
            try {
                future.get(WAIT_INITIALIZED_TIMEOUT, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throw new IllegalStateException(e);
            } catch (TimeoutException e) {
                throw new IllegalStateException(e);
            } catch (InterruptedException e) {
                // Throw exception when get interrupted because the initialization could not be
                // finished yet.
                throw new IllegalStateException(e);
            }
        }
        return cameraX;
    }

    /**
     * Registers the callbacks for the {@link BaseCamera} to the {@link UseCase}.
     *
     * @param cameraId the id for the {@link BaseCamera}
     * @param useCase  the use case to register the callback for
     */
    private static void attach(String cameraId, UseCase useCase) {
        CameraX cameraX = checkInitialized();

        BaseCamera camera = cameraX.getCameraRepository().getCamera(cameraId);
        if (camera == null) {
            throw new IllegalArgumentException("Invalid camera: " + cameraId);
        }

        useCase.addStateChangeListener(camera);
        useCase.attachCameraControl(cameraId, camera.getCameraControlInternal());
    }

    /**
     * Removes the callbacks registered by the {@link BaseCamera} to the {@link UseCase}.
     *
     * @param cameraId the id for the {@link BaseCamera}
     * @param useCases the list of use case to remove the callback from.
     */
    private static void detach(String cameraId, List<UseCase> useCases) {
        CameraX cameraX = checkInitialized();

        BaseCamera camera = cameraX.getCameraRepository().getCamera(cameraId);
        if (camera == null) {
            throw new IllegalArgumentException("Invalid camera: " + cameraId);
        }

        for (UseCase useCase : useCases) {
            useCase.removeStateChangeListener(camera);
            useCase.detachCameraControl(cameraId);
        }
        camera.removeOnlineUseCase(useCases);
    }

    private static void calculateSuggestedResolutions(LifecycleOwner lifecycleOwner,
            UseCase... useCases) {
        CameraX cameraX = checkInitialized();

        // There will only one lifecycleOwner active. Therefore, only collect use cases belong to
        // same lifecycleOwner and calculate the suggested resolutions.
        UseCaseGroupLifecycleController useCaseGroupLifecycleController =
                cameraX.getOrCreateUseCaseGroup(lifecycleOwner);
        UseCaseGroup useCaseGroupToBind = useCaseGroupLifecycleController.getUseCaseGroup();
        Map<String, List<UseCase>> originalCameraIdUseCaseMap = new HashMap<>();
        Map<String, List<UseCase>> newCameraIdUseCaseMap = new HashMap<>();

        // Collect original use cases for different camera devices
        for (UseCase useCase : useCaseGroupToBind.getUseCases()) {
            for (String cameraId : useCase.getAttachedCameraIds()) {
                List<UseCase> useCaseList = originalCameraIdUseCaseMap.get(cameraId);
                if (useCaseList == null) {
                    useCaseList = new ArrayList<>();
                    originalCameraIdUseCaseMap.put(cameraId, useCaseList);
                }
                useCaseList.add(useCase);
            }
        }

        // Collect new use cases for different camera devices
        for (UseCase useCase : useCases) {
            String cameraId = null;
            try {
                cameraId = getCameraWithCameraDeviceConfig(
                        (CameraDeviceConfig) useCase.getUseCaseConfig());
            } catch (CameraInfoUnavailableException e) {
                throw new IllegalArgumentException(
                        "Unable to get camera id for the camera device config.", e);
            }

            List<UseCase> useCaseList = newCameraIdUseCaseMap.get(cameraId);
            if (useCaseList == null) {
                useCaseList = new ArrayList<>();
                newCameraIdUseCaseMap.put(cameraId, useCaseList);
            }
            useCaseList.add(useCase);
        }

        // Get suggested resolutions and update the use case session configuration
        for (String cameraId : newCameraIdUseCaseMap.keySet()) {
            Map<UseCase, Size> suggestResolutionsMap =
                    getSurfaceManager()
                            .getSuggestedResolutions(
                                    cameraId,
                                    originalCameraIdUseCaseMap.get(cameraId),
                                    newCameraIdUseCaseMap.get(cameraId));

            for (UseCase useCase : newCameraIdUseCaseMap.get(cameraId)) {
                Size resolution = suggestResolutionsMap.get(useCase);
                Map<String, Size> suggestedCameraSurfaceResolutionMap = new HashMap<>();
                suggestedCameraSurfaceResolutionMap.put(cameraId, resolution);
                useCase.updateSuggestedResolution(suggestedCameraSurfaceResolutionMap);
            }
        }
    }

    /**
     * Returns the {@link CameraDeviceSurfaceManager} instance.
     *
     * @throws IllegalStateException if the {@link CameraDeviceSurfaceManager} has not been set, due
     *                               to being uninitialized.
     */
    private CameraDeviceSurfaceManager getCameraDeviceSurfaceManager() {
        if (mSurfaceManager == null) {
            throw new IllegalStateException("CameraX not initialized yet.");
        }

        return mSurfaceManager;
    }

    private UseCaseConfigFactory getDefaultConfigFactory() {
        if (mDefaultConfigFactory == null) {
            throw new IllegalStateException("CameraX not initialized yet.");
        }

        return mDefaultConfigFactory;
    }

    private ListenableFuture<Void> initInternal(Context context, AppConfig appConfig) {
        synchronized (mInitDeinitLock) {
            Preconditions.checkState(mInitState == InternalInitState.UNINITIALIZED,
                    "CameraX.initInternal() should only be called once per instance");
            mInitState = InternalInitState.INITIALIZING;

            return CallbackToFutureAdapter.getFuture(
                    completer -> {
                        mCameraExecutor.execute(() -> {
                            Exception e = null;
                            try {
                                mContext = context.getApplicationContext();
                                mCameraFactory = appConfig.getCameraFactory(null);
                                if (mCameraFactory == null) {
                                    e = new IllegalArgumentException(
                                            "Invalid app configuration provided. Missing "
                                                    + "CameraFactory.");
                                    return;
                                }

                                mSurfaceManager = appConfig.getDeviceSurfaceManager(null);
                                if (mSurfaceManager == null) {
                                    e = new IllegalArgumentException(
                                            "Invalid app configuration provided. Missing "
                                                    + "CameraDeviceSurfaceManager.");
                                    return;
                                }
                                mSurfaceManager.init();

                                mDefaultConfigFactory = appConfig.getUseCaseConfigRepository(null);
                                if (mDefaultConfigFactory == null) {
                                    e = new IllegalArgumentException(
                                            "Invalid app configuration provided. Missing "
                                                    + "UseCaseConfigFactory.");
                                    return;
                                }

                                if (mCameraExecutor instanceof CameraExecutor) {
                                    CameraExecutor executor = (CameraExecutor) mCameraExecutor;
                                    executor.init(mCameraFactory);
                                }

                                mCameraRepository.init(mCameraFactory);
                            } finally {
                                synchronized (mInitDeinitLock) {
                                    mInitState = InternalInitState.INITIALIZED;
                                }
                                if (e != null) {
                                    completer.setException(e);
                                } else {
                                    completer.set(null);
                                }
                            }
                        });
                        return "CameraX initInternal";
                    });
        }
    }

    @NonNull
    private ListenableFuture<Void> deinitInternal() {
        synchronized (mInitDeinitLock) {
            switch (mInitState) {
                case UNINITIALIZED:
                    mInitState = InternalInitState.DEINITIALIZED;
                    return Futures.immediateFuture(null);

                case INITIALIZING:
                    throw new IllegalStateException(
                            "CameraX could not be deinitialized when it is initializing.");

                case INITIALIZED:
                    mInitState = InternalInitState.DEINITIALIZED;

                    mDeinitInternalFuture = CallbackToFutureAdapter.getFuture(
                            completer -> {
                                ListenableFuture<Void> future = mCameraRepository.deinit();

                                // Deinit camera executor at last to avoid RejectExecutionException.
                                future.addListener(() -> {
                                    if (mCameraExecutor instanceof CameraExecutor) {
                                        CameraExecutor executor = (CameraExecutor) mCameraExecutor;
                                        executor.deinit();
                                    }
                                    completer.set(null);
                                }, mCameraExecutor);
                                return "CameraX deinitInternal";
                            }
                    );
                    // Fall through
                case DEINITIALIZED:
                    break;
            }
            // Already deinitialized. Return the deinit future.
            return mDeinitInternalFuture;
        }
    }

    private boolean isInitializedInternal() {
        synchronized (mInitDeinitLock) {
            return mInitState == InternalInitState.INITIALIZED;
        }
    }

    private UseCaseGroupLifecycleController getOrCreateUseCaseGroup(LifecycleOwner lifecycleOwner) {
        return mUseCaseGroupRepository.getOrCreateUseCaseGroup(
                lifecycleOwner, new UseCaseGroupRepository.UseCaseGroupSetup() {
                    @Override
                    public void setup(UseCaseGroup useCaseGroup) {
                        useCaseGroup.setListener(mCameraRepository);
                    }
                });
    }

    private CameraRepository getCameraRepository() {
        return mCameraRepository;
    }

    /** The types of error states that can occur. */
    public enum ErrorCode {
        /** The camera has moved into an unexpected state from which it can not recover from. */
        CAMERA_STATE_INCONSISTENT,
        /** A {@link UseCase} has encountered an error from which it can not recover from. */
        USE_CASE_ERROR
    }

    /** The direction the camera faces relative to device screen. */
    public enum LensFacing {
        /** A camera on the device facing the same direction as the device's screen. */
        FRONT,
        /** A camera on the device facing the opposite direction as the device's screen. */
        BACK
    }

    /** Listener called whenever an error condition occurs within CameraX. */
    public interface ErrorListener {

        /**
         * Called whenever an error occurs within CameraX.
         *
         * @param error   the type of error that occurred
         * @param message detailed message of the error condition
         */
        void onError(@NonNull ErrorCode error, @NonNull String message);
    }

    /** Internal initialization state. */
    private enum InternalInitState {
        /** The CameraX instance has not yet been initialized. */
        UNINITIALIZED,

        /** The CameraX instance is initializing. */
        INITIALIZING,

        /** The CameraX instance has been initialized. */
        INITIALIZED,

        /**
         * The CameraX instance has been deinitialized.
         *
         * <p>Once the CameraX instance has been deinitialized, it can't be used or re-initialized.
         */
        DEINITIALIZED
    }
}
