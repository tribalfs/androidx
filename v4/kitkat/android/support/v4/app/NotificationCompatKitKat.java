/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.support.v4.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.SparseArray;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

class NotificationCompatKitKat {
    public static class Builder implements NotificationBuilderWithBuilderAccessor,
            NotificationBuilderWithActions {
        private Notification.Builder b;
        private Bundle mExtras;
        private List<Bundle> mActionExtrasList = new ArrayList<Bundle>();

        public Builder(Context context, Notification n,
                CharSequence contentTitle, CharSequence contentText, CharSequence contentInfo,
                RemoteViews tickerView, int number,
                PendingIntent contentIntent, PendingIntent fullScreenIntent, Bitmap largeIcon,
                int mProgressMax, int mProgress, boolean mProgressIndeterminate,
                boolean useChronometer, int priority, CharSequence subText, boolean localOnly,
                ArrayList<String> people, Bundle extras) {
            b = new Notification.Builder(context)
                .setWhen(n.when)
                .setSmallIcon(n.icon, n.iconLevel)
                .setContent(n.contentView)
                .setTicker(n.tickerText, tickerView)
                .setSound(n.sound, n.audioStreamType)
                .setVibrate(n.vibrate)
                .setLights(n.ledARGB, n.ledOnMS, n.ledOffMS)
                .setOngoing((n.flags & Notification.FLAG_ONGOING_EVENT) != 0)
                .setOnlyAlertOnce((n.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0)
                .setAutoCancel((n.flags & Notification.FLAG_AUTO_CANCEL) != 0)
                .setDefaults(n.defaults)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSubText(subText)
                .setContentInfo(contentInfo)
                .setContentIntent(contentIntent)
                .setDeleteIntent(n.deleteIntent)
                .setFullScreenIntent(fullScreenIntent,
                        (n.flags & Notification.FLAG_HIGH_PRIORITY) != 0)
                .setLargeIcon(largeIcon)
                .setNumber(number)
                .setUsesChronometer(useChronometer)
                .setPriority(priority)
                .setProgress(mProgressMax, mProgress, mProgressIndeterminate);
            mExtras = extras;
            if (people != null && !people.isEmpty()) {
                getExtras().putStringArray(Notification.EXTRA_PEOPLE,
                        people.toArray(new String[people.size()]));
            }
            if (localOnly) {
                getExtras().putBoolean(NotificationCompatJellybean.EXTRA_LOCAL_ONLY, true);
            }
        }

        @Override
        public void addAction(int icon, CharSequence title, PendingIntent intent, Bundle extras) {
            b.addAction(icon, title, intent);
            mActionExtrasList.add(extras);
        }

        @Override
        public Notification.Builder getBuilder() {
            return b;
        }

        public Notification build() {
            SparseArray<Bundle> actionExtrasMap = NotificationCompatJellybean.buildActionExtrasMap(
                    mActionExtrasList);
            if (actionExtrasMap != null) {
                // Add the action extras sparse array if any action was added with extras.
                getExtras().putSparseParcelableArray(
                        NotificationCompatJellybean.EXTRA_ACTION_EXTRAS, actionExtrasMap);
            }
            if (mExtras != null) {
                b.setExtras(mExtras);
            }
            return b.build();
        }

        private Bundle getExtras() {
            if (mExtras == null) {
                mExtras = new Bundle();
            }
            return mExtras;
        }
    }

    public static Bundle getExtras(Notification notif) {
        return notif.extras;
    }

    public static int getActionCount(Notification notif) {
        return notif.actions != null ? notif.actions.length : 0;
    }

    public static void getAction(Notification notif, int actionIndex,
            NotificationActionHolder holder) {
        Notification.Action action = notif.actions[actionIndex];
        Bundle actionExtras = null;
        SparseArray<Bundle> actionExtrasMap = notif.extras.getSparseParcelableArray(
                NotificationCompatJellybean.EXTRA_ACTION_EXTRAS);
        if (actionExtrasMap != null) {
            actionExtras = actionExtrasMap.get(actionIndex);
        }
        holder.set(action.icon, action.title, action.actionIntent, actionExtras);
    }

    public static boolean getLocalOnly(Notification notif) {
        return getExtras(notif).getBoolean(NotificationCompatJellybean.EXTRA_LOCAL_ONLY);
    }
}
