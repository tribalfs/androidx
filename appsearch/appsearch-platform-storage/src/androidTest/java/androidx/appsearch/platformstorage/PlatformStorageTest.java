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
package androidx.appsearch.platformstorage;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.Build;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;

import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
public class PlatformStorageTest {
    @Test
    public void testSearchContext_databaseName() {
        PlatformStorage.SearchContext searchContext =
                new PlatformStorage.SearchContext.Builder(
                        ApplicationProvider.getApplicationContext(),
                        /*databaseName=*/"dbName").build();

        assertThat(searchContext.getDatabaseName()).isEqualTo("dbName");
    }

    @Test
    public void testSearchContext_withClientExecutor() {
        Executor executor = Executors.newSingleThreadExecutor();
        PlatformStorage.SearchContext searchContext = new PlatformStorage.SearchContext.Builder(
                ApplicationProvider.getApplicationContext(),
                /*databaseName=*/"dbName")
                .setWorkerExecutor(executor)
                .build();

        assertThat(searchContext.getWorkerExecutor()).isEqualTo(executor);
        assertThat(searchContext.getDatabaseName()).isEqualTo("dbName");
    }

    @Test
    public void testSearchContext_withDefaultExecutor() {
        PlatformStorage.SearchContext searchContext = new PlatformStorage.SearchContext.Builder(
                ApplicationProvider.getApplicationContext(),
                /*databaseName=*/"dbName")
                .build();

        assertThat(searchContext.getWorkerExecutor()).isNotNull();
        assertThat(searchContext.getDatabaseName()).isEqualTo("dbName");
    }

    @Test
    public void testSearchContext_withInvalidDatabaseName() {
        // Test special character can present in database name. When a special character is banned
        // in database name, add checker in SearchContext.Builder and reflect it in java doc.

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new PlatformStorage.SearchContext.Builder(
                        ApplicationProvider.getApplicationContext(),
                        "testDatabaseNameEndWith/").build());
        assertThat(e).hasMessageThat().isEqualTo("Database name cannot contain '/'");
        e = assertThrows(IllegalArgumentException.class,
                () -> new PlatformStorage.SearchContext.Builder(
                        ApplicationProvider.getApplicationContext(),
                        "/testDatabaseNameStartWith").build());
        assertThat(e).hasMessageThat().isEqualTo("Database name cannot contain '/'");
    }
}
