/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.support.v7.internal.view;

import android.support.v7.view.MenuItem.OnActionExpandListener;
import android.view.View;

/**
 * When a {@link View} implements this interface it will receive callbacks when expanded or
 * collapsed as an action view alongside the optional, app-specified callbacks to
 * {@link OnActionExpandListener}.
 *
 * <p>See {@link android.support.v7.view.MenuItem} for more information about action views.
 * See {@link android.app.ActionBar} for more information about the action bar.
 */
public interface CollapsibleActionView {

    /**
     * Called when this view is expanded as an action view. See {@link
     * android.support.v7.view.MenuItem#expandActionView()}.
     */
    public void onActionViewExpanded();

    /**
     * Called when this view is collapsed as an action view. See {@link
     * android.support.v7.view.MenuItem#collapseActionView()}.
     */
    public void onActionViewCollapsed();
}
