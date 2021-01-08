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

package androidx.appsearch.localstorage;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;

import androidx.appsearch.app.AppSearchSchema;
import androidx.appsearch.app.GenericDocument;
import androidx.appsearch.app.PackageIdentifier;
import androidx.appsearch.app.SearchResult;
import androidx.appsearch.app.SearchResultPage;
import androidx.appsearch.app.SearchSpec;
import androidx.appsearch.exceptions.AppSearchException;
import androidx.appsearch.localstorage.converter.GenericDocumentToProtoConverter;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.icing.proto.DocumentProto;
import com.google.android.icing.proto.GetOptimizeInfoResultProto;
import com.google.android.icing.proto.PropertyConfigProto;
import com.google.android.icing.proto.PropertyProto;
import com.google.android.icing.proto.SchemaProto;
import com.google.android.icing.proto.SchemaTypeConfigProto;
import com.google.android.icing.proto.SearchResultProto;
import com.google.android.icing.proto.SearchSpecProto;
import com.google.android.icing.proto.StringIndexingConfig;
import com.google.android.icing.proto.TermMatchType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppSearchImplTest {
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();
    private MockPackageManager mMockPackageManager = new MockPackageManager();
    private Context mContext;
    private AppSearchImpl mAppSearchImpl;
    private int mGlobalQuerierUid;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        mContext = new ContextWrapper(context) {
            @Override
            public PackageManager getPackageManager() {
                return mMockPackageManager.getMockPackageManager();
            }
        };

        // Give ourselves global query permissions
        mAppSearchImpl = AppSearchImpl.create(mTemporaryFolder.newFolder(),
                mContext, /*globalQuerierPackage=*/ mContext.getPackageName());
        mGlobalQuerierUid = mContext.getPackageManager().getPackageUid(
                mContext.getPackageName(), /*flags=*/ 0);
    }

    //TODO(b/175430168) add test to verify reset is working properly.

    /**
     * Ensure that we can rewrite an incoming schema type by adding the database as a prefix. While
     * also keeping any other existing schema types that may already be part of Icing's persisted
     * schema.
     */
    @Test
    public void testRewriteSchema_addType() throws Exception {
        SchemaProto.Builder existingSchemaBuilder = SchemaProto.newBuilder()
                .addTypes(SchemaTypeConfigProto.newBuilder()
                        .setSchemaType("package$existingDatabase/Foo").build());

        // Create a copy so we can modify it.
        List<SchemaTypeConfigProto> existingTypes =
                new ArrayList<>(existingSchemaBuilder.getTypesList());

        SchemaProto newSchema = SchemaProto.newBuilder()
                .addTypes(SchemaTypeConfigProto.newBuilder()
                        .setSchemaType("Foo").build())
                .addTypes(SchemaTypeConfigProto.newBuilder()
                        .setSchemaType("TestType")
                        .addProperties(PropertyConfigProto.newBuilder()
                                .setPropertyName("subject")
                                .setDataType(PropertyConfigProto.DataType.Code.STRING)
                                .setCardinality(PropertyConfigProto.Cardinality.Code.OPTIONAL)
                                .setStringIndexingConfig(StringIndexingConfig.newBuilder()
                                        .setTokenizerType(
                                                StringIndexingConfig.TokenizerType.Code.PLAIN)
                                        .setTermMatchType(TermMatchType.Code.PREFIX)
                                        .build()
                                ).build()
                        ).addProperties(PropertyConfigProto.newBuilder()
                                .setPropertyName("link")
                                .setDataType(PropertyConfigProto.DataType.Code.DOCUMENT)
                                .setCardinality(PropertyConfigProto.Cardinality.Code.OPTIONAL)
                                .setSchemaType("RefType")
                                .build()
                        ).build()
                ).build();

        AppSearchImpl.RewrittenSchemaResults rewrittenSchemaResults = mAppSearchImpl.rewriteSchema(
                AppSearchImpl.createPrefix("package", "newDatabase"), existingSchemaBuilder,
                newSchema);

        // We rewrote all the new types that were added. And nothing was removed.
        assertThat(rewrittenSchemaResults.mRewrittenPrefixedTypes)
                .containsExactly("package$newDatabase/Foo", "package$newDatabase/TestType");
        assertThat(rewrittenSchemaResults.mDeletedPrefixedTypes).isEmpty();

        SchemaProto expectedSchema = SchemaProto.newBuilder()
                .addTypes(SchemaTypeConfigProto.newBuilder()
                        .setSchemaType("package$newDatabase/Foo").build())
                .addTypes(SchemaTypeConfigProto.newBuilder()
                        .setSchemaType("package$newDatabase/TestType")
                        .addProperties(PropertyConfigProto.newBuilder()
                                .setPropertyName("subject")
                                .setDataType(PropertyConfigProto.DataType.Code.STRING)
                                .setCardinality(PropertyConfigProto.Cardinality.Code.OPTIONAL)
                                .setStringIndexingConfig(StringIndexingConfig.newBuilder()
                                        .setTokenizerType(
                                                StringIndexingConfig.TokenizerType.Code.PLAIN)
                                        .setTermMatchType(TermMatchType.Code.PREFIX)
                                        .build()
                                ).build()
                        ).addProperties(PropertyConfigProto.newBuilder()
                                .setPropertyName("link")
                                .setDataType(PropertyConfigProto.DataType.Code.DOCUMENT)
                                .setCardinality(PropertyConfigProto.Cardinality.Code.OPTIONAL)
                                .setSchemaType("package$newDatabase/RefType")
                                .build()
                        ).build())
                .build();

        existingTypes.addAll(expectedSchema.getTypesList());
        assertThat(existingSchemaBuilder.getTypesList()).containsExactlyElementsIn(existingTypes);
    }

    /**
     * Ensure that we track all types that were rewritten in the input schema. Even if they were
     * not technically "added" to the existing schema.
     */
    @Test
    public void testRewriteSchema_rewriteType() throws Exception {
        SchemaProto.Builder existingSchemaBuilder = SchemaProto.newBuilder()
                .addTypes(SchemaTypeConfigProto.newBuilder()
                        .setSchemaType("package$existingDatabase/Foo").build());

        SchemaProto newSchema = SchemaProto.newBuilder()
                .addTypes(SchemaTypeConfigProto.newBuilder()
                        .setSchemaType("Foo").build())
                .build();

        AppSearchImpl.RewrittenSchemaResults rewrittenSchemaResults = mAppSearchImpl.rewriteSchema(
                AppSearchImpl.createPrefix("package", "existingDatabase"), existingSchemaBuilder,
                newSchema);

        // Nothing was removed, but the method did rewrite the type name.
        assertThat(rewrittenSchemaResults.mRewrittenPrefixedTypes)
                .containsExactly("package$existingDatabase/Foo");
        assertThat(rewrittenSchemaResults.mDeletedPrefixedTypes).isEmpty();

        // Same schema since nothing was added.
        SchemaProto expectedSchema = existingSchemaBuilder.build();
        assertThat(existingSchemaBuilder.getTypesList())
                .containsExactlyElementsIn(expectedSchema.getTypesList());
    }

    /**
     * Ensure that we track which types from the existing schema are deleted when a new schema is
     * set.
     */
    @Test
    public void testRewriteSchema_deleteType() throws Exception {
        SchemaProto.Builder existingSchemaBuilder = SchemaProto.newBuilder()
                .addTypes(SchemaTypeConfigProto.newBuilder()
                        .setSchemaType("package$existingDatabase/Foo").build());

        SchemaProto newSchema = SchemaProto.newBuilder()
                .addTypes(SchemaTypeConfigProto.newBuilder()
                        .setSchemaType("Bar").build())
                .build();

        AppSearchImpl.RewrittenSchemaResults rewrittenSchemaResults = mAppSearchImpl.rewriteSchema(
                AppSearchImpl.createPrefix("package", "existingDatabase"), existingSchemaBuilder,
                newSchema);

        // Bar type was rewritten, but Foo ended up being deleted since it wasn't included in the
        // new schema.
        assertThat(rewrittenSchemaResults.mRewrittenPrefixedTypes)
                .containsExactly("package$existingDatabase/Bar");
        assertThat(rewrittenSchemaResults.mDeletedPrefixedTypes)
                .containsExactly("package$existingDatabase/Foo");

        // Same schema since nothing was added.
        SchemaProto expectedSchema = SchemaProto.newBuilder()
                .addTypes(SchemaTypeConfigProto.newBuilder()
                        .setSchemaType("package$existingDatabase/Bar").build())
                .build();

        assertThat(existingSchemaBuilder.getTypesList())
                .containsExactlyElementsIn(expectedSchema.getTypesList());
    }

    @Test
    public void testAddDocumentTypePrefix() {
        DocumentProto insideDocument = DocumentProto.newBuilder()
                .setUri("inside-uri")
                .setSchema("type")
                .setNamespace("namespace")
                .build();
        DocumentProto documentProto = DocumentProto.newBuilder()
                .setUri("uri")
                .setSchema("type")
                .setNamespace("namespace")
                .addProperties(PropertyProto.newBuilder().addDocumentValues(insideDocument))
                .build();

        DocumentProto expectedInsideDocument = DocumentProto.newBuilder()
                .setUri("inside-uri")
                .setSchema("package$databaseName/type")
                .setNamespace("package$databaseName/namespace")
                .build();
        DocumentProto expectedDocumentProto = DocumentProto.newBuilder()
                .setUri("uri")
                .setSchema("package$databaseName/type")
                .setNamespace("package$databaseName/namespace")
                .addProperties(PropertyProto.newBuilder().addDocumentValues(expectedInsideDocument))
                .build();

        DocumentProto.Builder actualDocument = documentProto.toBuilder();
        mAppSearchImpl.addPrefixToDocument(actualDocument, AppSearchImpl.createPrefix("package",
                "databaseName"));
        assertThat(actualDocument.build()).isEqualTo(expectedDocumentProto);
    }

    @Test
    public void testRemoveDocumentTypePrefixes() throws Exception {
        DocumentProto insideDocument = DocumentProto.newBuilder()
                .setUri("inside-uri")
                .setSchema("package$databaseName/type")
                .setNamespace("package$databaseName/namespace")
                .build();
        DocumentProto documentProto = DocumentProto.newBuilder()
                .setUri("uri")
                .setSchema("package$databaseName/type")
                .setNamespace("package$databaseName/namespace")
                .addProperties(PropertyProto.newBuilder().addDocumentValues(insideDocument))
                .build();

        DocumentProto expectedInsideDocument = DocumentProto.newBuilder()
                .setUri("inside-uri")
                .setSchema("type")
                .setNamespace("namespace")
                .build();

        DocumentProto expectedDocumentProto = DocumentProto.newBuilder()
                .setUri("uri")
                .setSchema("type")
                .setNamespace("namespace")
                .addProperties(PropertyProto.newBuilder().addDocumentValues(expectedInsideDocument))
                .build();

        DocumentProto.Builder actualDocument = documentProto.toBuilder();
        assertThat(mAppSearchImpl.removePrefixesFromDocument(actualDocument)).isEqualTo(
                "package$databaseName/");
        assertThat(actualDocument.build()).isEqualTo(expectedDocumentProto);
    }

    @Test
    public void testRemoveDatabasesFromDocumentThrowsException() throws Exception {
        // Set two different database names in the document, which should never happen
        DocumentProto documentProto = DocumentProto.newBuilder()
                .setUri("uri")
                .setSchema("prefix1/type")
                .setNamespace("prefix2/namespace")
                .build();

        DocumentProto.Builder actualDocument = documentProto.toBuilder();
        AppSearchException e = assertThrows(AppSearchException.class, () ->
                mAppSearchImpl.removePrefixesFromDocument(actualDocument));
        assertThat(e).hasMessageThat().contains("Found unexpected multiple prefix names");
    }

    @Test
    public void testNestedRemoveDatabasesFromDocumentThrowsException() throws Exception {
        // Set two different database names in the outer and inner document, which should never
        // happen.
        DocumentProto insideDocument = DocumentProto.newBuilder()
                .setUri("inside-uri")
                .setSchema("prefix1/type")
                .setNamespace("prefix1/namespace")
                .build();
        DocumentProto documentProto = DocumentProto.newBuilder()
                .setUri("uri")
                .setSchema("prefix2/type")
                .setNamespace("prefix2/namespace")
                .addProperties(PropertyProto.newBuilder().addDocumentValues(insideDocument))
                .build();

        DocumentProto.Builder actualDocument = documentProto.toBuilder();
        AppSearchException e = assertThrows(AppSearchException.class, () ->
                mAppSearchImpl.removePrefixesFromDocument(actualDocument));
        assertThat(e).hasMessageThat().contains("Found unexpected multiple prefix names");
    }

    @Test
    public void testOptimize() throws Exception {
        // Insert schema
        List<AppSearchSchema> schemas =
                Collections.singletonList(new AppSearchSchema.Builder("type").build());
        mAppSearchImpl.setSchema("package", "database", schemas, /*schemasNotPlatformSurfaceable=*/
                Collections.emptyList(), /*schemasPackageAccessible=*/ Collections.emptyMap(),
                /*forceOverride=*/ false);

        // Insert enough documents.
        for (int i = 0; i < AppSearchImpl.OPTIMIZE_THRESHOLD_DOC_COUNT
                + AppSearchImpl.CHECK_OPTIMIZE_INTERVAL; i++) {
            GenericDocument document =
                    new GenericDocument.Builder<>("uri" + i, "type").setNamespace(
                            "namespace").build();
            mAppSearchImpl.putDocument("package", "database", document);
        }

        // Check optimize() will release 0 docs since there is no deletion.
        GetOptimizeInfoResultProto optimizeInfo = mAppSearchImpl.getOptimizeInfoResultLocked();
        assertThat(optimizeInfo.getOptimizableDocs()).isEqualTo(0);

        // delete 999 documents , we will reach the threshold to trigger optimize() in next
        // deletion.
        for (int i = 0; i < AppSearchImpl.OPTIMIZE_THRESHOLD_DOC_COUNT - 1; i++) {
            mAppSearchImpl.remove("package", "database", "namespace", "uri" + i);
        }

        // optimize() still not be triggered since we are in the interval to call getOptimizeInfo()
        optimizeInfo = mAppSearchImpl.getOptimizeInfoResultLocked();
        assertThat(optimizeInfo.getOptimizableDocs())
                .isEqualTo(AppSearchImpl.OPTIMIZE_THRESHOLD_DOC_COUNT - 1);

        // Keep delete docs, will reach the interval this time and trigger optimize().
        for (int i = AppSearchImpl.OPTIMIZE_THRESHOLD_DOC_COUNT;
                i < AppSearchImpl.OPTIMIZE_THRESHOLD_DOC_COUNT
                        + AppSearchImpl.CHECK_OPTIMIZE_INTERVAL; i++) {
            mAppSearchImpl.remove("package", "database", "namespace", "uri" + i);
        }

        // Verify optimize() is triggered
        optimizeInfo = mAppSearchImpl.getOptimizeInfoResultLocked();
        assertThat(optimizeInfo.getOptimizableDocs())
                .isLessThan(AppSearchImpl.CHECK_OPTIMIZE_INTERVAL);
    }

    @Test
    public void testRewriteSearchSpec_oneInstance() throws Exception {
        SearchSpecProto.Builder searchSpecProto =
                SearchSpecProto.newBuilder().setQuery("");

        // Insert schema
        List<AppSearchSchema> schemas =
                Collections.singletonList(new AppSearchSchema.Builder("type").build());
        mAppSearchImpl.setSchema("package", "database", schemas, /*schemasNotPlatformSurfaceable=*/
                Collections.emptyList(), /*schemasPackageAccessible=*/
                Collections.emptyMap(), /*forceOverride=*/ false);

        // Insert document
        GenericDocument document = new GenericDocument.Builder<>("uri", "type").setNamespace(
                "namespace").build();
        mAppSearchImpl.putDocument("package", "database", document);

        // Rewrite SearchSpec
        mAppSearchImpl.rewriteSearchSpecForPrefixesLocked(searchSpecProto,
                Collections.singleton(AppSearchImpl.createPrefix("package", "database")),
                ImmutableSet.of("package$database/type"));
        assertThat(searchSpecProto.getSchemaTypeFiltersList()).containsExactly(
                "package$database/type");
        assertThat(searchSpecProto.getNamespaceFiltersList()).containsExactly(
                "package$database/namespace");
    }

    @Test
    public void testRewriteSearchSpec_twoInstances() throws Exception {
        SearchSpecProto.Builder searchSpecProto =
                SearchSpecProto.newBuilder().setQuery("");

        // Insert schema
        List<AppSearchSchema> schemas = ImmutableList.of(
                new AppSearchSchema.Builder("typeA").build(),
                new AppSearchSchema.Builder("typeB").build());
        mAppSearchImpl.setSchema("package", "database1", schemas, /*schemasNotPlatformSurfaceable=*/
                Collections.emptyList(), /*schemasPackageAccessible=*/
                Collections.emptyMap(), /*forceOverride=*/ false);
        mAppSearchImpl.setSchema("package", "database2", schemas, /*schemasNotPlatformSurfaceable=*/
                Collections.emptyList(), /*schemasPackageAccessible=*/
                Collections.emptyMap(), /*forceOverride=*/ false);

        // Insert documents
        GenericDocument document1 = new GenericDocument.Builder<>("uri", "typeA").setNamespace(
                "namespace").build();
        mAppSearchImpl.putDocument("package", "database1", document1);

        GenericDocument document2 = new GenericDocument.Builder<>("uri", "typeB").setNamespace(
                "namespace").build();
        mAppSearchImpl.putDocument("package", "database2", document2);

        // Rewrite SearchSpec
        mAppSearchImpl.rewriteSearchSpecForPrefixesLocked(searchSpecProto,
                ImmutableSet.of(AppSearchImpl.createPrefix("package", "database1"),
                        AppSearchImpl.createPrefix("package", "database2")), ImmutableSet.of(
                        "package$database1/typeA", "package$database1/typeB",
                        "package$database2/typeA", "package$database2/typeB"));
        assertThat(searchSpecProto.getSchemaTypeFiltersList()).containsExactly(
                "package$database1/typeA", "package$database1/typeB", "package$database2/typeA",
                "package$database2/typeB");
        assertThat(searchSpecProto.getNamespaceFiltersList()).containsExactly(
                "package$database1/namespace", "package$database2/namespace");
    }

    @Test
    public void testRewriteSearchSpec_ignoresSearchSpecSchemaFilters() throws Exception {
        SearchSpecProto.Builder searchSpecProto =
                SearchSpecProto.newBuilder().setQuery("").addSchemaTypeFilters("type");

        // Insert schema
        List<AppSearchSchema> schemas =
                Collections.singletonList(new AppSearchSchema.Builder("type").build());
        mAppSearchImpl.setSchema("package", "database", schemas, /*schemasNotPlatformSurfaceable=*/
                Collections.emptyList(), /*schemasPackageAccessible=*/
                Collections.emptyMap(), /*forceOverride=*/ false);

        // Insert document
        GenericDocument document = new GenericDocument.Builder<>("uri", "type").setNamespace(
                "namespace").build();
        mAppSearchImpl.putDocument("package", "database", document);

        // If 'allowedPrefixedSchemas' is empty, this returns false since there's nothing to
        // search over. Despite the searchSpecProto having schema type filters.
        assertThat(mAppSearchImpl.rewriteSearchSpecForPrefixesLocked(searchSpecProto,
                Collections.singleton(AppSearchImpl.createPrefix("package", "database")),
                /*allowedPrefixedSchemas=*/ Collections.emptySet())).isFalse();
    }

    @Test
    public void testQueryEmptyDatabase() throws Exception {
        SearchSpec searchSpec =
                new SearchSpec.Builder().setTermMatch(TermMatchType.Code.PREFIX_VALUE).build();
        SearchResultPage searchResultPage = mAppSearchImpl.query("package", "EmptyDatabase", "",
                searchSpec);
        assertThat(searchResultPage.getResults()).isEmpty();
    }

    /**
     * TODO(b/169883602): This should be an integration test at the cts-level. This is a
     * short-term test until we have official support for multiple-apps indexing at once.
     */
    @Test
    public void testQueryWithMultiplePackages_noPackageFilters() throws Exception {
        // Insert package1 schema
        List<AppSearchSchema> schema1 =
                ImmutableList.of(new AppSearchSchema.Builder("schema1").build());
        mAppSearchImpl.setSchema("package1", "database1", schema1,
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ Collections.emptyMap(),
                /*forceOverride=*/ false);

        // Insert package2 schema
        List<AppSearchSchema> schema2 =
                ImmutableList.of(new AppSearchSchema.Builder("schema2").build());
        mAppSearchImpl.setSchema("package2", "database2", schema2,
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ Collections.emptyMap(),
                /*forceOverride=*/ false);

        // Insert package1 document
        GenericDocument document = new GenericDocument.Builder<>("uri", "schema1")
                .setNamespace("namespace").build();
        mAppSearchImpl.putDocument("package1", "database1", document);

        // No query filters specified, package2 shouldn't be able to query for package1's documents.
        SearchSpec searchSpec =
                new SearchSpec.Builder().setTermMatch(TermMatchType.Code.PREFIX_VALUE).build();
        SearchResultPage searchResultPage = mAppSearchImpl.query("package2", "database2", "",
                searchSpec);
        assertThat(searchResultPage.getResults()).isEmpty();

        // Insert package2 document
        document = new GenericDocument.Builder<>("uri", "schema2")
                .setNamespace("namespace").build();
        mAppSearchImpl.putDocument("package2", "database2", document);

        // No query filters specified. package2 should only get its own documents back.
        searchResultPage = mAppSearchImpl.query("package2", "database2", "", searchSpec);
        assertThat(searchResultPage.getResults()).hasSize(1);
        assertThat(searchResultPage.getResults().get(0).getDocument()).isEqualTo(document);
    }

    /**
     * TODO(b/169883602): This should be an integration test at the cts-level. This is a
     * short-term test until we have official support for multiple-apps indexing at once.
     */
    @Test
    public void testQueryWithMultiplePackages_withPackageFilters() throws Exception {
        // Insert package1 schema
        List<AppSearchSchema> schema1 =
                ImmutableList.of(new AppSearchSchema.Builder("schema1").build());
        mAppSearchImpl.setSchema("package1", "database1", schema1,
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ Collections.emptyMap(),
                /*forceOverride=*/ false);

        // Insert package2 schema
        List<AppSearchSchema> schema2 =
                ImmutableList.of(new AppSearchSchema.Builder("schema2").build());
        mAppSearchImpl.setSchema("package2", "database2", schema2,
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ Collections.emptyMap(),
                /*forceOverride=*/ false);

        // Insert package1 document
        GenericDocument document = new GenericDocument.Builder<>("uri", "schema1")
                .setNamespace("namespace").build();
        mAppSearchImpl.putDocument("package1", "database1", document);

        // "package1" filter specified, but package2 shouldn't be able to query for package1's
        // documents.
        SearchSpec searchSpec = new SearchSpec.Builder()
                .setTermMatch(TermMatchType.Code.PREFIX_VALUE)
                .addFilterPackageNames("package1")
                .build();
        SearchResultPage searchResultPage = mAppSearchImpl.query("package2", "database2", "",
                searchSpec);
        assertThat(searchResultPage.getResults()).isEmpty();

        // Insert package2 document
        document = new GenericDocument.Builder<>("uri", "schema2")
                .setNamespace("namespace").build();
        mAppSearchImpl.putDocument("package2", "database2", document);

        // "package2" filter specified, package2 should only get its own documents back.
        searchSpec = new SearchSpec.Builder()
                .setTermMatch(TermMatchType.Code.PREFIX_VALUE)
                .addFilterPackageNames("package2")
                .build();
        searchResultPage = mAppSearchImpl.query("package2", "database2", "", searchSpec);
        assertThat(searchResultPage.getResults()).hasSize(1);
        assertThat(searchResultPage.getResults().get(0).getDocument()).isEqualTo(document);
    }

    @Test
    public void testGlobalQueryEmptyDatabase() throws Exception {
        SearchSpec searchSpec =
                new SearchSpec.Builder().setTermMatch(TermMatchType.Code.PREFIX_VALUE).build();
        SearchResultPage searchResultPage = mAppSearchImpl.globalQuery("", searchSpec,
                mContext.getPackageName(), /*callerUid=*/ 0);
        assertThat(searchResultPage.getResults()).isEmpty();
    }

    /**
     * TODO(b/169883602): This should be an integration test at the cts-level. This is a
     * short-term test until we have official support for multiple-apps indexing at once.
     */
    @Test
    public void testGlobalQueryWithMultiplePackages_noPackageFilters() throws Exception {
        // Insert package1 schema
        List<AppSearchSchema> schema1 =
                ImmutableList.of(new AppSearchSchema.Builder("schema1").build());
        mAppSearchImpl.setSchema("package1", "database1", schema1,
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ Collections.emptyMap(),
                /*forceOverride=*/ false);

        // Insert package2 schema
        List<AppSearchSchema> schema2 =
                ImmutableList.of(new AppSearchSchema.Builder("schema2").build());
        mAppSearchImpl.setSchema("package2", "database2", schema2,
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ Collections.emptyMap(),
                /*forceOverride=*/ false);

        // Insert package1 document
        GenericDocument document1 = new GenericDocument.Builder<>("uri", "schema1")
                .setNamespace("namespace").build();
        mAppSearchImpl.putDocument("package1", "database1", document1);

        // Insert package2 document
        GenericDocument document2 = new GenericDocument.Builder<>("uri", "schema2")
                .setNamespace("namespace").build();
        mAppSearchImpl.putDocument("package2", "database2", document2);

        // No query filters specified, global query can retrieve all documents.
        SearchSpec searchSpec =
                new SearchSpec.Builder().setTermMatch(TermMatchType.Code.PREFIX_VALUE).build();
        SearchResultPage searchResultPage = mAppSearchImpl.globalQuery("", searchSpec,
                mContext.getPackageName(), mGlobalQuerierUid);
        assertThat(searchResultPage.getResults()).hasSize(2);

        // Document2 will be first since it got indexed later and has a "better", aka more recent
        // score.
        assertThat(searchResultPage.getResults().get(0).getDocument()).isEqualTo(document2);
        assertThat(searchResultPage.getResults().get(1).getDocument()).isEqualTo(document1);
    }

    /**
     * TODO(b/169883602): This should be an integration test at the cts-level. This is a
     * short-term test until we have official support for multiple-apps indexing at once.
     */
    @Test
    public void testGlobalQueryWithMultiplePackages_withPackageFilters() throws Exception {
        // Insert package1 schema
        List<AppSearchSchema> schema1 =
                ImmutableList.of(new AppSearchSchema.Builder("schema1").build());
        mAppSearchImpl.setSchema("package1", "database1", schema1,
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ Collections.emptyMap(),
                /*forceOverride=*/ false);

        // Insert package2 schema
        List<AppSearchSchema> schema2 =
                ImmutableList.of(new AppSearchSchema.Builder("schema2").build());
        mAppSearchImpl.setSchema("package2", "database2", schema2,
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ Collections.emptyMap(),
                /*forceOverride=*/ false);

        // Insert package1 document
        GenericDocument document1 = new GenericDocument.Builder<>("uri", "schema1")
                .setNamespace("namespace").build();
        mAppSearchImpl.putDocument("package1", "database1", document1);

        // Insert package2 document
        GenericDocument document2 = new GenericDocument.Builder<>("uri", "schema2")
                .setNamespace("namespace").build();
        mAppSearchImpl.putDocument("package2", "database2", document2);

        // "package1" filter specified
        SearchSpec searchSpec = new SearchSpec.Builder()
                .setTermMatch(TermMatchType.Code.PREFIX_VALUE)
                .addFilterPackageNames("package1")
                .build();
        SearchResultPage searchResultPage = mAppSearchImpl.globalQuery("", searchSpec,
                mContext.getPackageName(), mGlobalQuerierUid);
        assertThat(searchResultPage.getResults()).hasSize(1);
        assertThat(searchResultPage.getResults().get(0).getDocument()).isEqualTo(document1);

        // "package2" filter specified
        searchSpec = new SearchSpec.Builder()
                .setTermMatch(TermMatchType.Code.PREFIX_VALUE)
                .addFilterPackageNames("package2")
                .build();
        searchResultPage = mAppSearchImpl.globalQuery("", searchSpec, mContext.getPackageName(),
                mGlobalQuerierUid);
        assertThat(searchResultPage.getResults()).hasSize(1);
        assertThat(searchResultPage.getResults().get(0).getDocument()).isEqualTo(document2);
    }

    @Test
    public void testRemoveEmptyDatabase_noExceptionThrown() throws Exception {
        SearchSpec searchSpec =
                new SearchSpec.Builder().addSchemaType("FakeType").setTermMatch(
                        TermMatchType.Code.PREFIX_VALUE).build();
        mAppSearchImpl.removeByQuery("package", "EmptyDatabase",
                "", searchSpec);

        searchSpec =
                new SearchSpec.Builder().addNamespace("FakeNamespace").setTermMatch(
                        TermMatchType.Code.PREFIX_VALUE).build();
        mAppSearchImpl.removeByQuery("package", "EmptyDatabase",
                "", searchSpec);

        searchSpec = new SearchSpec.Builder().setTermMatch(TermMatchType.Code.PREFIX_VALUE).build();
        mAppSearchImpl.removeByQuery("package", "EmptyDatabase", "", searchSpec);
    }

    @Test
    public void testSetSchema() throws Exception {
        List<SchemaTypeConfigProto> existingSchemas =
                mAppSearchImpl.getSchemaProtoLocked().getTypesList();

        List<AppSearchSchema> schemas =
                Collections.singletonList(new AppSearchSchema.Builder("Email").build());
        // Set schema Email to AppSearch database1
        mAppSearchImpl.setSchema("package", "database1", schemas, /*schemasNotPlatformSurfaceable=*/
                Collections.emptyList(), /*schemasPackageAccessible=*/
                Collections.emptyMap(), /*forceOverride=*/ false);

        // Create expected schemaType proto.
        SchemaProto expectedProto = SchemaProto.newBuilder()
                .addTypes(
                        SchemaTypeConfigProto.newBuilder().setSchemaType("package$database1/Email"))
                .build();

        List<SchemaTypeConfigProto> expectedTypes = new ArrayList<>();
        expectedTypes.addAll(existingSchemas);
        expectedTypes.addAll(expectedProto.getTypesList());
        assertThat(mAppSearchImpl.getSchemaProtoLocked().getTypesList())
                .containsExactlyElementsIn(expectedTypes);
    }

    @Test
    public void testSetSchema_existingSchemaRetainsVisibilitySetting() throws Exception {
        // Values for a "foo" client
        String packageNameFoo = "packageFoo";
        byte[] sha256CertFoo = new byte[]{10};
        int uidFoo = 1;

        // Make sure foo package will pass package manager checks.
        mMockPackageManager.mockGetPackageUid(packageNameFoo, uidFoo);
        mMockPackageManager.mockAddSigningCertificate(packageNameFoo, sha256CertFoo);

        // Set schema1
        String prefix = AppSearchImpl.createPrefix("package", "database");
        mAppSearchImpl.setSchema("package", "database",
                Collections.singletonList(new AppSearchSchema.Builder("schema1").build()),
                /*schemasNotPlatformSurfaceable=*/ Collections.singletonList("schema1"),
                /*schemasPackageAccessible=*/ ImmutableMap.of("schema1",
                        ImmutableList.of(new PackageIdentifier(packageNameFoo, sha256CertFoo))),
                /*forceOverride=*/ false);

        // "schema1" is platform hidden now and package visible to package1
        assertThat(mAppSearchImpl.getVisibilityStoreLocked().isSchemaSearchableByCaller(
                prefix, prefix + "schema1", mGlobalQuerierUid)).isFalse();
        assertThat(mAppSearchImpl.getVisibilityStoreLocked().isSchemaSearchableByCaller(
                prefix, prefix + "schema1", uidFoo)).isTrue();

        // Add a new schema, and include the already-existing "schema1"
        mAppSearchImpl.setSchema(
                "package", "database",
                ImmutableList.of(
                        new AppSearchSchema.Builder("schema1").build(),
                        new AppSearchSchema.Builder("schema2").build()),
                /*schemasNotPlatformSurfaceable=*/ Collections.singletonList("schema1"),
                /*schemasPackageAccessible=*/ ImmutableMap.of(
                        "schema1",
                        ImmutableList.of(new PackageIdentifier(packageNameFoo, sha256CertFoo))),
                /*forceOverride=*/ false);

        // Check that "schema1" still has the same visibility settings
        assertThat(mAppSearchImpl.getVisibilityStoreLocked().isSchemaSearchableByCaller(
                prefix, prefix + "schema1", mGlobalQuerierUid)).isFalse();
        assertThat(mAppSearchImpl.getVisibilityStoreLocked().isSchemaSearchableByCaller(
                prefix, prefix + "schema1", uidFoo)).isTrue();

        // "schema2" has default visibility settings
        assertThat(mAppSearchImpl.getVisibilityStoreLocked().isSchemaSearchableByCaller(
                prefix, prefix + "schema2", mGlobalQuerierUid)).isTrue();
        assertThat(mAppSearchImpl.getVisibilityStoreLocked().isSchemaSearchableByCaller(
                prefix, prefix + "schema2", uidFoo)).isFalse();
    }

    @Test
    public void testRemoveSchema() throws Exception {
        List<SchemaTypeConfigProto> existingSchemas =
                mAppSearchImpl.getSchemaProtoLocked().getTypesList();

        List<AppSearchSchema> schemas = ImmutableList.of(
                new AppSearchSchema.Builder("Email").build(),
                new AppSearchSchema.Builder("Document").build());
        // Set schema Email and Document to AppSearch database1
        mAppSearchImpl.setSchema("package", "database1", schemas,
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ Collections.emptyMap(), /*forceOverride=*/ false);

        // Create expected schemaType proto.
        SchemaProto expectedProto = SchemaProto.newBuilder()
                .addTypes(
                        SchemaTypeConfigProto.newBuilder().setSchemaType("package$database1/Email"))
                .addTypes(SchemaTypeConfigProto.newBuilder().setSchemaType(
                        "package$database1/Document"))
                .build();

        // Check both schema Email and Document saved correctly.
        List<SchemaTypeConfigProto> expectedTypes = new ArrayList<>();
        expectedTypes.addAll(existingSchemas);
        expectedTypes.addAll(expectedProto.getTypesList());
        assertThat(mAppSearchImpl.getSchemaProtoLocked().getTypesList())
                .containsExactlyElementsIn(expectedTypes);

        final List<AppSearchSchema> finalSchemas = Collections.singletonList(
                new AppSearchSchema.Builder("Email").build());
        // Check the incompatible error has been thrown.
        AppSearchException e = assertThrows(AppSearchException.class, () ->
                mAppSearchImpl.setSchema("package", "database1", finalSchemas,
                        /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                        /*schemasPackageAccessible=*/ Collections.emptyMap(),
                        /*forceOverride=*/ false));
        assertThat(e).hasMessageThat().contains("Schema is incompatible");
        assertThat(e).hasMessageThat().contains("Deleted types: [package$database1/Document]");

        // ForceOverride to delete.
        mAppSearchImpl.setSchema("package", "database1", finalSchemas,
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ Collections.emptyMap(), /*forceOverride=*/ true);

        // Check Document schema is removed.
        expectedProto = SchemaProto.newBuilder()
                .addTypes(
                        SchemaTypeConfigProto.newBuilder().setSchemaType("package$database1/Email"))
                .build();

        expectedTypes = new ArrayList<>();
        expectedTypes.addAll(existingSchemas);
        expectedTypes.addAll(expectedProto.getTypesList());
        assertThat(mAppSearchImpl.getSchemaProtoLocked().getTypesList())
                .containsExactlyElementsIn(expectedTypes);
    }

    @Test
    public void testRemoveSchema_differentDataBase() throws Exception {
        List<SchemaTypeConfigProto> existingSchemas =
                mAppSearchImpl.getSchemaProtoLocked().getTypesList();

        // Create schemas
        List<AppSearchSchema> schemas = ImmutableList.of(
                new AppSearchSchema.Builder("Email").build(),
                new AppSearchSchema.Builder("Document").build());

        // Set schema Email and Document to AppSearch database1 and 2
        mAppSearchImpl.setSchema("package", "database1", schemas,
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ Collections.emptyMap(), /*forceOverride=*/ false);
        mAppSearchImpl.setSchema("package", "database2", schemas,
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ Collections.emptyMap(), /*forceOverride=*/ false);

        // Create expected schemaType proto.
        SchemaProto expectedProto = SchemaProto.newBuilder()
                .addTypes(
                        SchemaTypeConfigProto.newBuilder().setSchemaType("package$database1/Email"))
                .addTypes(SchemaTypeConfigProto.newBuilder().setSchemaType(
                        "package$database1/Document"))
                .addTypes(
                        SchemaTypeConfigProto.newBuilder().setSchemaType("package$database2/Email"))
                .addTypes(SchemaTypeConfigProto.newBuilder().setSchemaType(
                        "package$database2/Document"))
                .build();

        // Check Email and Document is saved in database 1 and 2 correctly.
        List<SchemaTypeConfigProto> expectedTypes = new ArrayList<>();
        expectedTypes.addAll(existingSchemas);
        expectedTypes.addAll(expectedProto.getTypesList());
        assertThat(mAppSearchImpl.getSchemaProtoLocked().getTypesList())
                .containsExactlyElementsIn(expectedTypes);

        // Save only Email to database1 this time.
        schemas = Collections.singletonList(new AppSearchSchema.Builder("Email").build());
        mAppSearchImpl.setSchema("package", "database1", schemas,
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ Collections.emptyMap(), /*forceOverride=*/ true);

        // Create expected schemaType list, database 1 should only contain Email but database 2
        // remains in same.
        expectedProto = SchemaProto.newBuilder()
                .addTypes(
                        SchemaTypeConfigProto.newBuilder().setSchemaType("package$database1/Email"))
                .addTypes(
                        SchemaTypeConfigProto.newBuilder().setSchemaType("package$database2/Email"))
                .addTypes(SchemaTypeConfigProto.newBuilder().setSchemaType(
                        "package$database2/Document"))
                .build();

        // Check nothing changed in database2.
        expectedTypes = new ArrayList<>();
        expectedTypes.addAll(existingSchemas);
        expectedTypes.addAll(expectedProto.getTypesList());
        assertThat(mAppSearchImpl.getSchemaProtoLocked().getTypesList())
                .containsExactlyElementsIn(expectedTypes);
    }


    @Test
    public void testRemoveSchema_removedFromVisibilityStore() throws Exception {
        // Values for a "foo" client
        String packageNameFoo = "packageFoo";
        byte[] sha256CertFoo = new byte[]{10};
        int uidFoo = 1;

        // Make sure foo package will pass package manager checks.
        mMockPackageManager.mockGetPackageUid(packageNameFoo, uidFoo);
        mMockPackageManager.mockAddSigningCertificate(packageNameFoo, sha256CertFoo);

        String prefix = AppSearchImpl.createPrefix("package", "database");
        mAppSearchImpl.setSchema("package", "database",
                Collections.singletonList(new AppSearchSchema.Builder("schema1").build()),
                /*schemasNotPlatformSurfaceable=*/ Collections.singletonList("schema1"),
                /*schemasPackageAccessible=*/ ImmutableMap.of("schema1",
                        ImmutableList.of(new PackageIdentifier(packageNameFoo, sha256CertFoo))),
                /*forceOverride=*/ false);

        // "schema1" is platform hidden now and package accessible
        assertThat(mAppSearchImpl.getVisibilityStoreLocked().isSchemaSearchableByCaller(
                prefix, prefix + "schema1", mGlobalQuerierUid)).isFalse();
        assertThat(mAppSearchImpl.getVisibilityStoreLocked().isSchemaSearchableByCaller(
                prefix, prefix + "schema1", uidFoo)).isTrue();

        // Remove "schema1" by force overriding
        mAppSearchImpl.setSchema("package", "database", /*schemas=*/ Collections.emptyList(),
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ Collections.emptyMap(), /*forceOverride=*/ true);

        // Check that "schema1" is no longer considered platform hidden or package accessible
        assertThat(
                mAppSearchImpl.getVisibilityStoreLocked().isSchemaSearchableByCaller(
                        prefix, prefix + "schema1", mGlobalQuerierUid)).isTrue();
        assertThat(mAppSearchImpl.getVisibilityStoreLocked().isSchemaSearchableByCaller(
                prefix, prefix + "schema1", uidFoo)).isFalse();

        // Add "schema1" back, it gets default visibility settings which means it's not platform
        // hidden and not package accessible
        mAppSearchImpl.setSchema("package", "database",
                Collections.singletonList(new AppSearchSchema.Builder("schema1").build()),
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ Collections.emptyMap(), /*forceOverride=*/ false);
        assertThat(
                mAppSearchImpl.getVisibilityStoreLocked().isSchemaSearchableByCaller(
                        prefix, prefix + "schema1", mGlobalQuerierUid)).isTrue();
        assertThat(mAppSearchImpl.getVisibilityStoreLocked().isSchemaSearchableByCaller(
                prefix, prefix + "schema1", uidFoo)).isFalse();
    }

    @Test
    public void testSetSchema_defaultPlatformVisible() throws Exception {
        String prefix = AppSearchImpl.createPrefix("package", "database");
        mAppSearchImpl.setSchema("package", "database",
                Collections.singletonList(new AppSearchSchema.Builder("Schema").build()),
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ Collections.emptyMap(), /*forceOverride=*/ false);
        assertThat(
                mAppSearchImpl.getVisibilityStoreLocked().isSchemaSearchableByCaller(
                        prefix, prefix + "Schema", mGlobalQuerierUid)).isTrue();
    }

    @Test
    public void testSetSchema_platformHidden() throws Exception {
        String prefix = AppSearchImpl.createPrefix("package", "database");
        mAppSearchImpl.setSchema("package", "database",
                Collections.singletonList(new AppSearchSchema.Builder("Schema").build()),
                /*schemasNotPlatformSurfaceable=*/ Collections.singletonList("Schema"),
                /*schemasPackageAccessible=*/ Collections.emptyMap(), /*forceOverride=*/ false);
        assertThat(mAppSearchImpl.getVisibilityStoreLocked().isSchemaSearchableByCaller(
                prefix, prefix + "Schema", mGlobalQuerierUid)).isFalse();
    }

    @Test
    public void testSetSchema_defaultNotPackageAccessible() throws Exception {
        String prefix = AppSearchImpl.createPrefix("package", "database");
        mAppSearchImpl.setSchema("package", "database",
                Collections.singletonList(new AppSearchSchema.Builder("Schema").build()),
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ Collections.emptyMap(), /*forceOverride=*/ false);
        assertThat(
                mAppSearchImpl.getVisibilityStoreLocked().isSchemaSearchableByCaller(
                        prefix, prefix + "Schema", /*callerUid=*/ 42)).isFalse();
    }

    @Test
    public void testSetSchema_packageAccessible() throws Exception {
        // Values for a "foo" client
        String packageNameFoo = "packageFoo";
        byte[] sha256CertFoo = new byte[]{10};
        int uidFoo = 1;

        // Make sure foo package will pass package manager checks.
        mMockPackageManager.mockGetPackageUid(packageNameFoo, uidFoo);
        mMockPackageManager.mockAddSigningCertificate(packageNameFoo, sha256CertFoo);

        String prefix = AppSearchImpl.createPrefix("package", "database");
        mAppSearchImpl.setSchema("package", "database",
                Collections.singletonList(new AppSearchSchema.Builder("Schema").build()),
                /*schemasNotPlatformSurfaceable=*/ Collections.emptyList(),
                /*schemasPackageAccessible=*/ ImmutableMap.of(
                        "Schema",
                        ImmutableList.of(new PackageIdentifier(packageNameFoo, sha256CertFoo))),
                /*forceOverride=*/ false);
        assertThat(mAppSearchImpl.getVisibilityStoreLocked().isSchemaSearchableByCaller(
                prefix, prefix + "Schema", uidFoo)).isTrue();
    }

    @Test
    public void testHasSchemaType() throws Exception {
        // Nothing exists yet
        assertThat(mAppSearchImpl.hasSchemaTypeLocked("package", "database", "Schema")).isFalse();

        mAppSearchImpl.setSchema("package", "database",
                Collections.singletonList(new AppSearchSchema.Builder(
                        "Schema").build()), /*schemasNotPlatformSurfaceable=*/
                Collections.emptyList(), /*schemasPackageAccessible=*/
                Collections.emptyMap(), /*forceOverride=*/ false);
        assertThat(mAppSearchImpl.hasSchemaTypeLocked("package", "database", "Schema")).isTrue();

        assertThat(mAppSearchImpl.hasSchemaTypeLocked("package", "database",
                "UnknownSchema")).isFalse();
    }

    @Test
    public void testGetDatabases() throws Exception {
        // No client databases exist yet, but the VisibilityStore's does
        assertThat(mAppSearchImpl.getPrefixesLocked()).containsExactly(
                AppSearchImpl.createPrefix(VisibilityStore.PACKAGE_NAME,
                        VisibilityStore.DATABASE_NAME));

        // Has database1
        mAppSearchImpl.setSchema("package", "database1",
                Collections.singletonList(new AppSearchSchema.Builder(
                        "schema").build()), /*schemasNotPlatformSurfaceable=*/
                Collections.emptyList(), /*schemasPackageAccessible=*/
                Collections.emptyMap(), /*forceOverride=*/ false);
        assertThat(mAppSearchImpl.getPrefixesLocked()).containsExactly(
                AppSearchImpl.createPrefix(VisibilityStore.PACKAGE_NAME,
                        VisibilityStore.DATABASE_NAME),
                AppSearchImpl.createPrefix("package", "database1"));

        // Has both databases
        mAppSearchImpl.setSchema("package", "database2",
                Collections.singletonList(new AppSearchSchema.Builder(
                        "schema").build()), /*schemasNotPlatformSurfaceable=*/
                Collections.emptyList(), /*schemasPackageAccessible=*/
                Collections.emptyMap(), /*forceOverride=*/ false);
        assertThat(mAppSearchImpl.getPrefixesLocked()).containsExactly(
                AppSearchImpl.createPrefix(VisibilityStore.PACKAGE_NAME,
                        VisibilityStore.DATABASE_NAME),
                AppSearchImpl.createPrefix("package", "database1"), AppSearchImpl.createPrefix(
                        "package", "database2"));
    }

    @Test
    public void testRewriteSearchResultProto() throws Exception {
        final String prefix =
                "com.package.foo" + AppSearchImpl.PACKAGE_DELIMITER + "databaseName"
                        + AppSearchImpl.DATABASE_DELIMITER;
        final String uri = "uri";
        final String namespace = prefix + "namespace";
        final String schemaType = prefix + "schema";

        // Building the SearchResult received from query.
        DocumentProto documentProto = DocumentProto.newBuilder()
                .setUri(uri)
                .setNamespace(namespace)
                .setSchema(schemaType)
                .build();
        SearchResultProto.ResultProto resultProto = SearchResultProto.ResultProto.newBuilder()
                .setDocument(documentProto)
                .build();
        SearchResultProto searchResultProto = SearchResultProto.newBuilder()
                .addResults(resultProto)
                .build();

        DocumentProto.Builder strippedDocumentProto = documentProto.toBuilder();
        AppSearchImpl.removePrefixesFromDocument(strippedDocumentProto);
        SearchResultPage searchResultPage =
                AppSearchImpl.rewriteSearchResultProto(searchResultProto);
        for (SearchResult result : searchResultPage.getResults()) {
            assertThat(result.getPackageName()).isEqualTo("com.package.foo");
            assertThat(result.getDatabaseName()).isEqualTo("databaseName");
            assertThat(result.getDocument()).isEqualTo(
                    GenericDocumentToProtoConverter.toGenericDocument(
                            strippedDocumentProto.build()));
        }
    }
}
