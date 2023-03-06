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

package androidx.core.app;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;
import static androidx.core.app.NotificationCompat.DEFAULT_SOUND;
import static androidx.core.app.NotificationCompat.DEFAULT_VIBRATE;
import static androidx.core.app.NotificationCompat.FLAG_GROUP_SUMMARY;
import static androidx.core.app.NotificationCompat.GROUP_ALERT_ALL;
import static androidx.core.app.NotificationCompat.GROUP_ALERT_CHILDREN;
import static androidx.core.app.NotificationCompat.GROUP_ALERT_SUMMARY;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.LocusId;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.SparseArray;
import android.widget.RemoteViews;

import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.collection.ArraySet;
import androidx.core.graphics.drawable.IconCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper around {@link Notification.Builder} that works in a backwards compatible way.
 *
 */
@RestrictTo(LIBRARY_GROUP_PREFIX)
class NotificationCompatBuilder implements NotificationBuilderWithBuilderAccessor {
    private final Context mContext;
    private final Notification.Builder mBuilder;
    private final NotificationCompat.Builder mBuilderCompat;

    // @RequiresApi(16) - uncomment when lint bug is fixed.
    private RemoteViews mContentView;
    // @RequiresApi(16) - uncomment when lint bug is fixed.
    private RemoteViews mBigContentView;
    // @RequiresApi(16) - uncomment when lint bug is fixed.
    private final List<Bundle> mActionExtrasList = new ArrayList<>();
    // @RequiresApi(16) - uncomment when lint bug is fixed.
    private final Bundle mExtras = new Bundle();
    // @RequiresApi(20) - uncomment when lint bug is fixed.
    private int mGroupAlertBehavior;
    // @RequiresApi(21) - uncomment when lint bug is fixed.
    private RemoteViews mHeadsUpContentView;

    @SuppressWarnings("deprecation")
    NotificationCompatBuilder(NotificationCompat.Builder b) {
        mBuilderCompat = b;
        mContext = b.mContext;
        if (Build.VERSION.SDK_INT >= 26) {
            mBuilder = Api26Impl.createBuilder(b.mContext, b.mChannelId);
        } else {
            mBuilder = new Notification.Builder(b.mContext);
        }
        Notification n = b.mNotification;
        mBuilder.setWhen(n.when)
                .setSmallIcon(n.icon, n.iconLevel)
                .setContent(n.contentView)
                .setTicker(n.tickerText, b.mTickerView)
                .setVibrate(n.vibrate)
                .setLights(n.ledARGB, n.ledOnMS, n.ledOffMS)
                .setOngoing((n.flags & Notification.FLAG_ONGOING_EVENT) != 0)
                .setOnlyAlertOnce((n.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0)
                .setAutoCancel((n.flags & Notification.FLAG_AUTO_CANCEL) != 0)
                .setDefaults(n.defaults)
                .setContentTitle(b.mContentTitle)
                .setContentText(b.mContentText)
                .setContentInfo(b.mContentInfo)
                .setContentIntent(b.mContentIntent)
                .setDeleteIntent(n.deleteIntent)
                .setFullScreenIntent(b.mFullScreenIntent,
                        (n.flags & Notification.FLAG_HIGH_PRIORITY) != 0)
                .setNumber(b.mNumber)
                .setProgress(b.mProgressMax, b.mProgress, b.mProgressIndeterminate);
        if (Build.VERSION.SDK_INT < 23) {
            mBuilder.setLargeIcon(b.mLargeIcon == null ? null : b.mLargeIcon.getBitmap());
        } else {
            Api23Impl.setLargeIcon(mBuilder,
                    b.mLargeIcon == null ? null : b.mLargeIcon.toIcon(mContext));
        }
        if (Build.VERSION.SDK_INT < 21) {
            mBuilder.setSound(n.sound, n.audioStreamType);
        }
        if (Build.VERSION.SDK_INT >= 16) {
            Api16Impl.setPriority(
                    Api16Impl.setUsesChronometer(Api16Impl.setSubText(mBuilder, b.mSubText),
                            b.mUseChronometer), b.mPriority);
            for (NotificationCompat.Action action : b.mActions) {
                addAction(action);
            }

            if (b.mExtras != null) {
                mExtras.putAll(b.mExtras);
            }
            if (Build.VERSION.SDK_INT < 20) {
                if (b.mLocalOnly) {
                    mExtras.putBoolean(NotificationCompatExtras.EXTRA_LOCAL_ONLY, true);
                }
                if (b.mGroupKey != null) {
                    mExtras.putString(NotificationCompatExtras.EXTRA_GROUP_KEY, b.mGroupKey);
                    if (b.mGroupSummary) {
                        mExtras.putBoolean(NotificationCompatExtras.EXTRA_GROUP_SUMMARY, true);
                    } else {
                        mExtras.putBoolean(
                                NotificationManagerCompat.EXTRA_USE_SIDE_CHANNEL, true);
                    }
                }
                if (b.mSortKey != null) {
                    mExtras.putString(NotificationCompatExtras.EXTRA_SORT_KEY, b.mSortKey);
                }
            }

            mContentView = b.mContentView;
            mBigContentView = b.mBigContentView;
        }
        if (Build.VERSION.SDK_INT >= 17) {
            Api17Impl.setShowWhen(mBuilder, b.mShowWhen);
        }
        if (Build.VERSION.SDK_INT >= 19) {
            if (Build.VERSION.SDK_INT < 21) {
                final List<String> people = combineLists(getPeople(b.mPersonList), b.mPeople);
                if (people != null && !people.isEmpty()) {
                    mExtras.putStringArray(Notification.EXTRA_PEOPLE,
                            people.toArray(new String[people.size()]));
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 20) {
            Api20Impl.setLocalOnly(mBuilder, b.mLocalOnly);
            Api20Impl.setGroup(mBuilder, b.mGroupKey);
            Api20Impl.setSortKey(mBuilder, b.mSortKey);
            Api20Impl.setGroupSummary(mBuilder, b.mGroupSummary);
            mGroupAlertBehavior = b.mGroupAlertBehavior;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            Api21Impl.setCategory(mBuilder, b.mCategory);
            Api21Impl.setColor(mBuilder, b.mColor);
            Api21Impl.setVisibility(mBuilder, b.mVisibility);
            Api21Impl.setPublicVersion(mBuilder, b.mPublicVersion);
            Api21Impl.setSound(mBuilder, n.sound, n.audioAttributes);

            final List<String> people;
            if (Build.VERSION.SDK_INT < 28) {
                people = combineLists(getPeople(b.mPersonList), b.mPeople);
            } else {
                people = b.mPeople;
            }
            if (people != null && !people.isEmpty()) {
                for (String person : people) {
                    Api21Impl.addPerson(mBuilder, person);
                }
            }

            mHeadsUpContentView = b.mHeadsUpContentView;

            if (b.mInvisibleActions.size() > 0) {
                // Invisible actions should be stored in the extender so we need to check if one
                // exists already.
                Bundle carExtenderBundle =
                        b.getExtras().getBundle(NotificationCompat.CarExtender.EXTRA_CAR_EXTENDER);
                if (carExtenderBundle == null) {
                    carExtenderBundle = new Bundle();
                }
                Bundle extenderBundleCopy = new Bundle(carExtenderBundle);
                Bundle listBundle = new Bundle();
                for (int i = 0; i < b.mInvisibleActions.size(); i++) {
                    listBundle.putBundle(
                            Integer.toString(i),
                            NotificationCompatJellybean.getBundleForAction(
                                    b.mInvisibleActions.get(i)));
                }
                carExtenderBundle.putBundle(
                        NotificationCompat.CarExtender.EXTRA_INVISIBLE_ACTIONS, listBundle);
                extenderBundleCopy.putBundle(
                        NotificationCompat.CarExtender.EXTRA_INVISIBLE_ACTIONS, listBundle);
                b.getExtras().putBundle(
                        NotificationCompat.CarExtender.EXTRA_CAR_EXTENDER, carExtenderBundle);
                mExtras.putBundle(
                        NotificationCompat.CarExtender.EXTRA_CAR_EXTENDER, extenderBundleCopy);
            }
        }
        if (Build.VERSION.SDK_INT >= 23) {
            if (b.mSmallIcon != null) {
                Api23Impl.setSmallIcon(mBuilder, b.mSmallIcon);
            }
        }
        if (Build.VERSION.SDK_INT >= 24) {
            Api19Impl.setExtras(mBuilder, b.mExtras);
            Api24Impl.setRemoteInputHistory(mBuilder, b.mRemoteInputHistory);
            if (b.mContentView != null) {
                Api24Impl.setCustomContentView(mBuilder, b.mContentView);
            }
            if (b.mBigContentView != null) {
                Api24Impl.setCustomBigContentView(mBuilder, b.mBigContentView);
            }
            if (b.mHeadsUpContentView != null) {
                Api24Impl.setCustomHeadsUpContentView(mBuilder, b.mHeadsUpContentView);
            }
        }
        if (Build.VERSION.SDK_INT >= 26) {
            Api26Impl.setBadgeIconType(mBuilder, b.mBadgeIcon);
            Api26Impl.setSettingsText(mBuilder, b.mSettingsText);
            Api26Impl.setShortcutId(mBuilder, b.mShortcutId);
            Api26Impl.setTimeoutAfter(mBuilder, b.mTimeout);
            Api26Impl.setGroupAlertBehavior(mBuilder, b.mGroupAlertBehavior);
            if (b.mColorizedSet) {
                Api26Impl.setColorized(mBuilder, b.mColorized);
            }

            if (!TextUtils.isEmpty(b.mChannelId)) {
                mBuilder.setSound(null)
                        .setDefaults(0)
                        .setLights(0, 0, 0)
                        .setVibrate(null);
            }
        }
        if (Build.VERSION.SDK_INT >= 28) {
            for (Person p : b.mPersonList) {
                Api28Impl.addPerson(mBuilder, p.toAndroidPerson());
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            Api29Impl.setAllowSystemGeneratedContextualActions(mBuilder,
                    b.mAllowSystemGeneratedContextualActions);
            // TODO: Consider roundtripping NotificationCompat.BubbleMetadata on pre-Q platforms.
            Api29Impl.setBubbleMetadata(mBuilder,
                    NotificationCompat.BubbleMetadata.toPlatform(b.mBubbleMetadata));
            if (b.mLocusId != null) {
                Api29Impl.setLocusId(mBuilder, b.mLocusId.toLocusId());
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            if (b.mFgsDeferBehavior != NotificationCompat.FOREGROUND_SERVICE_DEFAULT) {
                Api31Impl.setForegroundServiceBehavior(mBuilder, b.mFgsDeferBehavior);
            }
        }

        if (b.mSilent) {
            if (mBuilderCompat.mGroupSummary) {
                mGroupAlertBehavior = GROUP_ALERT_CHILDREN;
            } else {
                mGroupAlertBehavior = GROUP_ALERT_SUMMARY;
            }

            mBuilder.setVibrate(null);
            mBuilder.setSound(null);
            n.defaults &= ~DEFAULT_SOUND;
            n.defaults &= ~DEFAULT_VIBRATE;
            mBuilder.setDefaults(n.defaults);

            if (Build.VERSION.SDK_INT >= 26) {
                if (TextUtils.isEmpty(mBuilderCompat.mGroupKey)) {
                    Api20Impl.setGroup(mBuilder, NotificationCompat.GROUP_KEY_SILENT);
                }
                Api26Impl.setGroupAlertBehavior(mBuilder, mGroupAlertBehavior);
            }
        }
    }

    @Nullable
    private static List<String> combineLists(@Nullable final List<String> first,
            @Nullable final List<String> second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        final ArraySet<String> people = new ArraySet<>(first.size() + second.size());
        people.addAll(first);
        people.addAll(second);
        return new ArrayList<>(people);
    }

    @Nullable
    private static List<String> getPeople(@Nullable final List<Person> people) {
        if (people == null) {
            return null;
        }
        final ArrayList<String> result = new ArrayList<>(people.size());
        for (Person person : people) {
            result.add(person.resolveToLegacyUri());
        }
        return result;
    }

    @Override
    public Notification.Builder getBuilder() {
        return mBuilder;
    }

    Context getContext() {
        return mContext;
    }

    public Notification build() {
        final NotificationCompat.Style style = mBuilderCompat.mStyle;
        if (style != null) {
            style.apply(this);
        }

        RemoteViews styleContentView = style != null
                ? style.makeContentView(this)
                : null;
        Notification n = buildInternal();
        if (styleContentView != null) {
            n.contentView = styleContentView;
        } else if (mBuilderCompat.mContentView != null) {
            n.contentView = mBuilderCompat.mContentView;
        }
        if (Build.VERSION.SDK_INT >= 16 && style != null) {
            RemoteViews styleBigContentView = style.makeBigContentView(this);
            if (styleBigContentView != null) {
                n.bigContentView = styleBigContentView;
            }
        }
        if (Build.VERSION.SDK_INT >= 21 && style != null) {
            RemoteViews styleHeadsUpContentView =
                    mBuilderCompat.mStyle.makeHeadsUpContentView(this);
            if (styleHeadsUpContentView != null) {
                n.headsUpContentView = styleHeadsUpContentView;
            }
        }

        if (Build.VERSION.SDK_INT >= 16 && style != null) {
            Bundle extras = NotificationCompat.getExtras(n);
            if (extras != null) {
                style.addCompatExtras(extras);
            }
        }

        return n;
    }

    private void addAction(NotificationCompat.Action action) {
        if (Build.VERSION.SDK_INT >= 20) {
            Notification.Action.Builder actionBuilder;
            IconCompat iconCompat = action.getIconCompat();
            if (Build.VERSION.SDK_INT >= 23) {
                actionBuilder = Api23Impl.createBuilder(
                        iconCompat != null ? iconCompat.toIcon() : null, action.getTitle(),
                        action.getActionIntent());
            } else {
                actionBuilder = Api20Impl.createBuilder(
                        iconCompat != null ? iconCompat.getResId() : 0, action.getTitle(),
                        action.getActionIntent());
            }
            if (action.getRemoteInputs() != null) {
                for (android.app.RemoteInput remoteInput : RemoteInput.fromCompat(
                        action.getRemoteInputs())) {
                    Api20Impl.addRemoteInput(actionBuilder, remoteInput);
                }
            }
            Bundle actionExtras;
            if (action.getExtras() != null) {
                actionExtras = new Bundle(action.getExtras());
            } else {
                actionExtras = new Bundle();
            }
            actionExtras.putBoolean(NotificationCompatJellybean.EXTRA_ALLOW_GENERATED_REPLIES,
                    action.getAllowGeneratedReplies());
            if (Build.VERSION.SDK_INT >= 24) {
                Api24Impl.setAllowGeneratedReplies(actionBuilder,
                        action.getAllowGeneratedReplies());
            }

            actionExtras.putInt(NotificationCompat.Action.EXTRA_SEMANTIC_ACTION,
                    action.getSemanticAction());
            if (Build.VERSION.SDK_INT >= 28) {
                Api28Impl.setSemanticAction(actionBuilder, action.getSemanticAction());
            }

            if (Build.VERSION.SDK_INT >= 29) {
                Api29Impl.setContextual(actionBuilder, action.isContextual());
            }

            if (Build.VERSION.SDK_INT >= 31) {
                Api31Impl.setAuthenticationRequired(actionBuilder,
                        action.isAuthenticationRequired());
            }

            actionExtras.putBoolean(NotificationCompat.Action.EXTRA_SHOWS_USER_INTERFACE,
                    action.getShowsUserInterface());
            Api20Impl.addExtras(actionBuilder, actionExtras);
            Api20Impl.addAction(mBuilder, Api20Impl.build(actionBuilder));
        } else if (Build.VERSION.SDK_INT >= 16) {
            mActionExtrasList.add(
                    NotificationCompatJellybean.writeActionAndGetExtras(mBuilder, action));
        }
    }

    @SuppressWarnings("deprecation")
    protected Notification buildInternal() {
        if (Build.VERSION.SDK_INT >= 26) {
            return Api16Impl.build(mBuilder);
        } else if (Build.VERSION.SDK_INT >= 24) {
            Notification notification =  Api16Impl.build(mBuilder);

            if (mGroupAlertBehavior != GROUP_ALERT_ALL) {
                // if is summary and only children should alert
                if (Api20Impl.getGroup(notification) != null
                        && (notification.flags & FLAG_GROUP_SUMMARY) != 0
                        && mGroupAlertBehavior == GROUP_ALERT_CHILDREN) {
                    removeSoundAndVibration(notification);
                }
                // if is group child and only summary should alert
                if (Api20Impl.getGroup(notification) != null
                        && (notification.flags & FLAG_GROUP_SUMMARY) == 0
                        && mGroupAlertBehavior == GROUP_ALERT_SUMMARY) {
                    removeSoundAndVibration(notification);
                }
            }

            return notification;
        } else if (Build.VERSION.SDK_INT >= 21) {
            Api19Impl.setExtras(mBuilder, mExtras);
            Notification notification = Api16Impl.build(mBuilder);
            if (mContentView != null) {
                notification.contentView = mContentView;
            }
            if (mBigContentView != null) {
                notification.bigContentView = mBigContentView;
            }
            if (mHeadsUpContentView != null) {
                notification.headsUpContentView = mHeadsUpContentView;
            }

            if (mGroupAlertBehavior != GROUP_ALERT_ALL) {
                // if is summary and only children should alert
                if (Api20Impl.getGroup(notification) != null
                        && (notification.flags & FLAG_GROUP_SUMMARY) != 0
                        && mGroupAlertBehavior == GROUP_ALERT_CHILDREN) {
                    removeSoundAndVibration(notification);
                }
                // if is group child and only summary should alert
                if (Api20Impl.getGroup(notification) != null
                        && (notification.flags & FLAG_GROUP_SUMMARY) == 0
                        && mGroupAlertBehavior == GROUP_ALERT_SUMMARY) {
                    removeSoundAndVibration(notification);
                }
            }
            return notification;
        } else if (Build.VERSION.SDK_INT >= 20) {
            Api19Impl.setExtras(mBuilder, mExtras);
            Notification notification = Api16Impl.build(mBuilder);
            if (mContentView != null) {
                notification.contentView = mContentView;
            }
            if (mBigContentView != null) {
                notification.bigContentView = mBigContentView;
            }

            if (mGroupAlertBehavior != GROUP_ALERT_ALL) {
                // if is summary and only children should alert
                if (Api20Impl.getGroup(notification) != null
                        && (notification.flags & FLAG_GROUP_SUMMARY) != 0
                        && mGroupAlertBehavior == GROUP_ALERT_CHILDREN) {
                    removeSoundAndVibration(notification);
                }
                // if is group child and only summary should alert
                if (Api20Impl.getGroup(notification) != null
                        && (notification.flags & FLAG_GROUP_SUMMARY) == 0
                        && mGroupAlertBehavior == GROUP_ALERT_SUMMARY) {
                    removeSoundAndVibration(notification);
                }
            }

            return notification;
        } else if (Build.VERSION.SDK_INT >= 19) {
            SparseArray<Bundle> actionExtrasMap =
                    NotificationCompatJellybean.buildActionExtrasMap(mActionExtrasList);
            if (actionExtrasMap != null) {
                // Add the action extras sparse array if any action was added with extras.
                mExtras.putSparseParcelableArray(
                        NotificationCompatExtras.EXTRA_ACTION_EXTRAS, actionExtrasMap);
            }
            Api19Impl.setExtras(mBuilder, mExtras);
            Notification notification = Api16Impl.build(mBuilder);
            if (mContentView != null) {
                notification.contentView = mContentView;
            }
            if (mBigContentView != null) {
                notification.bigContentView = mBigContentView;
            }
            return notification;
        } else if (Build.VERSION.SDK_INT >= 16) {
            Notification notification = Api16Impl.build(mBuilder);
            // Merge in developer provided extras, but let the values already set
            // for keys take precedence.
            Bundle extras = NotificationCompat.getExtras(notification);
            Bundle mergeBundle = new Bundle(mExtras);
            for (String key : mExtras.keySet()) {
                if (extras.containsKey(key)) {
                    mergeBundle.remove(key);
                }
            }
            extras.putAll(mergeBundle);
            SparseArray<Bundle> actionExtrasMap =
                    NotificationCompatJellybean.buildActionExtrasMap(mActionExtrasList);
            if (actionExtrasMap != null) {
                // Add the action extras sparse array if any action was added with extras.
                NotificationCompat.getExtras(notification).putSparseParcelableArray(
                        NotificationCompatExtras.EXTRA_ACTION_EXTRAS, actionExtrasMap);
            }
            if (mContentView != null) {
                notification.contentView = mContentView;
            }
            if (mBigContentView != null) {
                notification.bigContentView = mBigContentView;
            }
            return notification;
        } else {
            return mBuilder.getNotification();
        }
    }

    private void removeSoundAndVibration(Notification notification) {
        notification.sound = null;
        notification.vibrate = null;
        notification.defaults &= ~DEFAULT_SOUND;
        notification.defaults &= ~DEFAULT_VIBRATE;
    }

    /**
     * A class for wrapping calls to {@link NotificationCompatBuilder} methods which
     * were added in API 16; these calls must be wrapped to avoid performance issues.
     * See the UnsafeNewApiCall lint rule for more details.
     */
    @RequiresApi(16)
    static class Api16Impl {
        private Api16Impl() { }

        @DoNotInline
        static Notification.Builder setSubText(Notification.Builder builder, CharSequence text) {
            return builder.setSubText(text);
        }

        @DoNotInline
        static Notification.Builder setUsesChronometer(Notification.Builder builder, boolean b) {
            return builder.setUsesChronometer(b);
        }

        @DoNotInline
        static Notification.Builder setPriority(Notification.Builder builder, int pri) {
            return builder.setPriority(pri);
        }

        @DoNotInline
        static Notification build(Notification.Builder builder) {
            return builder.build();
        }
    }

    /**
     * A class for wrapping calls to {@link NotificationCompatBuilder} methods which
     * were added in API 17; these calls must be wrapped to avoid performance issues.
     * See the UnsafeNewApiCall lint rule for more details.
     */
    @RequiresApi(17)
    static class Api17Impl {
        private Api17Impl() {
        }

        @DoNotInline
        static Notification.Builder setShowWhen(Notification.Builder builder, boolean show) {
            return builder.setShowWhen(show);
        }

    }

    /**
     * A class for wrapping calls to {@link NotificationCompatBuilder} methods which
     * were added in API 19; these calls must be wrapped to avoid performance issues.
     * See the UnsafeNewApiCall lint rule for more details.
     */
    @RequiresApi(19)
    static class Api19Impl {
        private Api19Impl() { }

        @DoNotInline
        static Notification.Builder setExtras(Notification.Builder builder, Bundle extras) {
            return builder.setExtras(extras);
        }
    }

    /**
     * A class for wrapping calls to {@link NotificationCompatBuilder} methods which
     * were added in API 20; these calls must be wrapped to avoid performance issues.
     * See the UnsafeNewApiCall lint rule for more details.
     */
    @RequiresApi(20)
    static class Api20Impl {
        private Api20Impl() { }

        @DoNotInline
        static Notification.Action.Builder createBuilder(int icon, CharSequence title,
                PendingIntent intent) {
            return new Notification.Action.Builder(icon, title, intent);
        }

        @DoNotInline
        static Notification.Action.Builder addRemoteInput(Notification.Action.Builder builder,
                android.app.RemoteInput remoteInput) {
            return builder.addRemoteInput(remoteInput);
        }

        @DoNotInline
        static Notification.Action.Builder addExtras(Notification.Action.Builder builder,
                Bundle extras) {
            return builder.addExtras(extras);
        }


        @DoNotInline
        static Notification.Builder addAction(Notification.Builder builder,
                Notification.Action action) {
            return builder.addAction(action);
        }

        @DoNotInline
        static Notification.Action build(Notification.Action.Builder builder) {
            return builder.build();
        }

        @DoNotInline
        static String getGroup(Notification notification) {
            return notification.getGroup();
        }

        @DoNotInline
        static Notification.Builder setGroup(Notification.Builder builder, String groupKey) {
            return builder.setGroup(groupKey);
        }

        @DoNotInline
        static Notification.Builder setGroupSummary(Notification.Builder builder,
                boolean isGroupSummary) {
            return builder.setGroupSummary(isGroupSummary);
        }

        @DoNotInline
        static Notification.Builder setLocalOnly(Notification.Builder builder, boolean localOnly) {
            return builder.setLocalOnly(localOnly);
        }

        @DoNotInline
        static Notification.Builder setSortKey(Notification.Builder builder, String sortKey) {
            return builder.setSortKey(sortKey);
        }
    }

    /**
     * A class for wrapping calls to {@link NotificationCompatBuilder} methods which
     * were added in API 21; these calls must be wrapped to avoid performance issues.
     * See the UnsafeNewApiCall lint rule for more details.
     */
    @RequiresApi(21)
    static class Api21Impl {
        private Api21Impl() { }

        @DoNotInline
        static Notification.Builder addPerson(Notification.Builder builder, String uri) {
            return builder.addPerson(uri);
        }

        @DoNotInline
        static Notification.Builder setCategory(Notification.Builder builder, String category) {
            return builder.setCategory(category);
        }

        @DoNotInline
        static Notification.Builder setColor(Notification.Builder builder, int argb) {
            return builder.setColor(argb);
        }

        @DoNotInline
        static Notification.Builder setVisibility(Notification.Builder builder, int visibility) {
            return builder.setVisibility(visibility);
        }

        @DoNotInline
        static Notification.Builder setPublicVersion(Notification.Builder builder, Notification n) {
            return builder.setPublicVersion(n);
        }

        @DoNotInline
        static Notification.Builder setSound(Notification.Builder builder, Uri sound,
                Object audioAttributes /** AudioAttributes **/) {
            return builder.setSound(sound, (AudioAttributes) audioAttributes);
        }
    }

    /**
     * A class for wrapping calls to {@link NotificationCompatBuilder} methods which
     * were added in API 23; these calls must be wrapped to avoid performance issues.
     * See the UnsafeNewApiCall lint rule for more details.
     */
    @RequiresApi(23)
    static class Api23Impl {
        private Api23Impl() { }

        @DoNotInline
        static Notification.Action.Builder createBuilder(Icon icon, CharSequence title,
                PendingIntent intent) {
            return new Notification.Action.Builder(icon, title, intent);
        }

        @DoNotInline
        static Notification.Builder setSmallIcon(Notification.Builder builder,
                Object icon /** Icon **/) {
            return builder.setSmallIcon((Icon) icon);
        }

        @DoNotInline
        static Notification.Builder setLargeIcon(Notification.Builder builder, Icon icon) {
            return builder.setLargeIcon(icon);
        }
    }

    /**
     * A class for wrapping calls to {@link NotificationCompatBuilder} methods which
     * were added in API 24; these calls must be wrapped to avoid performance issues.
     * See the UnsafeNewApiCall lint rule for more details.
     */
    @RequiresApi(24)
    static class Api24Impl {
        private Api24Impl() { }

        @DoNotInline
        static Notification.Action.Builder setAllowGeneratedReplies(
                Notification.Action.Builder builder, boolean allowGeneratedReplies) {
            return builder.setAllowGeneratedReplies(allowGeneratedReplies);
        }

        @DoNotInline
        static Notification.Builder setRemoteInputHistory(Notification.Builder builder,
                CharSequence[] text) {
            return builder.setRemoteInputHistory(text);
        }

        @DoNotInline
        static Notification.Builder setCustomContentView(Notification.Builder builder,
                RemoteViews contentView) {
            return builder.setCustomContentView(contentView);
        }

        @DoNotInline
        static Notification.Builder setCustomBigContentView(Notification.Builder builder,
                RemoteViews contentView) {
            return builder.setCustomBigContentView(contentView);
        }

        @DoNotInline
        static Notification.Builder setCustomHeadsUpContentView(Notification.Builder builder,
                RemoteViews contentView) {
            return builder.setCustomHeadsUpContentView(contentView);
        }
    }

    /**
     * A class for wrapping calls to {@link NotificationCompatBuilder} methods which
     * were added in API 26; these calls must be wrapped to avoid performance issues.
     * See the UnsafeNewApiCall lint rule for more details.
     */
    @RequiresApi(26)
    static class Api26Impl {
        private Api26Impl() { }

        @DoNotInline
        static Notification.Builder createBuilder(Context context, String channelId) {
            return new Notification.Builder(context, channelId);
        }

        @DoNotInline
        static Notification.Builder setGroupAlertBehavior(Notification.Builder builder,
                int groupAlertBehavior) {
            return builder.setGroupAlertBehavior(groupAlertBehavior);
        }

        @DoNotInline
        static Notification.Builder setColorized(Notification.Builder builder, boolean colorize) {
            return builder.setColorized(colorize);
        }

        @DoNotInline
        static Notification.Builder setBadgeIconType(Notification.Builder builder, int icon) {
            return builder.setBadgeIconType(icon);
        }

        @DoNotInline
        static Notification.Builder setSettingsText(Notification.Builder builder,
                CharSequence text) {
            return builder.setSettingsText(text);
        }

        @DoNotInline
        static Notification.Builder setShortcutId(Notification.Builder builder, String shortcutId) {
            return builder.setShortcutId(shortcutId);
        }

        @DoNotInline
        static Notification.Builder setTimeoutAfter(Notification.Builder builder, long durationMs) {
            return builder.setTimeoutAfter(durationMs);
        }
    }

    /**
     * A class for wrapping calls to {@link NotificationCompatBuilder} methods which
     * were added in API 28; these calls must be wrapped to avoid performance issues.
     * See the UnsafeNewApiCall lint rule for more details.
     */
    @RequiresApi(28)
    static class Api28Impl {
        private Api28Impl() {
        }

        @DoNotInline
        static Notification.Action.Builder setSemanticAction(Notification.Action.Builder builder,
                int semanticAction) {
            return builder.setSemanticAction(semanticAction);
        }

        @DoNotInline
        static Notification.Builder addPerson(Notification.Builder builder,
                android.app.Person person) {
            return builder.addPerson(person);
        }
    }

    /**
     * A class for wrapping calls to {@link NotificationCompatBuilder} methods which
     * were added in API 29; these calls must be wrapped to avoid performance issues.
     * See the UnsafeNewApiCall lint rule for more details.
     */
    @RequiresApi(29)
    static class Api29Impl {
        private Api29Impl() { }

        @DoNotInline
        static Notification.Action.Builder setContextual(Notification.Action.Builder builder,
                boolean isContextual) {
            return builder.setContextual(isContextual);
        }

        @DoNotInline
        static Notification.Builder setLocusId(Notification.Builder builder,
                Object locusId /** LocusId **/) {
            return builder.setLocusId((LocusId) locusId);
        }

        @DoNotInline
        static Notification.Builder setBubbleMetadata(Notification.Builder builder,
                Notification.BubbleMetadata data) {
            return builder.setBubbleMetadata(data);
        }

        @DoNotInline
        static Notification.Builder setAllowSystemGeneratedContextualActions(
                Notification.Builder builder, boolean allowed) {
            return builder.setAllowSystemGeneratedContextualActions(allowed);
        }
    }

    /**
     * A class for wrapping calls to {@link NotificationCompatBuilder} methods which
     * were added in API 31; these calls must be wrapped to avoid performance issues.
     * See the UnsafeNewApiCall lint rule for more details.
     */
    @RequiresApi(31)
    static class Api31Impl {
        private Api31Impl() {
        }

        @DoNotInline
        static Notification.Action.Builder setAuthenticationRequired(
                Notification.Action.Builder builder, boolean authenticationRequired) {
            return builder.setAuthenticationRequired(authenticationRequired);
        }

        @DoNotInline
        static Notification.Builder setForegroundServiceBehavior(Notification.Builder builder,
                int behavior) {
            return builder.setForegroundServiceBehavior(behavior);
        }
    }
}
