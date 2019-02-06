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

package androidx.car.widget;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static androidx.test.espresso.contrib.RecyclerViewActions.scrollToPosition;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.car.test.R;
import androidx.car.util.CarUxRestrictionsTestUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
* Tests the layout configuration and switch functionality of {@link SwitchListItem}.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class SwitchListItemTest {

    @Rule
    public ActivityTestRule<PagedListViewTestActivity> mActivityRule =
            new ActivityTestRule<>(PagedListViewTestActivity.class);

    private PagedListViewTestActivity mActivity;
    private PagedListView mPagedListView;
    private ListItemAdapter mAdapter;

    private boolean isAutoDevice() {
        PackageManager packageManager = mActivityRule.getActivity().getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    @Before
    public void setUp() {
        Assume.assumeTrue(isAutoDevice());

        mActivity = mActivityRule.getActivity();
        mPagedListView = mActivity.findViewById(R.id.paged_list_view);
    }

    @Test
    public void testDefaultVisibility_EmptyItemShowsSwitch() {
        SwitchListItem item = new SwitchListItem(mActivity);
        setupPagedListView(Arrays.asList(item));

        ViewGroup itemView = (ViewGroup)
                mPagedListView.getRecyclerView().getLayoutManager().getChildAt(0);
        int childCount = itemView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View view = itemView.getChildAt(i);
            // |view| could be container in view holder, so exempt ViewGroup.
            if (view instanceof Switch || view instanceof ViewGroup) {
                assertThat(view.getVisibility(), is(equalTo(View.VISIBLE)));
            } else {
                assertThat("Visibility of view "
                                + mActivity.getResources().getResourceEntryName(view.getId())
                                + " by default should be GONE.",
                        view.getVisibility(), is(equalTo(View.GONE)));
            }
        }
    }

    @Test
    public void testItemIsEnabledByDefault() {
        SwitchListItem item0 = new SwitchListItem(mActivity);

        List<SwitchListItem> items = Arrays.asList(item0);
        setupPagedListView(items);

        assertTrue(getViewHolderAtPosition(0).itemView.isEnabled());
    }

    @Test
    public void testDisablingItem() {
        SwitchListItem item0 = new SwitchListItem(mActivity);

        List<SwitchListItem> items = Arrays.asList(item0);
        setupPagedListView(items);

        item0.setEnabled(false);
        refreshUi();

        assertFalse(getViewHolderAtPosition(0).itemView.isEnabled());
    }

    @Test
    public void testClickableItem_DefaultNotClickable() {
        SwitchListItem item0 = new SwitchListItem(mActivity);

        List<SwitchListItem> items = Arrays.asList(item0);
        setupPagedListView(items);

        assertFalse(getViewHolderAtPosition(0).itemView.isClickable());
    }

    @Test
    public void testClickableItem_setClickable() {
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setClickable(true);

        List<SwitchListItem> items = Arrays.asList(item0);
        setupPagedListView(items);

        assertTrue(getViewHolderAtPosition(0).itemView.isClickable());
    }

    @Test
    public void testClickableItem_ClickingTogglesSwitch() {
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setClickable(true);

        List<SwitchListItem> items = Arrays.asList(item0);
        setupPagedListView(items);

        onView(withId(R.id.recycler_view)).perform(actionOnItemAtPosition(0, click()));

        assertTrue(getViewHolderAtPosition(0).getSwitch().isChecked());
    }

    @Test
    public void testSwitchStatePersistsOnRebind() {
        SwitchListItem item0 = new SwitchListItem(mActivity);
        // Switch initially checked.
        item0.setSwitchState(true);

        setupPagedListView(Collections.singletonList(item0));
        SwitchListItem.ViewHolder viewHolder = getViewHolderAtPosition(0);

        toggleChecked(viewHolder.getSwitch());

        viewHolder = getViewHolderAtPosition(0);
        assertThat(viewHolder.getSwitch().isChecked(), is(equalTo(false)));
    }

    @Test
    public void testSetSwitchState() {
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setSwitchState(true);

        setupPagedListView(Arrays.asList(item0));

        item0.setSwitchState(false);
        refreshUi();

        SwitchListItem.ViewHolder viewHolder = getViewHolderAtPosition(0);
        assertThat(viewHolder.getSwitch().getVisibility(), is(equalTo(View.VISIBLE)));
        assertThat(viewHolder.getSwitch().isChecked(), is(equalTo(false)));
    }

    @Test
    public void testSetSwitchStateCallsListener() {
        CompoundButton.OnCheckedChangeListener listener =
                mock(CompoundButton.OnCheckedChangeListener.class);
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setSwitchOnCheckedChangeListener(listener);

        setupPagedListView(Collections.singletonList(item0));

        item0.setSwitchState(true);
        refreshUi();
        verify(listener).onCheckedChanged(any(CompoundButton.class), eq(true));
    }

    @Test
    public void testRefreshingUiDoesNotCallListener() {
        CompoundButton.OnCheckedChangeListener listener =
                mock(CompoundButton.OnCheckedChangeListener.class);
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setSwitchOnCheckedChangeListener(listener);

        setupPagedListView(Collections.singletonList(item0));

        refreshUi();
        verify(listener, never()).onCheckedChanged(any(CompoundButton.class), anyBoolean());
    }

    @Test
    public void testSetSwitchStateBeforeFirstBindCallsListener() {
        CompoundButton.OnCheckedChangeListener listener =
                mock(CompoundButton.OnCheckedChangeListener.class);
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setSwitchOnCheckedChangeListener(listener);
        item0.setSwitchState(true);

        setupPagedListView(Collections.singletonList(item0));

        verify(listener).onCheckedChanged(any(CompoundButton.class), eq(true));
    }

    @Test
    public void testSwitchToggleCallsListener() {
        CompoundButton.OnCheckedChangeListener listener =
                mock(CompoundButton.OnCheckedChangeListener.class);
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setSwitchOnCheckedChangeListener(listener);

        setupPagedListView(Collections.singletonList(item0));

        SwitchListItem.ViewHolder viewHolder = getViewHolderAtPosition(0);
        toggleChecked(viewHolder.getSwitch());

        // Expect true because switch defaults to false.
        verify(listener).onCheckedChanged(any(CompoundButton.class), eq(true));
    }

    @Test
    public void testSetSwitchStateNotDirtyDoesNotCallListener() {
        CompoundButton.OnCheckedChangeListener listener =
                mock(CompoundButton.OnCheckedChangeListener.class);
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setSwitchState(true);
        item0.setSwitchOnCheckedChangeListener(listener);

        setupPagedListView(Collections.singletonList(item0));

        item0.setSwitchState(true);
        refreshUi();

        verify(listener, never()).onCheckedChanged(any(CompoundButton.class), anyBoolean());
    }

    @Test
    public void testCheckingSwitch() {
        final boolean[] clicked = {false};
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setSwitchOnCheckedChangeListener((button, isChecked) -> {
            // Initial value is false.
            assertTrue(isChecked);
            clicked[0] = true;
        });

        List<SwitchListItem> items = Arrays.asList(item0);
        setupPagedListView(items);

        onView(withId(R.id.recycler_view)).perform(
                actionOnItemAtPosition(0, clickChildViewWithId(R.id.switch_widget)));
        assertTrue(clicked[0]);
    }

    @Test
    public void testDividerVisibility() {
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setShowSwitchDivider(true);

        SwitchListItem item1 = new SwitchListItem(mActivity);
        item0.setShowSwitchDivider(false);

        List<SwitchListItem> items = Arrays.asList(item0, item1);
        setupPagedListView(items);

        SwitchListItem.ViewHolder viewHolder = getViewHolderAtPosition(0);
        assertThat(viewHolder.getSwitch().getVisibility(), is(equalTo(View.VISIBLE)));
        assertThat(viewHolder.getSwitch().getVisibility(), is(equalTo(View.VISIBLE)));

        viewHolder = getViewHolderAtPosition(1);
        assertThat(viewHolder.getSwitch().getVisibility(), is(equalTo(View.VISIBLE)));
        assertThat(viewHolder.getSwitchDivider().getVisibility(), is(equalTo(View.GONE)));
    }

    @Test
    public void testPrimaryActionVisible() {
        SwitchListItem largeIcon = new SwitchListItem(mActivity);
        largeIcon.setPrimaryActionIcon(
                Icon.createWithResource(getContext(), android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_LARGE);

        SwitchListItem mediumIcon = new SwitchListItem(mActivity);
        mediumIcon.setPrimaryActionIcon(
                Icon.createWithResource(getContext(), android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_MEDIUM);

        SwitchListItem smallIcon = new SwitchListItem(mActivity);
        smallIcon.setPrimaryActionIcon(
                Icon.createWithResource(getContext(), android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_SMALL);

        List<SwitchListItem> items = Arrays.asList(largeIcon, mediumIcon, smallIcon);
        setupPagedListView(items);

        assertThat(getViewHolderAtPosition(0).getPrimaryIcon().getVisibility(),
                is(equalTo(View.VISIBLE)));
        assertThat(getViewHolderAtPosition(1).getPrimaryIcon().getVisibility(),
                is(equalTo(View.VISIBLE)));
        assertThat(getViewHolderAtPosition(2).getPrimaryIcon().getVisibility(),
                is(equalTo(View.VISIBLE)));
    }

    @Test
    public void testTextVisible() {
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setTitle("title");

        SwitchListItem item1 = new SwitchListItem(mActivity);
        item1.setBody("body");

        List<SwitchListItem> items = Arrays.asList(item0, item1);
        setupPagedListView(items);

        assertThat(getViewHolderAtPosition(0).getTitle().getVisibility(),
                is(equalTo(View.VISIBLE)));
        assertThat(getViewHolderAtPosition(1).getBody().getVisibility(),
                is(equalTo(View.VISIBLE)));
    }

    @Test
    public void testTextStartMarginMatchesPrimaryActionType() {
        SwitchListItem largeIcon = new SwitchListItem(mActivity);
        largeIcon.setPrimaryActionIcon(
                Icon.createWithResource(getContext(), android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_LARGE);

        SwitchListItem mediumIcon = new SwitchListItem(mActivity);
        mediumIcon.setPrimaryActionIcon(
                Icon.createWithResource(getContext(), android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_MEDIUM);

        SwitchListItem smallIcon = new SwitchListItem(mActivity);
        smallIcon.setPrimaryActionIcon(
                Icon.createWithResource(getContext(), android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_SMALL);

        SwitchListItem emptyIcon = new SwitchListItem(mActivity);
        emptyIcon.setPrimaryActionEmptyIcon();

        SwitchListItem noIcon = new SwitchListItem(mActivity);
        noIcon.setPrimaryActionNoIcon();

        List<SwitchListItem> items = Arrays.asList(
                largeIcon, mediumIcon, smallIcon, emptyIcon, noIcon);
        List<Integer> expectedStartMargin = Arrays.asList(
                R.dimen.car_keyline_4,  // Large icon.
                R.dimen.car_keyline_3,  // Medium icon.
                R.dimen.car_keyline_3,  // Small icon.
                R.dimen.car_keyline_3,  // Empty icon.
                R.dimen.car_keyline_1); // No icon.
        setupPagedListView(items);

        for (int i = 0; i < items.size(); i++) {
            SwitchListItem.ViewHolder viewHolder = getViewHolderAtPosition(i);

            int expected = ApplicationProvider.getApplicationContext().getResources()
                    .getDimensionPixelSize(expectedStartMargin.get(i));
            assertThat(((ViewGroup.MarginLayoutParams) viewHolder.getTitle().getLayoutParams())
                    .getMarginStart(), is(equalTo(expected)));
            assertThat(((ViewGroup.MarginLayoutParams) viewHolder.getBody().getLayoutParams())
                    .getMarginStart(), is(equalTo(expected)));
        }
    }

    @Test
    public void testItemWithOnlyTitleIsSingleLine() {
        // Only space.
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setTitle(" ");

        // Underscore.
        SwitchListItem item1 = new SwitchListItem(mActivity);
        item1.setTitle("______");

        SwitchListItem item2 = new SwitchListItem(mActivity);
        item2.setTitle("ALL UPPER CASE");

        // String wouldn't fit in one line.
        SwitchListItem item3 = new SwitchListItem(mActivity);
        item3.setTitle(ApplicationProvider.getApplicationContext().getResources().getString(
                R.string.over_uxr_text_length_limit));

        List<SwitchListItem> items = Arrays.asList(item0, item1, item2, item3);
        setupPagedListView(items);

        double singleLineHeight =
                ApplicationProvider.getApplicationContext().getResources().getDimension(
                R.dimen.car_single_line_list_item_height);

        LinearLayoutManager layoutManager =
                (LinearLayoutManager) mPagedListView.getRecyclerView().getLayoutManager();
        for (int i = 0; i < items.size(); i++) {
            assertThat((double) layoutManager.findViewByPosition(i).getHeight(),
                    is(closeTo(singleLineHeight, 1.0d)));
        }
    }

    @Test
    public void testItemWithBodyTextIsAtLeastDoubleLine() {
        // Only space.
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setBody(" ");

        // Underscore.
        SwitchListItem item1 = new SwitchListItem(mActivity);
        item1.setBody("____");

        // String wouldn't fit in one line.
        SwitchListItem item2 = new SwitchListItem(mActivity);
        item2.setBody(ApplicationProvider.getApplicationContext().getResources().getString(
                R.string.over_uxr_text_length_limit));

        List<SwitchListItem> items = Arrays.asList(item0, item1, item2);
        setupPagedListView(items);

        final int doubleLineHeight =
                (int) ApplicationProvider.getApplicationContext().getResources().getDimension(
                        R.dimen.car_double_line_list_item_height);

        LinearLayoutManager layoutManager =
                (LinearLayoutManager) mPagedListView.getRecyclerView().getLayoutManager();
        for (int i = 0; i < items.size(); i++) {
            assertThat(layoutManager.findViewByPosition(i).getHeight(),
                    is(greaterThanOrEqualTo(doubleLineHeight)));
        }
    }

    @Test
    public void testSetPrimaryActionIcon() {
        SwitchListItem item = new SwitchListItem(mActivity);
        item.setPrimaryActionIcon(
                Icon.createWithResource(mActivity, android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_LARGE);

        List<SwitchListItem> items = Arrays.asList(item);
        setupPagedListView(items);

        assertThat(getViewHolderAtPosition(0).getPrimaryIcon().getDrawable(), is(notNullValue()));
    }

    @Test
    public void testPrimaryIconSizesInIncreasingOrder() {
        SwitchListItem small = new SwitchListItem(mActivity);
        small.setPrimaryActionIcon(
                Icon.createWithResource(mActivity, android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_SMALL);

        SwitchListItem medium = new SwitchListItem(mActivity);
        medium.setPrimaryActionIcon(
                Icon.createWithResource(mActivity, android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_MEDIUM);

        SwitchListItem large = new SwitchListItem(mActivity);
        large.setPrimaryActionIcon(
                Icon.createWithResource(mActivity, android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_LARGE);

        List<SwitchListItem> items = Arrays.asList(small, medium, large);
        setupPagedListView(items);

        SwitchListItem.ViewHolder smallVH = getViewHolderAtPosition(0);
        SwitchListItem.ViewHolder mediumVH = getViewHolderAtPosition(1);
        SwitchListItem.ViewHolder largeVH = getViewHolderAtPosition(2);

        assertThat(largeVH.getPrimaryIcon().getHeight(), is(greaterThan(
                mediumVH.getPrimaryIcon().getHeight())));
        assertThat(mediumVH.getPrimaryIcon().getHeight(), is(greaterThan(
                smallVH.getPrimaryIcon().getHeight())));
    }

    @Test
    public void testLargePrimaryIconHasNoStartMargin() {
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setPrimaryActionIcon(
                Icon.createWithResource(mActivity, android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_LARGE);

        List<SwitchListItem> items = Arrays.asList(item0);
        setupPagedListView(items);

        SwitchListItem.ViewHolder viewHolder = getViewHolderAtPosition(0);
        assertThat(((ViewGroup.MarginLayoutParams) viewHolder.getPrimaryIcon().getLayoutParams())
                .getMarginStart(), is(equalTo(0)));
    }

    @Test
    public void testSmallAndMediumPrimaryIconStartMargin() {
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setPrimaryActionIcon(
                Icon.createWithResource(mActivity, android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_SMALL);

        SwitchListItem item1 = new SwitchListItem(mActivity);
        item1.setPrimaryActionIcon(
                Icon.createWithResource(mActivity, android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_MEDIUM);

        List<SwitchListItem> items = Arrays.asList(item0, item1);
        setupPagedListView(items);

        int expected =
                ApplicationProvider.getApplicationContext().getResources().getDimensionPixelSize(
                R.dimen.car_keyline_1);

        SwitchListItem.ViewHolder viewHolder = getViewHolderAtPosition(0);
        assertThat(((ViewGroup.MarginLayoutParams) viewHolder.getPrimaryIcon().getLayoutParams())
                .getMarginStart(), is(equalTo(expected)));

        viewHolder = getViewHolderAtPosition(1);
        assertThat(((ViewGroup.MarginLayoutParams) viewHolder.getPrimaryIcon().getLayoutParams())
                .getMarginStart(), is(equalTo(expected)));
    }

    @Test
    public void testSmallPrimaryIconTopMarginRemainsTheSameRegardlessOfTextLength() {
        final String longText =
                ApplicationProvider.getApplicationContext().getResources().getString(
                R.string.over_uxr_text_length_limit);

        // Single line item.
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setPrimaryActionIcon(
                Icon.createWithResource(mActivity, android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_SMALL);
        item0.setTitle("one line text");

        // Double line item with one line text.
        SwitchListItem item1 = new SwitchListItem(mActivity);
        item1.setPrimaryActionIcon(
                Icon.createWithResource(mActivity, android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_SMALL);
        item1.setTitle("one line text");
        item1.setBody("one line text");

        // Double line item with long text.
        SwitchListItem item2 = new SwitchListItem(mActivity);
        item2.setPrimaryActionIcon(
                Icon.createWithResource(mActivity, android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_SMALL);
        item2.setTitle("one line text");
        item2.setBody(longText);

        // Body text only - long text.
        SwitchListItem item3 = new SwitchListItem(mActivity);
        item3.setPrimaryActionIcon(
                Icon.createWithResource(mActivity, android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_SMALL);
        item3.setBody(longText);

        // Body text only - one line text.
        SwitchListItem item4 = new SwitchListItem(mActivity);
        item4.setPrimaryActionIcon(
                Icon.createWithResource(mActivity, android.R.drawable.sym_def_app_icon),
                SwitchListItem.PRIMARY_ACTION_ICON_SIZE_SMALL);
        item4.setBody("one line text");

        List<SwitchListItem> items = Arrays.asList(item0, item1, item2, item3, item4);
        setupPagedListView(items);

        for (int i = 1; i < items.size(); i++) {
            onView(withId(R.id.recycler_view)).perform(scrollToPosition(i));
            // Implementation uses integer division so it may be off by 1 vs centered vertically.
            assertThat((double) getViewHolderAtPosition(i - 1).getPrimaryIcon().getTop(),
                    is(closeTo(
                    (double) getViewHolderAtPosition(i).getPrimaryIcon().getTop(), 1.0d)));
        }
    }

    @Test
    public void testCustomViewBinderBindsLast() {
        final String updatedTitle = "updated title";

        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setTitle("original title");
        item0.addViewBinder((viewHolder) -> viewHolder.getTitle().setText(updatedTitle));

        List<SwitchListItem> items = Arrays.asList(item0);
        setupPagedListView(items);

        SwitchListItem.ViewHolder viewHolder = getViewHolderAtPosition(0);
        assertThat(viewHolder.getTitle().getText(), is(equalTo(updatedTitle)));
    }

    @Test
    public void testCustomViewBinderOnUnusedViewsHasNoEffect() {
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.addViewBinder((viewHolder) -> viewHolder.getBody().setText("text"));

        List<SwitchListItem> items = Arrays.asList(item0);
        setupPagedListView(items);

        SwitchListItem.ViewHolder viewHolder = getViewHolderAtPosition(0);
        assertThat(viewHolder.getBody().getVisibility(), is(equalTo(View.GONE)));
        // Custom binder interacts with body but has no effect.
        // Expect card height to remain single line.
        assertThat((double) viewHolder.itemView.getHeight(), is(closeTo(
                ApplicationProvider.getApplicationContext().getResources().getDimension(
                        R.dimen.car_single_line_list_item_height), 1.0d)));
    }

    @Test
    public void testRevertingViewBinder() throws Throwable {
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setBody("one item");
        item0.addViewBinder(
                (viewHolder) -> viewHolder.getBody().setEllipsize(TextUtils.TruncateAt.END),
                (viewHolder -> viewHolder.getBody().setEllipsize(null)));

        List<SwitchListItem> items = Arrays.asList(item0);
        setupPagedListView(items);

        SwitchListItem.ViewHolder viewHolder = getViewHolderAtPosition(0);

        // Bind view holder to a new item - the customization made by item0 should be reverted.
        SwitchListItem item1 = new SwitchListItem(mActivity);
        item1.setBody("new item");
        mActivityRule.runOnUiThread(() -> item1.bind(viewHolder));

        assertThat(viewHolder.getBody().getEllipsize(), is(equalTo(null)));
    }

    @Test
    public void testRemovingViewBinder() {
        SwitchListItem item0 = new SwitchListItem(mActivity);
        item0.setBody("one item");
        ListItem.ViewBinder<SwitchListItem.ViewHolder> binder =
                (viewHolder) -> viewHolder.getTitle().setEllipsize(TextUtils.TruncateAt.END);
        item0.addViewBinder(binder);

        assertTrue(item0.removeViewBinder(binder));

        List<SwitchListItem> items = Arrays.asList(item0);
        setupPagedListView(items);

        assertThat(getViewHolderAtPosition(0).getBody().getEllipsize(), is(equalTo(null)));
    }

    @Test
    public void testUpdateItem() {
        SwitchListItem item = new SwitchListItem(mActivity);
        setupPagedListView(Arrays.asList(item));

        String title = "updated title";
        item.setTitle(title);

        refreshUi();

        SwitchListItem.ViewHolder viewHolder = getViewHolderAtPosition(0);
        assertThat(viewHolder.getTitle().getText(), is(equalTo(title)));
    }

    @Test
    public void testUxRestrictionsChange() {
        String longText = mActivity.getString(R.string.over_uxr_text_length_limit);
        SwitchListItem item = new SwitchListItem(mActivity);
        item.setBody(longText);

        setupPagedListView(Arrays.asList(item));

        SwitchListItem.ViewHolder viewHolder = getViewHolderAtPosition(0);
        // Default behavior without UXR is unrestricted.
        assertThat(viewHolder.getBody().getText(), is(equalTo(longText)));

        viewHolder.onUxRestrictionsChanged(CarUxRestrictionsTestUtils.getFullyRestricted());
        refreshUi();

        // Verify that the body text length is limited.
        assertThat(viewHolder.getBody().getText().length(), is(lessThan(longText.length())));
    }

    @Test
    public void testUxRestrictionsChangesDoNotAlterExistingInputFilters() {
        InputFilter filter = new InputFilter.AllCaps(Locale.US);
        String bodyText = "body_text";
        SwitchListItem item = new SwitchListItem(mActivity);
        item.setBody(bodyText);
        item.addViewBinder(vh -> vh.getBody().setFilters(new InputFilter[] {filter}));

        setupPagedListView(Arrays.asList(item));

        SwitchListItem.ViewHolder viewHolder = getViewHolderAtPosition(0);

        // Toggle UX restrictions between fully restricted and unrestricted should not affect
        // existing filters.
        viewHolder.onUxRestrictionsChanged(CarUxRestrictionsTestUtils.getFullyRestricted());
        refreshUi();
        assertTrue(Arrays.asList(viewHolder.getBody().getFilters()).contains(filter));

        viewHolder.onUxRestrictionsChanged(CarUxRestrictionsTestUtils.getBaseline());
        refreshUi();
        assertTrue(Arrays.asList(viewHolder.getBody().getFilters()).contains(filter));
    }

    @Test
    public void testDisabledItemDisablesViewHolder() {
        SwitchListItem item = new SwitchListItem(mActivity);
        item.setTitle("title");
        item.setBody("body");
        item.setEnabled(false);

        setupPagedListView(Arrays.asList(item));

        SwitchListItem.ViewHolder viewHolder = getViewHolderAtPosition(0);
        assertFalse(viewHolder.getTitle().isEnabled());
        assertFalse(viewHolder.getBody().isEnabled());
        assertFalse(viewHolder.getSwitch().isEnabled());
    }

    @Test
    public void testDisabledItemDoesNotRespondToClick() {
        // Disabled view will not respond to touch event.
        // Current test setup makes it hard to test, since clickChildViewWithId() directly calls
        // performClick() on a view, bypassing the way UI handles disabled state.

        // We are explicitly setting itemView so test it here.
        boolean[] clicked = new boolean[]{false};
        SwitchListItem item = new SwitchListItem(mActivity);
        item.setEnabled(false);

        setupPagedListView(Arrays.asList(item));

        onView(withId(R.id.recycler_view)).perform(
                actionOnItemAtPosition(0, click()));

        assertFalse(clicked[0]);
    }

    private Context getContext() {
        return mActivity;
    }

    private void refreshUi() {
        try {
            mActivityRule.runOnUiThread(() -> mAdapter.notifyDataSetChanged());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            throw new RuntimeException(throwable);
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void setupPagedListView(List<SwitchListItem> items) {
        ListItemProvider provider = new ListItemProvider.ListProvider(new ArrayList<>(items));
        try {
            mAdapter = new ListItemAdapter(mActivity, provider);
            mActivityRule.runOnUiThread(() -> mPagedListView.setAdapter(mAdapter));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            throw new RuntimeException(throwable);
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private SwitchListItem.ViewHolder getViewHolderAtPosition(int position) {
        return (SwitchListItem.ViewHolder) mPagedListView.getRecyclerView()
                .findViewHolderForAdapterPosition(position);
    }

    private void toggleChecked(CompoundButton button) {
        try {
            mActivityRule.runOnUiThread(button::toggle);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            throw new RuntimeException(throwable);
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static ViewAction clickChildViewWithId(final int id) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return null;
            }

            @Override
            public String getDescription() {
                return "Click on a child view with specific id.";
            }

            @Override
            public void perform(UiController uiController, View view) {
                View v = view.findViewById(id);
                v.performClick();
            }
        };
    }
}
