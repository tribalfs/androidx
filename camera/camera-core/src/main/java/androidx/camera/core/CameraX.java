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

import android.app.Application;
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
import androidx.camera.core.impl.CameraDeviceSurfaceManager;
import androidx.camera.core.impl.CameraFactory;
import androidx.camera.core.impl.CameraIdFilter;
import androidx.camera.core.impl.CameraInfoInternal;
import androidx.camera.core.impl.CameraInternal;
import androidx.camera.core.impl.CameraRepository;
import androidx.camera.core.impl.UseCaseConfig;
import androidx.camera.core.impl.UseCaseConfigFactory;
import androidx.camera.core.impl.UseCaseGroup;
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
 * {@code #bindToLifecycle(LifecycleOwner, CameraSelector, UseCase...)}  Once bound, the
 * lifecycle of the {@link LifecycleOwner} determines when the camera is started and stopped, and
 * when camera data is available to the use case.
 *
 * <p>It is often sufficient to just bind the use cases once when the activity is created, and
 * let the lifecycle handle the rest, so application code generally does not need to call
 * {@code #unbind(UseCase...)} nor call {@code #bindToLifecycle} more than once.
 *
 * <p>A lifecycle transition from {@link Lifecycle.State#CREATED} to {@link Lifecycle.State#STARTED}
 * state (via {@link Lifecycle.Event#ON_START}) initializes the camera asynchronously on a
 * CameraX managed thread. After initialization, the camera is opened and a camera capture
 * session is created.   If a {@link Preview} or {@link ImageAnalysis} is bound, those use cases
 * will begin to receive camera data after initialization completes. {@link ImageCapture} can
 * receive data via specific calls (such as {@link ImageCapture#takePicture}) after initialization
 * completes. Calling #bindToLifecycle with no Use Cases does nothing.
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
 * bound use cases are unbound and use case resources are freed.  Calls to #bindToLifecycle
 * when the lifecycle is in the {@link Lifecycle.State#DESTROYED} state will fail.
 * A call to #bindToLifecycle will need to be made with another lifecycle to rebind the
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
 *   // Select a camera
 *   cameraSelector = ...;
 *
 *   // UseCase binding event
 *   CameraX.bindToLifecycle(lifecycleOwner, cameraSelector, useCase);
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
 *
 * @hide
 */
@MainThread
@RestrictTo(Scope.LIBRARY_GROUP)
public final class CameraX {
    private static final String TAG = "CameraX";
    private static final long WAIT_INITIALIZED_TIMEOUT = 3L;

    static final Object sInitializeLock = new Object();

    @GuardedBy("sInitializeLock")
    @Nullable
    static CameraX sInstance = null;

    @GuardedBy("sInitializeLock")
    private static boolean sTargetInitialized = false;

    @GuardedBy("sInitializeLock")
    @NonNull
    private static ListenableFuture<Void> sInitializeFuture = Futures.immediateFailedFuture(
            new IllegalStateException("CameraX is not initialized."));

    @GuardedBy("sInitializeLock")
    @NonNull
    private static ListenableFuture<Void> sShutdownFuture = Futures.immediateFuture(null);

    final CameraRepository mCameraRepository = new CameraRepository();
    private final Object mInitializeLock = new Object();
    private final UseCaseGroupRepository mUseCaseGroupRepository = new UseCaseGroupRepository();
    private final Executor mCameraExecutor;
    private CameraFactory mCameraFactory;
    private CameraDeviceSurfaceManager mSurfaceManager;
    private UseCaseConfigFactory mDefaultConfigFactory;
    private Context mContext;
    @GuardedBy("mInitializeLock")
    private InternalInitState mInitState = InternalInitState.UNINITIALIZED;
    @GuardedBy("mInitializeLock")
    private ListenableFuture<Void> mShutdownInternalFuture = Futures.immediateFuture(null);

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
     * android.hardware.camera2.CameraCaptureSession.StateCallback, Handler)}.
     *
     * <p>Currently up to 3 use cases may be bound to a {@link Lifecycle} at any time. Exceeding
     * capability of target camera device will throw an IllegalArgumentException.
     *
     * <p>A UseCase should only be bound to a single lifecycle and camera selector a time.
     * Attempting to bind a use case to a lifecycle when it is already bound to another lifecycle
     * is an error, and the use case binding will not change. Attempting to bind the same use case
     * to multiple camera selectors is also an error and will not change the binding.
     *
     * <p>If different use cases are bound to different camera selectors that resolve to distinct
     * cameras, but the same lifecycle, only one of the cameras will operate at a time. The
     * non-operating camera will not become active until it is the only camera with use cases bound.
     *
     * <p>Only {@link UseCase} bound to latest active {@link Lifecycle} can keep alive.
     * {@link UseCase} bound to other {@link Lifecycle} will be stopped.
     *
     * @param lifecycleOwner The lifecycleOwner which controls the lifecycle transitions of the use
     *                       cases.
     * @param cameraSelector The camera selector which determines the camera to use for set of
     *                       use cases.
     * @param useCases       The use cases to bind to a lifecycle.
     * @return The {@link Camera} instance which is determined by the camera selector.
     * @throws IllegalStateException    If the use case has already been bound to another lifecycle
     *                                  or method is not called on main thread.
     * @throws IllegalArgumentException If the provided camera selector is unable to resolve a
     *                                  camera to be used for the given use cases.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @SuppressWarnings("lambdaLast")
    @NonNull
    public static Camera bindToLifecycle(@NonNull LifecycleOwner lifecycleOwner,
            @NonNull CameraSelector cameraSelector, @NonNull UseCase... useCases) {
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

        // TODO(b/142840814): Camera should be selected here and bound to the use case.
        //  Should not need to use CameraDeviceConfig anymore.
        CameraSelector.Builder selectorBuilder =
                CameraSelector.Builder.fromSelector(cameraSelector);
        // Copy existing filters from use cases into the new selector
        for (UseCase useCase : useCases) {
            CameraSelector selector = useCase.getUseCaseConfig().getCameraSelector(null);
            if (selector != null) {
                for (CameraIdFilter filter : selector.getCameraFilterSet()) {
                    selectorBuilder.appendFilter(filter);
                }
            }
        }

        String newCameraId = CameraX.getCameraWithCameraSelector(selectorBuilder.build());

        // Try to get the camera before bind to the use case, and throw the IllegalArgumentException
        // if the camera not found.
        CameraInternal camera = cameraX.getCameraRepository().getCamera(newCameraId);

        for (UseCase useCase : useCases) {
            // Sets bound camera to use case.
            useCase.onBind(camera);
        }

        calculateSuggestedResolutions(lifecycleOwner, newCameraId, useCases);

        for (UseCase useCase : useCases) {
            useCaseGroupToBind.addUseCase(useCase);
            for (String cameraId : useCase.getAttachedCameraIds()) {
                attach(cameraId, useCase);
            }
        }

        useCaseGroupLifecycleController.notifyState();

        return camera;
    }

    /**
     * Returns true if the {@link UseCase} is bound to a lifecycle. Otherwise returns false.
     *
     * <p>After binding a use case with {@link #bindToLifecycle}, use cases remain bound until the
     * lifecycle reaches a {@link Lifecycle.State#DESTROYED} state or if is unbound by calls to
     * {@link #unbind(UseCase...)} or {@link #unbindAll()}.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static boolean isBound(@NonNull UseCase useCase) {
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
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static void unbind(@NonNull UseCase... useCases) {
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
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
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
     * Checks if the device supports at least one camera that meets the requirements from a
     * {@link CameraSelector}.
     *
     * @param cameraSelector the {@link CameraSelector} that filters available cameras.
     * @return true if the device has at least one available camera, otherwise false.
     * @throws CameraInfoUnavailableException if unable to access cameras, perhaps due to
     *                                        insufficient permissions.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static boolean hasCamera(@NonNull CameraSelector cameraSelector)
            throws CameraInfoUnavailableException {
        checkInitialized();

        try {
            cameraSelector.select(getCameraFactory().getAvailableCameraIds());
        } catch (IllegalArgumentException e) {
            return false;
        }

        return true;
    }

    /**
     * Returns the camera id for a camera with the specified lens facing.
     *
     * <p>This only gives the first (primary) camera found with the specified facing.
     *
     * @param lensFacing the lens facing of the camera
     * @return the camera id if camera exists or {@code null} if no camera with specified facing
     * exists
     * @throws CameraInfoUnavailableException if unable to access cameras, perhaps due to
     *                                        insufficient permissions.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public static String getCameraWithLensFacing(@CameraSelector.LensFacing int lensFacing)
            throws CameraInfoUnavailableException {
        checkInitialized();
        return getCameraFactory().cameraIdForLensFacing(lensFacing);
    }

    /**
     * Returns the camera id for a camera defined by the given {@link CameraSelector}.
     *
     * @param cameraSelector the camera selector
     * @return the camera id if camera exists or {@code null} if no camera can be resolved with
     * the camera selector
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public static String getCameraWithCameraSelector(@NonNull CameraSelector cameraSelector) {
        checkInitialized();

        Set<String> availableCameraIds;
        String resultCameraId = null;
        try {
            availableCameraIds = getCameraFactory().getAvailableCameraIds();
            resultCameraId = cameraSelector.select(availableCameraIds);
        } catch (CameraInfoUnavailableException e) {
            return null;
        }

        return resultCameraId;
    }

    /**
     * Gets the default lens facing, or throws a {@link IllegalStateException} if there is no
     * available camera.
     *
     * @return The default lens facing.
     * @throws CameraInfoUnavailableException if unable to access cameras, perhaps due to
     *                                        insufficient permissions.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @CameraSelector.LensFacing
    public static int getDefaultLensFacing() throws CameraInfoUnavailableException {
        checkInitialized();

        Integer lensFacingCandidate = null;
        List<Integer> lensFacingList = Arrays.asList(CameraSelector.LENS_FACING_BACK,
                CameraSelector.LENS_FACING_FRONT);
        for (Integer lensFacing : lensFacingList) {
            String cameraId = getCameraFactory().cameraIdForLensFacing(lensFacing);
            if (cameraId != null) {
                lensFacingCandidate = lensFacing;
                break;
            }
        }
        if (lensFacingCandidate == null) {
            throw new IllegalStateException("Unable to get default lens facing.");
        }
        return lensFacingCandidate;
    }

    /**
     * Returns the camera info for the camera with the given camera id.
     *
     * @param cameraId the internal id of the camera
     * @return the camera info if it can be retrieved for the given id.
     * @throws IllegalArgumentException if unable to access cameras, perhaps due to
     *                                  insufficient permissions.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public static CameraInfoInternal getCameraInfo(String cameraId) {
        CameraX cameraX = checkInitialized();

        return cameraX.getCameraRepository().getCamera(cameraId).getCameraInfoInternal();
    }

    /**
     * Returns the {@link CameraDeviceSurfaceManager} which can be used to query for valid surface
     * configurations.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
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
     * @param lensFacing The {@link CameraSelector.LensFacing} that the default configuration
     *                   will target to.
     * @return the default configuration for the given configuration type
     * @throws IllegalStateException if Camerax has not yet been initialized.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Nullable
    public static <C extends UseCaseConfig<?>> C getDefaultUseCaseConfig(Class<C> configType,
            @Nullable Integer lensFacing) {
        CameraX cameraX = checkInitialized();

        return cameraX.getDefaultConfigFactory().getConfig(configType, lensFacing);
    }

    /**
     * Initializes CameraX with the given context and application configuration.
     *
     * <p>The context enables CameraX to obtain access to necessary services, including the camera
     * service. For example, the context can be provided by the application.
     *
     * @param context       to attach
     * @param cameraXConfig configuration options for this application session.
     * @return A {@link ListenableFuture} representing the initialization task.
     */
    @NonNull
    public static ListenableFuture<Void> initialize(@NonNull Context context,
            @NonNull CameraXConfig cameraXConfig) {
        synchronized (sInitializeLock) {
            return initializeLocked(context, cameraXConfig);
        }
    }

    @GuardedBy("sInitializeLock")
    @NonNull
    private static ListenableFuture<Void> initializeLocked(@NonNull Context context,
            @NonNull CameraXConfig cameraXConfig) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(cameraXConfig);
        Preconditions.checkState(!sTargetInitialized, "Must call CameraX.shutdown() first.");
        sTargetInitialized = true;

        Executor executor = cameraXConfig.getCameraExecutor(null);
        // Set a default camera executor if not set.
        if (executor == null) {
            executor = new CameraExecutor();
        }

        CameraX cameraX = new CameraX(executor);
        sInstance = cameraX;

        sInitializeFuture = CallbackToFutureAdapter.getFuture(completer -> {
            synchronized (sInitializeLock) {
                // The sShutdownFuture should always be successful, otherwise it will not
                // propagate to transformAsync() due to the behavior of FutureChain.
                ListenableFuture<Void> future = FutureChain.from(sShutdownFuture)
                        .transformAsync(input -> cameraX.initInternal(context, cameraXConfig),
                                CameraXExecutors.directExecutor());

                Futures.addCallback(future, new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(@Nullable Void result) {
                        completer.set(null);
                    }

                    @SuppressWarnings("FutureReturnValueIgnored")
                    @Override
                    public void onFailure(Throwable t) {
                        Log.w(TAG, "CameraX initialize() failed", t);
                        // Call shutdown() automatically, if initialization fails.
                        synchronized (sInitializeLock) {
                            // Make sure it is the same instance to prevent reinitialization
                            // during initialization.
                            if (sInstance == cameraX) {
                                shutdown();
                            }
                        }
                        completer.setException(t);
                    }
                }, CameraXExecutors.directExecutor());
                return "CameraX-initialize";
            }
        });

        return sInitializeFuture;
    }

    /**
     * Shutdown CameraX so that it can be initialized again.
     *
     * @return A {@link ListenableFuture} representing the shutdown task.
     */
    @NonNull
    public static ListenableFuture<Void> shutdown() {
        synchronized (sInitializeLock) {
            return shutdownLocked();
        }
    }

    @GuardedBy("sInitializeLock")
    @NonNull
    private static ListenableFuture<Void> shutdownLocked() {
        if (!sTargetInitialized) {
            // If it is already or will be shutdown, return the future directly.
            return sShutdownFuture;
        }
        sTargetInitialized = false;

        CameraX cameraX = sInstance;
        sInstance = null;

        // Do not use FutureChain to chain the initFuture, because FutureChain.transformAsync()
        // will not propagate if the input initFuture is failed. We want to always
        // shutdown the CameraX instance to ensure that resources are freed.
        sShutdownFuture = CallbackToFutureAdapter.getFuture(
                completer -> {
                    synchronized (sInitializeLock) {
                        // Wait initialize complete
                        sInitializeFuture.addListener(() -> {
                            // Wait shutdownInternal complete
                            Futures.propagate(cameraX.shutdownInternal(), completer);
                        }, CameraXExecutors.directExecutor());
                        return "CameraX shutdown";
                    }
                });
        return sShutdownFuture;
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
     * <p>Any previous call to {@link #initialize(Context, CameraXConfig)} would have initialized
     * CameraX.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static boolean isInitialized() {
        synchronized (sInitializeLock) {
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
     * Wait for the initialize or shutdown task finished and then check if it is initialized.
     *
     * @return CameraX instance
     * @throws IllegalStateException if it is not initialized
     */
    @NonNull
    private static CameraX checkInitialized() {
        CameraX cameraX = waitInitialized();
        Preconditions.checkState(cameraX.isInitializedInternal(),
                "Must call CameraX.initialize() first");
        return cameraX;
    }

    /**
     * Returns a future which contains a CameraX instance after initialization is complete.
     *
     * @hide
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public static ListenableFuture<CameraX> getOrCreateInstance(@NonNull Context context) {
        Preconditions.checkNotNull(context, "Context must not be null.");
        synchronized (sInitializeLock) {
            ListenableFuture<CameraX> instanceFuture = getInstanceLocked();
            if (instanceFuture.isDone()) {
                try {
                    instanceFuture.get();
                } catch (InterruptedException e) {
                    // Should not be possible since future is complete.
                    throw new RuntimeException("Unexpected thread interrupt. Should not be "
                            + "possible since future is already complete.", e);
                } catch (ExecutionException e) {
                    // Either initialization failed or initialize() has not been called, ensure we
                    // can try to reinitialize.
                    shutdownLocked();
                    instanceFuture = null;
                }
            }

            // Attempt initialization through Application
            if (instanceFuture == null) {
                Application app = (Application) context.getApplicationContext();
                if (app instanceof CameraXConfig.Provider) {
                    initializeLocked(app, ((CameraXConfig.Provider) app).getCameraXConfig());
                    instanceFuture = getInstanceLocked();
                } else {
                    throw new IllegalStateException("CameraX is not initialized properly. Either "
                            + "CameraX.initialize() needs to have been called or the CameraXConfig"
                            + ".Provider interface must be implemented by your Application class.");
                }
            }

            return instanceFuture;
        }
    }

    @NonNull
    private static ListenableFuture<CameraX> getInstance() {
        synchronized (sInitializeLock) {
            return getInstanceLocked();
        }
    }

    @GuardedBy("sInitializeLock")
    @NonNull
    private static ListenableFuture<CameraX> getInstanceLocked() {
        if (!sTargetInitialized) {
            return Futures.immediateFailedFuture(new IllegalStateException("Must "
                    + "call CameraX.initialize() first"));
        }

        CameraX cameraX = sInstance;
        return Futures.transform(sInitializeFuture, nullVoid -> cameraX,
                CameraXExecutors.directExecutor());
    }

    /**
     * Wait for the initialize or shutdown task finished.
     *
     * @throws IllegalStateException if the initialization is fail or timeout
     */
    @NonNull
    private static CameraX waitInitialized() {
        ListenableFuture<CameraX> future = getInstance();
        try {
            return future.get(WAIT_INITIALIZED_TIMEOUT, TimeUnit.SECONDS);
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

    /**
     * Registers the callbacks for the {@link CameraInternal} to the {@link UseCase}.
     *
     * @param cameraId the id for the {@link CameraInternal}
     * @param useCase  the use case to register the callback for
     */
    private static void attach(String cameraId, UseCase useCase) {
        CameraX cameraX = checkInitialized();

        CameraInternal cameraInternal = cameraX.getCameraRepository().getCamera(cameraId);

        useCase.addStateChangeCallback(cameraInternal);
        useCase.attachCameraControl(cameraId, cameraInternal.getCameraControlInternal());
    }

    /**
     * Removes the callbacks registered by the {@link CameraInternal} to the {@link UseCase}.
     *
     * @param cameraId the id for the {@link CameraInternal}
     * @param useCases the list of use case to remove the callback from.
     */
    private static void detach(String cameraId, List<UseCase> useCases) {
        CameraX cameraX = checkInitialized();

        CameraInternal cameraInternal = cameraX.getCameraRepository().getCamera(cameraId);

        for (UseCase useCase : useCases) {
            useCase.removeStateChangeCallback(cameraInternal);
            useCase.detachCameraControl(cameraId);
        }
        cameraInternal.removeOnlineUseCase(useCases);
    }

    private static void calculateSuggestedResolutions(@NonNull LifecycleOwner lifecycleOwner,
            @NonNull String newCameraId, @NonNull UseCase... useCases) {
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
            List<UseCase> useCaseList = newCameraIdUseCaseMap.get(newCameraId);
            if (useCaseList == null) {
                useCaseList = new ArrayList<>();
                newCameraIdUseCaseMap.put(newCameraId, useCaseList);
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

    private ListenableFuture<Void> initInternal(Context context, CameraXConfig cameraXConfig) {
        synchronized (mInitializeLock) {
            Preconditions.checkState(mInitState == InternalInitState.UNINITIALIZED,
                    "CameraX.initInternal() should only be called once per instance");
            mInitState = InternalInitState.INITIALIZING;

            return CallbackToFutureAdapter.getFuture(
                    completer -> {
                        mCameraExecutor.execute(() -> {
                            Exception e = null;
                            try {
                                mContext = context.getApplicationContext();
                                CameraFactory.Provider cameraFactoryProvider =
                                        cameraXConfig.getCameraFactoryProvider(null);
                                if (cameraFactoryProvider == null) {
                                    e = new IllegalArgumentException(
                                            "Invalid app configuration provided. Missing "
                                                    + "CameraFactory.");
                                    return;
                                }
                                mCameraFactory = cameraFactoryProvider.newInstance(context);

                                CameraDeviceSurfaceManager.Provider surfaceManagerProvider =
                                        cameraXConfig.getDeviceSurfaceManagerProvider(null);
                                if (surfaceManagerProvider == null) {
                                    e = new IllegalArgumentException(
                                            "Invalid app configuration provided. Missing "
                                                    + "CameraDeviceSurfaceManager.");
                                    return;
                                }
                                mSurfaceManager = surfaceManagerProvider.newInstance(context);

                                UseCaseConfigFactory.Provider configFactoryProvider =
                                        cameraXConfig.getUseCaseConfigFactoryProvider(
                                        null);
                                if (configFactoryProvider == null) {
                                    e = new IllegalArgumentException(
                                            "Invalid app configuration provided. Missing "
                                                    + "UseCaseConfigFactory.");
                                    return;
                                }
                                mDefaultConfigFactory = configFactoryProvider.newInstance(context);

                                if (mCameraExecutor instanceof CameraExecutor) {
                                    CameraExecutor executor = (CameraExecutor) mCameraExecutor;
                                    executor.init(mCameraFactory);
                                }

                                mCameraRepository.init(mCameraFactory);
                            } finally {
                                synchronized (mInitializeLock) {
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
    private ListenableFuture<Void> shutdownInternal() {
        synchronized (mInitializeLock) {
            switch (mInitState) {
                case UNINITIALIZED:
                    mInitState = InternalInitState.SHUTDOWN;
                    return Futures.immediateFuture(null);

                case INITIALIZING:
                    throw new IllegalStateException(
                            "CameraX could not be shutdown when it is initializing.");

                case INITIALIZED:
                    mInitState = InternalInitState.SHUTDOWN;

                    mShutdownInternalFuture = CallbackToFutureAdapter.getFuture(
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
                                return "CameraX shutdownInternal";
                            }
                    );
                    // Fall through
                case SHUTDOWN:
                    break;
            }
            // Already shutdown. Return the shutdown future.
            return mShutdownInternalFuture;
        }
    }

    private boolean isInitializedInternal() {
        synchronized (mInitializeLock) {
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

    /** Internal initialization state. */
    private enum InternalInitState {
        /** The CameraX instance has not yet been initialized. */
        UNINITIALIZED,

        /** The CameraX instance is initializing. */
        INITIALIZING,

        /** The CameraX instance has been initialized. */
        INITIALIZED,

        /**
         * The CameraX instance has been shutdown.
         *
         * <p>Once the CameraX instance has been shutdown, it can't be used or re-initialized.
         */
        SHUTDOWN
    }
}
