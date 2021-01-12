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
import androidx.annotation.RestrictTo;

/**
 * The helper class for {@link AppSearchSchema} migration.
 *
 * <p>It will query and migrate {@link GenericDocument} in given type to a new version.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AppSearchMigrationHelper {

    /**
     * Queries all documents that need to be migrated to new version, and transform documents to
     * new version by passing them to the provided Transformer.
     *
     * @param schemaType   The schema that need be updated and migrated {@link GenericDocument}
     *                     under this type.
     * @param transformer  The {@link Transformer} that will upgrade or downgrade a
     *                     {@link GenericDocument} to new  version.
     */
    void queryAndTransform(@NonNull String schemaType, @NonNull Transformer transformer)
            throws Exception;

    /**
     * The class to migrate {@link GenericDocument} between different version.
     */
    interface Transformer {

        /**
         * Translates a {@link GenericDocument} from a version to a different version.
         *
         * @param currentVersion The current version of the document's schema.
         * @param finalVersion   The final version that documents need to be migrated to.
         * @param document       The {@link GenericDocument} need to be translated to new version.
         * @return         A {@link GenericDocument} in new version.
         */
        @NonNull
        GenericDocument transform(int currentVersion,
                int finalVersion, @NonNull GenericDocument document) throws Exception;
    }
}
