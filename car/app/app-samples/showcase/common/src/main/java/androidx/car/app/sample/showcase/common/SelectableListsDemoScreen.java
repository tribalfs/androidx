/*
 * Copyright (C) 2021 The Android Open Source Project
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

package androidx.car.app.sample.showcase.common;

import static androidx.car.app.CarToast.LENGTH_LONG;
import static androidx.car.app.model.Action.BACK;
import static androidx.car.app.model.CarColor.YELLOW;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.SpannableString;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.SectionedItemList;
import androidx.car.app.model.Template;
import androidx.car.app.sample.showcase.common.common.Utils;
import androidx.core.graphics.drawable.IconCompat;

/** A screen demonstrating selectable lists. */
public final class SelectableListsDemoScreen extends Screen {

    public SelectableListsDemoScreen(@NonNull CarContext carContext) {
        super(carContext);
    }

    private boolean mIsEnabled = true;

    // Get colorized spannable string
    private static CharSequence getColoredString(String str, boolean isEnabled) {
        if (isEnabled && !str.isEmpty()) {
            SpannableString ss = new SpannableString(str);
            Utils.colorize(ss, YELLOW, 0, str.length());
            return ss;
        }
        return str;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ListTemplate.Builder templateBuilder = new ListTemplate.Builder();

        // The Image to be displayed in a row
        Resources resources = getCarContext().getResources();
        Bitmap bitmap = BitmapFactory.decodeResource(resources, R.drawable.test_image_square);
        IconCompat mImage = IconCompat.createWithBitmap(bitmap);

        ItemList radioList =
                new ItemList.Builder()
                        .addItem(new Row.Builder()
                                .setTitle(getCarContext()
                                        .getString(R.string.option_row_radio_title))
                                .addText(getCarContext().getString(
                                        R.string.additional_text))
                                .setEnabled(mIsEnabled)
                                .build())
                        .addItem(new Row.Builder()
                                .setImage(
                                        new CarIcon.Builder(
                                                IconCompat.createWithResource(
                                                        getCarContext(),
                                                        R.drawable
                                                                .ic_fastfood_white_48dp))
                                                .build(),
                                        Row.IMAGE_TYPE_ICON)
                                .setTitle(getCarContext()
                                        .getString(R.string.option_row_radio_icon_title))
                                .addText(getCarContext().getString(
                                        R.string.additional_text))
                                .setEnabled(mIsEnabled)
                                .build())
                        .addItem(new Row.Builder()
                                .setImage(
                                        new CarIcon.Builder(mImage).build(),
                                        Row.IMAGE_TYPE_LARGE)
                                .setTitle(getCarContext()
                                        .getString(
                                                R.string.option_row_radio_icon_colored_text_title))
                                .addText(getColoredString(
                                        getCarContext().getString(
                                                R.string.additional_text), mIsEnabled))
                                .setEnabled(mIsEnabled)
                                .build())
                        .setOnSelectedListener(this::onSelected)
                        .build();
        templateBuilder.addSectionedList(
                SectionedItemList.create(radioList,
                        getCarContext().getString(R.string.sample_additional_list)));

        return templateBuilder
                .setTitle(getCarContext().getString(R.string.selectable_lists_demo_title))
                .setActionStrip(
                        new ActionStrip.Builder()
                                .addAction(
                                        new Action.Builder()
                                                .setTitle(mIsEnabled
                                                        ? getCarContext().getString(
                                                        R.string.disable_all_rows)
                                                        : getCarContext().getString(
                                                                R.string.enable_all_rows))
                                                .setOnClickListener(
                                                        () -> {
                                                            mIsEnabled = !mIsEnabled;
                                                            invalidate();
                                                        })
                                                .build())
                                .build())
                .setHeaderAction(
                        BACK).build();
    }

    private void onSelected(int index) {
        CarToast.makeText(getCarContext(),
                        getCarContext()
                                .getString(R.string.changes_selection_to_index_toast_msg_prefix)
                                + ":"
                                + " " + index, LENGTH_LONG)
                .show();
    }
}
