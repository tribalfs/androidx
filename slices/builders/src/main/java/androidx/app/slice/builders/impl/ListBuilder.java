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

package androidx.app.slice.builders.impl;

import static android.support.annotation.RestrictTo.Scope.LIBRARY;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

/**
 * @hide
 */
@RestrictTo(LIBRARY)
public interface ListBuilder {

    /**
     * Add a row to list builder.
     */
    void addRow(TemplateBuilderImpl impl);
    /**
     * Add a grid row to the list builder.
     */
    void addGrid(TemplateBuilderImpl impl);

    /**
     * Adds a header to this template.
     * <p>
     * The header should contain a title that is representative of the content in this slice along
     * with an intent that links to the app activity associated with this content.
     */
    void setHeader(TemplateBuilderImpl impl);

    /**
     * Sets the group of actions for this template. These actions may be shown on the template in
     * large or small formats.
     */
    void setActions(TemplateBuilderImpl impl);

    /**
     * Add an input range row to the list builder.
     */
    void addInputRange(TemplateBuilderImpl builder);

    /**
     * Add a range row to the list builder.
     */
    void addRange(TemplateBuilderImpl builder);

    /**
     * If all content in a slice cannot be shown, the row added here will be displayed where the
     * content is cut off. This row should have an affordance to take the user to an activity to
     * see all of the content.
     * <p>
     * Only one see more affordance can be added, this throws {@link IllegalStateException} if
     * a row or action has been previously added.
     * </p>
     */
    void addSeeMoreRow(TemplateBuilderImpl builder);
    /**
     * If all content in a slice cannot be shown, a "see more" affordance will be displayed where
     * the content is cut off. The action added here should take the user to an activity to see
     * all of the content, and will be invoked when the "see more" affordance is tapped.
     * <p>
     * Only one see more affordance can be added, this throws {@link IllegalStateException} if
     * a row or action has been previously added.
     * </p>
     */
    void addSeeMoreAction(PendingIntent intent);

    /**
     * Sets the color to tint items displayed by this template (e.g. icons).
     */
    void setColor(int color);

    /**
     * Create a builder that implements {@link RowBuilder}.
     */
    TemplateBuilderImpl createRowBuilder();
    /**
     * Create a builder that implements {@link RowBuilder}.
     */
    TemplateBuilderImpl createRowBuilder(Uri uri);
    /**
     * Create a builder that implements {@link GridBuilder}.
     */
    TemplateBuilderImpl createGridBuilder();
    /**
     * Create a builder that implements {@link HeaderBuilder}.
     */
    TemplateBuilderImpl createHeaderBuilder();
    /**
     * Create a builder that implements {@link HeaderBuilder}.
     */
    TemplateBuilderImpl createHeaderBuilder(Uri uri);
    /**
     * Create a builder that implements {@link ActionBuilder}.
     */
    TemplateBuilderImpl createActionBuilder();
    /**
     * Create a builder that implements {@link ActionBuilder}.
     */
    TemplateBuilderImpl createActionBuilder(Uri uri);

    /**
     * Create a builder that implements {@link InputRangeBuilder}.
     */
    TemplateBuilderImpl createInputRangeBuilder();

    /**
     * Create a builder that implements {@link RangeBuilder}.
     */
    TemplateBuilderImpl createRangeBuilder();

    /**
     * Builder to construct a range.
     */
    interface RangeBuilder {
        /**
         * Set the upper limit.
         */
        void setMax(int max);

        /**
         * Set the current value.
         */
        void setValue(int value);

        /**
         * Set the title.
         */
        void setTitle(@NonNull CharSequence title);
    }

    /**
     * Builder to construct an input range.
     */
    interface InputRangeBuilder extends RangeBuilder {
        /**
         * Set the {@link PendingIntent} to send when the value changes.
         */
        void setAction(@NonNull PendingIntent action);

        /**
         * Set the {@link Icon} to be displayed as the thumb on the input range.
         */
        void setThumb(@NonNull Icon thumb);
    }

    /**
     */
    interface RowBuilder {

        /**
         * Sets the title item to be the provided timestamp. Only one timestamp can be added, if
         * one is already added this will throw {@link IllegalArgumentException}.
         * <p>
         * There can only be one title item, this will replace any other title
         * items that may have been set.
         */
        void setTitleItem(long timeStamp);

        /**
         * Sets the title item to be the provided icon. There can only be one title item, this
         * will replace any other title items that may have been set.
         */
        void setTitleItem(Icon icon);

        /**
         * Sets the title item to be the provided icon. There can only be one title item, this
         * will replace any other title items that may have been set.
         * <p>
         * When set to true, the parameter {@code isLoading} indicates that the app is doing work
         * to load this content in the background, in this case the template displays a placeholder
         * until updated.
         */
        void setTitleItem(Icon icon, boolean isLoading);

        /**
         * Sets the title item to be a tappable icon. There can only be one title item, this will
         * replace any other title items that may have been set.
         */
        void setTitleItem(Icon icon, PendingIntent action);

        /**
         * Sets the title item to be a tappable icon. There can only be one title item, this will
         * replace any other title items that may have been set.
         * <p>
         * When set to true, the parameter {@code isLoading} indicates that the app is doing work
         * to load this content in the background, in this case the template displays a placeholder
         * until updated.
         */
        void setTitleItem(Icon icon, PendingIntent action, boolean isLoading);

        /**
         * Sets the action to be invoked if the user taps on the main content of the template.
         */
        void setContentIntent(PendingIntent action);

        /**
         * Sets the title text.
         */
        void setTitle(CharSequence title);

        /**
         * Sets the title text.
         * <p>
         * When set to true, the parameter {@code isLoading} indicates that the app is doing work
         * to load this content in the background, in this case the template displays a placeholder
         * until updated.
         */
        void setTitle(CharSequence title, boolean isLoading);

        /**
         * Sets the subtitle text.
         */
        void setSubtitle(CharSequence subtitle);

        /**
         * Sets the subtitle text.
         * <p>
         * When set to true, the parameter {@code isLoading} indicates that the app is doing work
         * to load this content in the background, in this case the template displays a placeholder
         * until updated.
         */
        void setSubtitle(CharSequence subtitle, boolean isLoading);

        /**
         * Adds a timestamp to be displayed at the end of the row.
         */
        void addEndItem(long timeStamp);

        /**
         * Adds an icon to be displayed at the end of the row.
         */
        void addEndItem(Icon icon);

        /**
         * Adds an icon to be displayed at the end of the row.
         * <p>
         * When set to true, the parameter {@code isLoading} indicates that the app is doing work
         * to load this content in the background, in this case the template displays a placeholder
         * until updated.
         */
        void addEndItem(Icon icon, boolean isLoading);

        /**
         * Adds a tappable icon to be displayed at the end of the row.
         */
        void addEndItem(Icon icon, PendingIntent action);

        /**
         * Adds a tappable icon to be displayed at the end of the row.
         * <p>
         * When set to true, the parameter {@code isLoading} indicates that the app is doing work
         * to load this content in the background, in this case the template displays a placeholder
         * until updated.
         */
        void addEndItem(Icon icon, PendingIntent action, boolean isLoading);

        /**
         * Adds a toggle action to the template with custom icons to represent checked and unchecked
         * state.
         */
        void addToggle(PendingIntent action, boolean isChecked, Icon icon);

        /**
         * Adds a toggle action to the template with custom icons to represent checked and unchecked
         * state.
         * <p>
         * When set to true, the parameter {@code isLoading} indicates that the app is doing work
         * to load this content in the background, in this case the template displays a placeholder
         * until updated.
         */
        void addToggle(PendingIntent action, boolean isChecked, Icon icon, boolean isLoading);
    }


    /**
     * Builder to construct a header. A header is displayed at the top of a list and can have
     * a title, subtitle, and an action.
     */
    public interface HeaderBuilder {

        /**
         * Sets the title to be shown in this header.
         */
        void setTitle(CharSequence title);

        /**
         * Sets the subtitle to be shown in this header.
         */
        void setSubtitle(CharSequence subtitle);

        /**
         * Sets the summary subtitle to be shown in this header. If unset, the normal subtitle
         * will be used. The summary is used when the parent template is presented in a
         * small format.
         */
        void setSummarySubtitle(CharSequence summarySubtitle);

        /**
         * Sets the pending intent to activate when the header is activated.
         */
        void setContentIntent(PendingIntent intent);
    }

    /**
     * Builder to construct a group of actions.
     */
    public interface ActionBuilder {

        /**
         * Adds an action to this builder.
         *
         * @param action the pending intent to send when the action is activated.
         * @param actionIcon the icon to display for this action.
         * @param contentDescription the content description to use for accessibility.
         * @param priority what priority to display this action in, with the lowest priority having
         *                 the highest ranking.
         */
        void addAction(PendingIntent action, Icon actionIcon, CharSequence contentDescription,
                int priority);
    }
}

