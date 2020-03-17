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

package androidx.startup;

import static android.content.pm.PackageManager.GET_META_DATA;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.tracing.Trace;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An {@link AppInitializer} can be used to initialize all discovered [ComponentInitializer]s.
 * <br/>
 * The discovery mechanism is via `<meta-data>` entries in the merged `AndroidManifest.xml`.
 */
@SuppressWarnings("WeakerAccess")
public final class AppInitializer {

    // Tracing
    private static final String SECTION_NAME = "Startup";

    /**
     * The {@link AppInitializer} instance.
     */
    private static AppInitializer sInstance;

    /**
     * Guards app initialization.
     */
    private static final Object sLock = new Object();

    @NonNull
    final Map<Class<?>, Object> mInitialized;

    @NonNull
    final Context mContext;

    /**
     * Creates an instance of {@link AppInitializer}
     *
     * @param context The application context
     */
    AppInitializer(@NonNull Context context) {
        mContext = context.getApplicationContext();
        mInitialized = new HashMap<>();
    }

    /**
     * @param context The Application {@link Context}
     * @return The instance of {@link AppInitializer} after initialization.
     */
    @NonNull
    @SuppressWarnings("UnusedReturnValue")
    public static AppInitializer getInstance(@NonNull Context context) {
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new AppInitializer(context);
            }
            return sInstance;
        }
    }

    /**
     * Initializes a {@link ComponentInitializer} class type.
     *
     * @param component The {@link Class} of {@link ComponentInitializer} to initialize.
     * @param <T>       The instance type being initialized
     * @return The initialized instance
     */
    @NonNull
    @SuppressWarnings("unused")
    public <T> T initializeComponent(@NonNull Class<? extends ComponentInitializer<T>> component) {
        return doInitialize(component, new HashSet<>());
    }

    @NonNull
    @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
    <T> T doInitialize(
            @NonNull Class<? extends ComponentInitializer<?>> component,
            @NonNull Set<Class<?>> initializing) {
        synchronized (sLock) {
            boolean isTracingEnabled = Trace.isEnabled();
            try {
                if (isTracingEnabled) {
                    // Use the simpleName here because section names would get too big otherwise.
                    Trace.beginSection(component.getSimpleName());
                }
                if (initializing.contains(component)) {
                    String message = String.format(
                            "Cannot initialize %s. Cycle detected.", component.getName()
                    );
                    throw new IllegalStateException(message);
                }
                Object result;
                if (!mInitialized.containsKey(component)) {
                    initializing.add(component);
                    try {
                        Object instance = component.getDeclaredConstructor().newInstance();
                        ComponentInitializer<?> initializer = (ComponentInitializer<?>) instance;
                        List<Class<? extends ComponentInitializer<?>>> dependencies =
                                initializer.dependencies();

                        if (!dependencies.isEmpty()) {
                            for (Class<? extends ComponentInitializer<?>> clazz : dependencies) {
                                if (!mInitialized.containsKey(clazz)) {
                                    doInitialize(clazz, initializing);
                                }
                            }
                        }
                        if (StartupLogger.DEBUG) {
                            StartupLogger.i(String.format("Initializing %s", component.getName()));
                        }
                        result = initializer.create(mContext);
                        if (StartupLogger.DEBUG) {
                            StartupLogger.i(String.format("Initialized %s", component.getName()));
                        }
                        initializing.remove(component);
                        mInitialized.put(component, result);
                    } catch (Throwable throwable) {
                        throw new StartupException(throwable);
                    }
                } else {
                    result = mInitialized.get(component);
                }
                return (T) result;
            } finally {
                Trace.endSection();
            }
        }
    }

    @SuppressWarnings("unchecked")
    void discoverAndInitialize() {
        try {
            Trace.beginSection(SECTION_NAME);
            ApplicationInfo applicationInfo =
                    mContext.getPackageManager()
                            .getApplicationInfo(mContext.getPackageName(), GET_META_DATA);

            Bundle metadata = applicationInfo.metaData;
            String startup = mContext.getString(R.string.androidx_startup);
            if (metadata != null) {
                Set<Class<?>> initializing = new HashSet<>();
                Set<String> keys = metadata.keySet();
                for (String key : keys) {
                    String value = metadata.getString(key, null);
                    if (startup.equals(value)) {
                        Class<?> clazz = Class.forName(key);
                        if (ComponentInitializer.class.isAssignableFrom(clazz)) {
                            Class<? extends ComponentInitializer<?>> component =
                                    (Class<? extends ComponentInitializer<?>>) clazz;
                            if (StartupLogger.DEBUG) {
                                StartupLogger.i(String.format("Discovered %s", key));
                            }
                            doInitialize(component, initializing);
                        }
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException | ClassNotFoundException exception) {
            throw new StartupException(exception);
        } finally {
            Trace.endSection();
        }
    }
}
