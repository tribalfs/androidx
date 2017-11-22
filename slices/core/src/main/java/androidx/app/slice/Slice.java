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

package androidx.app.slice;

import static android.app.slice.Slice.HINT_ACTIONS;
import static android.app.slice.Slice.HINT_HORIZONTAL;
import static android.app.slice.Slice.HINT_LARGE;
import static android.app.slice.Slice.HINT_LIST;
import static android.app.slice.Slice.HINT_LIST_ITEM;
import static android.app.slice.Slice.HINT_NO_TINT;
import static android.app.slice.Slice.HINT_PARTIAL;
import static android.app.slice.Slice.HINT_SELECTED;
import static android.app.slice.Slice.HINT_TITLE;
import static android.app.slice.SliceItem.FORMAT_ACTION;
import static android.app.slice.SliceItem.FORMAT_COLOR;
import static android.app.slice.SliceItem.FORMAT_IMAGE;
import static android.app.slice.SliceItem.FORMAT_REMOTE_INPUT;
import static android.app.slice.SliceItem.FORMAT_SLICE;
import static android.app.slice.SliceItem.FORMAT_TEXT;
import static android.app.slice.SliceItem.FORMAT_TIMESTAMP;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.ContentProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.RestrictTo.Scope;
import android.support.annotation.StringDef;
import android.support.v4.os.BuildCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.app.slice.compat.SliceProviderCompat;
import androidx.app.slice.core.SliceHints;
import androidx.app.slice.core.SliceSpecs;

/**
 * A slice is a piece of app content and actions that can be surfaced outside of the app.
 *
 * <p>They are constructed using {@link Builder} in a tree structure
 * that provides the OS some information about how the content should be displayed.
 */
public final class Slice {

    private static final String HINTS = "hints";
    private static final String ITEMS = "items";
    private static final String URI = "uri";

    /**
     * @hide
     */
    @RestrictTo(Scope.LIBRARY)
    @StringDef({HINT_TITLE, HINT_LIST, HINT_LIST_ITEM, HINT_LARGE, HINT_ACTIONS, HINT_SELECTED,
            HINT_HORIZONTAL, HINT_NO_TINT, HINT_PARTIAL,
            SliceHints.HINT_HIDDEN, SliceHints.HINT_TOGGLE})
    public @interface SliceHint{ }

    private final SliceItem[] mItems;
    private final @SliceHint String[] mHints;
    private Uri mUri;

    /**
     * @hide
     */
    @RestrictTo(Scope.LIBRARY)
    Slice(ArrayList<SliceItem> items, @SliceHint String[] hints, Uri uri) {
        mHints = hints;
        mItems = items.toArray(new SliceItem[items.size()]);
        mUri = uri;
    }

    /**
     * @hide
     */
    @RestrictTo(Scope.LIBRARY)
    public Slice(Bundle in) {
        mHints = in.getStringArray(HINTS);
        Parcelable[] items = in.getParcelableArray(ITEMS);
        mItems = new SliceItem[items.length];
        for (int i = 0; i < mItems.length; i++) {
            if (items[i] instanceof Bundle) {
                mItems[i] = new SliceItem((Bundle) items[i]);
            }
        }
        mUri = in.getParcelable(URI);
    }

    /**
     * @hide
     */
    @RestrictTo(Scope.LIBRARY)
    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putStringArray(HINTS, mHints);
        Parcelable[] p = new Parcelable[mItems.length];
        for (int i = 0; i < mItems.length; i++) {
            p[i] = mItems[i].toBundle();
        }
        b.putParcelableArray(ITEMS, p);
        b.putParcelable(URI, mUri);
        return b;
    }

    /**
     * @return The Uri that this Slice represents.
     */
    public Uri getUri() {
        return mUri;
    }

    /**
     * @return All child {@link SliceItem}s that this Slice contains.
     */
    public List<SliceItem> getItems() {
        return Arrays.asList(mItems);
    }

    /**
     * @return All hints associated with this Slice.
     */
    public @SliceHint List<String> getHints() {
        return Arrays.asList(mHints);
    }

    /**
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public boolean hasHint(@SliceHint String hint) {
        return ArrayUtils.contains(mHints, hint);
    }

    /**
     * A Builder used to construct {@link Slice}s
     */
    public static class Builder {

        private final Uri mUri;
        private ArrayList<SliceItem> mItems = new ArrayList<>();
        private @SliceHint ArrayList<String> mHints = new ArrayList<>();

        /**
         * Create a builder which will construct a {@link Slice} for the Given Uri.
         * @param uri Uri to tag for this slice.
         */
        public Builder(@NonNull Uri uri) {
            mUri = uri;
        }

        /**
         * Create a builder for a {@link Slice} that is a sub-slice of the slice
         * being constructed by the provided builder.
         * @param parent The builder constructing the parent slice
         */
        public Builder(@NonNull Slice.Builder parent) {
            mUri = parent.mUri.buildUpon().appendPath("_gen")
                    .appendPath(String.valueOf(mItems.size())).build();
        }

        /**
         * Add hints to the Slice being constructed
         */
        public Builder addHints(@SliceHint String... hints) {
            mHints.addAll(Arrays.asList(hints));
            return this;
        }

        /**
         * Add hints to the Slice being constructed
         */
        public Builder addHints(@SliceHint List<String> hints) {
            return addHints(hints.toArray(new String[hints.size()]));
        }

        /**
         * Add a sub-slice to the slice being constructed
         */
        public Builder addSubSlice(@NonNull Slice slice) {
            return addSubSlice(slice, null);
        }

        /**
         * Add a sub-slice to the slice being constructed
         * @param subType Optional template-specific type information
         * @see {@link SliceItem#getSubType()}
         */
        public Builder addSubSlice(@NonNull Slice slice, String subType) {
            mItems.add(new SliceItem(slice, FORMAT_SLICE, subType, slice.getHints().toArray(
                    new String[slice.getHints().size()])));
            return this;
        }

        /**
         * Add an action to the slice being constructed
         * @param subType Optional template-specific type information
         * @see {@link SliceItem#getSubType()}
         */
        public Slice.Builder addAction(@NonNull PendingIntent action,
                @NonNull Slice s, @Nullable String subType) {
            mItems.add(new SliceItem(action, s, FORMAT_ACTION, subType, new String[0]));
            return this;
        }

        /**
         * Add text to the slice being constructed
         * @param subType Optional template-specific type information
         * @see {@link SliceItem#getSubType()}
         */
        public Builder addText(CharSequence text, @Nullable String subType,
                @SliceHint String... hints) {
            mItems.add(new SliceItem(text, FORMAT_TEXT, subType, hints));
            return this;
        }

        /**
         * Add text to the slice being constructed
         * @param subType Optional template-specific type information
         * @see {@link SliceItem#getSubType()}
         */
        public Builder addText(CharSequence text, @Nullable String subType,
                @SliceHint List<String> hints) {
            return addText(text, subType, hints.toArray(new String[hints.size()]));
        }

        /**
         * Add an image to the slice being constructed
         * @param subType Optional template-specific type information
         * @see {@link SliceItem#getSubType()}
         */
        public Builder addIcon(Icon icon, @Nullable String subType,
                @SliceHint String... hints) {
            mItems.add(new SliceItem(icon, FORMAT_IMAGE, subType, hints));
            return this;
        }

        /**
         * Add an image to the slice being constructed
         * @param subType Optional template-specific type information
         * @see {@link SliceItem#getSubType()}
         */
        public Builder addIcon(Icon icon, @Nullable String subType,
                @SliceHint List<String> hints) {
            return addIcon(icon, subType, hints.toArray(new String[hints.size()]));
        }

        /**
         * Add remote input to the slice being constructed
         * @param subType Optional template-specific type information
         * @see {@link SliceItem#getSubType()}
         */
        public Slice.Builder addRemoteInput(RemoteInput remoteInput, @Nullable String subType,
                @SliceHint List<String> hints) {
            return addRemoteInput(remoteInput, subType, hints.toArray(new String[hints.size()]));
        }

        /**
         * Add remote input to the slice being constructed
         * @param subType Optional template-specific type information
         * @see {@link SliceItem#getSubType()}
         */
        public Slice.Builder addRemoteInput(RemoteInput remoteInput, @Nullable String subType,
                @SliceHint String... hints) {
            mItems.add(new SliceItem(remoteInput, FORMAT_REMOTE_INPUT, subType, hints));
            return this;
        }

        /**
         * Add a color to the slice being constructed
         * @param subType Optional template-specific type information
         * @see {@link SliceItem#getSubType()}
         */
        public Builder addColor(int color, @Nullable String subType,
                @SliceHint String... hints) {
            mItems.add(new SliceItem(color, FORMAT_COLOR, subType, hints));
            return this;
        }

        /**
         * Add a color to the slice being constructed
         * @param subType Optional template-specific type information
         * @see {@link SliceItem#getSubType()}
         */
        public Builder addColor(int color, @Nullable String subType,
                @SliceHint List<String> hints) {
            return addColor(color, subType, hints.toArray(new String[hints.size()]));
        }

        /**
         * Add a timestamp to the slice being constructed
         * @param subType Optional template-specific type information
         * @see {@link SliceItem#getSubType()}
         */
        public Slice.Builder addTimestamp(long time, @Nullable String subType,
                @SliceHint String... hints) {
            mItems.add(new SliceItem(time, FORMAT_TIMESTAMP, subType, hints));
            return this;
        }

        /**
         * Add a timestamp to the slice being constructed
         * @param subType Optional template-specific type information
         * @see {@link SliceItem#getSubType()}
         */
        public Slice.Builder addTimestamp(long time, @Nullable String subType,
                @SliceHint List<String> hints) {
            return addTimestamp(time, subType, hints.toArray(new String[hints.size()]));
        }

        /**
         * Construct the slice.
         */
        public Slice build() {
            return new Slice(mItems, mHints.toArray(new String[mHints.size()]), mUri);
        }
    }

    /**
     * @hide
     * @return A string representation of this slice.
     */
    @RestrictTo(Scope.LIBRARY)
    @Override
    public String toString() {
        return toString("");
    }

    private String toString(String indent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mItems.length; i++) {
            sb.append(indent);
            if (FORMAT_SLICE.equals(mItems[i].getFormat())) {
                sb.append("slice:\n");
                sb.append(mItems[i].getSlice().toString(indent + "   "));
            } else if (FORMAT_TEXT.equals(mItems[i].getFormat())) {
                sb.append("text: ");
                sb.append(mItems[i].getText());
                sb.append("\n");
            } else {
                sb.append(SliceItem.typeToString(mItems[i].getFormat()));
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Turns a slice Uri into slice content.
     *
     * @param context Context to be used.
     * @param uri The URI to a slice provider
     * @return The Slice provided by the app or null if none is given.
     * @see Slice
     */
    @SuppressWarnings("NewApi")
    public static @Nullable Slice bindSlice(Context context, @NonNull Uri uri) {
        if (BuildCompat.isAtLeastP()) {
            return callBindSlice(context, uri);
        } else {
            return SliceProviderCompat.bindSlice(context, uri);
        }
    }

    @TargetApi(28)
    private static Slice callBindSlice(Context context, Uri uri) {
        return SliceConvert.wrap(android.app.slice.Slice.bindSlice(
                context.getContentResolver(), uri, SliceSpecs.SUPPORTED_SPECS));
    }


    /**
     * Turns a slice intent into slice content. Expects an explicit intent. If there is no
     * {@link ContentProvider} associated with the given intent this will throw
     * {@link IllegalArgumentException}.
     *
     * @param context The context to use.
     * @param intent The intent associated with a slice.
     * @return The Slice provided by the app or null if none is given.
     * @see Slice
     * @see SliceProvider#onMapIntentToUri(Intent)
     * @see Intent
     */
    @SuppressWarnings("NewApi")
    public static @Nullable Slice bindSlice(Context context, @NonNull Intent intent) {
        if (BuildCompat.isAtLeastP()) {
            return callBindSlice(context, intent);
        } else {
            return SliceProviderCompat.bindSlice(context, intent);
        }
    }

    @TargetApi(28)
    private static Slice callBindSlice(Context context, Intent intent) {
        return SliceConvert.wrap(android.app.slice.Slice.bindSlice(
                context, intent, SliceSpecs.SUPPORTED_SPECS));
    }
}
