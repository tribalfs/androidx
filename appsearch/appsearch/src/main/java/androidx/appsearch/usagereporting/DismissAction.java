/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.appsearch.usagereporting;

import androidx.annotation.RequiresFeature;
import androidx.appsearch.annotation.CanIgnoreReturnValue;
import androidx.appsearch.annotation.Document;
import androidx.appsearch.app.AppSearchSchema.StringPropertyConfig;
import androidx.appsearch.app.ExperimentalAppSearchApi;
import androidx.appsearch.app.Features;
import androidx.core.util.Preconditions;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * {@link DismissAction} is a built-in AppSearch document type that contains different metrics.
 * Clients can report the user's dismiss actions on a {@link androidx.appsearch.app.SearchResult}
 * document.
 *
 *  <ul>
 *      <li>Dismiss action means the user showed uninterest and took actions on the result document,
 *      such as hitting the X button to dismiss the item from the result list.
 *      <li>It is possible that the user hovered, clicked and opened the result document first, then
 *      dismissed it (or other actions expressing uninterest) after viewing it. In this case,
 *      {@link androidx.appsearch.usagereporting.ImpressionAction},
 *      {@link androidx.appsearch.usagereporting.ClickAction}, {@link DismissAction} occurred at the
 *      same time, but it is recommended that only a single {@link DismissAction} should be reported
 *      for this result document in order to report a negative signal to AppSearch for future
 *      ranking demotion.
 *  </ul>
 *
 * <p>In order to use this document type, the client must explicitly set this schema type via
 * {@link androidx.appsearch.app.SetSchemaRequest.Builder#addDocumentClasses}.
 *
 * <p>Dismiss actions can be used as signals to demote ranking via
 * {@link androidx.appsearch.app.JoinSpec} API in future search requests.
 *
 * <p>Since {@link DismissAction} is an AppSearch document, the client can handle deletion via
 * {@link androidx.appsearch.app.AppSearchSession#removeAsync} or document time-to-live (TTL). The
 * default TTL is 60 days.
 */
// In DismissAction document, there is a joinable property "referencedQualifiedId" for reporting
// the qualified id of the document that the user dismissed. The client can create personal
// navboost (demotion) with dismiss action signals by join query with this property. Therefore,
// DismissAction document class requires join feature.
@RequiresFeature(
        enforcement = "androidx.appsearch.app.Features#isFeatureSupported",
        name = Features.JOIN_SPEC_AND_QUALIFIED_ID)
@Document(name = "builtin:DismissAction")
@ExperimentalAppSearchApi
public class DismissAction extends TakenAction {
    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_PREFIXES)
    private final @Nullable String mQuery;

    @Document.StringProperty(joinableValueType =
            StringPropertyConfig.JOINABLE_VALUE_TYPE_QUALIFIED_ID)
    private final @Nullable String mReferencedQualifiedId;

    @Document.LongProperty
    private final int mResultRankInBlock;

    @Document.LongProperty
    private final int mResultRankGlobal;

    DismissAction(@NonNull String namespace, @NonNull String id, long documentTtlMillis,
            long actionTimestampMillis, @TakenAction.ActionType int actionType,
            @Nullable String query, @Nullable String referencedQualifiedId, int resultRankInBlock,
            int resultRankGlobal) {
        super(namespace, id, documentTtlMillis, actionTimestampMillis, actionType);

        mQuery = query;
        mReferencedQualifiedId = referencedQualifiedId;
        mResultRankInBlock = resultRankInBlock;
        mResultRankGlobal = resultRankGlobal;
    }

    /**
     * Returns the user-entered search input (without any operators or rewriting) that yielded the
     * {@link androidx.appsearch.app.SearchResult} which impressed the user.
     */
    public @Nullable String getQuery() {
        return mQuery;
    }

    /**
     * Returns the qualified id of the {@link androidx.appsearch.app.SearchResult} document that
     * impressed the user.
     *
     * <p>A qualified id is a string generated by package, database, namespace, and document id. See
     * {@link androidx.appsearch.util.DocumentIdUtil#createQualifiedId(String,String,String,String)}
     * for more details.
     */
    public @Nullable String getReferencedQualifiedId() {
        return mReferencedQualifiedId;
    }

    /**
     * Returns the rank of the {@link androidx.appsearch.app.SearchResult} document among the
     * user-defined block.
     *
     * <p>The client can define its own custom definition for block, e.g. corpus name, group, etc.
     *
     * <p>For example, a client defines the block as corpus, and AppSearch returns 5 documents with
     * corpus = ["corpus1", "corpus1", "corpus2", "corpus3", "corpus2"]. Then the block ranks of
     * them = [1, 2, 1, 1, 2].
     *
     * <p>If the client is not presenting the results in multiple blocks, they should set this value
     * to match {@link #getResultRankGlobal}.
     *
     * <p>If unset, then the block rank of the {@link androidx.appsearch.app.SearchResult} document
     * will be set to -1 to mark invalid.
     */
    public int getResultRankInBlock() {
        return mResultRankInBlock;
    }

    /**
     * Returns the global rank of the {@link androidx.appsearch.app.SearchResult} document.
     *
     * <p>Global rank reflects the order of {@link androidx.appsearch.app.SearchResult} documents
     * returned by AppSearch.
     *
     * <p>For example, AppSearch returns 2 pages with 10 {@link androidx.appsearch.app.SearchResult}
     * documents for each page. Then the global ranks of them will be 1 to 10 for the first page,
     * and 11 to 20 for the second page.
     *
     * <p>If unset, then the global rank of the {@link androidx.appsearch.app.SearchResult} document
     * will be set to -1 to mark invalid.
     */
    public int getResultRankGlobal() {
        return mResultRankGlobal;
    }

    // TODO(b/372929164): redesign builder for inheritance to fix the base setter return type issue.
    /** Builder for {@link DismissAction}. */
    @Document.BuilderProducer
    public static final class Builder extends BuilderImpl<Builder> {
        private String mQuery;
        private String mReferencedQualifiedId;
        private int mResultRankInBlock;
        private int mResultRankGlobal;

        /**
         * Constructor for {@link DismissAction.Builder}.
         *
         * @param namespace             Namespace for the Document. See {@link Document.Namespace}.
         * @param id                    Unique identifier for the Document. See {@link Document.Id}.
         * @param actionTimestampMillis The timestamp when the user took the action, in milliseconds
         *                              since Unix epoch.
         */
        public Builder(@NonNull String namespace, @NonNull String id, long actionTimestampMillis) {
            this(namespace, id, actionTimestampMillis, ActionConstants.ACTION_TYPE_DISMISS);
        }

        /**
         * Constructs {@link DismissAction.Builder} by copying existing values from the given
         * {@link DismissAction}.
         *
         * @param dismissAction an existing {@link DismissAction} object.
         */
        public Builder(@NonNull DismissAction dismissAction) {
            super(Preconditions.checkNotNull(dismissAction));

            mQuery = dismissAction.getQuery();
            mReferencedQualifiedId = dismissAction.getReferencedQualifiedId();
            mResultRankInBlock = dismissAction.getResultRankInBlock();
            mResultRankGlobal = dismissAction.getResultRankGlobal();
        }

        /**
         * Constructor for {@link DismissAction.Builder}.
         *
         * <p>It is required by {@link Document.BuilderProducer}.
         *
         * @param namespace             Namespace for the Document. See {@link Document.Namespace}.
         * @param id                    Unique identifier for the Document. See {@link Document.Id}.
         * @param actionTimestampMillis The timestamp when the user took the action, in milliseconds
         *                              since Unix epoch.
         * @param actionType            Action type enum for the Document. See
         *                              {@link TakenAction.ActionType}.
         */
        Builder(@NonNull String namespace, @NonNull String id, long actionTimestampMillis,
                @TakenAction.ActionType int actionType) {
            super(namespace, id, actionTimestampMillis, actionType);

            // Default for unset result rank fields. Since negative number is invalid for ranking,
            // -1 is used as an unset value and AppSearch will ignore it.
            mResultRankInBlock = -1;
            mResultRankGlobal = -1;
        }

        /**
         * Sets the user-entered search input (without any operators or rewriting) that yielded
         * the {@link androidx.appsearch.app.SearchResult} which impressed the user.
         */
        @CanIgnoreReturnValue
        public @NonNull Builder setQuery(@Nullable String query) {
            mQuery = query;
            return this;
        }

        /**
         * Sets the qualified id of the {@link androidx.appsearch.app.SearchResult} document that
         * the user takes action on.
         *
         * <p>A qualified id is a string generated by package, database, namespace, and document id.
         * See {@link androidx.appsearch.util.DocumentIdUtil#createQualifiedId(
         * String,String,String,String)} for more details.
         */
        @CanIgnoreReturnValue
        public @NonNull Builder setReferencedQualifiedId(@Nullable String referencedQualifiedId) {
            mReferencedQualifiedId = referencedQualifiedId;
            return this;
        }

        /**
         * Sets the rank of the {@link androidx.appsearch.app.SearchResult} document among the
         * user-defined block.
         *
         * @see DismissAction#getResultRankInBlock
         */
        @CanIgnoreReturnValue
        public @NonNull Builder setResultRankInBlock(int resultRankInBlock) {
            mResultRankInBlock = resultRankInBlock;
            return this;
        }

        /**
         * Sets the global rank of the {@link androidx.appsearch.app.SearchResult} document.
         *
         * @see DismissAction#getResultRankGlobal
         */
        @CanIgnoreReturnValue
        public @NonNull Builder setResultRankGlobal(int resultRankGlobal) {
            mResultRankGlobal = resultRankGlobal;
            return this;
        }

        /** Builds an {@link DismissAction}. */
        @Override
        public @NonNull DismissAction build() {
            return new DismissAction(mNamespace, mId, mDocumentTtlMillis, mActionTimestampMillis,
                    mActionType, mQuery, mReferencedQualifiedId, mResultRankInBlock,
                    mResultRankGlobal);
        }
    }
}
