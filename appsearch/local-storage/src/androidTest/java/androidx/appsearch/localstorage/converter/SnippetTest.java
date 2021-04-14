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

package androidx.appsearch.localstorage.converter;

import static com.google.common.truth.Truth.assertThat;

import androidx.appsearch.app.SearchResult;
import androidx.appsearch.app.SearchResultPage;
import androidx.appsearch.localstorage.util.PrefixUtil;

import com.google.android.icing.proto.DocumentProto;
import com.google.android.icing.proto.PropertyProto;
import com.google.android.icing.proto.SchemaTypeConfigProto;
import com.google.android.icing.proto.SearchResultProto;
import com.google.android.icing.proto.SnippetMatchProto;
import com.google.android.icing.proto.SnippetProto;

import org.junit.Test;

import java.util.Collections;
import java.util.Map;

public class SnippetTest {
    private static final String SCHEMA_TYPE = "schema1";
    private static final String PACKAGE_NAME = "packageName";
    private static final String DATABASE_NAME = "databaseName";
    private static final String PREFIX = PrefixUtil.createPrefix(PACKAGE_NAME, DATABASE_NAME);
    private static final SchemaTypeConfigProto SCHEMA_TYPE_CONFIG_PROTO =
            SchemaTypeConfigProto.newBuilder()
                    .setSchemaType(PREFIX + SCHEMA_TYPE)
                    .build();
    private static final Map<String, Map<String, SchemaTypeConfigProto>> SCHEMA_MAP =
            Collections.singletonMap(PREFIX,
                    Collections.singletonMap(PREFIX + SCHEMA_TYPE,
                            SCHEMA_TYPE_CONFIG_PROTO));

    // TODO(tytytyww): Add tests for Double and Long Snippets.
    @Test
    public void testSingleStringSnippet() {
        final String propertyKeyString = "content";
        final String propertyValueString = "A commonly used fake word is foo.\n"
                + "   Another nonsense word that’s used a lot\n"
                + "   is bar.\n";
        final String uri = "uri1";
        final String searchWord = "foo";
        final String exactMatch = "foo";
        final String window = "is foo";

        // Building the SearchResult received from query.
        PropertyProto property = PropertyProto.newBuilder()
                .setName(propertyKeyString)
                .addStringValues(propertyValueString)
                .build();
        DocumentProto documentProto = DocumentProto.newBuilder()
                .setUri(uri)
                .setSchema(SCHEMA_TYPE)
                .addProperties(property)
                .build();
        SnippetProto snippetProto = SnippetProto.newBuilder()
                .addEntries(SnippetProto.EntryProto.newBuilder()
                        .setPropertyName(propertyKeyString)
                        .addSnippetMatches(SnippetMatchProto.newBuilder()
                                .setExactMatchPosition(29)
                                .setExactMatchBytes(3)
                                .setWindowPosition(26)
                                .setWindowBytes(6)
                                .build())
                        .build())
                .build();
        SearchResultProto.ResultProto resultProto = SearchResultProto.ResultProto.newBuilder()
                .setDocument(documentProto)
                .setSnippet(snippetProto)
                .build();
        SearchResultProto searchResultProto = SearchResultProto.newBuilder()
                .addResults(resultProto)
                .build();

        // Making ResultReader and getting Snippet values.
        SearchResultPage searchResultPage =
                SearchResultToProtoConverter.toSearchResultPage(searchResultProto,
                        Collections.singletonList(PACKAGE_NAME), Collections.singletonList(
                                DATABASE_NAME), SCHEMA_MAP);
        for (SearchResult result : searchResultPage.getResults()) {
            SearchResult.MatchInfo match = result.getMatches().get(0);
            assertThat(match.getPropertyPath()).isEqualTo(propertyKeyString);
            assertThat(match.getFullText()).isEqualTo(propertyValueString);
            assertThat(match.getExactMatch()).isEqualTo(exactMatch);
            assertThat(match.getExactMatchRange()).isEqualTo(
                    new SearchResult.MatchRange(/*lower=*/29, /*upper=*/32));
            assertThat(match.getFullText()).isEqualTo(propertyValueString);
            assertThat(match.getSnippetRange()).isEqualTo(
                    new SearchResult.MatchRange(/*lower=*/26, /*upper=*/32));
            assertThat(match.getSnippet()).isEqualTo(window);
        }
    }

    // TODO(tytytyww): Add tests for Double and Long Snippets.
    @Test
    public void testNoSnippets() throws Exception {

        final String propertyKeyString = "content";
        final String propertyValueString = "A commonly used fake word is foo.\n"
                + "   Another nonsense word that’s used a lot\n"
                + "   is bar.\n";
        final String uri = "uri1";
        final String searchWord = "foo";
        final String exactMatch = "foo";
        final String window = "is foo";

        // Building the SearchResult received from query.
        PropertyProto property = PropertyProto.newBuilder()
                .setName(propertyKeyString)
                .addStringValues(propertyValueString)
                .build();
        DocumentProto documentProto = DocumentProto.newBuilder()
                .setUri(uri)
                .setSchema(SCHEMA_TYPE)
                .addProperties(property)
                .build();
        SearchResultProto.ResultProto resultProto = SearchResultProto.ResultProto.newBuilder()
                .setDocument(documentProto)
                .build();
        SearchResultProto searchResultProto = SearchResultProto.newBuilder()
                .addResults(resultProto)
                .build();

        SearchResultPage searchResultPage =
                SearchResultToProtoConverter.toSearchResultPage(searchResultProto,
                        Collections.singletonList(PACKAGE_NAME), Collections.singletonList(
                                DATABASE_NAME), SCHEMA_MAP);
        for (SearchResult result : searchResultPage.getResults()) {
            assertThat(result.getMatches()).isEmpty();
        }
    }

    @Test
    public void testMultipleStringSnippet() throws Exception {
        final String searchWord = "Test";

        // Building the SearchResult received from query.
        PropertyProto property1 = PropertyProto.newBuilder()
                .setName("sender.name")
                .addStringValues("Test Name Jr.")
                .build();
        PropertyProto property2 = PropertyProto.newBuilder()
                .setName("sender.email")
                .addStringValues("TestNameJr@gmail.com")
                .build();
        DocumentProto documentProto = DocumentProto.newBuilder()
                .setUri("uri1")
                .setSchema(SCHEMA_TYPE)
                .addProperties(property1)
                .addProperties(property2)
                .build();
        SnippetProto snippetProto = SnippetProto.newBuilder()
                .addEntries(
                        SnippetProto.EntryProto.newBuilder()
                                .setPropertyName("sender.name")
                                .addSnippetMatches(
                                        SnippetMatchProto.newBuilder()
                                                .setExactMatchPosition(0)
                                                .setExactMatchBytes(4)
                                                .setWindowPosition(0)
                                                .setWindowBytes(9)
                                                .build())
                                .build())
                .addEntries(
                        SnippetProto.EntryProto.newBuilder()
                                .setPropertyName("sender.email")
                                .addSnippetMatches(
                                        SnippetMatchProto.newBuilder()
                                                .setExactMatchPosition(0)
                                                .setExactMatchBytes(20)
                                                .setWindowPosition(0)
                                                .setWindowBytes(20)
                                                .build())
                                .build()
                )
                .build();
        SearchResultProto.ResultProto resultProto = SearchResultProto.ResultProto.newBuilder()
                .setDocument(documentProto)
                .setSnippet(snippetProto)
                .build();
        SearchResultProto searchResultProto = SearchResultProto.newBuilder()
                .addResults(resultProto)
                .build();

        // Making ResultReader and getting Snippet values.
        SearchResultPage searchResultPage =
                SearchResultToProtoConverter.toSearchResultPage(searchResultProto,
                        Collections.singletonList(PACKAGE_NAME), Collections.singletonList(
                                DATABASE_NAME), SCHEMA_MAP);
        for (SearchResult result : searchResultPage.getResults()) {

            SearchResult.MatchInfo match1 = result.getMatches().get(0);
            assertThat(match1.getPropertyPath()).isEqualTo("sender.name");
            assertThat(match1.getFullText()).isEqualTo("Test Name Jr.");
            assertThat(match1.getExactMatchRange()).isEqualTo(
                    new SearchResult.MatchRange(/*lower=*/0, /*upper=*/4));
            assertThat(match1.getExactMatch()).isEqualTo("Test");
            assertThat(match1.getSnippetRange()).isEqualTo(
                    new SearchResult.MatchRange(/*lower=*/0, /*upper=*/9));
            assertThat(match1.getSnippet()).isEqualTo("Test Name");

            SearchResult.MatchInfo match2 = result.getMatches().get(1);
            assertThat(match2.getPropertyPath()).isEqualTo("sender.email");
            assertThat(match2.getFullText()).isEqualTo("TestNameJr@gmail.com");
            assertThat(match2.getExactMatchRange()).isEqualTo(
                    new SearchResult.MatchRange(/*lower=*/0, /*upper=*/20));
            assertThat(match2.getExactMatch()).isEqualTo("TestNameJr@gmail.com");
            assertThat(match2.getSnippetRange()).isEqualTo(
                    new SearchResult.MatchRange(/*lower=*/0, /*upper=*/20));
            assertThat(match2.getSnippet()).isEqualTo("TestNameJr@gmail.com");
        }
    }
}
