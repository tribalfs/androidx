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

import static androidx.appsearch.app.AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES;
import static androidx.appsearch.app.AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN;
import static androidx.appsearch.app.util.AppSearchTestUtils.checkIsBatchResultSuccess;
import static androidx.appsearch.app.util.AppSearchTestUtils.convertSearchResultsToDocuments;

import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.NonNull;
import androidx.appsearch.annotation.Document;
import androidx.appsearch.localstorage.LocalStorage;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class AnnotationProcessorTestBase {
    private AppSearchSession mSession;
    private static final String DB_NAME_1 = LocalStorage.DEFAULT_DATABASE_NAME;

    protected abstract ListenableFuture<AppSearchSession> createSearchSession(
            @NonNull String dbName);

    @Before
    public void setUp() throws Exception {
        mSession = createSearchSession(DB_NAME_1).get();

        // Cleanup whatever documents may still exist in these databases. This is needed in
        // addition to tearDown in case a test exited without completing properly.
        cleanup();
    }

    @After
    public void tearDown() throws Exception {
        // Cleanup whatever documents may still exist in these databases.
        cleanup();
    }

    private void cleanup() throws Exception {
        mSession.setSchema(new SetSchemaRequest.Builder().setForceOverride(true).build()).get();
    }

    @Document
    static class Card {
        @Document.Uri
        String mUri;
        @Document.Property
                (indexingType = INDEXING_TYPE_PREFIXES, tokenizerType = TOKENIZER_TYPE_PLAIN)
        String mString;        // 3a

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Card)) {
                return false;
            }
            Card otherCard = (Card) other;
            assertThat(otherCard.mUri).isEqualTo(this.mUri);
            return true;
        }
    }

    @Document
    static class Gift {
        @Document.Uri
        String mUri;

        // Collections
        @Document.Property
        Collection<Long> mCollectLong;         // 1a
        @Document.Property
        Collection<Integer> mCollectInteger;   // 1a
        @Document.Property
        Collection<Double> mCollectDouble;     // 1a
        @Document.Property
        Collection<Float> mCollectFloat;       // 1a
        @Document.Property
        Collection<Boolean> mCollectBoolean;   // 1a
        @Document.Property
        Collection<byte[]> mCollectByteArr;    // 1a
        @Document.Property
        Collection<String> mCollectString;     // 1b
        @Document.Property
        Collection<Card> mCollectCard;         // 1c

        // Arrays
        @Document.Property
        Long[] mArrBoxLong;         // 2a
        @Document.Property
        long[] mArrUnboxLong;       // 2b
        @Document.Property
        Integer[] mArrBoxInteger;   // 2a
        @Document.Property
        int[] mArrUnboxInt;         // 2a
        @Document.Property
        Double[] mArrBoxDouble;     // 2a
        @Document.Property
        double[] mArrUnboxDouble;   // 2b
        @Document.Property
        Float[] mArrBoxFloat;       // 2a
        @Document.Property
        float[] mArrUnboxFloat;     // 2a
        @Document.Property
        Boolean[] mArrBoxBoolean;   // 2a
        @Document.Property
        boolean[] mArrUnboxBoolean; // 2b
        @Document.Property
        byte[][] mArrUnboxByteArr;  // 2b
        @Document.Property
        Byte[] mBoxByteArr;         // 2a
        @Document.Property
        String[] mArrString;        // 2b
        @Document.Property
        Card[] mArrCard;            // 2c

        // Single values
        @Document.Property
        String mString;        // 3a
        @Document.Property
        Long mBoxLong;         // 3a
        @Document.Property
        long mUnboxLong;       // 3b
        @Document.Property
        Integer mBoxInteger;   // 3a
        @Document.Property
        int mUnboxInt;         // 3b
        @Document.Property
        Double mBoxDouble;     // 3a
        @Document.Property
        double mUnboxDouble;   // 3b
        @Document.Property
        Float mBoxFloat;       // 3a
        @Document.Property
        float mUnboxFloat;     // 3b
        @Document.Property
        Boolean mBoxBoolean;   // 3a
        @Document.Property
        boolean mUnboxBoolean; // 3b
        @Document.Property
        byte[] mUnboxByteArr;  // 3a
        @Document.Property
        Card mCard;            // 3c

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Gift)) {
                return false;
            }
            Gift otherGift = (Gift) other;
            assertThat(otherGift.mUri).isEqualTo(this.mUri);
            assertThat(otherGift.mArrBoxBoolean).isEqualTo(this.mArrBoxBoolean);
            assertThat(otherGift.mArrBoxDouble).isEqualTo(this.mArrBoxDouble);
            assertThat(otherGift.mArrBoxFloat).isEqualTo(this.mArrBoxFloat);
            assertThat(otherGift.mArrBoxLong).isEqualTo(this.mArrBoxLong);
            assertThat(otherGift.mArrBoxInteger).isEqualTo(this.mArrBoxInteger);
            assertThat(otherGift.mArrString).isEqualTo(this.mArrString);
            assertThat(otherGift.mBoxByteArr).isEqualTo(this.mBoxByteArr);
            assertThat(otherGift.mArrUnboxBoolean).isEqualTo(this.mArrUnboxBoolean);
            assertThat(otherGift.mArrUnboxByteArr).isEqualTo(this.mArrUnboxByteArr);
            assertThat(otherGift.mArrUnboxDouble).isEqualTo(this.mArrUnboxDouble);
            assertThat(otherGift.mArrUnboxFloat).isEqualTo(this.mArrUnboxFloat);
            assertThat(otherGift.mArrUnboxLong).isEqualTo(this.mArrUnboxLong);
            assertThat(otherGift.mArrUnboxInt).isEqualTo(this.mArrUnboxInt);
            assertThat(otherGift.mArrCard).isEqualTo(this.mArrCard);

            assertThat(otherGift.mCollectLong).isEqualTo(this.mCollectLong);
            assertThat(otherGift.mCollectInteger).isEqualTo(this.mCollectInteger);
            assertThat(otherGift.mCollectBoolean).isEqualTo(this.mCollectBoolean);
            assertThat(otherGift.mCollectString).isEqualTo(this.mCollectString);
            assertThat(otherGift.mCollectDouble).isEqualTo(this.mCollectDouble);
            assertThat(otherGift.mCollectFloat).isEqualTo(this.mCollectFloat);
            assertThat(otherGift.mCollectCard).isEqualTo(this.mCollectCard);
            checkCollectByteArr(otherGift.mCollectByteArr, this.mCollectByteArr);

            assertThat(otherGift.mString).isEqualTo(this.mString);
            assertThat(otherGift.mBoxLong).isEqualTo(this.mBoxLong);
            assertThat(otherGift.mUnboxLong).isEqualTo(this.mUnboxLong);
            assertThat(otherGift.mBoxInteger).isEqualTo(this.mBoxInteger);
            assertThat(otherGift.mUnboxInt).isEqualTo(this.mUnboxInt);
            assertThat(otherGift.mBoxDouble).isEqualTo(this.mBoxDouble);
            assertThat(otherGift.mUnboxDouble).isEqualTo(this.mUnboxDouble);
            assertThat(otherGift.mBoxFloat).isEqualTo(this.mBoxFloat);
            assertThat(otherGift.mUnboxFloat).isEqualTo(this.mUnboxFloat);
            assertThat(otherGift.mBoxBoolean).isEqualTo(this.mBoxBoolean);
            assertThat(otherGift.mUnboxBoolean).isEqualTo(this.mUnboxBoolean);
            assertThat(otherGift.mUnboxByteArr).isEqualTo(this.mUnboxByteArr);
            assertThat(otherGift.mCard).isEqualTo(this.mCard);
            return true;
        }

        void checkCollectByteArr(Collection<byte[]> first, Collection<byte[]> second) {
            if (first == null && second == null) {
                return;
            }
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            assertThat(first.toArray()).isEqualTo(second.toArray());
        }
    }

    @Test
    public void testAnnotationProcessor() throws Exception {
        //TODO(b/156296904) add test for int, float, GenericDocument, and class with
        // @Document annotation
        mSession.setSchema(
                new SetSchemaRequest.Builder().addDocumentClasses(Card.class, Gift.class).build())
                .get();

        // Create a Gift object and assign values.
        Gift inputDocument = new Gift();
        inputDocument.mUri = "gift.uri";

        inputDocument.mArrBoxBoolean = new Boolean[]{true, false};
        inputDocument.mArrBoxDouble = new Double[]{0.0, 1.0};
        inputDocument.mArrBoxFloat = new Float[]{2.0F, 3.0F};
        inputDocument.mArrBoxInteger = new Integer[]{4, 5};
        inputDocument.mArrBoxLong = new Long[]{6L, 7L};
        inputDocument.mArrString = new String[]{"cat", "dog"};
        inputDocument.mBoxByteArr = new Byte[]{8, 9};
        inputDocument.mArrUnboxBoolean = new boolean[]{false, true};
        inputDocument.mArrUnboxByteArr = new byte[][]{{0, 1}, {2, 3}};
        inputDocument.mArrUnboxDouble = new double[]{1.0, 0.0};
        inputDocument.mArrUnboxFloat = new float[]{3.0f, 2.0f};
        inputDocument.mArrUnboxInt = new int[]{5, 4};
        inputDocument.mArrUnboxLong = new long[]{7, 6};

        Card card1 = new Card();
        card1.mUri = "card.uri1";
        Card card2 = new Card();
        card2.mUri = "card.uri2";
        inputDocument.mArrCard = new Card[]{card2, card2};

        inputDocument.mCollectLong = Arrays.asList(inputDocument.mArrBoxLong);
        inputDocument.mCollectInteger = Arrays.asList(inputDocument.mArrBoxInteger);
        inputDocument.mCollectBoolean = Arrays.asList(inputDocument.mArrBoxBoolean);
        inputDocument.mCollectString = Arrays.asList(inputDocument.mArrString);
        inputDocument.mCollectDouble = Arrays.asList(inputDocument.mArrBoxDouble);
        inputDocument.mCollectFloat = Arrays.asList(inputDocument.mArrBoxFloat);
        inputDocument.mCollectByteArr = Arrays.asList(inputDocument.mArrUnboxByteArr);
        inputDocument.mCollectCard = Arrays.asList(card2, card2);

        inputDocument.mString = "String";
        inputDocument.mBoxLong = 1L;
        inputDocument.mUnboxLong = 2L;
        inputDocument.mBoxInteger = 3;
        inputDocument.mUnboxInt = 4;
        inputDocument.mBoxDouble = 5.0;
        inputDocument.mUnboxDouble = 6.0;
        inputDocument.mBoxFloat = 7.0F;
        inputDocument.mUnboxFloat = 8.0f;
        inputDocument.mBoxBoolean = true;
        inputDocument.mUnboxBoolean = false;
        inputDocument.mUnboxByteArr = new byte[]{1, 2, 3};
        inputDocument.mCard = card1;

        // Index the Gift document and query it.
        checkIsBatchResultSuccess(mSession.put(
                new PutDocumentsRequest.Builder().addDocuments(inputDocument).build()));
        SearchResults searchResults = mSession.search("", new SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                .build());
        List<GenericDocument> documents = convertSearchResultsToDocuments(searchResults);
        assertThat(documents).hasSize(1);

        // Create DocumentClassFactory for Gift.
        DocumentClassFactoryRegistry registry = DocumentClassFactoryRegistry.getInstance();
        DocumentClassFactory<Gift> factory = registry.getOrCreateFactory(Gift.class);

        // Convert GenericDocument to Gift and check values.
        Gift outputDocument = factory.fromGenericDocument(documents.get((0)));
        assertThat(outputDocument).isEqualTo(inputDocument);
    }

    @Test
    public void testAnnotationProcessor_queryByType() throws Exception {
        mSession.setSchema(
                new SetSchemaRequest.Builder()
                        .addDocumentClasses(Card.class, Gift.class)
                        .addSchemas(AppSearchEmail.SCHEMA).build())
                .get();

        // Create documents and index them
        Gift inputDocument1 = new Gift();
        inputDocument1.mUri = "gift.uri1";
        Gift inputDocument2 = new Gift();
        inputDocument2.mUri = "gift.uri2";
        AppSearchEmail email1 =
                new AppSearchEmail.Builder("uri3")
                        .setNamespace("namespace")
                        .setFrom("from@example.com")
                        .setTo("to1@example.com", "to2@example.com")
                        .setSubject("testPut example")
                        .setBody("This is the body of the testPut email")
                        .build();
        checkIsBatchResultSuccess(mSession.put(
                new PutDocumentsRequest.Builder()
                        .addDocuments(inputDocument1, inputDocument2)
                        .addGenericDocuments(email1).build()));

        // Query the documents by it's schema type.
        SearchResults searchResults = mSession.search("",
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                        .addFilterSchemas("Gift", AppSearchEmail.SCHEMA_TYPE)
                        .build());
        List<GenericDocument> documents = convertSearchResultsToDocuments(searchResults);
        assertThat(documents).hasSize(3);

        // Query the documents by it's class.
        searchResults = mSession.search("",
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                        .addFilterDocumentClasses(Gift.class)
                        .build());
        documents = convertSearchResultsToDocuments(searchResults);
        assertThat(documents).hasSize(2);

        // Query the documents by schema type and class mix.
        searchResults = mSession.search("",
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                        .addFilterSchemas(AppSearchEmail.SCHEMA_TYPE)
                        .addFilterDocumentClasses(Gift.class)
                        .build());
        documents = convertSearchResultsToDocuments(searchResults);
        assertThat(documents).hasSize(3);
    }
}
