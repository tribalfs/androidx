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

package androidx.media2;

import android.app.PendingIntent;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.session.MediaSessionCompat.Token;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.collection.ArrayMap;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media2.MediaLibraryService2.LibraryRoot;
import androidx.media2.MediaLibraryService2.MediaLibrarySession;
import androidx.media2.MediaLibraryService2.MediaLibrarySession.MediaLibrarySessionCallback;
import androidx.media2.MediaLibraryService2.MediaLibrarySession.MediaLibrarySessionImpl;
import androidx.media2.MediaSession2.ControllerCb;
import androidx.media2.MediaSession2.ControllerInfo;
import androidx.media2.MediaSession2.SessionCallback;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

class MediaLibrarySessionImplBase extends MediaSession2ImplBase implements MediaLibrarySessionImpl {
    @GuardedBy("mLock")
    private final ArrayMap<ControllerCb, Set<String>> mSubscriptions = new ArrayMap<>();

    MediaLibrarySessionImplBase(MediaSession2 instance, Context context, String id,
            MediaPlayerConnector player, MediaPlaylistAgent playlistAgent,
            PendingIntent sessionActivity, Executor callbackExecutor, SessionCallback callback) {
        super(instance, context, id, player, playlistAgent, sessionActivity, callbackExecutor,
                callback);
    }

    @Override
    MediaBrowserServiceCompat createLegacyBrowserService(Context context, SessionToken2 token,
            Token sessionToken) {
        return new MediaLibraryService2LegacyStub(context, this, sessionToken);
    }

    @Override
    public MediaLibrarySession getInstance() {
        return (MediaLibrarySession) super.getInstance();
    }

    @Override
    public MediaLibrarySessionCallback getCallback() {
        return (MediaLibrarySessionCallback) super.getCallback();
    }

    @Override
    MediaLibraryService2LegacyStub getLegacyBrowserService() {
        return (MediaLibraryService2LegacyStub) super.getLegacyBrowserService();
    }

    @Override
    public List<ControllerInfo> getConnectedControllers() {
        List<ControllerInfo> list = super.getConnectedControllers();
        list.addAll(getLegacyBrowserService().getConnectedControllersManager()
                .getConnectedControllers());
        return list;
    }

    @Override
    public boolean isConnected(ControllerInfo controller) {
        return super.isConnected(controller)
                || getLegacyBrowserService().getConnectedControllersManager().isConnected(
                        controller);
    }

    @Override
    public void notifyChildrenChanged(final String parentId, final int itemCount,
            final Bundle extras) {
        if (TextUtils.isEmpty(parentId)) {
            throw new IllegalArgumentException("query shouldn't be empty");
        }
        if (itemCount < 0) {
            throw new IllegalArgumentException("itemCount shouldn't be negative");
        }

        notifyToAllControllers(new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                if (isSubscribed(callback, parentId)) {
                    callback.onChildrenChanged(parentId, itemCount, extras);
                }
            }
        });
    }

    @Override
    public void notifyChildrenChanged(final ControllerInfo controller, final String parentId,
            final int itemCount, final Bundle extras) {
        if (controller == null) {
            throw new IllegalArgumentException("controller shouldn't be null");
        }
        if (TextUtils.isEmpty(parentId)) {
            throw new IllegalArgumentException("query shouldn't be empty");
        }
        if (itemCount < 0) {
            throw new IllegalArgumentException("itemCount shouldn't be negative");
        }
        notifyToController(controller, new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                if (!isSubscribed(callback, parentId)) {
                    if (DEBUG) {
                        Log.d(TAG, "Skipping notifyChildrenChanged() to " + controller
                                + " because it hasn't subscribed");
                        dumpSubscription();
                    }
                    return;
                }
                callback.onChildrenChanged(parentId, itemCount, extras);
            }
        });
    }

    @Override
    public void notifySearchResultChanged(ControllerInfo controller, final String query,
            final int itemCount, final Bundle extras) {
        if (controller == null) {
            throw new IllegalArgumentException("controller shouldn't be null");
        }
        if (TextUtils.isEmpty(query)) {
            throw new IllegalArgumentException("query shouldn't be empty");
        }
        notifyToController(controller, new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                callback.onSearchResultChanged(query, itemCount, extras);
            }
        });
    }

    /**
     * Called by {@link MediaSession2Stub#getLibraryRoot(IMediaController2, Bundle)}.
     *
     * @param controller
     * @param rootHints
     */
    @Override
    public void onGetLibraryRootOnExecutor(ControllerInfo controller, final Bundle rootHints) {
        final LibraryRoot root = getCallback().onGetLibraryRoot(
                getInstance(), controller, rootHints);
        notifyToController(controller, new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                callback.onGetLibraryRootDone(rootHints,
                        root == null ? null : root.getRootId(),
                        root == null ? null : root.getExtras());
            }
        });
    }

    /**
     * Called by {@link MediaSession2Stub#getItem(IMediaController2, String)}.
     *
     * @param controller
     * @param mediaId
     */
    @Override
    public void onGetItemOnExecutor(ControllerInfo controller, final String mediaId) {
        final MediaItem2 result = getCallback().onGetItem(getInstance(), controller, mediaId);
        notifyToController(controller, new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                callback.onGetItemDone(mediaId, result);
            }
        });
    }

    @Override
    public void onGetChildrenOnExecutor(ControllerInfo controller, final String parentId,
            final int page, final int pageSize, final Bundle extras) {
        final List<MediaItem2> result = getCallback().onGetChildren(getInstance(),
                controller, parentId, page, pageSize, extras);
        if (result != null && result.size() > pageSize) {
            throw new IllegalArgumentException("onGetChildren() shouldn't return media items "
                    + "more than pageSize. result.size()=" + result.size() + " pageSize="
                    + pageSize);
        }
        notifyToController(controller, new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                callback.onGetChildrenDone(parentId, page, pageSize, result, extras);
            }
        });
    }

    @Override
    public void onSubscribeOnExecutor(ControllerInfo controller, String parentId, Bundle option) {
        synchronized (mLock) {
            Set<String> subscription = mSubscriptions.get(controller.getControllerCb());
            if (subscription == null) {
                subscription = new HashSet<>();
                mSubscriptions.put(controller.getControllerCb(), subscription);
            }
            subscription.add(parentId);
        }
        // Call callbacks after adding it to the subscription list because library session may want
        // to call notifyChildrenChanged() in the callback.
        getCallback().onSubscribe(getInstance(), controller, parentId, option);
    }

    @Override
    public void onUnsubscribeOnExecutor(ControllerInfo controller, String parentId) {
        getCallback().onUnsubscribe(getInstance(), controller, parentId);
        synchronized (mLock) {
            mSubscriptions.remove(controller.getControllerCb());
        }
    }

    @Override
    public void onSearchOnExecutor(ControllerInfo controller, String query, Bundle extras) {
        getCallback().onSearch(getInstance(), controller, query, extras);
    }

    @Override
    public void onGetSearchResultOnExecutor(ControllerInfo controller, final String query,
            final int page, final int pageSize, final Bundle extras) {
        final List<MediaItem2> result = getCallback().onGetSearchResult(getInstance(),
                controller, query, page, pageSize, extras);
        if (result != null && result.size() > pageSize) {
            throw new IllegalArgumentException("onGetSearchResult() shouldn't return media "
                    + "items more than pageSize. result.size()=" + result.size() + " pageSize="
                    + pageSize);
        }
        notifyToController(controller, new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                callback.onGetSearchResultDone(query, page, pageSize, result, extras);
            }
        });
    }

    @Override
    void notifyToAllControllers(NotifyRunnable runnable) {
        super.notifyToAllControllers(runnable);
        notifyToController(getLegacyBrowserService().getControllersForAll(), runnable);
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    boolean isSubscribed(ControllerCb callback, String parentId) {
        synchronized (mLock) {
            Set<String> subscriptions = mSubscriptions.get(callback);
            if (subscriptions == null || !subscriptions.contains(parentId)) {
                return false;
            }
        }
        return true;
    }

    // Debug only
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void dumpSubscription() {
        if (!DEBUG) {
            return;
        }
        synchronized (mLock) {
            Log.d(TAG, "Dumping subscription, controller sz=" + mSubscriptions.size());
            for (int i = 0; i < mSubscriptions.size(); i++) {
                Log.d(TAG, "  controller " + mSubscriptions.valueAt(i));
                for (String parentId : mSubscriptions.valueAt(i)) {
                    Log.d(TAG, "  - " + parentId);
                }
            }
        }
    }
}
