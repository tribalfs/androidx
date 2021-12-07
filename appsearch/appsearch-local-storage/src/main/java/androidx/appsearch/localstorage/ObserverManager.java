/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.appsearch.localstorage;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.appsearch.observer.AppSearchObserverCallback;
import androidx.appsearch.observer.DocumentChangeInfo;
import androidx.appsearch.observer.ObserverSpec;
import androidx.collection.ArrayMap;
import androidx.collection.ArraySet;
import androidx.core.util.ObjectsCompat;
import androidx.core.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Manages {@link AppSearchObserverCallback} instances and queues notifications to them for later
 * dispatch.
 *
 * <p>This class is not thread-safe.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ObserverManager {
    private static final String TAG = "AppSearchObserverManage";

    /** The combination of fields by which {@link DocumentChangeInfo} is grouped. */
    private static final class DocumentChangeGroupKey {
        final String mPackageName;
        final String mDatabaseName;
        final String mNamespace;
        final String mSchemaName;

        DocumentChangeGroupKey(
                @NonNull String packageName,
                @NonNull String databaseName,
                @NonNull String namespace,
                @NonNull String schemaName) {
            mPackageName = Preconditions.checkNotNull(packageName);
            mDatabaseName = Preconditions.checkNotNull(databaseName);
            mNamespace = Preconditions.checkNotNull(namespace);
            mSchemaName = Preconditions.checkNotNull(schemaName);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (!(o instanceof DocumentChangeGroupKey)) return false;
            DocumentChangeGroupKey that = (DocumentChangeGroupKey) o;
            return mPackageName.equals(that.mPackageName)
                    && mDatabaseName.equals(that.mDatabaseName)
                    && mNamespace.equals(that.mNamespace)
                    && mSchemaName.equals(that.mSchemaName);
        }

        @Override
        public int hashCode() {
            return ObjectsCompat.hash(mPackageName, mDatabaseName, mNamespace, mSchemaName);
        }
    }

    private static final class ObserverInfo {
        final ObserverSpec mObserverSpec;
        final Executor mExecutor;
        final AppSearchObserverCallback mObserver;
        volatile Set<DocumentChangeGroupKey> mDocumentChanges = new ArraySet<>();

        ObserverInfo(
                @NonNull ObserverSpec observerSpec,
                @NonNull Executor executor,
                @NonNull AppSearchObserverCallback observer) {
            mObserverSpec = Preconditions.checkNotNull(observerSpec);
            mExecutor = Preconditions.checkNotNull(executor);
            mObserver = Preconditions.checkNotNull(observer);
        }
    }

    /** Maps observed package to observer infos watching something in that package. */
    private final Map<String, List<ObserverInfo>> mObservers = new ArrayMap<>();
    private volatile boolean mHasNotifications = false;

    /**
     * Adds an {@link AppSearchObserverCallback} to monitor changes within the
     * databases owned by {@code observedPackage} if they match the given
     * {@link androidx.appsearch.observer.ObserverSpec}.
     *
     * <p>If the data owned by {@code observedPackage} is not visible to you, the registration call
     * will succeed but no notifications will be dispatched. Notifications could start flowing later
     * if {@code observedPackage} changes its schema visibility settings.
     *
     * <p>If no package matching {@code observedPackage} exists on the system, the registration call
     * will succeed but no notifications will be dispatched. Notifications could start flowing later
     * if {@code observedPackage} is installed and starts indexing data.
     */
    public void registerObserver(
            @NonNull String observedPackage,
            @NonNull ObserverSpec spec,
            @NonNull Executor executor,
            @NonNull AppSearchObserverCallback observer) {
        List<ObserverInfo> infos = mObservers.get(observedPackage);
        if (infos == null) {
            infos = new ArrayList<>();
            mObservers.put(observedPackage, infos);
        }
        infos.add(new ObserverInfo(spec, executor, observer));
    }

    /**
     * Should be called when a change occurs to a document.
     *
     * <p>The notification will be queued in memory for later dispatch. You must call
     * {@link #dispatchPendingNotifications} to dispatch all such pending notifications.
     */
    public void onDocumentChange(
            @NonNull String packageName,
            @NonNull String databaseName,
            @NonNull String namespace,
            @NonNull String schemaType) {
        List<ObserverInfo> allObserverInfosForPackage = mObservers.get(packageName);
        if (allObserverInfosForPackage == null || allObserverInfosForPackage.isEmpty()) {
            return; // No observers for this type
        }
        // Enqueue changes for later dispatch once the call returns
        DocumentChangeGroupKey key = null;
        for (int i = 0; i < allObserverInfosForPackage.size(); i++) {
            ObserverInfo observerInfo = allObserverInfosForPackage.get(i);
            if (matchesSpec(schemaType, observerInfo.mObserverSpec)) {
                if (key == null) {
                    key = new DocumentChangeGroupKey(
                            packageName, databaseName, namespace, schemaType);
                }
                observerInfo.mDocumentChanges.add(key);
            }
        }
        mHasNotifications = true;
    }

    /** Returns whether there are any observers registered to watch the given package. */
    public boolean isPackageObserved(@NonNull String packageName) {
        return mObservers.containsKey(packageName);
    }

    /**
     * Returns whether there are any observers registered to watch the given package and
     * unprefixed schema type.
     */
    public boolean isSchemaTypeObserved(@NonNull String packageName, @NonNull String schemaType) {
        List<ObserverInfo> allObserverInfosForPackage = mObservers.get(packageName);
        if (allObserverInfosForPackage == null) {
            return false;
        }
        for (int i = 0; i < allObserverInfosForPackage.size(); i++) {
            ObserverInfo observerInfo = allObserverInfosForPackage.get(i);
            if (matchesSpec(schemaType, observerInfo.mObserverSpec)) {
                return true;
            }
        }
        return false;
    }

    /** This method is thread safe. */
    public boolean hasNotifications() {
        return mHasNotifications;
    }

    /** Dispatches notifications on their corresponding executors. */
    public void dispatchPendingNotifications() {
        if (mObservers.isEmpty() || !mHasNotifications) {
            return;
        }
        for (List<ObserverInfo> observerInfos : mObservers.values()) {
            for (int i = 0; i < observerInfos.size(); i++) {
                dispatchPendingNotifications(observerInfos.get(i));
            }
        }
        mHasNotifications = false;
    }

    /** Dispatches pending notifications for the given observerInfo and clears the pending list. */
    private void dispatchPendingNotifications(@NonNull ObserverInfo observerInfo) {
        // Get and clear the pending changes
        Set<DocumentChangeGroupKey> documentChanges = observerInfo.mDocumentChanges;
        if (documentChanges.isEmpty()) {
            return;
        }
        observerInfo.mDocumentChanges = new ArraySet<>();

        // Dispatch the pending changes
        observerInfo.mExecutor.execute(() -> {
            for (DocumentChangeGroupKey entry : documentChanges) {
                // TODO(b/193494000): Buffer document URIs as the values of mDocumentChanges
                // and include them in the final ChangeInfo
                DocumentChangeInfo documentChangeInfo = new DocumentChangeInfo(
                        entry.mPackageName,
                        entry.mDatabaseName,
                        entry.mNamespace,
                        entry.mSchemaName);

                try {
                    // TODO(b/193494000): Add code to dispatch SchemaChangeInfo too.
                    observerInfo.mObserver.onDocumentChanged(documentChangeInfo);
                } catch (Throwable t) {
                    Log.w(TAG, "AppSearchObserverCallback threw exception during dispatch", t);
                }
            }
        });
    }

    /**
     * Checks whether a change in the given {@code databaseName}, {@code namespace} and
     * {@code schemaType} passes all the filters defined in the given {@code observerSpec}.
     *
     * <p>Note that this method does not check packageName; you must only use it to check
     * observerSpecs which you know are observing the same package as the change.
     */
    private boolean matchesSpec(@NonNull String schemaType, @NonNull ObserverSpec observerSpec) {
        Set<String> schemaFilters = observerSpec.getFilterSchemas();
        if (!schemaFilters.isEmpty() && !schemaFilters.contains(schemaType)) {
            return false;
        }
        // TODO(b/193494000): We also need to check VisibilityStore to see if the observer is
        //  allowed to access this type before granting access.
        return true;
    }
}
