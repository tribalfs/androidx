/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.autofill;

import static android.app.slice.SliceItem.FORMAT_ACTION;
import static android.app.slice.SliceItem.FORMAT_IMAGE;
import static android.app.slice.SliceItem.FORMAT_TEXT;

import static androidx.autofill.InlinePresentationBuilder.HINT_INLINE_ATTRIBUTION;
import static androidx.autofill.InlinePresentationBuilder.HINT_INLINE_END_ICON;
import static androidx.autofill.InlinePresentationBuilder.HINT_INLINE_START_ICON;
import static androidx.autofill.InlinePresentationBuilder.HINT_INLINE_SUBTITLE;
import static androidx.autofill.InlinePresentationBuilder.HINT_INLINE_TITLE;

import android.app.PendingIntent;
import android.app.slice.Slice;
import android.app.slice.SliceItem;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;

/**
 * Helper class for rendering {@link Slice} as an Inline Suggestion.
 */
@RequiresApi(api = Build.VERSION_CODES.Q) // TODO(b/147116534): Update to R.
public final class InlinePresentationRenderer {

    private static final String TAG = "InlineRenderer";

    /**
     * Renders an {@link Slice} into an Inline Suggestion as a {@link View}.
     */
    @NonNull
    public static View renderSlice(@NonNull Context context, @NonNull Slice slice,
            @Nullable String style) {
        context = InlineSuggestionThemeUtils.getContextThemeWrapper(context, style);

        final LayoutInflater inflater = LayoutInflater.from(context);
        final ViewGroup suggestionView =
                (ViewGroup) inflater.inflate(R.layout.autofill_inline_suggestion, null);

        final ImageView startIconView =
                suggestionView.findViewById(R.id.autofill_inline_suggestion_start_icon);
        final TextView titleView =
                suggestionView.findViewById(R.id.autofill_inline_suggestion_title);
        final TextView subtitleView =
                suggestionView.findViewById(R.id.autofill_inline_suggestion_subtitle);
        final ImageView endIconView =
                suggestionView.findViewById(R.id.autofill_inline_suggestion_end_icon);

        boolean hasStartIcon = false;
        boolean hasEndIcon = false;
        boolean hasTitle = false;
        boolean hasSubtitle = false;
        PendingIntent attribution = null;
        final List<SliceItem> sliceItems = slice.getItems();
        for (int i = 0; i < sliceItems.size(); i++) {
            final SliceItem sliceItem = sliceItems.get(i);
            final List<String> sliceHints = sliceItem.getHints();
            final String sliceFormat = sliceItem.getFormat();
            if (sliceFormat.equals(FORMAT_IMAGE)) {
                final Icon sliceIcon = sliceItem.getIcon();
                if (sliceHints.contains(HINT_INLINE_START_ICON)) {
                    startIconView.setImageIcon(sliceIcon);
                    hasStartIcon = true;
                } else if (sliceHints.contains(HINT_INLINE_END_ICON)) {
                    endIconView.setImageIcon(sliceIcon);
                    hasEndIcon = true;
                } else {
                    throw new IllegalStateException("Unrecognized Image SliceItem in Inline "
                            + "Presentation");
                }
            } else if (sliceFormat.equals(FORMAT_TEXT)) {
                final String sliceText = sliceItem.getText().toString();
                if (sliceHints.contains(HINT_INLINE_TITLE)) {
                    titleView.setText(sliceText);
                    hasTitle = true;
                } else if (sliceHints.contains(HINT_INLINE_SUBTITLE)) {
                    subtitleView.setText(sliceText);
                    hasSubtitle = true;
                } else {
                    throw new IllegalStateException("Unrecognized Text SliceItem in Inline "
                            + "Presentation");
                }
            } else if (sliceFormat.equals(FORMAT_ACTION)) {
                if (sliceHints.contains(HINT_INLINE_ATTRIBUTION)) {
                    attribution = sliceItem.getAction();
                } else {
                    throw new IllegalStateException("Unrecognized Action SliceItem in Inline "
                            + "Presentation");
                }
            }
        }
        if (hasStartIcon) {
            startIconView.setVisibility(View.VISIBLE);
        }
        if (hasEndIcon) {
            endIconView.setVisibility(View.VISIBLE);
        }
        if (hasTitle) {
            titleView.setVisibility(View.VISIBLE);
        }
        if (hasSubtitle) {
            subtitleView.setVisibility(View.VISIBLE);
        }
        if (attribution != null) {
            final PendingIntent attributionCopy = attribution;
            suggestionView.setOnLongClickListener(v -> {
                try {
                    attributionCopy.send();
                    return true;
                } catch (PendingIntent.CanceledException e) {
                    Log.w(TAG, "Attribution intent cancelled");
                }
                return false;
            });
        }
        return suggestionView;
    }

    private InlinePresentationRenderer() {
    }
}
