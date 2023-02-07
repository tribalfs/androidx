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

package androidx.appsearch.platformstorage.converter;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.appsearch.app.Features;
import androidx.appsearch.app.SearchSpec;
import androidx.core.util.Preconditions;

import java.util.List;
import java.util.Map;

/**
 * Translates between Platform and Jetpack versions of {@link SearchSpec}.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@RequiresApi(Build.VERSION_CODES.S)
public final class SearchSpecToPlatformConverter {
    private SearchSpecToPlatformConverter() {
    }

    /** Translates from Jetpack to Platform version of {@link SearchSpec}. */
    // Most jetpackSearchSpec.get calls cause WrongConstant lint errors because the methods are not
    // defined as returning the same constants as the corresponding setter expects, but they do
    @SuppressLint("WrongConstant")
    @NonNull
    public static android.app.appsearch.SearchSpec toPlatformSearchSpec(
            @NonNull SearchSpec jetpackSearchSpec) {
        Preconditions.checkNotNull(jetpackSearchSpec);

        if (!jetpackSearchSpec.getAdvancedRankingExpression().isEmpty()) {
            // TODO(b/261474063): Remove this once advanced ranking becomes available.
            throw new UnsupportedOperationException(
                    Features.SEARCH_SPEC_ADVANCED_RANKING_EXPRESSION
                            + " is not available on this AppSearch implementation.");
        }

        android.app.appsearch.SearchSpec.Builder platformBuilder =
                new android.app.appsearch.SearchSpec.Builder();

        platformBuilder
                .setTermMatch(jetpackSearchSpec.getTermMatch())
                .addFilterSchemas(jetpackSearchSpec.getFilterSchemas())
                .addFilterNamespaces(jetpackSearchSpec.getFilterNamespaces())
                .addFilterPackageNames(jetpackSearchSpec.getFilterPackageNames())
                .setResultCountPerPage(jetpackSearchSpec.getResultCountPerPage())
                .setRankingStrategy(jetpackSearchSpec.getRankingStrategy())
                .setOrder(jetpackSearchSpec.getOrder())
                .setSnippetCount(jetpackSearchSpec.getSnippetCount())
                .setSnippetCountPerProperty(jetpackSearchSpec.getSnippetCountPerProperty())
                .setMaxSnippetSize(jetpackSearchSpec.getMaxSnippetSize());
        //TODO(b/262512396): add the enabledFeatures set from the SearchSpec once it is synced
        // across to platform.
        if (jetpackSearchSpec.getResultGroupingTypeFlags() != 0) {
            platformBuilder.setResultGrouping(
                    jetpackSearchSpec.getResultGroupingTypeFlags(),
                    jetpackSearchSpec.getResultGroupingLimit());
        }
        for (Map.Entry<String, List<String>> projection :
                jetpackSearchSpec.getProjections().entrySet()) {
            platformBuilder.addProjection(projection.getKey(), projection.getValue());
        }

        // TODO(b/203700301) : Update to reflect support in Android U+ once this
        // feature is synced over into service-appsearch.
        if (!jetpackSearchSpec.getPropertyWeights().isEmpty()) {
            throw new UnsupportedOperationException(
                    "Property weights are not supported with this backend/Android API level "
                            + "combination.");
        }
        return platformBuilder.build();
    }
}
