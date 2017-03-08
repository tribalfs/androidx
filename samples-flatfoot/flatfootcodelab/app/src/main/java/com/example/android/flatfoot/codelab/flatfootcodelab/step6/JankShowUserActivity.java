/*
 * Copyright 2017, The Android Open Source Project
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

package com.example.android.flatfoot.codelab.flatfootcodelab.step6;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.TextView;

import com.android.support.lifecycle.LifecycleActivity;
import com.example.android.flatfoot.codelab.flatfootcodelab.R;
import com.example.android.flatfoot.codelab.flatfootcodelab.orm_db.AppDatabase;
import com.example.android.flatfoot.codelab.flatfootcodelab.orm_db.Book;
import com.example.android.flatfoot.codelab.flatfootcodelab.orm_db.utils.DatabaseInitializer;

import java.util.Iterator;
import java.util.List;

public class JankShowUserActivity extends LifecycleActivity {

    private AppDatabase mDb;

    private TextView mBooksTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.db_activity);

        mBooksTextView = (TextView) findViewById(R.id.books_tv);

        //TODO: A database reference should not live in the activity
        mDb = AppDatabase.getInMemoryDatabase(getApplicationContext());

        populateDb();

        fetchData();
    }

    private void populateDb() {
        DatabaseInitializer.populateSync(mDb);
    }

    private void fetchData() {
        // This activity is executing a query on the main thread, making the UI perform badly.
        List<Book> books = mDb.bookModel().findBooksBorrowedByNameSync("Mike");
        showListInUI(books, mBooksTextView);
    }

    private static void showListInUI(final @NonNull List<Book> books,
                                     final TextView booksTextView) {
        StringBuilder sb = new StringBuilder();
        for (Book book : books) {
            sb.append(book.title);
            sb.append("\n");
        }
        booksTextView.setText(sb.toString());
    }

    public void onRefreshBtClicked(View view) {
        mBooksTextView.setText("");
        fetchData();
    }
}
