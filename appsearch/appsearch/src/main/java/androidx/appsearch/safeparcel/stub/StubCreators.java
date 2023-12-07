/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.appsearch.safeparcel.stub;

import androidx.annotation.RestrictTo;
import androidx.appsearch.app.StorageInfo;
import androidx.appsearch.app.VisibilityDocument;
import androidx.appsearch.safeparcel.GenericDocumentParcel;
import androidx.appsearch.safeparcel.PropertyConfigParcel;
import androidx.appsearch.safeparcel.PropertyConfigParcel.DocumentIndexingConfigParcel;
import androidx.appsearch.safeparcel.PropertyConfigParcel.IntegerIndexingConfigParcel;
import androidx.appsearch.safeparcel.PropertyConfigParcel.JoinableConfigParcel;
import androidx.appsearch.safeparcel.PropertyConfigParcel.StringIndexingConfigParcel;
import androidx.appsearch.safeparcel.PropertyParcel;
import androidx.appsearch.stats.SchemaMigrationStats;

/**
 * Stub creators for any classes extending
 * {@link androidx.appsearch.safeparcel.SafeParcelable}.
 *
 * <p>We don't have SafeParcelProcessor in Jetpack, so for each
 * {@link androidx.appsearch.safeparcel.SafeParcelable}, a stub creator class needs to
 * be provided for code sync purpose.
 */
// @exportToFramework:skipFile()
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class StubCreators {
    /** Stub creator for {@link androidx.appsearch.app.StorageInfo}. */
    public static class StorageInfoCreator extends AbstractCreator<StorageInfo> {
    }

    /** Stub creator for {@link PropertyParcel}. */
    public static class PropertyParcelCreator extends AbstractCreator<PropertyParcel> {
    }

    /** Stub creator for {@link PropertyConfigParcel}. */
    public static class PropertyConfigParcelCreator extends AbstractCreator<PropertyConfigParcel> {
    }

    /**
     * Stub creator for
     * {@link PropertyConfigParcel.JoinableConfigParcel}.
     */
    public static class JoinableConfigParcelCreator extends AbstractCreator<JoinableConfigParcel> {
    }

    /**
     * Stub creator for
     * {@link PropertyConfigParcel.StringIndexingConfigParcel}.
     */
    public static class StringIndexingConfigParcelCreator extends
            AbstractCreator<StringIndexingConfigParcel> {
    }

    /**
     * Stub creator for
     * {@link PropertyConfigParcel.IntegerIndexingConfigParcel}.
     */
    public static class IntegerIndexingConfigParcelCreator extends
            AbstractCreator<IntegerIndexingConfigParcel> {
    }

    /**
     * Stub creator for
     * {@link PropertyConfigParcel.DocumentIndexingConfigParcel}.
     */
    public static class DocumentIndexingConfigParcelCreator extends
            AbstractCreator<DocumentIndexingConfigParcel> {
    }

    /** Stub creator for {@link GenericDocumentParcel}. */
    public static class GenericDocumentParcelCreator extends
            AbstractCreator<GenericDocumentParcel> {
    }

    /** Stub creator for {@link androidx.appsearch.app.VisibilityPermissionDocument}. */
    public static class VisibilityPermissionDocumentCreator extends
            AbstractCreator<VisibilityPermissionDocumentCreator> {
    }

    /** Stub creator for {@link androidx.appsearch.app.VisibilityDocument}. */
    public static class VisibilityDocumentCreator extends AbstractCreator<VisibilityDocument> {
    }

    /** Stub creator for {@link androidx.appsearch.stats.SchemaMigrationStats}. */
    public static class SchemaMigrationStatsCreator extends AbstractCreator<SchemaMigrationStats> {
    }
}
