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

package androidx.navigation.ui;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.StringRes;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v7.widget.Toolbar;

import androidx.navigation.NavController;
import androidx.navigation.NavDestination;

import java.lang.ref.WeakReference;

/**
 * The OnDestinationChangedListener specifically for keeping a
 * CollapsingToolbarLayout+Toolbar updated.
 * This handles both updating the title and updating the Up Indicator, transitioning between
 * the drawer icon and up arrow as needed.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
class CollapsingToolbarOnDestinationChangedListener
        extends AbstractAppBarOnDestinationChangedListener {
    private final WeakReference<CollapsingToolbarLayout> mCollapsingToolbarLayoutWeakReference;
    private final WeakReference<Toolbar> mToolbarWeakReference;

    CollapsingToolbarOnDestinationChangedListener(
            @NonNull CollapsingToolbarLayout collapsingToolbarLayout,
            @NonNull Toolbar toolbar, @NonNull AppBarConfiguration configuration) {
        super(collapsingToolbarLayout.getContext(), configuration);
        mCollapsingToolbarLayoutWeakReference = new WeakReference<>(collapsingToolbarLayout);
        mToolbarWeakReference = new WeakReference<>(toolbar);
    }

    @Override
    public void onDestinationChanged(@NonNull NavController controller,
            @NonNull NavDestination destination, @Nullable Bundle arguments) {
        CollapsingToolbarLayout collapsingToolbarLayout =
                mCollapsingToolbarLayoutWeakReference.get();
        Toolbar toolbar = mToolbarWeakReference.get();
        if (collapsingToolbarLayout == null || toolbar == null) {
            controller.removeOnDestinationChangedListener(this);
            return;
        }
        super.onDestinationChanged(controller, destination, arguments);
    }

    @Override
    protected void setTitle(CharSequence title) {
        CollapsingToolbarLayout collapsingToolbarLayout =
                mCollapsingToolbarLayoutWeakReference.get();
        if (collapsingToolbarLayout != null) {
            collapsingToolbarLayout.setTitle(title);
        }
    }

    @Override
    protected void setNavigationIcon(Drawable icon,
            @StringRes int contentDescription) {
        Toolbar toolbar = mToolbarWeakReference.get();
        if (toolbar != null) {
            toolbar.setNavigationIcon(icon);
            toolbar.setNavigationContentDescription(contentDescription);
        }
    }
}
