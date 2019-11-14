/*
 * Copyright 2018 The Android Open Source Project
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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A {@link ToggleButton} which supports compatible features on older versions of the platform,
 * including:
 * <ul>
 *     <li>Allows setting of the font family using {@link android.R.attr#fontFamily}</li>
 * </ul>
 *
 * <p>This will automatically be used when you use {@link ToggleButton} in your layouts.
 * You should only need to manually use this class when writing custom views.</p>
 */
public class AppCompatToggleButton extends ToggleButton {

    private final AppCompatTextHelper mTextHelper;

    public AppCompatToggleButton(@NonNull Context context) {
        this(context, null);
    }

    public AppCompatToggleButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, android.R.attr.buttonStyleToggle);
    }

    public AppCompatToggleButton(
            @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        ThemeUtils.checkAppCompatTheme(this, getContext());

        mTextHelper = new AppCompatTextHelper(this);
        mTextHelper.loadFromAttributes(attrs, defStyleAttr);
    }
}
