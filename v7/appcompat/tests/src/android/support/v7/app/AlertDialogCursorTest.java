/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.support.v7.app;

import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.test.espresso.DataInteraction;
import android.support.v7.appcompat.test.R;
import android.support.v7.testutils.TestUtilsMatchers;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.View;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ListAdapter;
import android.widget.ListView;
import org.hamcrest.Matcher;

import java.io.File;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.RootMatchers.isDialog;
import static android.support.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.Is.is;

public class AlertDialogCursorTest
        extends ActivityInstrumentationTestCase2<AlertDialogTestActivity> {

    private Button mButton;

    private int mClickedItemIndex = -1;

    private static final String TEXT_COLUMN_NAME = "text";
    private static final String CHECKED_COLUMN_NAME = "checked";

    private String[] mTextContent;
    private boolean[] mCheckedContent;

    private String[] mProjectionWithChecked;
    private String[] mProjectionWithoutChecked;

    private SQLiteDatabase mDatabase;
    private File mDatabaseFile;
    private Cursor mCursor;

    private AlertDialog mAlertDialog;

    public AlertDialogCursorTest() {
        super(AlertDialogTestActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Ideally these constant arrays would be defined as final static fields on the
        // class level, but for some reason those get reset to null on v9- devices after
        // the first test method has been executed.
        mTextContent = new String[] { "Adele", "Beyonce", "Ciara", "Dido" };
        mCheckedContent = new boolean[] { false, false, true, false };

        mProjectionWithChecked = new String[] {
                "_id",                       // 0
                TEXT_COLUMN_NAME,            // 1
                CHECKED_COLUMN_NAME          // 2
        };
        mProjectionWithoutChecked = new String[] {
                "_id",                       // 0
                TEXT_COLUMN_NAME             // 1
        };

        final AlertDialogTestActivity activity = getActivity();
        mButton = (Button) activity.findViewById(R.id.test_button);

        Context context = getInstrumentation().getTargetContext();
        File dbDir = context.getDir("tests", Context.MODE_PRIVATE);
        mDatabaseFile = new File(dbDir, "database_alert_dialog_test.db");
        if (mDatabaseFile.exists()) {
            mDatabaseFile.delete();
        }
        mDatabase = SQLiteDatabase.openOrCreateDatabase(mDatabaseFile.getPath(), null);
        assertNotNull(mDatabase);
        // Create and populate a test table
        mDatabase.execSQL(
                "CREATE TABLE test (_id INTEGER PRIMARY KEY, " + TEXT_COLUMN_NAME +
                        " TEXT, " + CHECKED_COLUMN_NAME + " INTEGER);");
        for (int i = 0; i < mTextContent.length; i++) {
            mDatabase.execSQL("INSERT INTO test (" + TEXT_COLUMN_NAME + ", " +
                    CHECKED_COLUMN_NAME + ") VALUES ('" + mTextContent[i] + "', " +
                    (mCheckedContent[i] ? "1" : "0") + ");");
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mCursor != null) {
            try {
                // Close the cursor on the UI thread as the list view in the alert dialog
                // will get notified of any change to the underlying cursor.
                runTestOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCursor.close();
                        mCursor = null;
                    }
                });
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        if (mDatabase != null) {
            mDatabase.close();
        }
        if (mDatabaseFile != null) {
            mDatabaseFile.delete();
        }
        super.tearDown();
    }

    private void wireBuilder(final AlertDialog.Builder builder) {
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAlertDialog = builder.show();
            }
        });
    }

    private void verifySimpleItemsContent(String[] expectedContent) {
        final int expectedCount = expectedContent.length;

        onView(withId(R.id.test_button)).perform(click());

        final ListView listView = mAlertDialog.getListView();
        assertNotNull("List view is shown", listView);

        final ListAdapter listAdapter = listView.getAdapter();
        assertEquals("List has " + expectedCount + " entries",
                expectedCount, listAdapter.getCount());

        // Test that all items are showing
        onView(withText("Dialog title")).inRoot(isDialog()).check(matches(isDisplayed()));
        for (int i = 0; i < expectedCount; i++) {
            DataInteraction rowInteraction = onData(allOf(
                    is(instanceOf(SQLiteCursor.class)),
                    TestUtilsMatchers.withCursorItemContent(TEXT_COLUMN_NAME, expectedContent[i])));
            rowInteraction.inRoot(isDialog()).check(matches(isDisplayed()));
        }

        // Test that a click on an item invokes the registered listener
        assertEquals("Before list item click", -1, mClickedItemIndex);
        int indexToClick = expectedCount - 2;
        DataInteraction interactionForClick = onData(allOf(
                is(instanceOf(SQLiteCursor.class)),
                TestUtilsMatchers.withCursorItemContent(
                        TEXT_COLUMN_NAME, expectedContent[indexToClick])));
        interactionForClick.inRoot(isDialog()).perform(click());
        assertEquals("List item clicked", indexToClick, mClickedItemIndex);
    }

    @SmallTest
    public void testSimpleItemsFromCursor() {
        mCursor = mDatabase.query("test", mProjectionWithoutChecked,
                null, null, null, null, null);
        assertNotNull(mCursor);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.alert_dialog_title)
                .setCursor(mCursor,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mClickedItemIndex = which;
                            }
                        }, "text");
        wireBuilder(builder);

        verifySimpleItemsContent(mTextContent);
    }

    /**
     * Helper method to verify the state of the multi-choice items list. It gets the String
     * array of content and verifies that:
     *
     * 1. The items in the array are rendered as CheckedTextViews inside a ListView
     * 2. Each item in the array is displayed
     * 3. Checked state of each row in the ListView corresponds to the matching entry in the
     *    passed boolean array
     */
    private void verifyMultiChoiceItemsState(String[] expectedContent,
            boolean[] checkedTracker) {
        final int expectedCount = expectedContent.length;

        final ListView listView = mAlertDialog.getListView();
        assertNotNull("List view is shown", listView);

        final ListAdapter listAdapter = listView.getAdapter();
        assertEquals("List has " + expectedCount + " entries",
                expectedCount, listAdapter.getCount());

        for (int i = 0; i < expectedCount; i++) {
            Matcher checkedStateMatcher = checkedTracker[i] ? TestUtilsMatchers.isCheckedTextView() :
                    TestUtilsMatchers.isNonCheckedTextView();
            // Check that the corresponding row is rendered as CheckedTextView with expected
            // checked state.
            DataInteraction rowInteraction = onData(allOf(
                    is(instanceOf(SQLiteCursor.class)),
                    TestUtilsMatchers.withCursorItemContent(TEXT_COLUMN_NAME, expectedContent[i])));
            rowInteraction.inRoot(isDialog()).
                    check(matches(allOf(
                            isDisplayed(),
                            isAssignableFrom(CheckedTextView.class),
                            isDescendantOfA(isAssignableFrom(ListView.class)),
                            checkedStateMatcher)));
        }
    }

    private void verifyMultiChoiceItemsContent(String[] expectedContent,
            final boolean[] checkedTracker) {
        final int expectedCount = expectedContent.length;

        onView(withId(R.id.test_button)).perform(click());

        final ListView listView = mAlertDialog.getListView();
        assertNotNull("List view is shown", listView);

        final ListAdapter listAdapter = listView.getAdapter();
        assertEquals("List has " + expectedCount + " entries",
                expectedCount, listAdapter.getCount());

        // Test that all items are showing
        onView(withText("Dialog title")).inRoot(isDialog()).check(matches(isDisplayed()));
        verifyMultiChoiceItemsState(expectedContent, checkedTracker);

        // We're going to click item #1 and test that the click listener has been invoked to
        // update the original state array
        boolean[] expectedAfterClick1 = checkedTracker.clone();
        expectedAfterClick1[1] = !expectedAfterClick1[1];
        DataInteraction interactionForClick = onData(allOf(
                is(instanceOf(SQLiteCursor.class)),
                TestUtilsMatchers.withCursorItemContent(TEXT_COLUMN_NAME, expectedContent[1])));
        interactionForClick.inRoot(isDialog()).perform(click());
        verifyMultiChoiceItemsState(expectedContent, expectedAfterClick1);

        // Now click item #1 again and test that the click listener has been invoked to update the
        // original state array again
        expectedAfterClick1[1] = !expectedAfterClick1[1];
        interactionForClick.inRoot(isDialog()).perform(click());
        verifyMultiChoiceItemsState(expectedContent, expectedAfterClick1);

        // Now we're going to click the last item and test that the click listener has been invoked
        // to update the original state array
        boolean[] expectedAfterClickLast = checkedTracker.clone();
        expectedAfterClickLast[expectedCount - 1] = !expectedAfterClickLast[expectedCount - 1];
        interactionForClick = onData(allOf(
                is(instanceOf(SQLiteCursor.class)),
                TestUtilsMatchers.withCursorItemContent(TEXT_COLUMN_NAME,
                        expectedContent[expectedCount - 1])));
        interactionForClick.inRoot(isDialog()).perform(click());
        verifyMultiChoiceItemsState(expectedContent, expectedAfterClickLast);
    }

    @SmallTest
    public void testMultiChoiceItemsFromCursor() {
        mCursor = mDatabase.query("test", mProjectionWithChecked,
                null, null, null, null, null);
        assertNotNull(mCursor);

        final boolean[] checkedTracker = mCheckedContent.clone();
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.alert_dialog_title)
                .setMultiChoiceItems(mCursor, CHECKED_COLUMN_NAME, TEXT_COLUMN_NAME,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which,
                                    boolean isChecked) {
                                // Update the underlying database with the new checked
                                // state for the specific row
                                mCursor.moveToPosition(which);
                                ContentValues valuesToUpdate = new ContentValues();
                                valuesToUpdate.put(CHECKED_COLUMN_NAME, isChecked ? 1 : 0);
                                mDatabase.update("test", valuesToUpdate,
                                        TEXT_COLUMN_NAME + " = ?",
                                        new String[] { mCursor.getString(1) } );
                                mCursor.requery();
                                checkedTracker[which] = isChecked;
                            }
                        });
        wireBuilder(builder);

        // Pass the same boolean[] array as used for initialization since our click listener
        // will be updating its content.
        verifyMultiChoiceItemsContent(mTextContent, checkedTracker);
    }

    /**
     * Helper method to verify the state of the single-choice items list. It gets the String
     * array of content and verifies that:
     *
     * 1. The items in the array are rendered as CheckedTextViews inside a ListView
     * 2. Each item in the array is displayed
     * 3. Only one row in the ListView is checked, and that corresponds to the passed
     *    integer index.
     */
    private void verifySingleChoiceItemsState(String[] expectedContent,
            int currentlyExpectedSelectionIndex) {
        final int expectedCount = expectedContent.length;

        final ListView listView = mAlertDialog.getListView();
        assertNotNull("List view is shown", listView);

        final ListAdapter listAdapter = listView.getAdapter();
        assertEquals("List has " + expectedCount + " entries",
                expectedCount, listAdapter.getCount());

        for (int i = 0; i < expectedCount; i++) {
            Matcher checkedStateMatcher = (i == currentlyExpectedSelectionIndex) ?
                    TestUtilsMatchers.isCheckedTextView() :
                    TestUtilsMatchers.isNonCheckedTextView();
            // Check that the corresponding row is rendered as CheckedTextView with expected
            // checked state.
            DataInteraction rowInteraction = onData(allOf(
                    is(instanceOf(SQLiteCursor.class)),
                    TestUtilsMatchers.withCursorItemContent(TEXT_COLUMN_NAME, expectedContent[i])));
            rowInteraction.inRoot(isDialog()).
                    check(matches(allOf(
                            isDisplayed(),
                            isAssignableFrom(CheckedTextView.class),
                            isDescendantOfA(isAssignableFrom(ListView.class)),
                            checkedStateMatcher)));
        }
    }

    private void verifySingleChoiceItemsContent(String[] expectedContent,
            int initialSelectionIndex) {
        final int expectedCount = expectedContent.length;
        int currentlyExpectedSelectionIndex = initialSelectionIndex;

        onView(withId(R.id.test_button)).perform(click());

        // Test that all items are showing
        onView(withText("Dialog title")).inRoot(isDialog()).check(matches(isDisplayed()));
        verifySingleChoiceItemsState(expectedContent, currentlyExpectedSelectionIndex);

        // We're going to click the first unselected item and test that the click listener has
        // been invoked.
        currentlyExpectedSelectionIndex = (currentlyExpectedSelectionIndex == 0) ? 1 : 0;
        DataInteraction interactionForClick = onData(allOf(
                is(instanceOf(SQLiteCursor.class)),
                TestUtilsMatchers.withCursorItemContent(TEXT_COLUMN_NAME,
                        expectedContent[currentlyExpectedSelectionIndex])));
        interactionForClick.inRoot(isDialog()).perform(click());
        assertEquals("Selected first single-choice item",
                currentlyExpectedSelectionIndex, mClickedItemIndex);
        verifySingleChoiceItemsState(expectedContent, currentlyExpectedSelectionIndex);

        // Now click the same item again and test that the selection has not changed
        interactionForClick.inRoot(isDialog()).perform(click());
        assertEquals("Selected first single-choice item again",
                currentlyExpectedSelectionIndex, mClickedItemIndex);
        verifySingleChoiceItemsState(expectedContent, currentlyExpectedSelectionIndex);

        // Now we're going to click the last item and test that the click listener has been invoked
        // to update the original state array
        currentlyExpectedSelectionIndex = expectedCount - 1;
        interactionForClick = onData(allOf(
                is(instanceOf(SQLiteCursor.class)),
                TestUtilsMatchers.withCursorItemContent(TEXT_COLUMN_NAME,
                        expectedContent[currentlyExpectedSelectionIndex])));
        interactionForClick.inRoot(isDialog()).perform(click());
        assertEquals("Selected last single-choice item",
                currentlyExpectedSelectionIndex, mClickedItemIndex);
        verifySingleChoiceItemsState(expectedContent, currentlyExpectedSelectionIndex);
    }

    @SmallTest
    public void testSingleChoiceItemsFromCursor() {
        mCursor = mDatabase.query("test", mProjectionWithoutChecked,
                null, null, null, null, null);
        assertNotNull(mCursor);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.alert_dialog_title)
                .setSingleChoiceItems(mCursor, 2, TEXT_COLUMN_NAME,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mClickedItemIndex = which;
                            }
                        });
        wireBuilder(builder);

        verifySingleChoiceItemsContent(mTextContent, 2);
    }
}
