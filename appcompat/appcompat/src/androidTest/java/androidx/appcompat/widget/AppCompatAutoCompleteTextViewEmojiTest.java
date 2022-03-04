/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.appcompat.widget;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.text.method.DigitsKeyListener;
import android.text.method.KeyListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AppCompatAutoCompleteTextViewEmojiTest
        extends AppCompatBaseEditTextEmojiTest<AppCompatAutoCompleteTextViewEmojiActivity,
        AppCompatTextView> {

    public AppCompatAutoCompleteTextViewEmojiTest() {
        super(AppCompatAutoCompleteTextViewEmojiActivity.class);
    }

    /**
     * Verify b/221094907 is fixed
     */
    @Test
    @UiThreadTest
    public void respectsClickable() {
        AppCompatAutoCompleteTextView notClickable = mActivityTestRule.getActivity()
                .findViewById(androidx.appcompat.test.R.id.not_clickable);

        // matches platform behavior
        assertThat(notClickable.isClickable()).isTrue();
        assertThat(notClickable.isLongClickable()).isTrue();
    }

    /**
     * Verify b/221094907 is fixed
     */
    @Test
    @UiThreadTest
    public void respectsLongClickable() {
        AppCompatAutoCompleteTextView notLongClickable = mActivityTestRule.getActivity()
                .findViewById(androidx.appcompat.test.R.id.not_long_clickable);

        assertThat(notLongClickable.isLongClickable()).isFalse();
        assertThat(notLongClickable.isClickable()).isTrue();
    }

    @Test
    @UiThreadTest
    public void whenSubclassing_setKeyListener_notCalledDuringConstructor() {
        class MyAutoCompleteTextView extends AppCompatAutoCompleteTextView {
            private boolean mSetKeyListenerCalled = false;

            MyAutoCompleteTextView(@NonNull Context context) {
                super(context);
            }

            @Override
            public void setKeyListener(@Nullable KeyListener keyListener) {
                super.setKeyListener(keyListener);
                mSetKeyListenerCalled = true;
            }
        }

        MyAutoCompleteTextView myAutoCompleteTextView =
                new MyAutoCompleteTextView(mActivityTestRule.getActivity());
        assertThat(myAutoCompleteTextView.mSetKeyListenerCalled).isFalse();

        myAutoCompleteTextView.setKeyListener(DigitsKeyListener.getInstance("1234"));
        assertThat(myAutoCompleteTextView.mSetKeyListenerCalled).isTrue();
    }
}
