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

package androidx.car.app.sample.showcase.common.templates;

import static androidx.car.app.CarToast.LENGTH_LONG;
import static androidx.car.app.model.Action.BACK;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.constraints.ConstraintManager;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarText;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.ParkedOnlyOnClickListener;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.sample.showcase.common.R;
import androidx.car.app.versioning.CarAppApiLevels;
import androidx.lifecycle.DefaultLifecycleObserver;

/**
 * Creates a screen that demonstrates usage of the full screen {@link ListTemplate} to display a
 * full-screen list.
 */
public final class ListTemplateDemoScreen extends Screen implements DefaultLifecycleObserver {
    private static final int MAX_LIST_ITEMS = 100;

    public ListTemplateDemoScreen(@NonNull CarContext carContext) {
        super(carContext);
        getLifecycle().addObserver(this);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ItemList.Builder listBuilder = new ItemList.Builder();

        listBuilder.addItem(
                new Row.Builder()
                        .setOnClickListener(
                                ParkedOnlyOnClickListener.create(() -> onClick(
                                        getCarContext().getString(R.string.parked_toast_msg))))
                        .setTitle(getCarContext().getString(R.string.parked_only_title))
                        .addText(getCarContext().getString(R.string.parked_only_text))
                        .build());

        // Some hosts may allow more items in the list than others, so create more.
        if (getCarContext().getCarAppApiLevel() > CarAppApiLevels.LEVEL_1) {
            int listLimit =
                    Math.min(MAX_LIST_ITEMS,
                            getCarContext().getCarService(ConstraintManager.class).getContentLimit(
                                    ConstraintManager.CONTENT_LIMIT_TYPE_LIST));

            for (int i = 2; i <= listLimit; ++i) {
                // For row text, set text variants that fit best in different screen sizes.
                String secondTextStr = getCarContext().getString(R.string.second_line_text);
                CarText secondText =
                        new CarText.Builder(
                                "================= " + secondTextStr + " ================")
                                .addVariant("--------------------- " + secondTextStr
                                        + " ----------------------")
                                .addVariant(secondTextStr)
                                .build();
                final String onClickText = getCarContext().getString(R.string.clicked_row_prefix)
                        + ": " + i;
                Row.Builder rowBuilder = new Row.Builder()
                        .setOnClickListener(() -> onClick(onClickText))
                        .setTitle(
                                getCarContext().getString(R.string.title_prefix) + " " + i);
                if (i % 2 == 0) {
                    rowBuilder.addText(getCarContext().getString(R.string.long_line_text));
                } else {
                    rowBuilder
                            .addText(getCarContext().getString(R.string.first_line_text))
                            .addText(secondText);
                }
                listBuilder.addItem(rowBuilder.build());
            }
        }

        Action settings = new Action.Builder()
                .setTitle(getCarContext().getString(
                        R.string.settings_action_title))
                .setOnClickListener(
                        () -> CarToast.makeText(
                                        getCarContext(),
                                        getCarContext().getString(
                                                R.string.settings_toast_msg),
                                        LENGTH_LONG)
                                .show())
                .build();

        return new ListTemplate.Builder()
                .setSingleList(listBuilder.build())
                .setTitle(getCarContext().getString(R.string.list_template_demo_title))
                .setHeaderAction(BACK)
                .setActionStrip(
                        new ActionStrip.Builder()
                                .addAction(settings)
                                .build())
                .build();
    }

    private void onClick(String text) {
        CarToast.makeText(getCarContext(), text, LENGTH_LONG).show();
    }
}
