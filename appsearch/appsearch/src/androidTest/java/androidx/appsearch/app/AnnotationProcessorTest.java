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

package androidx.appsearch.app;

import static androidx.appsearch.app.AppSearchTestUtils.checkIsBatchResultSuccess;
import static androidx.appsearch.app.AppSearchTestUtils.checkIsResultSuccess;
import static androidx.appsearch.app.AppSearchTestUtils.doQuery;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.appsearch.annotation.AppSearchDocument;
import androidx.appsearch.localbackend.LocalBackend;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class AnnotationProcessorTest {
    private AppSearchManager mAppSearchManager;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        LocalBackend backend = LocalBackend.getInstance(context).getResultValue();
        mAppSearchManager = checkIsResultSuccess(new AppSearchManager.Builder()
                .setDatabaseName("testDb").setBackend(backend).build());

        // Remove all documents from any instances that may have been created in the tests.
        backend.resetAllDatabases().getResultValue();
    }

    @AppSearchDocument
    static class Gift {
        @AppSearchDocument.Uri String mUri;

        // Collections
        @AppSearchDocument.Property Collection<Long> mLongCollection;         // 1a
//        @AppSearchDocument.Property Collection<Integer> mCollectInteger;   // 1a
        @AppSearchDocument.Property Collection<Double> mCollectDouble;     // 1a
//        @AppSearchDocument.Property Collection<Float> mCollectFloat;       // 1a
        @AppSearchDocument.Property Collection<Boolean> mCollectBoolean;   // 1a
        @AppSearchDocument.Property Collection<byte[]> mCollectByteArr;    // 1a
        @AppSearchDocument.Property Collection<String> mCollectString;     // 1b
        @AppSearchDocument.Property Collection<Gift> mCollectGift;         // 1c

        // Arrays
        @AppSearchDocument.Property Long[] mArrBoxLong;         // 2a
        @AppSearchDocument.Property long[] mArrUnboxLong;       // 2b
//        @AppSearchDocument.Property Integer[] mArrBoxInteger;   // 2a
//        @AppSearchDocument.Property int[] mArrUnboxInt;         // 2a
        @AppSearchDocument.Property Double[] mArrBoxDouble;     // 2a
        @AppSearchDocument.Property double[] mArrUnboxDouble;   // 2b
//        @AppSearchDocument.Property Float[] mArrBoxFloat;       // 2a
//        @AppSearchDocument.Property float[] mArrUnboxFloat;     // 2a
        @AppSearchDocument.Property Boolean[] mArrBoxBoolean;   // 2a
        @AppSearchDocument.Property boolean[] mArrUnboxBoolean; // 2b
        @AppSearchDocument.Property byte[][] mArrUnboxByteArr;  // 2b
        @AppSearchDocument.Property Byte[] mBoxByteArr;         // 2a
        @AppSearchDocument.Property String[] mArrString;        // 2b
        @AppSearchDocument.Property Gift[] mArrGift;            // 2c

        // Single values
        @AppSearchDocument.Property String mString;        // 3a
        @AppSearchDocument.Property Long mBoxLong;         // 3a
        @AppSearchDocument.Property long mUnboxLong;       // 3b
//        @AppSearchDocument.Property Integer mBoxInteger;   // 3a
//        @AppSearchDocument.Property int mUnboxInt;         // 3b
        @AppSearchDocument.Property Double mBoxDouble;     // 3a
        @AppSearchDocument.Property double mUnboxDouble;   // 3b
//        @AppSearchDocument.Property Float mBoxFloat;       // 3a
//        @AppSearchDocument.Property float mUnboxFloat;     // 3b
        @AppSearchDocument.Property Boolean mBoxBoolean;   // 3a
        @AppSearchDocument.Property boolean mUnboxBoolean; // 3b
        @AppSearchDocument.Property byte[] mUnboxByteArr;  // 3a
        @AppSearchDocument.Property Gift mGift;            // 3c

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
            assertThat(otherGift.mArrBoxLong).isEqualTo(this.mArrBoxLong);
            assertThat(otherGift.mArrString).isEqualTo(this.mArrString);
            assertThat(otherGift.mBoxByteArr).isEqualTo(this.mBoxByteArr);
            assertThat(otherGift.mArrUnboxBoolean).isEqualTo(this.mArrUnboxBoolean);
            assertThat(otherGift.mArrUnboxByteArr).isEqualTo(this.mArrUnboxByteArr);
            assertThat(otherGift.mArrUnboxDouble).isEqualTo(this.mArrUnboxDouble);
            assertThat(otherGift.mArrUnboxLong).isEqualTo(this.mArrUnboxLong);
            assertThat(otherGift.mArrGift).isEqualTo(this.mArrGift);

            assertThat(otherGift.mLongCollection).isEqualTo(this.mLongCollection);
            assertThat(otherGift.mCollectBoolean).isEqualTo(this.mCollectBoolean);
            assertThat(otherGift.mCollectString).isEqualTo(this.mCollectString);
            assertThat(otherGift.mCollectDouble).isEqualTo(this.mCollectDouble);
            assertThat(otherGift.mCollectGift).isEqualTo(this.mCollectGift);
            checkCollectByteArr(otherGift.mCollectByteArr, this.mCollectByteArr);

            assertThat(otherGift.mString).isEqualTo(this.mString);
            assertThat(otherGift.mBoxLong).isEqualTo(this.mBoxLong);
            assertThat(otherGift.mUnboxLong).isEqualTo(this.mUnboxLong);
            assertThat(otherGift.mBoxDouble).isEqualTo(this.mBoxDouble);
            assertThat(otherGift.mUnboxDouble).isEqualTo(this.mUnboxDouble);
            assertThat(otherGift.mBoxBoolean).isEqualTo(this.mBoxBoolean);
            assertThat(otherGift.mUnboxBoolean).isEqualTo(this.mUnboxBoolean);
            assertThat(otherGift.mUnboxByteArr).isEqualTo(this.mUnboxByteArr);
            assertThat(otherGift.mGift).isEqualTo(this.mGift);
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
        // @AppSearchDocument annotation
        checkIsResultSuccess(mAppSearchManager.setSchema(
                new AppSearchManager.SetSchemaRequest.Builder()
                        .addDataClass(Gift.class)
                        .build()));

        // Create a Gift object and assign values.
        Gift inputDataClass = new Gift();
        inputDataClass.mUri = "gift.uri";

        inputDataClass.mArrBoxBoolean = new Boolean[]{true, false};
        inputDataClass.mArrBoxDouble = new Double[]{0.0, 1.0};
        inputDataClass.mArrBoxLong = new Long[]{6L, 7L};
        inputDataClass.mArrString = new String[]{"cat", "dog"};
        inputDataClass.mBoxByteArr = new Byte[]{8, 9};
        inputDataClass.mArrUnboxBoolean = new boolean[]{false, true};
        inputDataClass.mArrUnboxByteArr = new byte[][]{{0, 1}, {2, 3}};
        inputDataClass.mArrUnboxDouble = new double[]{1.0, 0.0};
        inputDataClass.mArrUnboxLong = new long[]{7, 6};

        Gift innerGift1 = new Gift();
        innerGift1.mUri = "innerGift.uri1";
        Gift innerGift2 = new Gift();
        innerGift2.mUri = "innerGift.uri2";
        inputDataClass.mArrGift = new Gift[]{innerGift1, innerGift2};

        inputDataClass.mLongCollection = Arrays.asList(inputDataClass.mArrBoxLong);
        inputDataClass.mCollectBoolean = Arrays.asList(inputDataClass.mArrBoxBoolean);
        inputDataClass.mCollectString = Arrays.asList(inputDataClass.mArrString);
        inputDataClass.mCollectDouble = Arrays.asList(inputDataClass.mArrBoxDouble);
        inputDataClass.mCollectByteArr = Arrays.asList(inputDataClass.mArrUnboxByteArr);
        inputDataClass.mCollectGift = Arrays.asList(innerGift1, innerGift2);

        inputDataClass.mString = "String";
        inputDataClass.mBoxLong = 1L;
        inputDataClass.mUnboxLong = 2L;
        inputDataClass.mBoxDouble = 5.0;
        inputDataClass.mUnboxDouble = 6.0;
        inputDataClass.mBoxBoolean = true;
        inputDataClass.mUnboxBoolean = false;
        inputDataClass.mUnboxByteArr = new byte[]{1, 2, 3};
        inputDataClass.mGift = innerGift1;

        // Index the Gift document and query it.
        checkIsBatchResultSuccess(mAppSearchManager.putDocuments(
                new AppSearchManager.PutDocumentsRequest.Builder()
                        .addDataClass(inputDataClass).build()));
        List<GenericDocument> searchResults = doQuery(mAppSearchManager, "");
        assertThat(searchResults).hasSize(1);

        // Create DataClassFactory for Gift.
        DataClassFactoryRegistry registry = DataClassFactoryRegistry.getInstance();
        DataClassFactory<Gift> factory = registry.getOrCreateFactory(Gift.class);

        // Convert GenericDocument to Gift and check values.
        Gift outputDataClass = factory.fromGenericDocument(searchResults.get((0)));
        assertThat(outputDataClass).isEqualTo(inputDataClass);
    }
}
