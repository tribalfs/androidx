/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.support.text.emoji.widget;

import android.content.Context;
import android.support.v7.widget.AppCompatButton;
import android.text.InputFilter;
import android.util.AttributeSet;

/**
 * AppCompatButton widget enhanced with emoji capability by using {@link EmojiTextViewHelper}. When
 * used on devices running API 18 or below, this widget acts as a regular {@link AppCompatButton}.
 */
public class EmojiAppCompatButton extends AppCompatButton {
    private EmojiTextViewHelper mEmojiTextViewHelper;
    private boolean mInitialized;

    public EmojiAppCompatButton(Context context) {
        super(context);
        init();
    }

    public EmojiAppCompatButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EmojiAppCompatButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        if (!mInitialized) {
            mInitialized = true;
            getEmojiTextViewHelper().updateTransformationMethod();
        }
    }

    @Override
    public void setFilters(InputFilter[] filters) {
        super.setFilters(getEmojiTextViewHelper().getFilters(filters));
    }

    @Override
    public void setAllCaps(boolean allCaps) {
        super.setAllCaps(allCaps);
        getEmojiTextViewHelper().setAllCaps(allCaps);
    }

    private EmojiTextViewHelper getEmojiTextViewHelper() {
        if (mEmojiTextViewHelper == null) {
            mEmojiTextViewHelper = new EmojiTextViewHelper(this);
        }
        return mEmojiTextViewHelper;
    }
}
