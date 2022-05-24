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

package androidx.car.app.sample.showcase.common.navigation;

import static androidx.car.app.CarToast.LENGTH_SHORT;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.Header;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.PlaceListNavigationTemplate;
import androidx.car.app.sample.showcase.common.R;
import androidx.car.app.sample.showcase.common.common.SamplePlaces;
import androidx.car.app.sample.showcase.common.navigation.routing.RoutingDemoModels;
import androidx.core.graphics.drawable.IconCompat;

/** Creates a screen using the {@link PlaceListNavigationTemplate} */
public final class PlaceListNavigationTemplateDemoScreen extends Screen {
    private final SamplePlaces mPlaces;

    private boolean mIsFavorite = false;

    public PlaceListNavigationTemplateDemoScreen(@NonNull CarContext carContext) {
        super(carContext);
        mPlaces = SamplePlaces.create(this);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        Header header = new Header.Builder()
                .setStartHeaderAction(Action.BACK)
                .addEndHeaderAction(new Action.Builder()
                        .setIcon(
                                new CarIcon.Builder(
                                        IconCompat.createWithResource(
                                                getCarContext(),
                                                mIsFavorite
                                                        ? R.drawable.ic_favorite_filled_white_24dp
                                                        : R.drawable.ic_favorite_white_24dp))
                                        .build())
                        .setOnClickListener(() -> {
                            CarToast.makeText(
                                            getCarContext(),
                                            mIsFavorite
                                                    ? getCarContext()
                                                    .getString(R.string.favorite_toast_msg)
                                                    : getCarContext().getString(
                                                            R.string.not_favorite_toast_msg),
                                            LENGTH_SHORT)
                                    .show();
                            mIsFavorite = !mIsFavorite;
                            invalidate();
                        })
                        .build())
                .addEndHeaderAction(new Action.Builder()
                        .setOnClickListener(() -> finish())
                        .setIcon(
                                new CarIcon.Builder(
                                        IconCompat.createWithResource(
                                                getCarContext(),
                                                R.drawable.ic_close_white_24dp))
                                        .build())
                        .build())
                .setTitle(getCarContext().getString(R.string.place_list_nav_template_demo_title))
                .build();

        return new PlaceListNavigationTemplate.Builder()
                .setItemList(mPlaces.getPlaceList())
                .setHeader(header)
                .setMapActionStrip(RoutingDemoModels.getMapActionStrip(getCarContext()))
                .setActionStrip(
                        new ActionStrip.Builder()
                                .addAction(
                                        new Action.Builder()
                                                .setTitle(getCarContext().getString(
                                                        R.string.search_action_title))
                                                .setOnClickListener(() -> {
                                                })
                                                .build())
                                .build())
                .build();
    }
}
