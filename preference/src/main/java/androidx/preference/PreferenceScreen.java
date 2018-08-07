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

package androidx.preference;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.RestrictTo;
import androidx.core.content.res.TypedArrayUtils;

/**
 * Represents a top-level {@link Preference} that is the root of a preference hierarchy. A
 * {@link PreferenceFragmentCompat} points to an instance of this class to show the preferences.
 * To instantiate this class, use {@link PreferenceManager#createPreferenceScreen(Context)}.
 *
 * <ul> This class can appear in two places:
 * <li> When a {@link PreferenceFragmentCompat} points to this, it is used as the root and is not
 * shown (only the contained preferences are shown).
 * <li> When it appears inside another preference hierarchy, it is shown and serves as the
 * gateway to another screen of preferences (either by showing another screen of preferences as a
 * {@link android.app.Dialog} or via a {@link Context#startActivity(android.content.Intent)} from
 * the {@link Preference#getIntent()}). The children of this {@link PreferenceScreen} are NOT
 * shown in the screen that this {@link PreferenceScreen} is shown in. Instead, a separate screen
 * will be shown when this preference is clicked.
 * </ul>
 *
 * <p>Here's an example XML layout of a PreferenceScreen:</p>
 * <pre>
 * &lt;PreferenceScreen
 * xmlns:android="http://schemas.android.com/apk/res/android"
 * android:key="first_preferencescreen"&gt;
 * &lt;CheckBoxPreference
 * android:key="wifi enabled"
 * android:title="WiFi" /&gt;
 * &lt;PreferenceScreen
 * android:key="second_preferencescreen"
 * android:title="WiFi settings"&gt;
 * &lt;CheckBoxPreference
 * android:key="prefer wifi"
 * android:title="Prefer WiFi" /&gt;
 * ... other preferences here ...
 * &lt;/PreferenceScreen&gt;
 * &lt;/PreferenceScreen&gt; </pre>
 * <p>
 *
 * In this example, the "first_preferencescreen" will be used as the root of the hierarchy and
 * given to a {@link PreferenceFragmentCompat}. The first screen will show preferences "WiFi"
 * (which can be used to quickly enable/disable WiFi) and "WiFi settings". The "WiFi settings" is
 * the "second_preferencescreen" and when clicked will show another screen of preferences such as
 * "Prefer WiFi" (and the other preferences that are children of the "second_preferencescreen" tag).
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 * <p>For information about building a settings UI with Preferences,
 * read the <a href="{@docRoot}guide/topics/ui/settings.html">Settings</a>
 * guide.</p>
 * </div>
 *
 * @see PreferenceCategory
 */
public final class PreferenceScreen extends PreferenceGroup {

    private boolean mShouldUseGeneratedIds = true;

    /**
     * Do NOT use this constructor, use {@link PreferenceManager#createPreferenceScreen(Context)}.
     *
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public PreferenceScreen(Context context, AttributeSet attrs) {
        super(context, attrs, TypedArrayUtils.getAttr(context, R.attr.preferenceScreenStyle,
                android.R.attr.preferenceScreenStyle));
    }

    @Override
    protected void onClick() {
        if (getIntent() != null || getFragment() != null || getPreferenceCount() == 0) {
            return;
        }
        final PreferenceManager.OnNavigateToScreenListener listener =
                getPreferenceManager().getOnNavigateToScreenListener();
        if (listener != null) {
            listener.onNavigateToScreen(this);
        }
    }

    @Override
    protected boolean isOnSameScreenAsChildren() {
        return false;
    }

    /**
     * See {@link #setShouldUseGeneratedIds(boolean)}
     *
     * @return {@code true} if the adapter should use the preference IDs generated by
     * {@link PreferenceGroup#addPreference(Preference)} as stable item IDs.
     */
    public boolean shouldUseGeneratedIds() {
        return mShouldUseGeneratedIds;
    }

    /**
     * Set whether the adapter created for this screen should attempt to use the preference IDs
     * generated by {@link PreferenceGroup#addPreference(Preference)} as stable item IDs. Setting
     * this to false can suppress unwanted animations if {@link Preference} objects are frequently
     * removed from and re-added to their containing {@link PreferenceGroup}.
     *
     * <p>This method may only be called when the preference screen is not attached to the
     * hierarchy.
     *
     * <p>Default value is {@code true}.
     *
     * @param shouldUseGeneratedIds {@code true} if the adapter should use the preference ID as a
     *                              stable ID, or {@code false} to disable the use of
     *                              stable IDs.
     */
    public void setShouldUseGeneratedIds(boolean shouldUseGeneratedIds) {
        if (isAttached()) {
            throw new IllegalStateException("Cannot change the usage of generated IDs while" +
                    " attached to the preference hierarchy");
        }
        mShouldUseGeneratedIds = shouldUseGeneratedIds;
    }
}
