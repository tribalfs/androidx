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

package androidx.car.app.model;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.os.RemoteException;

import androidx.car.app.OnDoneCallback;
import androidx.car.app.TestUtils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.internal.DoNotInstrument;

/** Tests for {@link SearchTemplate}. */
@RunWith(RobolectricTestRunner.class)
@DoNotInstrument
public class SearchTemplateTest {
    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock
    SearchTemplate.SearchCallback mMockSearchCallback;

    @Test
    public void createInstance_isLoading_hasList_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchTemplate.builder(mMockSearchCallback)
                        .setLoading(true)
                        .setItemList(ItemList.builder().build())
                        .build());
    }

    @Test
    public void addList_selectable_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchTemplate.builder(mMockSearchCallback)
                        .setItemList(TestUtils.createItemList(6, true))
                        .build());

        // Positive cases.
        SearchTemplate.builder(mMockSearchCallback)
                .setItemList(TestUtils.createItemList(6, false))
                .build();
    }

    @Test
    public void addList_moreThanMaxTexts_throws() {
        Row rowExceedsMaxTexts =
                Row.builder().setTitle("Title").addText("text1").addText("text2").addText(
                        "text3").build();
        Row rowMeetingMaxTexts =
                Row.builder().setTitle("Title").addText("text1").addText("text2").build();
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchTemplate.builder(mMockSearchCallback)
                        .setItemList(ItemList.builder().addItem(rowExceedsMaxTexts).build())
                        .build());

        // Positive cases.
        SearchTemplate.builder(mMockSearchCallback)
                .setItemList(ItemList.builder().addItem(rowMeetingMaxTexts).build())
                .build();
    }

    @Test
    public void addList_hasToggle_throws() {
        Row rowWithToggle =
                Row.builder().setTitle("Title").setToggle(Toggle.builder(isChecked -> {
                }).build()).build();
        Row rowMeetingRestrictions =
                Row.builder().setTitle("Title").addText("text1").addText("text2").build();
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchTemplate.builder(mMockSearchCallback)
                        .setItemList(ItemList.builder().addItem(rowWithToggle).build())
                        .build());

        // Positive cases.
        SearchTemplate.builder(mMockSearchCallback)
                .setItemList(ItemList.builder().addItem(rowMeetingRestrictions).build())
                .build();
    }

    @Test
    public void buildEmpty_nullValues() {
        SearchTemplate searchTemplate = SearchTemplate.builder(mMockSearchCallback).build();

        assertThat(searchTemplate.getInitialSearchText()).isNull();
        assertThat(searchTemplate.getSearchHint()).isNull();
        assertThat(searchTemplate.getActionStrip()).isNull();
        assertThat(searchTemplate.getHeaderAction()).isNull();
    }

    @Test
    public void buildWithValues() throws RemoteException {
        String initialSearchText = "searchTemplate for this!!";
        String searchHint = "This is not a hint";
        ItemList itemList = ItemList.builder().addItem(
                Row.builder().setTitle("foo").build()).build();
        ActionStrip actionStrip = ActionStrip.builder().addAction(Action.BACK).build();

        SearchTemplate searchTemplate =
                SearchTemplate.builder(mMockSearchCallback)
                        .setHeaderAction(Action.BACK)
                        .setActionStrip(actionStrip)
                        .setInitialSearchText(initialSearchText)
                        .setSearchHint(searchHint)
                        .setItemList(itemList)
                        .build();
        OnDoneCallback onDoneCallback = mock(OnDoneCallback.class);

        assertThat(searchTemplate.getInitialSearchText()).isEqualTo(initialSearchText);
        assertThat(searchTemplate.getSearchHint()).isEqualTo(searchHint);
        assertThat(searchTemplate.getItemList()).isEqualTo(itemList);
        assertThat(searchTemplate.getActionStrip()).isEqualTo(actionStrip);
        assertThat(searchTemplate.getHeaderAction()).isEqualTo(Action.BACK);

        String searchText = "foo";
        searchTemplate.getSearchCallback().onSearchSubmitted(searchText, onDoneCallback);
        verify(mMockSearchCallback).onSearchSubmitted(searchText);
        verify(onDoneCallback).onSuccess(null);
    }

    @Test
    public void buildWithValues_failureOnSearchSubmitted() throws RemoteException {
        String initialSearchText = "searchTemplate for this!!";
        String searchHint = "This is not a hint";
        ItemList itemList = ItemList.builder().addItem(
                Row.builder().setTitle("foo").build()).build();
        ActionStrip actionStrip = ActionStrip.builder().addAction(Action.BACK).build();

        SearchTemplate searchTemplate =
                SearchTemplate.builder(mMockSearchCallback)
                        .setHeaderAction(Action.BACK)
                        .setActionStrip(actionStrip)
                        .setInitialSearchText(initialSearchText)
                        .setSearchHint(searchHint)
                        .setItemList(itemList)
                        .build();


        assertThat(searchTemplate.getInitialSearchText()).isEqualTo(initialSearchText);
        assertThat(searchTemplate.getSearchHint()).isEqualTo(searchHint);
        assertThat(searchTemplate.getItemList()).isEqualTo(itemList);
        assertThat(searchTemplate.getActionStrip()).isEqualTo(actionStrip);
        assertThat(searchTemplate.getHeaderAction()).isEqualTo(Action.BACK);

        String searchText = "foo";
        String testExceptionMessage = "Test exception";
        doThrow(new RuntimeException(testExceptionMessage)).when(
                mMockSearchCallback).onSearchSubmitted(searchText);
        OnDoneCallback onDoneCallback = mock(OnDoneCallback.class);

        try {
            searchTemplate.getSearchCallback().onSearchSubmitted(searchText, onDoneCallback);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains(testExceptionMessage);
        }

        verify(mMockSearchCallback).onSearchSubmitted(searchText);
        verify(onDoneCallback).onFailure(any());
    }

    @Test
    public void createInstance_setHeaderAction_invalidActionThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SearchTemplate.builder(mMockSearchCallback)
                        .setHeaderAction(
                                Action.builder().setTitle("Action").setOnClickListener(
                                        () -> {
                                        }).build()));
    }

    @Test
    public void equals() {
        SearchTemplate template =
                SearchTemplate.builder(mMockSearchCallback)
                        .setHeaderAction(Action.BACK)
                        .setActionStrip(ActionStrip.builder().addAction(Action.BACK).build())
                        .setInitialSearchText("foo")
                        .setSearchHint("hint")
                        .setShowKeyboardByDefault(false)
                        .setLoading(false)
                        .setItemList(ItemList.builder().build())
                        .build();

        assertThat(template)
                .isEqualTo(
                        SearchTemplate.builder(mMockSearchCallback)
                                .setHeaderAction(Action.BACK)
                                .setActionStrip(
                                        ActionStrip.builder().addAction(Action.BACK).build())
                                .setInitialSearchText("foo")
                                .setSearchHint("hint")
                                .setShowKeyboardByDefault(false)
                                .setLoading(false)
                                .setItemList(ItemList.builder().build())
                                .build());
    }

    @Test
    public void notEquals_differentHeaderAction() {
        SearchTemplate template =
                SearchTemplate.builder(mMockSearchCallback).setHeaderAction(Action.BACK).build();
        assertThat(template)
                .isNotEqualTo(
                        SearchTemplate.builder(mMockSearchCallback).setHeaderAction(
                                Action.APP_ICON).build());
    }

    @Test
    public void notEquals_differentActionStrip() {
        SearchTemplate template =
                SearchTemplate.builder(mMockSearchCallback)
                        .setActionStrip(ActionStrip.builder().addAction(Action.BACK).build())
                        .build();
        assertThat(template)
                .isNotEqualTo(
                        SearchTemplate.builder(mMockSearchCallback)
                                .setActionStrip(
                                        ActionStrip.builder().addAction(Action.APP_ICON).build())
                                .build());
    }

    @Test
    public void notEquals_differentInitialSearchText() {
        SearchTemplate template =
                SearchTemplate.builder(mMockSearchCallback).setInitialSearchText("foo").build();
        assertThat(template)
                .isNotEqualTo(
                        SearchTemplate.builder(mMockSearchCallback).setInitialSearchText(
                                "bar").build());
    }

    @Test
    public void notEquals_differentSearchHint() {
        SearchTemplate template =
                SearchTemplate.builder(mMockSearchCallback).setSearchHint("foo").build();
        assertThat(template)
                .isNotEqualTo(SearchTemplate.builder(mMockSearchCallback).setSearchHint(
                        "bar").build());
    }

    @Test
    public void notEquals_differentKeyboardEnabled() {
        SearchTemplate template =
                SearchTemplate.builder(mMockSearchCallback).setShowKeyboardByDefault(true).build();
        assertThat(template)
                .isNotEqualTo(
                        SearchTemplate.builder(mMockSearchCallback).setShowKeyboardByDefault(
                                false).build());
    }

    @Test
    public void notEquals_differentItemList() {
        SearchTemplate template =
                SearchTemplate.builder(mMockSearchCallback).setItemList(
                        ItemList.builder().build()).build();
        assertThat(template)
                .isNotEqualTo(
                        SearchTemplate.builder(mMockSearchCallback)
                                .setItemList(
                                        ItemList.builder().addItem(
                                                Row.builder().setTitle("Title").build()).build())
                                .build());
    }
}
