/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.core.location;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.provider.Settings.Secure.LOCATION_MODE;
import static android.provider.Settings.Secure.LOCATION_MODE_OFF;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import android.location.GnssStatus;
import android.location.GpsStatus;
import android.location.LocationManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.text.TextUtils;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.collection.SimpleArrayMap;
import androidx.core.os.HandlerExecutor;
import androidx.core.util.Preconditions;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Helper for accessing features in {@link LocationManager}.
 */
public final class LocationManagerCompat {

    private static final long PRE_N_LOOPER_TIMEOUT_S = 4;

    private static Field sContextField;

    /**
     * Returns the current enabled/disabled state of location.
     * <p>
     * Calling this method on API levels prior to 20 <i>may</i> require the
     * {@link android.Manifest.permission#ACCESS_FINE_LOCATION ACCESS_FINE_LOCATION} or
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_COARSE_LOCATION}
     * permission.
     *
     * @return {@code true} if location is enabled or {@code false} if location is disabled
     */
    @SuppressWarnings("deprecation")
    public static boolean isLocationEnabled(@NonNull LocationManager locationManager) {
        if (VERSION.SDK_INT >= 28) {
            return Api28Impl.isLocationEnabled(locationManager);
        }

        if (VERSION.SDK_INT <= 19) {
            // KitKat and below have pointless location permission requirements when using
            // isProviderEnabled(). Instead, we attempt to reflect a context so that we can query
            // the underlying setting. If this fails, we fallback to isProviderEnabled() which may
            // require the caller to hold location permissions.
            try {
                if (sContextField == null) {
                    //noinspection JavaReflectionMemberAccess
                    sContextField = LocationManager.class.getDeclaredField("mContext");
                }
                sContextField.setAccessible(true);
                Context context = (Context) sContextField.get(locationManager);

                if (VERSION.SDK_INT == 19) {
                    return Secure.getInt(context.getContentResolver(), LOCATION_MODE,
                            LOCATION_MODE_OFF) != LOCATION_MODE_OFF;
                } else {
                    return !TextUtils.isEmpty(
                            Settings.Secure.getString(context.getContentResolver(),
                                    Settings.Secure.LOCATION_PROVIDERS_ALLOWED));
                }
            } catch (ClassCastException | SecurityException | NoSuchFieldException
                    | IllegalAccessException e) {
                // oh well, fallback to isProviderEnabled()
            }
        }

        return locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            || locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    @GuardedBy("sGnssStatusListeners")
    private static final SimpleArrayMap<Object, Object> sGnssStatusListeners =
            new SimpleArrayMap<>();

    /**
     * Registers a platform agnostic {@link GnssStatusCompat.Callback}. See
     * {@link LocationManager#addGpsStatusListener(GpsStatus.Listener)} and
     * {@link LocationManager#registerGnssStatusCallback(GnssStatus.Callback, Handler)}.
     *
     * @see #registerGnssStatusCallback(LocationManager, Executor, GnssStatusCompat.Callback)
     */
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public static boolean registerGnssStatusCallback(@NonNull LocationManager locationManager,
            @NonNull GnssStatusCompat.Callback callback, @NonNull Handler handler) {
        if (VERSION.SDK_INT >= VERSION_CODES.R) {
            return registerGnssStatusCallback(locationManager, new HandlerExecutor(handler),
                callback);
        } else {
            return registerGnssStatusCallback(locationManager, new InlineHandlerExecutor(handler),
                    callback);
        }
    }

    /**
     * Registers a platform agnostic {@link GnssStatusCompat.Callback}. See
     * {@link LocationManager#addGpsStatusListener(GpsStatus.Listener)} and
     * {@link LocationManager#registerGnssStatusCallback(Executor, GnssStatus.Callback)}.
     *
     * <p>Internally, this API will always utilize GnssStatus APIs and instances on Android N and
     * above, and will always utilize GpsStatus APIs and instances below Android N. Callbacks will
     * always occur on the given executor.
     *
     * <p>If invoked on Android M or below, this will result in GpsStatus registration being run on
     * either the current Looper or main Looper. If the thread this function is invoked on is
     * different from that Looper, the caller must ensure that the Looper thread cannot be blocked
     * by the thread this function is invoked on. The easiest way to avoid this is to ensure this
     * function is invoked on a Looper thread.
     *
     * @throws IllegalStateException on Android M or below, if the current Looper or main Looper
     *                               is blocked by the thread this function is invoked on
     */
    @RequiresPermission(ACCESS_FINE_LOCATION)
    public static boolean registerGnssStatusCallback(@NonNull LocationManager locationManager,
            @NonNull Executor executor, @NonNull GnssStatusCompat.Callback callback) {
        if (VERSION.SDK_INT >= VERSION_CODES.R) {
            return registerGnssStatusCallback(locationManager, null, executor, callback);
        } else {
            Looper looper = Looper.myLooper();
            if (looper == null) {
                looper = Looper.getMainLooper();
            }
            return registerGnssStatusCallback(locationManager, new Handler(looper), executor,
                    callback);
        }
    }

    @RequiresPermission(ACCESS_FINE_LOCATION)
    private static boolean registerGnssStatusCallback(final LocationManager locationManager,
            Handler baseHandler, Executor executor, GnssStatusCompat.Callback callback) {
        if (VERSION.SDK_INT >= VERSION_CODES.R) {
            synchronized (sGnssStatusListeners) {
                GnssStatusTransport transport =
                        (GnssStatusTransport) sGnssStatusListeners.get(callback);
                if (transport == null) {
                    transport = new GnssStatusTransport(callback);
                }
                if (locationManager.registerGnssStatusCallback(executor, transport)) {
                    sGnssStatusListeners.put(callback, transport);
                    return true;
                } else {
                    return false;
                }
            }
        } else if (VERSION.SDK_INT >= VERSION_CODES.N) {
            Preconditions.checkArgument(baseHandler != null);
            synchronized (sGnssStatusListeners) {
                PreRGnssStatusTransport transport =
                        (PreRGnssStatusTransport) sGnssStatusListeners.get(callback);
                if (transport == null) {
                    transport = new PreRGnssStatusTransport(callback);
                } else {
                    transport.unregister();
                }
                transport.register(executor);

                if (locationManager.registerGnssStatusCallback(transport, baseHandler)) {
                    sGnssStatusListeners.put(callback, transport);
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            Preconditions.checkArgument(baseHandler != null);
            synchronized (sGnssStatusListeners) {
                GpsStatusTransport transport =
                        (GpsStatusTransport) sGnssStatusListeners.get(callback);
                if (transport == null) {
                    transport = new GpsStatusTransport(locationManager, callback);
                } else {
                    transport.unregister();
                }
                transport.register(executor);

                final GpsStatusTransport myTransport = transport;
                FutureTask<Boolean> task = new FutureTask<>(new Callable<Boolean>() {
                    @RequiresPermission(ACCESS_FINE_LOCATION)
                    @Override
                    public Boolean call() {
                        return locationManager.addGpsStatusListener(myTransport);
                    }
                });

                if (Looper.myLooper() == baseHandler.getLooper()) {
                    task.run();
                } else if (!baseHandler.post(task)) {
                    throw new IllegalStateException(baseHandler + " is shutting down");
                }

                boolean interrupted = false;
                try {
                    long remainingNanos = SECONDS.toNanos(PRE_N_LOOPER_TIMEOUT_S);
                    long end = System.nanoTime() + remainingNanos;
                    while (true) {
                        try {
                            if (task.get(remainingNanos, NANOSECONDS)) {
                                sGnssStatusListeners.put(callback, myTransport);
                                return true;
                            } else {
                                return false;
                            }
                        } catch (InterruptedException e) {
                            // this is conceptually not an interruptible operation
                            interrupted = true;
                            remainingNanos = end - System.nanoTime();
                        }
                    }
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof RuntimeException) {
                        throw (RuntimeException) e.getCause();
                    } else if (e.getCause() instanceof Error) {
                        throw (Error) e.getCause();
                    } else {
                        throw new IllegalStateException(e);
                    }
                } catch (TimeoutException e) {
                    throw new IllegalStateException(baseHandler + " appears to be blocked, please"
                            + " run registerGnssStatusCallback() directly on a Looper thread or "
                            + "ensure the main Looper is not blocked by this thread", e);
                } finally {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    /**
     * Unregisters a platform agnostic {@link GnssStatusCompat.Callback}. See
     * {@link LocationManager#removeGpsStatusListener(GpsStatus.Listener)}
     * and {@link LocationManager#unregisterGnssStatusCallback(GnssStatus.Callback)}.
     */
    public static void unregisterGnssStatusCallback(@NonNull LocationManager locationManager,
            @NonNull GnssStatusCompat.Callback callback) {
        if (VERSION.SDK_INT >= VERSION_CODES.R) {
            synchronized (sGnssStatusListeners) {
                GnssStatusTransport transport =
                        (GnssStatusTransport) sGnssStatusListeners.remove(callback);
                if (transport != null) {
                    locationManager.unregisterGnssStatusCallback(transport);
                }
            }
        } else if (VERSION.SDK_INT >= VERSION_CODES.N) {
            synchronized (sGnssStatusListeners) {
                PreRGnssStatusTransport transport =
                        (PreRGnssStatusTransport) sGnssStatusListeners.remove(callback);
                if (transport != null) {
                    transport.unregister();
                    locationManager.unregisterGnssStatusCallback(transport);
                }
            }
        } else {
            synchronized (sGnssStatusListeners) {
                GpsStatusTransport transport =
                        (GpsStatusTransport) sGnssStatusListeners.remove(callback);
                if (transport != null) {
                    transport.unregister();
                    locationManager.removeGpsStatusListener(transport);
                }
            }
        }
    }

    private LocationManagerCompat() {}

    @RequiresApi(VERSION_CODES.R)
    private static class GnssStatusTransport extends GnssStatus.Callback {

        final GnssStatusCompat.Callback mCallback;

        GnssStatusTransport(GnssStatusCompat.Callback callback) {
            Preconditions.checkArgument(callback != null, "invalid null callback");
            mCallback = callback;
        }

        @Override
        public void onStarted() {
            mCallback.onStarted();
        }

        @Override
        public void onStopped() {
            mCallback.onStopped();
        }

        @Override
        public void onFirstFix(int ttffMillis) {
            mCallback.onFirstFix(ttffMillis);
        }

        @Override
        public void onSatelliteStatusChanged(GnssStatus status) {
            mCallback.onSatelliteStatusChanged(GnssStatusCompat.wrap(status));
        }
    }

    @RequiresApi(VERSION_CODES.N)
    private static class PreRGnssStatusTransport extends GnssStatus.Callback {

        final GnssStatusCompat.Callback mCallback;

        @Nullable volatile Executor mExecutor;

        PreRGnssStatusTransport(GnssStatusCompat.Callback callback) {
            Preconditions.checkArgument(callback != null, "invalid null callback");
            mCallback = callback;
        }

        public void register(Executor executor) {
            Preconditions.checkArgument(executor != null, "invalid null executor");
            Preconditions.checkState(mExecutor == null);
            mExecutor = executor;
        }

        public void unregister() {
            mExecutor = null;
        }

        @Override
        public void onStarted() {
            final Executor executor = mExecutor;
            if (executor == null) {
                return;
            }

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (mExecutor != executor) {
                        return;
                    }
                    mCallback.onStarted();
                }
            });
        }

        @Override
        public void onStopped() {
            final Executor executor = mExecutor;
            if (executor == null) {
                return;
            }

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (mExecutor != executor) {
                        return;
                    }
                    mCallback.onStopped();
                }
            });
        }

        @Override
        public void onFirstFix(final int ttffMillis) {
            final Executor executor = mExecutor;
            if (executor == null) {
                return;
            }

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (mExecutor != executor) {
                        return;
                    }
                    mCallback.onFirstFix(ttffMillis);
                }
            });
        }

        @Override
        public void onSatelliteStatusChanged(final GnssStatus status) {
            final Executor executor = mExecutor;
            if (executor == null) {
                return;
            }

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (mExecutor != executor) {
                        return;
                    }
                    mCallback.onSatelliteStatusChanged(GnssStatusCompat.wrap(status));
                }
            });
        }
    }

    private static class GpsStatusTransport implements GpsStatus.Listener {

        private final LocationManager mLocationManager;
        final GnssStatusCompat.Callback mCallback;

        @Nullable volatile Executor mExecutor;

        GpsStatusTransport(LocationManager locationManager,
                GnssStatusCompat.Callback callback) {
            Preconditions.checkArgument(callback != null, "invalid null callback");
            mLocationManager = locationManager;
            mCallback = callback;
        }

        public void register(Executor executor) {
            Preconditions.checkState(mExecutor == null);
            mExecutor = executor;
        }

        public void unregister() {
            mExecutor = null;
        }

        @RequiresPermission(ACCESS_FINE_LOCATION)
        @Override
        public void onGpsStatusChanged(int event) {
            final Executor executor = mExecutor;
            if (executor == null) {
                return;
            }

            GpsStatus gpsStatus;

            switch (event) {
                case GpsStatus.GPS_EVENT_STARTED:
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (mExecutor != executor) {
                                return;
                            }
                            mCallback.onStarted();
                        }
                    });
                    break;
                case GpsStatus.GPS_EVENT_STOPPED:
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (mExecutor != executor) {
                                return;
                            }
                            mCallback.onStopped();
                        }
                    });
                    break;
                case GpsStatus.GPS_EVENT_FIRST_FIX:
                    gpsStatus = mLocationManager.getGpsStatus(null);
                    if (gpsStatus != null) {
                        final int ttff = gpsStatus.getTimeToFirstFix();
                        executor.execute(new Runnable() {
                            @Override
                            public void run() {
                                if (mExecutor != executor) {
                                    return;
                                }
                                mCallback.onFirstFix(ttff);
                            }
                        });
                    }
                    break;
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    gpsStatus = mLocationManager.getGpsStatus(null);
                    if (gpsStatus != null) {
                        final GnssStatusCompat gnssStatus = GnssStatusCompat.wrap(gpsStatus);
                        executor.execute(new Runnable() {
                            @Override
                            public void run() {
                                if (mExecutor != executor) {
                                    return;
                                }
                                mCallback.onSatelliteStatusChanged(gnssStatus);
                            }
                        });
                    }
                    break;
            }
        }
    }

    // using this class allows listeners to be run more efficiently in the common case for pre-R
    // SDKs where the AOSP callback is already on the same Looper the listener wants
    private static class InlineHandlerExecutor implements Executor {
        private final Handler mHandler;

        InlineHandlerExecutor(@NonNull Handler handler) {
            mHandler = Preconditions.checkNotNull(handler);
        }

        @Override
        public void execute(@NonNull Runnable command) {
            if (Looper.myLooper() == mHandler.getLooper()) {
                command.run();
            } else if (!mHandler.post(Preconditions.checkNotNull(command))) {
                throw new RejectedExecutionException(mHandler + " is shutting down");
            }
        }
    }

    @RequiresApi(28)
    private static class Api28Impl {
        private Api28Impl() {
            // Non-instantiable.
        }

        public static boolean isLocationEnabled(LocationManager locationManager) {
            return locationManager.isLocationEnabled();
        }
    }
}
