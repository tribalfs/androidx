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

import static android.app.appsearch.AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_NONE;
import static android.app.appsearch.AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.DoNotInline;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.appsearch.app.AppSearchSchema;
import androidx.appsearch.app.Features;
import androidx.core.util.Preconditions;

import java.util.List;

/**
 * Translates a jetpack {@link AppSearchSchema} into a platform
 * {@link android.app.appsearch.AppSearchSchema}.
 * @exportToFramework:hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@RequiresApi(Build.VERSION_CODES.S)
public final class SchemaToPlatformConverter {
    private SchemaToPlatformConverter() {}

    /**
     * Translates a jetpack {@link AppSearchSchema} into a platform
     * {@link android.app.appsearch.AppSearchSchema}.
     */
    @NonNull
    public static android.app.appsearch.AppSearchSchema toPlatformSchema(
            @NonNull AppSearchSchema jetpackSchema) {
        Preconditions.checkNotNull(jetpackSchema);
        android.app.appsearch.AppSearchSchema.Builder platformBuilder =
                new android.app.appsearch.AppSearchSchema.Builder(jetpackSchema.getSchemaType());
        List<AppSearchSchema.PropertyConfig> properties = jetpackSchema.getProperties();
        for (int i = 0; i < properties.size(); i++) {
            android.app.appsearch.AppSearchSchema.PropertyConfig platformProperty =
                    toPlatformProperty(properties.get(i));
            platformBuilder.addProperty(platformProperty);
        }
        if (!jetpackSchema.getParentTypes().isEmpty()) {
            // TODO(b/269295094): Remove this once polymorphism becomes available.
            throw new UnsupportedOperationException(Features.SCHEMA_ADD_PARENT_TYPE
                    + " is not available on this AppSearch implementation.");
        }
        return platformBuilder.build();
    }

    /**
     * Translates a platform {@link android.app.appsearch.AppSearchSchema} to a jetpack
     * {@link AppSearchSchema}.
     */
    @NonNull
    public static AppSearchSchema toJetpackSchema(
            @NonNull android.app.appsearch.AppSearchSchema platformSchema) {
        Preconditions.checkNotNull(platformSchema);
        AppSearchSchema.Builder jetpackBuilder =
                new AppSearchSchema.Builder(platformSchema.getSchemaType());
        List<android.app.appsearch.AppSearchSchema.PropertyConfig> properties =
                platformSchema.getProperties();
        for (int i = 0; i < properties.size(); i++) {
            AppSearchSchema.PropertyConfig jetpackProperty = toJetpackProperty(properties.get(i));
            jetpackBuilder.addProperty(jetpackProperty);
        }
        // TODO(b/269295094): Call jetpackBuilder.addParentType() to add parent types once
        //  polymorphism becomes available in platform.
        return jetpackBuilder.build();
    }

    // Most stringProperty.get calls cause WrongConstant lint errors because the methods are not
    // defined as returning the same constants as the corresponding setter expects, but they do
    @SuppressLint("WrongConstant")
    @NonNull
    private static android.app.appsearch.AppSearchSchema.PropertyConfig toPlatformProperty(
            @NonNull AppSearchSchema.PropertyConfig jetpackProperty) {
        Preconditions.checkNotNull(jetpackProperty);
        if (jetpackProperty instanceof AppSearchSchema.StringPropertyConfig) {
            AppSearchSchema.StringPropertyConfig stringProperty =
                    (AppSearchSchema.StringPropertyConfig) jetpackProperty;
            android.app.appsearch.AppSearchSchema.StringPropertyConfig.Builder platformBuilder =
                    new android.app.appsearch.AppSearchSchema.StringPropertyConfig.Builder(
                    stringProperty.getName())
                    .setCardinality(stringProperty.getCardinality())
                    .setIndexingType(stringProperty.getIndexingType())
                    .setTokenizerType(stringProperty.getTokenizerType());

            // TODO(b/277344542): Handle RFC822 tokenization on T devices with U trains.
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU) {
                Preconditions.checkArgumentInRange(stringProperty.getTokenizerType(),
                        TOKENIZER_TYPE_NONE, TOKENIZER_TYPE_PLAIN, "tokenizerType");
            }

            if (stringProperty.getDeletionPropagation()) {
                // TODO(b/268521214): Update once deletion propagation is available.
                throw new UnsupportedOperationException("Setting deletion propagation is not "
                        + "supported on this AppSearch implementation.");
            }

            if (stringProperty.getJoinableValueType()
                    == AppSearchSchema.StringPropertyConfig.JOINABLE_VALUE_TYPE_QUALIFIED_ID) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    throw new UnsupportedOperationException(
                        "StringPropertyConfig.JOINABLE_VALUE_TYPE_QUALIFIED_ID is not supported"
                                + " on this AppSearch implementation.");
                }
                ApiHelperForU.setJoinableValueType(platformBuilder,
                        stringProperty.getJoinableValueType());
            }
            return platformBuilder.build();
        } else if (jetpackProperty instanceof AppSearchSchema.LongPropertyConfig) {
            AppSearchSchema.LongPropertyConfig longProperty =
                    (AppSearchSchema.LongPropertyConfig) jetpackProperty;
            android.app.appsearch.AppSearchSchema.LongPropertyConfig.Builder longPropertyBuilder =
                    new android.app.appsearch.AppSearchSchema.LongPropertyConfig.Builder(
                    jetpackProperty.getName())
                    .setCardinality(jetpackProperty.getCardinality());
            if (longProperty.getIndexingType()
                    == AppSearchSchema.LongPropertyConfig.INDEXING_TYPE_RANGE) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    throw new UnsupportedOperationException(
                        "LongProperty.INDEXING_TYPE_RANGE is not supported on this AppSearch "
                                + "implementation.");
                }
                ApiHelperForU.setIndexingType(
                        longPropertyBuilder, longProperty.getIndexingType());
            }
            return longPropertyBuilder.build();
        } else if (jetpackProperty instanceof AppSearchSchema.DoublePropertyConfig) {
            return new android.app.appsearch.AppSearchSchema.DoublePropertyConfig.Builder(
                    jetpackProperty.getName())
                    .setCardinality(jetpackProperty.getCardinality())
                    .build();
        } else if (jetpackProperty instanceof AppSearchSchema.BooleanPropertyConfig) {
            return new android.app.appsearch.AppSearchSchema.BooleanPropertyConfig.Builder(
                    jetpackProperty.getName())
                    .setCardinality(jetpackProperty.getCardinality())
                    .build();
        } else if (jetpackProperty instanceof AppSearchSchema.BytesPropertyConfig) {
            return new android.app.appsearch.AppSearchSchema.BytesPropertyConfig.Builder(
                    jetpackProperty.getName())
                    .setCardinality(jetpackProperty.getCardinality())
                    .build();
        } else if (jetpackProperty instanceof AppSearchSchema.DocumentPropertyConfig) {
            AppSearchSchema.DocumentPropertyConfig documentProperty =
                    (AppSearchSchema.DocumentPropertyConfig) jetpackProperty;
            if (!documentProperty.getIndexableNestedProperties().isEmpty()) {
                // TODO(b/289150947): Update and set list once indexable-nested-properties-list is
                //  available.
                throw new UnsupportedOperationException(
                        "DocumentPropertyConfig.addIndexableNestedProperties is not supported on "
                                + "this AppSearch implementation.");
            }
            return new android.app.appsearch.AppSearchSchema.DocumentPropertyConfig.Builder(
                    documentProperty.getName(), documentProperty.getSchemaType())
                    .setCardinality(documentProperty.getCardinality())
                    .setShouldIndexNestedProperties(documentProperty.shouldIndexNestedProperties())
                    .build();
        } else {
            throw new IllegalArgumentException(
                    "Invalid dataType: " + jetpackProperty.getDataType());
        }
    }

    // Most stringProperty.get calls cause WrongConstant lint errors because the methods are not
    // defined as returning the same constants as the corresponding setter expects, but they do
    @SuppressLint("WrongConstant")
    @NonNull
    private static AppSearchSchema.PropertyConfig toJetpackProperty(
            @NonNull android.app.appsearch.AppSearchSchema.PropertyConfig platformProperty) {
        Preconditions.checkNotNull(platformProperty);
        if (platformProperty
                instanceof android.app.appsearch.AppSearchSchema.StringPropertyConfig) {
            android.app.appsearch.AppSearchSchema.StringPropertyConfig stringProperty =
                    (android.app.appsearch.AppSearchSchema.StringPropertyConfig) platformProperty;
            AppSearchSchema.StringPropertyConfig.Builder jetpackBuilder =
                    new AppSearchSchema.StringPropertyConfig.Builder(stringProperty.getName())
                            .setCardinality(stringProperty.getCardinality())
                            .setIndexingType(stringProperty.getIndexingType())
                            .setTokenizerType(stringProperty.getTokenizerType());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                jetpackBuilder.setJoinableValueType(
                        ApiHelperForU.getJoinableValueType(stringProperty));
            }
            return jetpackBuilder.build();
        } else if (platformProperty
                instanceof android.app.appsearch.AppSearchSchema.LongPropertyConfig) {
            android.app.appsearch.AppSearchSchema.LongPropertyConfig longProperty =
                    (android.app.appsearch.AppSearchSchema.LongPropertyConfig) platformProperty;
            AppSearchSchema.LongPropertyConfig.Builder jetpackBuilder =
                    new AppSearchSchema.LongPropertyConfig.Builder(longProperty.getName())
                            .setCardinality(longProperty.getCardinality());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                jetpackBuilder.setIndexingType(
                        ApiHelperForU.getIndexingType(longProperty));
            }
            return jetpackBuilder.build();
        } else if (platformProperty
                instanceof android.app.appsearch.AppSearchSchema.DoublePropertyConfig) {
            return new AppSearchSchema.DoublePropertyConfig.Builder(platformProperty.getName())
                    .setCardinality(platformProperty.getCardinality())
                    .build();
        } else if (platformProperty
                instanceof android.app.appsearch.AppSearchSchema.BooleanPropertyConfig) {
            return new AppSearchSchema.BooleanPropertyConfig.Builder(platformProperty.getName())
                    .setCardinality(platformProperty.getCardinality())
                    .build();
        } else if (platformProperty
                instanceof android.app.appsearch.AppSearchSchema.BytesPropertyConfig) {
            return new AppSearchSchema.BytesPropertyConfig.Builder(platformProperty.getName())
                    .setCardinality(platformProperty.getCardinality())
                    .build();
        } else if (platformProperty
                instanceof android.app.appsearch.AppSearchSchema.DocumentPropertyConfig) {
            android.app.appsearch.AppSearchSchema.DocumentPropertyConfig documentProperty =
                    (android.app.appsearch.AppSearchSchema.DocumentPropertyConfig) platformProperty;
            return new AppSearchSchema.DocumentPropertyConfig.Builder(
                    documentProperty.getName(),
                    documentProperty.getSchemaType())
                    .setCardinality(documentProperty.getCardinality())
                    .setShouldIndexNestedProperties(documentProperty.shouldIndexNestedProperties())
                    .build();
            // TODO(b/289150947): Add the indexable_nested_properties_list once it becomes
            //  available in platform.
        } else {
            throw new IllegalArgumentException(
                    "Invalid property type " + platformProperty.getClass()
                            + ": " + platformProperty);
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private static class ApiHelperForU {
        private ApiHelperForU() {
            // This class is not instantiable.
        }

        @DoNotInline
        static void setJoinableValueType(
                android.app.appsearch.AppSearchSchema.StringPropertyConfig.Builder builder,
                @AppSearchSchema.StringPropertyConfig.JoinableValueType int joinableValueType) {
            builder.setJoinableValueType(joinableValueType);
        }

        // Most stringProperty.get calls cause WrongConstant lint errors because the methods are not
        // defined as returning the same constants as the corresponding setter expects, but they do
        @SuppressLint("WrongConstant")
        @DoNotInline
        @AppSearchSchema.StringPropertyConfig.JoinableValueType
        static int getJoinableValueType(
                android.app.appsearch.AppSearchSchema.StringPropertyConfig stringPropertyConfig) {
            return stringPropertyConfig.getJoinableValueType();
        }

        @DoNotInline
        static void setIndexingType(
                android.app.appsearch.AppSearchSchema.LongPropertyConfig.Builder builder,
                @AppSearchSchema.LongPropertyConfig.IndexingType int longIndexingType) {
            builder.setIndexingType(longIndexingType);
        }

        // Most LongProperty.get calls cause WrongConstant lint errors because the methods are not
        // defined as returning the same constants as the corresponding setter expects, but they do
        @SuppressLint("WrongConstant")
        @DoNotInline
        @AppSearchSchema.LongPropertyConfig.IndexingType
        static int getIndexingType(
                android.app.appsearch.AppSearchSchema.LongPropertyConfig longPropertyConfig) {
            return longPropertyConfig.getIndexingType();
        }
    }
}
