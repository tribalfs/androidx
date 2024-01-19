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

package androidx.appsearch.localstorage.visibilitystore;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.appsearch.app.AppSearchSchema;
import androidx.appsearch.app.GenericDocument;
import androidx.appsearch.app.PackageIdentifier;
import androidx.appsearch.app.SetSchemaRequest;
import androidx.appsearch.app.VisibilityConfig;

import com.google.android.appsearch.proto.AndroidVOverlayProto;
import com.google.android.appsearch.proto.PackageIdentifierProto;
import com.google.android.appsearch.proto.VisibilityConfigProto;
import com.google.android.appsearch.proto.VisibleToPermissionProto;
import com.google.android.icing.protobuf.ByteString;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class VisibilityToDocumentConverterTest {

    @Test
    public void testToGenericDocuments() throws Exception {
        // Create a SetSchemaRequest for testing
        byte[] cert1 = new byte[32];
        byte[] cert2 = new byte[32];
        byte[] cert3 = new byte[32];
        byte[] cert4 = new byte[32];
        Arrays.fill(cert1, (byte) 1);
        Arrays.fill(cert2, (byte) 2);
        Arrays.fill(cert3, (byte) 3);
        Arrays.fill(cert4, (byte) 4);

        VisibilityConfig visibleToConfig = new VisibilityConfig.Builder()
                .addVisibleToPackage(new PackageIdentifier("com.example.test1", cert1))
                .setPubliclyVisibleTargetPackage(
                        new PackageIdentifier("com.example.test2", cert2))
                .addVisibleToPermissions(ImmutableSet.of(1, 2))
                .setNotDisplayedBySystem(true)
                .build();
        SetSchemaRequest setSchemaRequest = new SetSchemaRequest.Builder()
                .addSchemas(new AppSearchSchema.Builder("someSchema").build())
                .setSchemaTypeDisplayedBySystem("someSchema", false)
                .setSchemaTypeVisibilityForPackage("someSchema", true,
                        new PackageIdentifier("com.example.test3", cert3))
                .addRequiredPermissionsForSchemaTypeVisibility(
                        "someSchema", ImmutableSet.of(3, 4))
                .setPubliclyVisibleSchema("someSchema",
                        new PackageIdentifier("com.example.test4", cert4))
                .addSchemaTypeVisibleToConfig("someSchema", visibleToConfig)
                .build();

        // Create android V overlay proto
        VisibilityConfigProto visibleToConfigProto = VisibilityConfigProto.newBuilder()
                .addVisibleToPackages(PackageIdentifierProto.newBuilder()
                        .setPackageName("com.example.test1")
                        .setPackageSha256Cert(ByteString.copyFrom(cert1)).build())
                .setPubliclyVisibleTargetPackage(PackageIdentifierProto.newBuilder()
                                .setPackageName("com.example.test2")
                                .setPackageSha256Cert(ByteString.copyFrom(cert2)).build())
                .addVisibleToPermissions(VisibleToPermissionProto.newBuilder()
                        .addAllPermissions(ImmutableSet.of(1, 2)).build())
                .setNotPlatformSurfaceable(true)
                .build();
        VisibilityConfigProto visibilityConfigProto = VisibilityConfigProto.newBuilder()
                .setPubliclyVisibleTargetPackage(PackageIdentifierProto.newBuilder()
                        .setPackageName("com.example.test4")
                        .setPackageSha256Cert(ByteString.copyFrom(cert4)).build())
                .build();
        AndroidVOverlayProto overlayProto = AndroidVOverlayProto.newBuilder()
                .setVisibilityConfig(visibilityConfigProto)
                .addVisibleToConfigs(visibleToConfigProto)
                .build();

        // Create the expected AndroidVOverlay document
        GenericDocument expectedAndroidVOverlay =
                new GenericDocument.Builder<GenericDocument.Builder<?>>("androidVOverlay",
                        "someSchema", "AndroidVOverlayType")
                        .setCreationTimestampMillis(0)
                        .setPropertyBytes("visibilityProtoSerializeProperty",
                                overlayProto.toByteArray())
                        .build();

        // Create the expected visibility document
        GenericDocument permissionDoc34 =
                new GenericDocument.Builder<GenericDocument.Builder<?>>("", "",
                        "VisibilityPermissionType")
                        .setCreationTimestampMillis(0)
                        .setPropertyLong("allRequiredPermissions", 3, 4).build();
        GenericDocument expectedVisibilityDocument =
                new GenericDocument.Builder<GenericDocument.Builder<?>>("", "someSchema",
                        "VisibilityType")
                        .setCreationTimestampMillis(0)
                        .setPropertyBoolean("notPlatformSurfaceable", true)
                        .setPropertyString("packageName", "com.example.test3")
                        .setPropertyBytes("sha256Cert", cert3)
                        .setPropertyDocument("permission", permissionDoc34)
                        .build();

        // Convert the SetSchemaRequest to a list of VisibilityConfig
        List<VisibilityConfig> visibilityConfigs =
                VisibilityConfig.toVisibilityConfigs(setSchemaRequest);

        // Check if the conversion is correct
        assertEquals(1, visibilityConfigs.size());
        VisibilityConfig visibilityConfig = visibilityConfigs.get(0);

        assertEquals(expectedVisibilityDocument,
                VisibilityToDocumentConverter.createVisibilityDocument(visibilityConfig));
        assertEquals(expectedAndroidVOverlay,
                VisibilityToDocumentConverter.createAndroidVOverlay(visibilityConfig));
    }

    @Test
    public void testToVisibilityConfig() {
        byte[] cert1 = new byte[32];
        byte[] cert2 = new byte[32];
        byte[] cert3 = new byte[32];
        byte[] cert4 = new byte[32];
        Arrays.fill(cert1, (byte) 1);
        Arrays.fill(cert2, (byte) 2);
        Arrays.fill(cert3, (byte) 3);
        Arrays.fill(cert4, (byte) 4);

        // Create visibility proto property
        VisibilityConfigProto visibleToConfigProto = VisibilityConfigProto.newBuilder()
                .addVisibleToPackages(PackageIdentifierProto.newBuilder()
                        .setPackageName("com.example.test1")
                        .setPackageSha256Cert(ByteString.copyFrom(cert1)).build())
                .setPubliclyVisibleTargetPackage(PackageIdentifierProto.newBuilder()
                        .setPackageName("com.example.test2")
                        .setPackageSha256Cert(ByteString.copyFrom(cert2)).build())
                .addVisibleToPermissions(VisibleToPermissionProto.newBuilder()
                        .addAllPermissions(ImmutableSet.of(1, 2)).build())
                .setNotPlatformSurfaceable(true)
                .build();
        VisibilityConfigProto visibilityConfigProto = VisibilityConfigProto.newBuilder()
                .setPubliclyVisibleTargetPackage(PackageIdentifierProto.newBuilder()
                        .setPackageName("com.example.test4")
                        .setPackageSha256Cert(ByteString.copyFrom(cert4)).build())
                .build();
        AndroidVOverlayProto overlayProto = AndroidVOverlayProto.newBuilder()
                .setVisibilityConfig(visibilityConfigProto)
                .addVisibleToConfigs(visibleToConfigProto)
                .build();

        // Create a visible config overlay for testing
        GenericDocument androidVOverlay =
                new GenericDocument.Builder<GenericDocument.Builder<?>>("androidVOverlay",
                        "someSchema", "AndroidVOverlayType")
                        .setCreationTimestampMillis(0)
                        .setPropertyBytes("visibilityProtoSerializeProperty",
                                overlayProto.toByteArray())
                        .build();

        // Create a VisibilityDocument for testing
        GenericDocument permissionDoc34 =
                new GenericDocument.Builder<GenericDocument.Builder<?>>("", "",
                        "VisibilityPermissionType")
                        .setCreationTimestampMillis(0)
                        .setPropertyLong("allRequiredPermissions", 3, 4).build();
        GenericDocument visibilityDoc =
                new GenericDocument.Builder<GenericDocument.Builder<?>>("", "someSchema",
                        "VisibilityType")
                        .setCreationTimestampMillis(0)
                        .setPropertyBoolean("notPlatformSurfaceable", false)
                        .setPropertyString("packageName", "com.example.test3")
                        .setPropertyBytes("sha256Cert", cert3)
                        .setPropertyDocument("permission", permissionDoc34)
                        .build();

        // Create a VisibilityConfig using the Builder
        VisibilityConfig visibilityConfig = VisibilityToDocumentConverter.createVisibilityConfig(
                visibilityDoc, androidVOverlay);

        // Check if the properties are set correctly
        assertEquals(visibilityDoc, VisibilityToDocumentConverter
                .createVisibilityDocument(visibilityConfig));
        assertEquals(androidVOverlay, VisibilityToDocumentConverter
                .createAndroidVOverlay(visibilityConfig));

        VisibilityConfig.Builder builder = new VisibilityConfig.Builder(visibilityConfig);

        VisibilityConfig rebuild = builder.build();
        assertTrue(visibilityConfig.equals(rebuild));

        VisibilityConfig modifiedConfig = builder
                .setSchemaType("prefixedSchema")
                .setNotDisplayedBySystem(true)
                .addVisibleToPermissions(ImmutableSet.of(SetSchemaRequest.READ_SMS,
                        SetSchemaRequest.READ_CALENDAR))
                .clearVisibleToPackages()
                .addVisibleToPackage(
                        new PackageIdentifier("com.example.other", new byte[32]))
                .setPubliclyVisibleTargetPackage(
                        new PackageIdentifier("com.example.other", new byte[32]))
                .clearVisibleToConfig()
                .build();
        assertEquals(modifiedConfig.getSchemaType(), "prefixedSchema");

        // Check that the rebuild stayed the same
        assertEquals(rebuild.getSchemaType(), "someSchema");
        assertFalse(rebuild.isNotDisplayedBySystem());
        assertEquals(rebuild.getVisibleToPermissions(), ImmutableSet.of(ImmutableSet.of(3, 4)));
        assertEquals(rebuild.getVisibleToPackages(),
                ImmutableList.of(new PackageIdentifier("com.example.test3", cert3)));
        assertEquals(rebuild.getPubliclyVisibleTargetPackage(),
                new PackageIdentifier("com.example.test4", cert4));

        VisibilityConfig expectedVisibleToConfig = new VisibilityConfig.Builder()
                .setNotDisplayedBySystem(true)
                .addVisibleToPermissions(ImmutableSet.of(1, 2))
                .addVisibleToPackage(new PackageIdentifier("com.example.test1", cert1))
                .setPubliclyVisibleTargetPackage(new PackageIdentifier("com.example.test2", cert2))
                .build();
        assertThat(rebuild.getVisibleToConfigs()).containsExactly(expectedVisibleToConfig);
    }

    @Test
    public void testToGenericDocumentAndBack() {
        // Create a SetSchemaRequest for testing
        byte[] cert1 = new byte[32];
        byte[] cert2 = new byte[32];
        byte[] cert3 = new byte[32];
        byte[] cert4 = new byte[32];
        byte[] cert5 = new byte[32];
        byte[] cert6 = new byte[32];
        byte[] cert7 = new byte[32];
        Arrays.fill(cert1, (byte) 1);
        Arrays.fill(cert2, (byte) 2);
        Arrays.fill(cert3, (byte) 3);
        Arrays.fill(cert4, (byte) 4);
        Arrays.fill(cert5, (byte) 5);
        Arrays.fill(cert6, (byte) 6);
        Arrays.fill(cert7, (byte) 7);

        VisibilityConfig config1 = new VisibilityConfig.Builder()
                .addVisibleToPackage(new PackageIdentifier("com.example.test1", cert1))
                .setPubliclyVisibleTargetPackage(
                        new PackageIdentifier("com.example.test2", cert2))
                .addVisibleToPermissions(ImmutableSet.of(1, 2))
                .build();
        VisibilityConfig config2 = new VisibilityConfig.Builder()
                .addVisibleToPackage(new PackageIdentifier("com.example.test3", cert3))
                .addVisibleToPermissions(ImmutableSet.of(3, 4))
                .build();
        VisibilityConfig config3 = new VisibilityConfig.Builder()
                .addVisibleToPackage(new PackageIdentifier("com.example.test4", cert4))
                .setPubliclyVisibleTargetPackage(
                        new PackageIdentifier("com.example.test5", cert5))
                .build();
        SetSchemaRequest setSchemaRequest = new SetSchemaRequest.Builder()
                .addSchemas(new AppSearchSchema.Builder("someSchema").build())
                .setSchemaTypeDisplayedBySystem("someSchema", /*displayed=*/ true)
                .setSchemaTypeVisibilityForPackage("someSchema", /*visible=*/ true,
                        new PackageIdentifier("com.example.test6", cert6))
                .addRequiredPermissionsForSchemaTypeVisibility("someSchema",
                        ImmutableSet.of(1, 2))
                .setPubliclyVisibleSchema("someSchema",
                        new PackageIdentifier("com.example.test7", cert7))
                .addSchemaTypeVisibleToConfig("someSchema", config1)
                .addSchemaTypeVisibleToConfig("someSchema", config2)
                .addSchemaTypeVisibleToConfig("someSchema", config3)
                .build();

        // Convert the SetSchemaRequest to a list of VisibilityConfig
        List<VisibilityConfig> visibilityConfigs =
                VisibilityConfig.toVisibilityConfigs(setSchemaRequest);
        VisibilityConfig visibilityConfig = visibilityConfigs.get(0);

        GenericDocument visibilityDoc =
                VisibilityToDocumentConverter.createVisibilityDocument(visibilityConfig);
        GenericDocument androidVOverlay =
                VisibilityToDocumentConverter.createAndroidVOverlay(visibilityConfig);

        VisibilityConfig rebuild = VisibilityToDocumentConverter.createVisibilityConfig(
                visibilityDoc, androidVOverlay);

        assertEquals(rebuild, visibilityConfig);
    }
}
