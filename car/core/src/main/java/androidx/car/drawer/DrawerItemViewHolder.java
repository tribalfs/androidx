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

package androidx.car.drawer;

import android.car.drivingstate.CarUxRestrictions;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.R;
import androidx.car.util.CarUxRestrictionsUtils;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Re-usable {@link RecyclerView.ViewHolder} for displaying items in the
 * {@link androidx.car.drawer.CarDrawerAdapter}.
 */
public class DrawerItemViewHolder extends RecyclerView.ViewHolder {
    private final ImageView mIcon;
    private final TextView mTitle;
    private final TextView mBody;
    private final ImageView mEndIcon;

    DrawerItemViewHolder(View view) {
        super(view);
        mIcon = view.findViewById(R.id.icon);
        if (mIcon == null) {
            throw new IllegalArgumentException("Icon view cannot be null!");
        }

        mTitle = view.findViewById(R.id.title);
        if (mTitle == null) {
            throw new IllegalArgumentException("Title view cannot be null!");
        }

        // Next two are optional and may be null.
        mBody = view.findViewById(R.id.body);
        mEndIcon = view.findViewById(R.id.end_icon);
    }

    /** Returns the view that should be used to display the main icon. */
    @NonNull
    public ImageView getIconView() {
        return mIcon;
    }

    /** Returns the view that will display the main title. */
    @NonNull
    public TextView getTitleView() {
        return mTitle;
    }

    /**
     * Returns the view that is used for the body text that is smaller than the title text and
     * appears beneath the title.
     */
    @Nullable
    public TextView getBodyView() {
        return mBody;
    }

    /** Returns the icon that is displayed at the end of the view. */
    @Nullable
    public ImageView getEndIconView() {
        return mEndIcon;
    }

    /**
     * Sets the listener that will be notified when the view held by this ViewHolder has been
     * clicked. Passing {@code null} will clear any previously set listeners.
     */
    void setItemClickListener(@Nullable DrawerItemClickListener listener) {
        itemView.setOnClickListener(listener != null
                ? v -> listener.onItemClick(getAdapterPosition())
                : null);
    }

    /**
     * Applies car UX restrictions to children views.
     *
     * <p>{@code Text} might be truncated to meet length limit required by regulation.
     *
     * @param restrictions current car UX restrictions.
     */
    void applyUxRestrictions(@NonNull CarUxRestrictions restrictions) {
        CarUxRestrictionsUtils.apply(itemView.getContext(), restrictions, getBodyView());
    }
}
