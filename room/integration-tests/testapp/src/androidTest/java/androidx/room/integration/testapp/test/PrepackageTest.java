/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.room.integration.testapp.test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.integration.testapp.dao.ProductDao;
import androidx.room.integration.testapp.vo.Product;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.filters.Suppress;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@SmallTest
@Suppress
@RunWith(AndroidJUnit4.class)
public class PrepackageTest {

    @Test
    public void createFromAsset() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("products.db");
        ProductsDatabase database = Room.databaseBuilder(
                context, ProductsDatabase.class, "products.db")
                .createFromAsset("databases/products_v1.db")
                .build();

        ProductDao dao = database.getProductDao();
        assertThat(dao.countProducts(), is(2));
    }

    @Test
    public void createFromAsset_badSchema() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("products_badSchema.db");
        ProductsDatabase database = Room.databaseBuilder(
                context, ProductsDatabase.class, "products_badSchema.db")
                .createFromAsset("databases/products_badSchema.db")
                .build();

        Throwable throwable = null;
        try {
            database.getProductDao().countProducts();
            fail("Opening database should fail due to bad schema.");
        } catch (Throwable t) {
            throwable = t;
        }
        assertThat(throwable, instanceOf(IllegalStateException.class));
        assertThat(throwable.getMessage(), containsString("Migration didn't properly handle"));
    }

    @Test
    public void createFromAsset_notFound() {
        Context context = ApplicationProvider.getApplicationContext();
        ProductsDatabase database = Room.databaseBuilder(
                context, ProductsDatabase.class, "products_notFound.db")
                .createFromAsset("databases/products_notFound.db")
                .build();

        Throwable throwable = null;
        try {
            database.getProductDao().countProducts();
            fail("Opening database should fail due to asset file not found.");
        } catch (Throwable t) {
            throwable = t;
        }
        assertThat(throwable, instanceOf(RuntimeException.class));
        assertThat(throwable.getCause(), instanceOf(FileNotFoundException.class));
    }

    @Test
    public void createFromAsset_versionZero() {
        // A 0 version DB goes through the create path because SQLiteOpenHelper thinks the opened
        // DB was created from scratch. Therefore our onCreate callbacks will be called and we need
        // to validate the schema before completely opening the DB.
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("products_v0.db");
        ProductsDatabase database = Room.databaseBuilder(
                context, ProductsDatabase.class, "products_v0.db")
                .createFromAsset("databases/products_v0.db")
                .build();

        ProductDao dao = database.getProductDao();
        assertThat(dao.countProducts(), is(2));
    }

    @Test
    public void createFromAsset_versionZero_badSchema() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("products_v0_badSchema.db");
        ProductsDatabase database = Room.databaseBuilder(
                context, ProductsDatabase.class, "products_v0_badSchema.db")
                .createFromAsset("databases/products_v0_badSchema.db")
                .build();

        Throwable throwable = null;
        try {
            database.getProductDao().countProducts();
            fail("Opening database should fail due to bad schema.");
        } catch (Throwable t) {
            throwable = t;
        }
        assertThat(throwable, instanceOf(IllegalStateException.class));
        assertThat(throwable.getMessage(), containsString("Migration didn't properly handle"));
    }

    @Test
    public void createFromAsset_closeAndReOpen() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("products.db");
        ProductsDatabase database;
        ProductDao dao;

        database = Room.databaseBuilder(
                context, ProductsDatabase.class, "products.db")
                .createFromAsset("databases/products_v1.db")
                .build();
        dao = database.getProductDao();
        assertThat(dao.countProducts(), is(2));
        dao.insert("a new product");
        assertThat(dao.countProducts(), is(3));

        database.close();

        database = Room.databaseBuilder(
                context, ProductsDatabase.class, "products.db")
                .createFromAsset("databases/products_v1.db")
                .build();
        dao = database.getProductDao();
        assertThat(dao.countProducts(), is(3));
    }

    @Test
    public void createFromAsset_badDatabaseFile() {
        // A bad database file is a 'corrupted' database, it'll get deleted and a new file will be
        // created, the usual corrupted db recovery process.
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("products_badFile.db");
        ProductsDatabase database = Room.databaseBuilder(
                context, ProductsDatabase.class, "products_badFile.db")
                .createFromAsset("databases/products_badFile.db")
                .build();

        ProductDao dao = database.getProductDao();
        assertThat(dao.countProducts(), is(0));
    }

    @Test
    public void createFromAsset_upgrade() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("products.db");
        ProductsDatabase_v2 database = Room.databaseBuilder(
                context, ProductsDatabase_v2.class, "products.db")
                .createFromAsset("databases/products_v1.db")
                .addMigrations(new Migration(1, 2) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase database) {
                        database.execSQL(
                                "INSERT INTO Products (id, name) VALUES (null, 'Mofongo')");
                    }
                })
                .build();

        ProductDao dao = database.getProductDao();
        assertThat(dao.countProducts(), is(3));
        assertThat(dao.getProductById(3).name, is("Mofongo"));
    }

    @Test
    public void createFromAsset_upgrade_destructiveMigration() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("products.db");
        ProductsDatabase_v2 database = Room.databaseBuilder(
                context, ProductsDatabase_v2.class, "products.db")
                .createFromAsset("databases/products_v1.db")
                .fallbackToDestructiveMigration()
                .build();

        ProductDao dao = database.getProductDao();
        assertThat(dao.countProducts(), is(0));
    }

    @Test
    public void createFromFile() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();

        File dataDbFile = new File(ContextCompat.getDataDir(context), "products_external.db");
        context.deleteDatabase(dataDbFile.getAbsolutePath());

        InputStream toCopyInput = context.getAssets().open("databases/products_v1.db");
        copyAsset(toCopyInput, dataDbFile);

        ProductsDatabase database = Room.databaseBuilder(
                context, ProductsDatabase.class, "products_external.db")
                .createFromFile(dataDbFile.getAbsolutePath())
                .build();

        ProductDao dao = database.getProductDao();
        assertThat(dao.countProducts(), is(2));
    }

    @Test
    public void openExternalDatabase() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();

        File dataDbFile = new File(ContextCompat.getDataDir(context), "products.db");
        context.deleteDatabase(dataDbFile.getAbsolutePath());

        InputStream toCopyInput = context.getAssets().open("databases/products_v1.db");
        copyAsset(toCopyInput, dataDbFile);

        ProductsDatabase database = Room.databaseBuilder(
                context, ProductsDatabase.class, dataDbFile.getAbsolutePath())
                .build();

        ProductDao dao = database.getProductDao();
        assertThat(dao.countProducts(), is(2));
    }

    @Test
    public void openExternalDatabase_badSchema() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();

        File dataDbFile = new File(ContextCompat.getDataDir(context), "products.db");
        context.deleteDatabase(dataDbFile.getAbsolutePath());

        InputStream toCopyInput = context.getAssets().open("databases/products_badSchema.db");
        copyAsset(toCopyInput, dataDbFile);

        ProductsDatabase database = Room.databaseBuilder(
                context, ProductsDatabase.class, dataDbFile.getAbsolutePath())
                .build();

        Throwable throwable = null;
        try {
            database.getProductDao().countProducts();
            fail("Opening database should fail due to bad schema.");
        } catch (Throwable t) {
            throwable = t;
        }
        assertThat(throwable, instanceOf(IllegalStateException.class));
        assertThat(throwable.getMessage(), containsString("Migration didn't properly handle"));
    }

    @Test
    public void openExternalDatabase_versionZero() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();

        File dataDbFile = new File(ContextCompat.getDataDir(context), "products.db");
        context.deleteDatabase(dataDbFile.getAbsolutePath());

        InputStream toCopyInput = context.getAssets().open("databases/products_v0.db");
        copyAsset(toCopyInput, dataDbFile);

        ProductsDatabase database = Room.databaseBuilder(
                context, ProductsDatabase.class, dataDbFile.getAbsolutePath())
                .build();

        ProductDao dao = database.getProductDao();
        assertThat(dao.countProducts(), is(2));
    }

    @Test
    public void openExternalDatabase_versionZero_badSchema() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();

        File dataDbFile = new File(ContextCompat.getDataDir(context), "products.db");
        context.deleteDatabase(dataDbFile.getAbsolutePath());

        InputStream toCopyInput = context.getAssets().open("databases/products_v0_badSchema.db");
        copyAsset(toCopyInput, dataDbFile);

        ProductsDatabase database = Room.databaseBuilder(
                context, ProductsDatabase.class, dataDbFile.getAbsolutePath())
                .build();

        Throwable throwable = null;
        try {
            database.getProductDao().countProducts();
            fail("Opening database should fail due to bad schema.");
        } catch (Throwable t) {
            throwable = t;
        }
        assertThat(throwable, instanceOf(IllegalStateException.class));
        assertThat(throwable.getMessage(), containsString("Migration didn't properly handle"));
    }

    @Database(entities = Product.class, version = 1, exportSchema = false)
    abstract static class ProductsDatabase extends RoomDatabase {
        abstract ProductDao getProductDao();
    }

    @Database(entities = Product.class, version = 2, exportSchema = false)
    abstract static class ProductsDatabase_v2 extends RoomDatabase {
        abstract ProductDao getProductDao();
    }

    private static void copyAsset(InputStream input, File outputFile) throws IOException {
        OutputStream output = new FileOutputStream(outputFile);
        try {
            int length;
            byte[] buffer = new byte[1024 * 4];
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
        } finally {
            input.close();
            output.close();
        }
    }
}
