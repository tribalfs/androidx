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
package androidx.appsearch.app;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.Closeable;
import java.util.Set;

/**
 * Represents a connection to an AppSearch storage system where {@link GenericDocument}s can be
 * placed and queried.
 *
 * All implementations of this interface must be thread safe.
 */
public interface AppSearchSession extends Closeable {

    /**
     * Sets the schema that represents the organizational structure of data within the AppSearch
     * database.
     *
     * <p>Upon creating an {@link AppSearchSession}, {@link #setSchema} should be called. If the
     * schema needs to be updated, or it has not been previously set, then the provided schema
     * will be saved and persisted to disk. Otherwise, {@link #setSchema} is handled efficiently
     * as a no-op call.
     *
     * @param  request the schema to set or update the AppSearch database to.
     * @return a {@link ListenableFuture} which resolves to a {@link SetSchemaResponse} object.
     */
    // TODO(b/169883602): Change @code references to @link when setPlatformSurfaceable APIs are
    //  exposed.
    @NonNull
    ListenableFuture<SetSchemaResponse> setSchema(
            @NonNull SetSchemaRequest request);

    /**
     * Retrieves the schema most recently successfully provided to {@link #setSchema}.
     *
     * @return The pending result of performing this operation.
     */
    // This call hits disk; async API prevents us from treating these calls as properties.
    @SuppressLint("KotlinPropertyAccess")
    @NonNull
    ListenableFuture<Set<AppSearchSchema>> getSchema();

    /**
     * Indexes documents into the {@link AppSearchSession} database.
     *
     * <p>Each {@link GenericDocument} object must have a {@code schemaType} field set to an
     * {@link AppSearchSchema} type that has been previously registered by calling the
     * {@link #setSchema} method.
     *
     * @param request containing documents to be indexed.
     * @return a {@link ListenableFuture} which resolves to an {@link AppSearchBatchResult}.
     * The keys of the returned {@link AppSearchBatchResult} are the URIs of the input documents.
     * The values are either {@code null} if the corresponding document was successfully indexed,
     * or a failed {@link AppSearchResult} otherwise.
     */
    @NonNull
    ListenableFuture<AppSearchBatchResult<String, Void>> put(@NonNull PutDocumentsRequest request);

    /**
     * Gets {@link GenericDocument} objects by URIs and namespace from the {@link AppSearchSession}
     * database.
     *
     * @param request a request containing URIs and namespace to get documents for.
     * @return A {@link ListenableFuture} which resolves to an {@link AppSearchBatchResult}.
     * The keys of the {@link AppSearchBatchResult} represent the input URIs from the
     * {@link GetByUriRequest} object. The values are either the corresponding
     * {@link GenericDocument} object for the URI on success, or an {@link AppSearchResult}
     * object on failure. For example, if a URI is not found, the value for that URI will be set
     * to an {@link AppSearchResult} object with result code:
     * {@link AppSearchResult#RESULT_NOT_FOUND}.
     */
    @NonNull
    ListenableFuture<AppSearchBatchResult<String, GenericDocument>> getByUri(
            @NonNull GetByUriRequest request);

    /**
     * Retrieves documents from the open {@link AppSearchSession} that match a given query string
     * and type of search provided.
     *
     * <p>Query strings can be empty, contain one term with no operators, or contain multiple
     * terms and operators.
     *
     * <p>For query strings that are empty, all documents that match the {@link SearchSpec} will be
     * returned.
     *
     * <p>For query strings with a single term and no operators, documents that match the
     * provided query string and {@link SearchSpec} will be returned.
     *
     * <p>The following operators are supported:
     *
     * <ul>
     *     <li>AND (implicit)
     *     <p>AND is an operator that matches documents that contain <i>all</i>
     *     provided terms.
     *     <p><b>NOTE:</b> A space between terms is treated as an "AND" operator. Explicitly
     *     including "AND" in a query string will treat "AND" as a term, returning documents that
     *     also contain "AND".
     *     <p>Example: "apple AND banana" matches documents that contain the
     *     terms "apple", "and", "banana".
     *     <p>Example: "apple banana" matches documents that contain both "apple" and
     *     "banana".
     *     <p>Example: "apple banana cherry" matches documents that contain "apple", "banana", and
     *     "cherry".
     *
     *     <li>OR
     *     <p>OR is an operator that matches documents that contain <i>any</i> provided term.
     *     <p>Example: "apple OR banana" matches documents that contain either "apple" or "banana".
     *     <p>Example: "apple OR banana OR cherry" matches documents that contain any of
     *     "apple", "banana", or "cherry".
     *
     *     <li>Exclusion (-)
     *     <p>Exclusion (-) is an operator that matches documents that <i>do not</i> contain the
     *     provided term.
     *     <p>Example: "-apple" matches documents that do not contain "apple".
     *
     *     <li>Grouped Terms
     *     <p>For queries that require multiple operators and terms, terms can be grouped into
     *     subqueries. Subqueries are contained within an open "(" and close ")" parenthesis.
     *     <p>Example: "(donut OR bagel) (coffee OR tea)" matches documents that contain
     *     either "donut" or "bagel" and either "coffee" or "tea".
     *
     *     <li>Property Restricts
     *     <p>For queries that require a term to match a specific {@link AppSearchSchema}
     *     property of a document, a ":" must be included between the property name and the term.
     *     <p>Example: "subject:important" matches documents that contain the term "important" in
     *     the "subject" property.
     * </ul>
     *
     * <p>Additional search specifications, such as filtering by {@link AppSearchSchema} type or
     * adding projection, can be set by calling the corresponding {@link SearchSpec.Builder} setter.
     *
     * <p>This method is lightweight. The heavy work will be done in
     * {@link SearchResults#getNextPage}.
     *
     * @param queryExpression query string to search.
     * @param searchSpec      spec for setting document filters, adding projection, setting term
     *                        match type, etc.
     * @return a {@link SearchResults} object for retrieved matched documents.
     */
    @NonNull
    SearchResults search(@NonNull String queryExpression, @NonNull SearchSpec searchSpec);

    /**
     * Reports usage of a particular document by URI and namespace.
     *
     * <p>A usage report represents an event in which a user interacted with or viewed a document.
     *
     * <p>For each call to {@link #reportUsage}, AppSearch updates usage count and usage recency
     * metrics for that particular document. These metrics are used for ordering {@link #search}
     * results by the {@link SearchSpec#RANKING_STRATEGY_USAGE_COUNT} and
     * {@link SearchSpec#RANKING_STRATEGY_USAGE_LAST_USED_TIMESTAMP} ranking strategies.
     *
     * <p>Reporting usage of a document is optional.
     *
     * @param request The usage reporting request.
     * @return The pending result of performing this operation which resolves to {@code null} on
     *     success.
     */
    @NonNull
    ListenableFuture<Void> reportUsage(@NonNull ReportUsageRequest request);

    /**
     * Removes {@link GenericDocument} objects by URIs and namespace from the
     * {@link AppSearchSession} database.
     *
     * <p>Removed documents will no longer be surfaced by {@link #search} or {@link #getByUri}
     * calls.
     * <p><b>NOTE:</b>By default, documents are removed via a soft delete operation. Once the
     * document crosses the count threshold or byte usage threshold, the documents will be
     * removed from disk.
     *
     * @param request {@link RemoveByUriRequest} with URIs and namespace to remove from the index.
     * @return a {@link ListenableFuture} which resolves to an {@link AppSearchBatchResult}.
     * The keys of the {@link AppSearchBatchResult} represent the input URIs from the
     * {@link RemoveByUriRequest} object. The values are either {@code null} on success,
     * or a failed {@link AppSearchResult} otherwise. URIs that are not found will return a failed
     * {@link AppSearchResult} with a result code of {@link AppSearchResult#RESULT_NOT_FOUND}.
     */
    @NonNull
    ListenableFuture<AppSearchBatchResult<String, Void>> remove(
            @NonNull RemoveByUriRequest request);

    /**
     * Removes {@link GenericDocument}s from the index by Query. Documents will be removed if they
     * match the {@code queryExpression} in given namespaces and schemaTypes which is set via
     * {@link SearchSpec.Builder#addFilterNamespaces} and
     * {@link SearchSpec.Builder#addFilterSchemas}.
     *
     * <p> An empty {@code queryExpression} matches all documents.
     *
     * <p> An empty set of namespaces or schemaTypes matches all namespaces or schemaTypes in
     * the current database.
     *
     * @param queryExpression Query String to search.
     * @param searchSpec      Spec containing schemaTypes, namespaces and query expression
     *                        indicates how document will be removed. All specific about how to
     *                        scoring, ordering, snippeting and resulting will be ignored.
     * @return The pending result of performing this operation.
     */
    @NonNull
    ListenableFuture<Void> remove(@NonNull String queryExpression, @NonNull SearchSpec searchSpec);

    /**
     * Flush all schema and document updates, additions, and deletes to disk if possible.
     *
     * @return The pending result of performing this operation.
     * {@link androidx.appsearch.exceptions.AppSearchException} with
     * {@link AppSearchResult#RESULT_INTERNAL_ERROR} will be set to the future if we hit error when
     * save to disk.
     */
    @NonNull
    ListenableFuture<Void> maybeFlush();

    /**
     * Closes the {@link AppSearchSession} to persist all schema and document updates, additions,
     * and deletes to disk.
     */
    @Override
    void close();
}
