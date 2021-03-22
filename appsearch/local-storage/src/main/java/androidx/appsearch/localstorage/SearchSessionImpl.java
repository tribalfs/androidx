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
// @exportToFramework:skipFile()
package androidx.appsearch.localstorage;

import static androidx.appsearch.app.AppSearchResult.throwableToFailedResult;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appsearch.app.AppSearchBatchResult;
import androidx.appsearch.app.AppSearchSession;
import androidx.appsearch.app.GenericDocument;
import androidx.appsearch.app.GetByUriRequest;
import androidx.appsearch.app.GetSchemaResponse;
import androidx.appsearch.app.Migrator;
import androidx.appsearch.app.PackageIdentifier;
import androidx.appsearch.app.PutDocumentsRequest;
import androidx.appsearch.app.RemoveByUriRequest;
import androidx.appsearch.app.ReportUsageRequest;
import androidx.appsearch.app.SearchResults;
import androidx.appsearch.app.SearchSpec;
import androidx.appsearch.app.SetSchemaRequest;
import androidx.appsearch.app.SetSchemaResponse;
import androidx.appsearch.app.StorageInfo;
import androidx.appsearch.exceptions.AppSearchException;
import androidx.appsearch.localstorage.util.FutureUtil;
import androidx.appsearch.util.SchemaMigrationUtil;
import androidx.collection.ArrayMap;
import androidx.collection.ArraySet;
import androidx.core.util.Preconditions;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * An implementation of {@link AppSearchSession} which stores data locally
 * in the app's storage space using a bundled version of the search native library.
 *
 * <p>Queries are executed multi-threaded, but a single thread is used for mutate requests (put,
 * delete, etc..).
 */
class SearchSessionImpl implements AppSearchSession {
    private static final String TAG = "AppSearchSessionImpl";
    private final AppSearchImpl mAppSearchImpl;
    private final Executor mExecutor;
    private final String mPackageName;
    private final String mDatabaseName;
    private volatile boolean mIsMutated = false;
    private volatile boolean mIsClosed = false;
    @Nullable private final AppSearchLogger mLogger;

    SearchSessionImpl(
            @NonNull AppSearchImpl appSearchImpl,
            @NonNull Executor executor,
            @NonNull String packageName,
            @NonNull String databaseName,
            @Nullable AppSearchLogger logger) {
        mAppSearchImpl = Preconditions.checkNotNull(appSearchImpl);
        mExecutor = Preconditions.checkNotNull(executor);
        mPackageName = packageName;
        mDatabaseName = Preconditions.checkNotNull(databaseName);
        mLogger = logger;
    }

    @Override
    @NonNull
    // TODO(b/151178558) return the batch result for migration documents.
    public ListenableFuture<SetSchemaResponse> setSchema(
            @NonNull SetSchemaRequest request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!mIsClosed, "AppSearchSession has already been closed");

        ListenableFuture<SetSchemaResponse> future = execute(() -> {
            // Convert the inner set into a List since Binder can't handle Set.
            Map<String, Set<PackageIdentifier>> schemasPackageAccessible =
                    request.getSchemasVisibleToPackagesInternal();
            Map<String, List<PackageIdentifier>> copySchemasPackageAccessible = new ArrayMap<>();
            for (Map.Entry<String, Set<PackageIdentifier>> entry :
                    schemasPackageAccessible.entrySet()) {
                copySchemasPackageAccessible.put(entry.getKey(),
                        new ArrayList<>(entry.getValue()));
            }

            Map<String, Migrator> migrators = request.getMigrators();
            // No need to trigger migration if user never set migrator.
            if (migrators.size() == 0) {
                return setSchemaNoMigrations(request, copySchemasPackageAccessible);
            }

            // Migration process
            // 1. Validate and retrieve all active migrators.
            GetSchemaResponse getSchemaResponse =
                    mAppSearchImpl.getSchema(mPackageName, mDatabaseName);
            int currentVersion = getSchemaResponse.getVersion();
            int finalVersion = request.getVersion();
            Map<String, Migrator> activeMigrators = SchemaMigrationUtil.getActiveMigrators(
                    getSchemaResponse.getSchemas(), migrators, currentVersion, finalVersion);
            // No need to trigger migration if no migrator is active.
            if (activeMigrators.size() == 0) {
                return setSchemaNoMigrations(request, copySchemasPackageAccessible);
            }

            // 2. SetSchema with forceOverride=false, to retrieve the list of incompatible/deleted
            // types.
            SetSchemaResponse setSchemaResponse = mAppSearchImpl.setSchema(
                    mPackageName,
                    mDatabaseName,
                    new ArrayList<>(request.getSchemas()),
                    new ArrayList<>(request.getSchemasNotDisplayedBySystem()),
                    copySchemasPackageAccessible,
                    /*forceOverride=*/false,
                    request.getVersion());


            // 3. If forceOverride is false, check that all incompatible types will be migrated.
            // If some aren't we must throw an error, rather than proceeding and deleting those
            // types.
            if (!request.isForceOverride()) {
                SchemaMigrationUtil.checkDeletedAndIncompatibleAfterMigration(setSchemaResponse,
                        activeMigrators.keySet());
            }

            try (AppSearchMigrationHelper migrationHelper = new AppSearchMigrationHelper(
                    mAppSearchImpl, mPackageName, mDatabaseName, request.getSchemas())) {
                // 4. Trigger migration for all activity migrators.
                // TODO(b/177266929) trigger migration for all types together rather than
                //  separately.
                for (Map.Entry<String, Migrator> entry : activeMigrators.entrySet()) {
                    migrationHelper.queryAndTransform(/*schemaType=*/ entry.getKey(),
                            /*migrator=*/ entry.getValue(), currentVersion,
                            finalVersion);
                }

                // 5. SetSchema a second time with forceOverride=true if the first attempted failed
                // due to backward incompatible changes.
                if (!setSchemaResponse.getIncompatibleTypes().isEmpty()
                        || !setSchemaResponse.getDeletedTypes().isEmpty()) {
                    setSchemaResponse = mAppSearchImpl.setSchema(
                            mPackageName,
                            mDatabaseName,
                            new ArrayList<>(request.getSchemas()),
                            new ArrayList<>(request.getSchemasNotDisplayedBySystem()),
                            copySchemasPackageAccessible,
                            /*forceOverride=*/ true,
                            request.getVersion());
                }
                SetSchemaResponse.Builder responseBuilder = setSchemaResponse.toBuilder()
                        .addMigratedTypes(activeMigrators.keySet());
                mIsMutated = true;

                // 6. Put all the migrated documents into the index, now that the new schema is set.
                return migrationHelper.readAndPutDocuments(responseBuilder);
            }
        });

        // setSchema will sync the schemas in the request to AppSearch, any existing schemas which
        // is not included in the request will be delete if we force override incompatible schemas.
        // And all documents of these types will be deleted as well. We should checkForOptimize for
        // these deletion.
        checkForOptimize();
        return future;
    }

    @Override
    @NonNull
    public ListenableFuture<GetSchemaResponse> getSchema() {
        Preconditions.checkState(!mIsClosed, "AppSearchSession has already been closed");
        return execute(() -> mAppSearchImpl.getSchema(mPackageName, mDatabaseName));
    }

    @NonNull
    @Override
    public ListenableFuture<Set<String>> getNamespaces() {
        Preconditions.checkState(!mIsClosed, "AppSearchSession has already been closed");
        return execute(() -> {
            List<String> namespaces = mAppSearchImpl.getNamespaces(mPackageName, mDatabaseName);
            return new ArraySet<>(namespaces);
        });
    }

    @Override
    @NonNull
    public ListenableFuture<AppSearchBatchResult<String, Void>> put(
            @NonNull PutDocumentsRequest request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!mIsClosed, "AppSearchSession has already been closed");
        ListenableFuture<AppSearchBatchResult<String, Void>> future = execute(() -> {
            AppSearchBatchResult.Builder<String, Void> resultBuilder =
                    new AppSearchBatchResult.Builder<>();
            for (int i = 0; i < request.getGenericDocuments().size(); i++) {
                GenericDocument document = request.getGenericDocuments().get(i);
                try {
                    mAppSearchImpl.putDocument(mPackageName, mDatabaseName, document, mLogger);
                    resultBuilder.setSuccess(document.getUri(), /*result=*/ null);
                } catch (Throwable t) {
                    resultBuilder.setResult(document.getUri(), throwableToFailedResult(t));
                }
            }
            mIsMutated = true;
            return resultBuilder.build();
        });

        // The existing documents with same URI will be deleted, so there maybe some
        // resources could be released after optimize().
        checkForOptimize(/*mutateBatchSize=*/ request.getGenericDocuments().size());
        return future;
    }

    @Override
    @NonNull
    public ListenableFuture<AppSearchBatchResult<String, GenericDocument>> getByUri(
            @NonNull GetByUriRequest request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!mIsClosed, "AppSearchSession has already been closed");
        return execute(() -> {
            AppSearchBatchResult.Builder<String, GenericDocument> resultBuilder =
                    new AppSearchBatchResult.Builder<>();

            Map<String, List<String>> typePropertyPaths = request.getProjectionsInternal();
            for (String uri : request.getUris()) {
                try {
                    GenericDocument document =
                            mAppSearchImpl.getDocument(mPackageName, mDatabaseName,
                                    request.getNamespace(), uri, typePropertyPaths);
                    resultBuilder.setSuccess(uri, document);
                } catch (Throwable t) {
                    resultBuilder.setResult(uri, throwableToFailedResult(t));
                }
            }
            return resultBuilder.build();
        });
    }

    @Override
    @NonNull
    public SearchResults search(
            @NonNull String queryExpression,
            @NonNull SearchSpec searchSpec) {
        Preconditions.checkNotNull(queryExpression);
        Preconditions.checkNotNull(searchSpec);
        Preconditions.checkState(!mIsClosed, "AppSearchSession has already been closed");
        return new SearchResultsImpl(
                mAppSearchImpl,
                mExecutor,
                mPackageName,
                mDatabaseName,
                queryExpression,
                searchSpec);
    }

    @Override
    @NonNull
    public ListenableFuture<Void> reportUsage(@NonNull ReportUsageRequest request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!mIsClosed, "AppSearchSession has already been closed");
        return execute(() -> {
            mAppSearchImpl.reportUsage(
                    mPackageName,
                    mDatabaseName,
                    request.getNamespace(),
                    request.getUri(),
                    request.getUsageTimeMillis(),
                    /*systemUsage=*/ false);
            mIsMutated = true;
            return null;
        });
    }

    @Override
    @NonNull
    public ListenableFuture<AppSearchBatchResult<String, Void>> remove(
            @NonNull RemoveByUriRequest request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!mIsClosed, "AppSearchSession has already been closed");
        ListenableFuture<AppSearchBatchResult<String, Void>> future = execute(() -> {
            AppSearchBatchResult.Builder<String, Void> resultBuilder =
                    new AppSearchBatchResult.Builder<>();
            for (String uri : request.getUris()) {
                try {
                    mAppSearchImpl.remove(mPackageName, mDatabaseName, request.getNamespace(), uri);
                    resultBuilder.setSuccess(uri, /*result=*/null);
                } catch (Throwable t) {
                    resultBuilder.setResult(uri, throwableToFailedResult(t));
                }
            }
            mIsMutated = true;
            return resultBuilder.build();
        });
        checkForOptimize(/*mutateBatchSize=*/ request.getUris().size());
        return future;
    }

    @Override
    @NonNull
    public ListenableFuture<Void> remove(
            @NonNull String queryExpression, @NonNull SearchSpec searchSpec) {
        Preconditions.checkNotNull(queryExpression);
        Preconditions.checkNotNull(searchSpec);
        Preconditions.checkState(!mIsClosed, "AppSearchSession has already been closed");
        ListenableFuture<Void> future = execute(() -> {
            mAppSearchImpl.removeByQuery(mPackageName, mDatabaseName, queryExpression, searchSpec);
            mIsMutated = true;
            return null;
        });
        checkForOptimize();
        return future;
    }

    @Override
    @NonNull
    public ListenableFuture<StorageInfo> getStorageInfo() {
        Preconditions.checkState(!mIsClosed, "AppSearchSession has already been closed");
        return execute(() -> mAppSearchImpl.getStorageInfoForDatabase(mPackageName, mDatabaseName));
    }

    @NonNull
    @Override
    public ListenableFuture<Void> maybeFlush() {
        return execute(() -> {
            mAppSearchImpl.persistToDisk();
            return null;
        });
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void close() {
        if (mIsMutated && !mIsClosed) {
            // No future is needed here since the method is void.
            FutureUtil.execute(mExecutor, () -> {
                mAppSearchImpl.persistToDisk();
                mIsClosed = true;
                return null;
            });
        }
    }

    private <T> ListenableFuture<T> execute(Callable<T> callable) {
        return FutureUtil.execute(mExecutor, callable);
    }

    /**
     * Set schema to Icing for no-migration scenario.
     *
     * <p>We only need one time {@link #setSchema} call for no-migration scenario by using the
     * forceoverride in the request.
     */
    private SetSchemaResponse setSchemaNoMigrations(@NonNull SetSchemaRequest request,
            @NonNull Map<String, List<PackageIdentifier>> copySchemasPackageAccessible)
            throws AppSearchException {
        SetSchemaResponse setSchemaResponse = mAppSearchImpl.setSchema(
                mPackageName,
                mDatabaseName,
                new ArrayList<>(request.getSchemas()),
                new ArrayList<>(request.getSchemasNotDisplayedBySystem()),
                copySchemasPackageAccessible,
                request.isForceOverride(),
                request.getVersion());
        if (!request.isForceOverride()) {
            // check both deleted types and incompatible types are empty. That's the only case we
            // swallowed in the AppSearchImpl#setSchema().
            SchemaMigrationUtil.checkDeletedAndIncompatible(setSchemaResponse.getDeletedTypes(),
                    setSchemaResponse.getIncompatibleTypes());
        }
        mIsMutated = true;
        return setSchemaResponse;
    }

    private void checkForOptimize(int mutateBatchSize) {
        mExecutor.execute(() -> {
            try {
                mAppSearchImpl.checkForOptimize(mutateBatchSize);
            } catch (AppSearchException e) {
                Log.w(TAG, "Error occurred when check for optimize", e);
            }
        });
    }

    private void checkForOptimize() {
        mExecutor.execute(() -> {
            try {
                mAppSearchImpl.checkForOptimize();
            } catch (AppSearchException e) {
                Log.w(TAG, "Error occurred when check for optimize", e);
            }
        });
    }
}
