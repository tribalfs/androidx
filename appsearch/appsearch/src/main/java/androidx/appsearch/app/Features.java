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
package androidx.appsearch.app;

import androidx.annotation.NonNull;

/**
 * A class that encapsulates all features that are only supported in certain cases (e.g. only on
 * certain implementations or only at a certain Android API Level).
 *
 * <p>Features do not depend on any runtime state, and features will never be removed. Once
 * {@link #isFeatureSupported} returns {@code true} for a certain feature, it is safe to assume that
 * the feature will be available forever on that AppSearch storage implementation, at that
 * Android API level, on that device.
 */
public interface Features {

    /**
     * Feature for {@link #isFeatureSupported(String)}. This feature covers
     * {@link SearchResult.MatchInfo#getSubmatchRange} and
     * {@link SearchResult.MatchInfo#getSubmatch}.
     */
    String SEARCH_RESULT_MATCH_INFO_SUBMATCH = "SEARCH_RESULT_MATCH_INFO_SUBMATCH";

    /**
     * Feature for {@link #isFeatureSupported(String)}. This feature covers
     * {@link GlobalSearchSession#registerObserverCallback} and
     * {@link GlobalSearchSession#unregisterObserverCallback}.
     */
    String GLOBAL_SEARCH_SESSION_REGISTER_OBSERVER_CALLBACK =
            "GLOBAL_SEARCH_SESSION_REGISTER_OBSERVER_CALLBACK";

    /**
     * Feature for {@link #isFeatureSupported(String)}. This feature covers
     * {@link GlobalSearchSession#getSchemaAsync}.
     */
    String GLOBAL_SEARCH_SESSION_GET_SCHEMA = "GLOBAL_SEARCH_SESSION_GET_SCHEMA";

    /**
     * Feature for {@link #isFeatureSupported(String)}. This feature covers
     * {@link GlobalSearchSession#getByDocumentIdAsync}.
     */
    String GLOBAL_SEARCH_SESSION_GET_BY_ID = "GLOBAL_SEARCH_SESSION_GET_BY_ID";

    /**
     * Feature for {@link #isFeatureSupported(String)}. This feature covers
     * {@link SetSchemaRequest.Builder#addAllowedRoleForSchemaTypeVisibility},
     * {@link SetSchemaRequest.Builder#clearAllowedRolesForSchemaTypeVisibility},
     * {@link GetSchemaResponse#getSchemaTypesNotDisplayedBySystem()},
     * {@link GetSchemaResponse#getSchemaTypesVisibleToPackages()},
     * {@link GetSchemaResponse#getRequiredPermissionsForSchemaTypeVisibility()},
     * {@link SetSchemaRequest.Builder#addRequiredPermissionsForSchemaTypeVisibility} and
     * {@link SetSchemaRequest.Builder#clearRequiredPermissionsForSchemaTypeVisibility}
     */
    String ADD_PERMISSIONS_AND_GET_VISIBILITY = "ADD_PERMISSIONS_AND_GET_VISIBILITY";

    /**
     * Feature for {@link #isFeatureSupported(String)}. This feature covers
     * {@link AppSearchSchema.StringPropertyConfig#TOKENIZER_TYPE_RFC822}.
     */
    String TOKENIZER_TYPE_RFC822 = "TOKENIZER_TYPE_RFC822";

    /**
     * Feature for {@link #isFeatureSupported(String)}. This feature covers
     * {@link AppSearchSchema.LongPropertyConfig#INDEXING_TYPE_RANGE} and all other numeric search
     * features.
     */
    String NUMERIC_SEARCH = "NUMERIC_SEARCH";

    /**
     * Feature for {@link #isFeatureSupported(String)}. This feature covers
     * {@link AppSearchSchema.StringPropertyConfig#TOKENIZER_TYPE_VERBATIM} and all other
     * verbatim search features within the query language that allows clients to search using the
     * verbatim string operator.
     *
     * <p>Ex. '"foo/bar" OR baz' will ensure that 'foo/bar' is treated as a single 'verbatim' token.
     */
    String VERBATIM_SEARCH = "VERBATIM_SEARCH";

    /**
     * Feature for {@link #isFeatureSupported(String)}. This feature covers the
     * expansion of the query language to conform to the definition of the list
     * filters language (https://aip.dev/160). This includes:
     * <ul>
     * <li>addition of explicit 'AND' and 'NOT' operators</li>
     * <li>property restricts are allowed with grouping (ex. "prop:(a OR b)")</li>
     * <li>addition of custom functions to control matching</li>
     * </ul>
     *
     * <p>The newly added custom functions covered by this feature are:
     * <ul>
     * <li>createList(String...)</li>
     * <li>termSearch(String, List<String>)</li>
     * </ul>
     *
     * <p>createList takes a variable number of strings and returns a list of strings.
     * It is for use with termSearch.
     *
     * <p>termSearch takes a query string that will be parsed according to the supported
     * query language and an optional list of strings that specify the properties to be
     * restricted to. This exists as a convenience for multiple property restricts. So,
     * for example, the query "(subject:foo OR body:foo) (subject:bar OR body:bar)"
     * could be rewritten as "termSearch(\"foo bar\", createList(\"subject\", \"bar\"))"
     */
    String LIST_FILTER_QUERY_LANGUAGE = "LIST_FILTER_QUERY_LANGUAGE";

    /** Feature for {@link #isFeatureSupported(String)}. This feature covers
     * {@link SearchSpec.Builder#setPropertyWeights}.
     */
    String SEARCH_SPEC_PROPERTY_WEIGHTS = "SEARCH_SPEC_PROPERTY_WEIGHTS";

    /**
     * Feature for {@link #isFeatureSupported(String)}. This feature covers
     * {@link SearchSpec.Builder#setRankingStrategy(String)}.
     */
    String SEARCH_SPEC_ADVANCED_RANKING_EXPRESSION = "SEARCH_SPEC_ADVANCED_RANKING_EXPRESSION";

    /**
     * Feature for {@link #isFeatureSupported(String)}. This feature covers
     * {@link AppSearchSchema.StringPropertyConfig#JOINABLE_VALUE_TYPE_QUALIFIED_ID},
     * {@link SearchSpec.Builder#setJoinSpec}, and all other join features.
     */
    String JOIN_SPEC_AND_QUALIFIED_ID = "JOIN_SPEC_AND_QUALIFIED_ID";

    /**
     * Returns whether a feature is supported at run-time. Feature support depends on the
     * feature in question, the AppSearch backend being used and the Android version of the
     * device.
     *
     * <p class="note"><b>Note:</b> If this method returns {@code false}, it is not safe to invoke
     * the methods requiring the desired feature.
     *
     * @param feature the feature to be checked
     * @return whether the capability is supported given the Android API level and AppSearch
     * backend.
     */
    boolean isFeatureSupported(@NonNull String feature);
}
