/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.support.v4.media;

import static android.support.v4.media.MediaBrowserProtocol.CLIENT_MSG_ADD_SUBSCRIPTION;
import static android.support.v4.media.MediaBrowserProtocol.CLIENT_MSG_CONNECT;
import static android.support.v4.media.MediaBrowserProtocol.CLIENT_MSG_DISCONNECT;
import static android.support.v4.media.MediaBrowserProtocol.CLIENT_MSG_GET_MEDIA_ITEM;
import static android.support.v4.media.MediaBrowserProtocol.CLIENT_MSG_REGISTER_CALLBACK_MESSENGER;
import static android.support.v4.media.MediaBrowserProtocol.CLIENT_MSG_REMOVE_SUBSCRIPTION;
import static android.support.v4.media.MediaBrowserProtocol.CLIENT_MSG_UNREGISTER_CALLBACK_MESSENGER;
import static android.support.v4.media.MediaBrowserProtocol.DATA_CALLBACK_TOKEN;
import static android.support.v4.media.MediaBrowserProtocol.DATA_CALLING_UID;
import static android.support.v4.media.MediaBrowserProtocol.DATA_MEDIA_ITEM_ID;
import static android.support.v4.media.MediaBrowserProtocol.DATA_MEDIA_ITEM_LIST;
import static android.support.v4.media.MediaBrowserProtocol.DATA_MEDIA_SESSION_TOKEN;
import static android.support.v4.media.MediaBrowserProtocol.DATA_OPTIONS;
import static android.support.v4.media.MediaBrowserProtocol.DATA_PACKAGE_NAME;
import static android.support.v4.media.MediaBrowserProtocol.DATA_RESULT_RECEIVER;
import static android.support.v4.media.MediaBrowserProtocol.DATA_ROOT_HINTS;
import static android.support.v4.media.MediaBrowserProtocol.EXTRA_CLIENT_VERSION;
import static android.support.v4.media.MediaBrowserProtocol.EXTRA_MESSENGER_BINDER;
import static android.support.v4.media.MediaBrowserProtocol.EXTRA_SERVICE_VERSION;
import static android.support.v4.media.MediaBrowserProtocol.SERVICE_MSG_ON_CONNECT;
import static android.support.v4.media.MediaBrowserProtocol.SERVICE_MSG_ON_CONNECT_FAILED;
import static android.support.v4.media.MediaBrowserProtocol.SERVICE_MSG_ON_LOAD_CHILDREN;
import static android.support.v4.media.MediaBrowserProtocol.SERVICE_VERSION_CURRENT;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.RemoteException;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.BundleCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.os.BuildCompat;
import android.support.v4.os.ResultReceiver;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.Pair;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Base class for media browse services.
 * <p>
 * Media browse services enable applications to browse media content provided by an application
 * and ask the application to start playing it. They may also be used to control content that
 * is already playing by way of a {@link MediaSessionCompat}.
 * </p>
 *
 * To extend this class, you must declare the service in your manifest file with
 * an intent filter with the {@link #SERVICE_INTERFACE} action.
 *
 * For example:
 * </p><pre>
 * &lt;service android:name=".MyMediaBrowserServiceCompat"
 *          android:label="&#64;string/service_name" >
 *     &lt;intent-filter>
 *         &lt;action android:name="android.media.browse.MediaBrowserService" />
 *     &lt;/intent-filter>
 * &lt;/service>
 * </pre>
 */
public abstract class MediaBrowserServiceCompat extends Service {
    private static final String TAG = "MBServiceCompat";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private MediaBrowserServiceImpl mImpl;

    /**
     * The {@link Intent} that must be declared as handled by the service.
     */
    public static final String SERVICE_INTERFACE = "android.media.browse.MediaBrowserService";

    /**
     * A key for passing the MediaItem to the ResultReceiver in getItem.
     *
     * @hide
     */
    public static final String KEY_MEDIA_ITEM = "media_item";

    private static final int RESULT_FLAG_OPTION_NOT_HANDLED = 0x00000001;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag=true, value = { RESULT_FLAG_OPTION_NOT_HANDLED })
    private @interface ResultFlags { }

    private final ArrayMap<IBinder, ConnectionRecord> mConnections = new ArrayMap<>();
    private ConnectionRecord mCurConnection;
    private final ServiceHandler mHandler = new ServiceHandler();
    MediaSessionCompat.Token mSession;

    interface MediaBrowserServiceImpl {
        void onCreate();
        IBinder onBind(Intent intent);
        void setSessionToken(MediaSessionCompat.Token token);
        void notifyChildrenChanged(final String parentId, final Bundle options);
        Bundle getBrowserRootHints();
    }

    class MediaBrowserServiceImplBase implements MediaBrowserServiceImpl {
        private Messenger mMessenger;

        @Override
        public void onCreate() {
            mMessenger = new Messenger(mHandler);
        }

        @Override
        public IBinder onBind(Intent intent) {
            if (SERVICE_INTERFACE.equals(intent.getAction())) {
                return mMessenger.getBinder();
            }
            return null;
        }

        @Override
        public void setSessionToken(final MediaSessionCompat.Token token) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Iterator<ConnectionRecord> iter = mConnections.values().iterator();
                    while (iter.hasNext()){
                        ConnectionRecord connection = iter.next();
                        try {
                            connection.callbacks.onConnect(connection.root.getRootId(), token,
                                    connection.root.getExtras());
                        } catch (RemoteException e) {
                            Log.w(TAG, "Connection for " + connection.pkg + " is no longer valid.");
                            iter.remove();
                        }
                    }
                }
            });
        }

        @Override
        public void notifyChildrenChanged(@NonNull final String parentId, final Bundle options) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (IBinder binder : mConnections.keySet()) {
                        ConnectionRecord connection = mConnections.get(binder);
                        List<Pair<IBinder, Bundle>> callbackList =
                                connection.subscriptions.get(parentId);
                        if (callbackList != null) {
                            for (Pair<IBinder, Bundle> callback : callbackList) {
                                if (MediaBrowserCompatUtils.hasDuplicatedItems(
                                        options, callback.second)) {
                                    performLoadChildren(parentId, connection, callback.second);
                                }
                            }
                        }
                    }
                }
            });
        }

        @Override
        public Bundle getBrowserRootHints() {
            if (mCurConnection == null) {
                throw new IllegalStateException("This should be called inside of onLoadChildren or"
                        + " onLoadItem methods");
            }
            return mCurConnection.rootHints == null ? null : new Bundle(mCurConnection.rootHints);
        }
    }

    class MediaBrowserServiceImplApi21 implements MediaBrowserServiceImpl,
            MediaBrowserServiceCompatApi21.ServiceCompatProxy {
        Object mServiceObj;
        Messenger mMessenger;

        @Override
        public void onCreate() {
            mServiceObj = MediaBrowserServiceCompatApi21.createService(
                    MediaBrowserServiceCompat.this, this);
            MediaBrowserServiceCompatApi21.onCreate(mServiceObj);
        }

        @Override
        public IBinder onBind(Intent intent) {
            return MediaBrowserServiceCompatApi21.onBind(mServiceObj, intent);
        }

        @Override
        public void setSessionToken(MediaSessionCompat.Token token) {
            MediaBrowserServiceCompatApi21.setSessionToken(mServiceObj, token.getToken());
        }

        @Override
        public void notifyChildrenChanged(final String parentId, final Bundle options) {
            if (mMessenger == null) {
                MediaBrowserServiceCompatApi21.notifyChildrenChanged(mServiceObj, parentId);
            } else {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        for (IBinder binder : mConnections.keySet()) {
                            ConnectionRecord connection = mConnections.get(binder);
                            List<Pair<IBinder, Bundle>> callbackList =
                                    connection.subscriptions.get(parentId);
                            if (callbackList != null) {
                                for (Pair<IBinder, Bundle> callback : callbackList) {
                                    if (MediaBrowserCompatUtils.hasDuplicatedItems(
                                            options, callback.second)) {
                                        performLoadChildren(parentId, connection, callback.second);
                                    }
                                }
                            }
                        }
                    }
                });
            }
        }

        @Override
        public Bundle getBrowserRootHints() {
            if (mMessenger == null) {
                // TODO: Handle getBrowserRootHints when connected with framework MediaBrowser.
                return null;
            }
            if (mCurConnection == null) {
                throw new IllegalStateException("This should be called inside of onLoadChildren or"
                        + " onLoadItem methods");
            }
            return mCurConnection.rootHints == null ? null : new Bundle(mCurConnection.rootHints);
        }

        @Override
        public MediaBrowserServiceCompatApi21.BrowserRoot onGetRoot(
                String clientPackageName, int clientUid, Bundle rootHints) {
            Bundle rootExtras = null;
            if (rootHints != null && rootHints.getInt(EXTRA_CLIENT_VERSION, 0) != 0) {
                rootHints.remove(EXTRA_CLIENT_VERSION);
                mMessenger = new Messenger(mHandler);
                rootExtras = new Bundle();
                rootExtras.putInt(EXTRA_SERVICE_VERSION, SERVICE_VERSION_CURRENT);
                BundleCompat.putBinder(rootExtras, EXTRA_MESSENGER_BINDER, mMessenger.getBinder());
            }
            BrowserRoot root = MediaBrowserServiceCompat.this.onGetRoot(
                    clientPackageName, clientUid, rootHints);
            if (root == null) {
                return null;
            }
            if (rootExtras == null) {
                rootExtras = root.getExtras();
            } else if (root.getExtras() != null) {
                rootExtras.putAll(root.getExtras());
            }
            return new MediaBrowserServiceCompatApi21.BrowserRoot(
                    root.getRootId(), rootExtras);
        }

        @Override
        public void onLoadChildren(String parentId,
                final MediaBrowserServiceCompatApi21.ResultWrapper<List<Parcel>> resultWrapper) {
            final Result<List<MediaBrowserCompat.MediaItem>> result
                    = new Result<List<MediaBrowserCompat.MediaItem>>(parentId) {
                @Override
                void onResultSent(List<MediaBrowserCompat.MediaItem> list, @ResultFlags int flags) {
                    List<Parcel> parcelList = null;
                    if (list != null) {
                        parcelList = new ArrayList<>();
                        for (MediaBrowserCompat.MediaItem item : list) {
                            Parcel parcel = Parcel.obtain();
                            item.writeToParcel(parcel, 0);
                            parcelList.add(parcel);
                        }
                    }
                    resultWrapper.sendResult(parcelList);
                }

                @Override
                public void detach() {
                    resultWrapper.detach();
                }
            };
            MediaBrowserServiceCompat.this.onLoadChildren(parentId, result);
        }
    }

    class MediaBrowserServiceImplApi23 extends MediaBrowserServiceImplApi21 implements
            MediaBrowserServiceCompatApi23.ServiceCompatProxy {
        @Override
        public void onCreate() {
            mServiceObj = MediaBrowserServiceCompatApi23.createService(
                    MediaBrowserServiceCompat.this, this);
            MediaBrowserServiceCompatApi21.onCreate(mServiceObj);
        }

        @Override
        public void onLoadItem(String itemId,
                final MediaBrowserServiceCompatApi21.ResultWrapper<Parcel> resultWrapper) {
            final Result<MediaBrowserCompat.MediaItem> result
                    = new Result<MediaBrowserCompat.MediaItem>(itemId) {
                @Override
                void onResultSent(MediaBrowserCompat.MediaItem item, @ResultFlags int flags) {
                    if (item == null) {
                        resultWrapper.sendResult(null);
                    } else {
                        Parcel parcelItem = Parcel.obtain();
                        item.writeToParcel(parcelItem, 0);
                        resultWrapper.sendResult(parcelItem);
                    }
                }

                @Override
                public void detach() {
                    resultWrapper.detach();
                }
            };
            MediaBrowserServiceCompat.this.onLoadItem(itemId, result);
        }
    }

    class MediaBrowserServiceImplApi24 extends MediaBrowserServiceImplApi23 implements
            MediaBrowserServiceCompatApi24.ServiceCompatProxy {
        @Override
        public void onCreate() {
            mServiceObj = MediaBrowserServiceCompatApi24.createService(
                    MediaBrowserServiceCompat.this, this);
            MediaBrowserServiceCompatApi21.onCreate(mServiceObj);
        }

        @Override
        public void notifyChildrenChanged(final String parentId, final Bundle options) {
            if (options == null) {
                MediaBrowserServiceCompatApi21.notifyChildrenChanged(mServiceObj, parentId);
            } else {
                MediaBrowserServiceCompatApi24.notifyChildrenChanged(mServiceObj, parentId,
                        options);
            }
        }

        @Override
        public void onLoadChildren(String parentId,
                final MediaBrowserServiceCompatApi24.ResultWrapper resultWrapper, Bundle options) {
            final Result<List<MediaBrowserCompat.MediaItem>> result
                    = new Result<List<MediaBrowserCompat.MediaItem>>(parentId) {
                @Override
                void onResultSent(List<MediaBrowserCompat.MediaItem> list, @ResultFlags int flags) {
                    List<Parcel> parcelList = null;
                    if (list != null) {
                        parcelList = new ArrayList<>();
                        for (MediaBrowserCompat.MediaItem item : list) {
                            Parcel parcel = Parcel.obtain();
                            item.writeToParcel(parcel, 0);
                            parcelList.add(parcel);
                        }
                    }
                    resultWrapper.sendResult(parcelList, flags);
                }

                @Override
                public void detach() {
                    resultWrapper.detach();
                }
            };
            MediaBrowserServiceCompat.this.onLoadChildren(parentId, result, options);
        }

        @Override
        public Bundle getBrowserRootHints() {
            return MediaBrowserServiceCompatApi24.getBrowserRootHints(mServiceObj);
        }
    }

    private final class ServiceHandler extends Handler {
        private final ServiceBinderImpl mServiceBinderImpl = new ServiceBinderImpl();

        @Override
        public void handleMessage(Message msg) {
            Bundle data = msg.getData();
            switch (msg.what) {
                case CLIENT_MSG_CONNECT:
                    mServiceBinderImpl.connect(data.getString(DATA_PACKAGE_NAME),
                            data.getInt(DATA_CALLING_UID), data.getBundle(DATA_ROOT_HINTS),
                            new ServiceCallbacksCompat(msg.replyTo));
                    break;
                case CLIENT_MSG_DISCONNECT:
                    mServiceBinderImpl.disconnect(new ServiceCallbacksCompat(msg.replyTo));
                    break;
                case CLIENT_MSG_ADD_SUBSCRIPTION:
                    mServiceBinderImpl.addSubscription(data.getString(DATA_MEDIA_ITEM_ID),
                            BundleCompat.getBinder(data, DATA_CALLBACK_TOKEN),
                            data.getBundle(DATA_OPTIONS),
                            new ServiceCallbacksCompat(msg.replyTo));
                    break;
                case CLIENT_MSG_REMOVE_SUBSCRIPTION:
                    mServiceBinderImpl.removeSubscription(data.getString(DATA_MEDIA_ITEM_ID),
                            BundleCompat.getBinder(data, DATA_CALLBACK_TOKEN),
                            new ServiceCallbacksCompat(msg.replyTo));
                    break;
                case CLIENT_MSG_GET_MEDIA_ITEM:
                    mServiceBinderImpl.getMediaItem(data.getString(DATA_MEDIA_ITEM_ID),
                            (ResultReceiver) data.getParcelable(DATA_RESULT_RECEIVER),
                            new ServiceCallbacksCompat(msg.replyTo));
                    break;
                case CLIENT_MSG_REGISTER_CALLBACK_MESSENGER:
                    mServiceBinderImpl.registerCallbacks(new ServiceCallbacksCompat(msg.replyTo),
                            data.getBundle(DATA_ROOT_HINTS));
                    break;
                case CLIENT_MSG_UNREGISTER_CALLBACK_MESSENGER:
                    mServiceBinderImpl.unregisterCallbacks(new ServiceCallbacksCompat(msg.replyTo));
                    break;
                default:
                    Log.w(TAG, "Unhandled message: " + msg
                            + "\n  Service version: " + SERVICE_VERSION_CURRENT
                            + "\n  Client version: " + msg.arg1);
            }
        }

        @Override
        public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
            // Binder.getCallingUid() in handleMessage will return the uid of this process.
            // In order to get the right calling uid, Binder.getCallingUid() should be called here.
            Bundle data = msg.getData();
            data.setClassLoader(MediaBrowserCompat.class.getClassLoader());
            data.putInt(DATA_CALLING_UID, Binder.getCallingUid());
            return super.sendMessageAtTime(msg, uptimeMillis);
        }

        public void postOrRun(Runnable r) {
            if (Thread.currentThread() == getLooper().getThread()) {
                r.run();
            } else {
                post(r);
            }
        }
    }

    /**
     * All the info about a connection.
     */
    private class ConnectionRecord {
        String pkg;
        Bundle rootHints;
        ServiceCallbacks callbacks;
        BrowserRoot root;
        HashMap<String, List<Pair<IBinder, Bundle>>> subscriptions = new HashMap();
    }

    /**
     * Completion handler for asynchronous callback methods in {@link MediaBrowserServiceCompat}.
     * <p>
     * Each of the methods that takes one of these to send the result must call
     * {@link #sendResult} to respond to the caller with the given results. If those
     * functions return without calling {@link #sendResult}, they must instead call
     * {@link #detach} before returning, and then may call {@link #sendResult} when
     * they are done. If more than one of those methods is called, an exception will
     * be thrown.
     *
     * @see MediaBrowserServiceCompat#onLoadChildren
     * @see MediaBrowserServiceCompat#onLoadItem
     */
    public static class Result<T> {
        private Object mDebug;
        private boolean mDetachCalled;
        private boolean mSendResultCalled;
        private int mFlags;

        Result(Object debug) {
            mDebug = debug;
        }

        /**
         * Send the result back to the caller.
         */
        public void sendResult(T result) {
            if (mSendResultCalled) {
                throw new IllegalStateException("sendResult() called twice for: " + mDebug);
            }
            mSendResultCalled = true;
            onResultSent(result, mFlags);
        }

        /**
         * Detach this message from the current thread and allow the {@link #sendResult}
         * call to happen later.
         */
        public void detach() {
            if (mDetachCalled) {
                throw new IllegalStateException("detach() called when detach() had already"
                        + " been called for: " + mDebug);
            }
            if (mSendResultCalled) {
                throw new IllegalStateException("detach() called when sendResult() had already"
                        + " been called for: " + mDebug);
            }
            mDetachCalled = true;
        }

        boolean isDone() {
            return mDetachCalled || mSendResultCalled;
        }

        void setFlags(@ResultFlags int flags) {
            mFlags = flags;
        }

        /**
         * Called when the result is sent, after assertions about not being called twice
         * have happened.
         */
        void onResultSent(T result, @ResultFlags int flags) {
        }
    }

    private class ServiceBinderImpl {
        public void connect(final String pkg, final int uid, final Bundle rootHints,
                final ServiceCallbacks callbacks) {

            if (!isValidPackage(pkg, uid)) {
                throw new IllegalArgumentException("Package/uid mismatch: uid=" + uid
                        + " package=" + pkg);
            }

            mHandler.postOrRun(new Runnable() {
                @Override
                public void run() {
                    final IBinder b = callbacks.asBinder();

                    // Clear out the old subscriptions. We are getting new ones.
                    mConnections.remove(b);

                    final ConnectionRecord connection = new ConnectionRecord();
                    connection.pkg = pkg;
                    connection.rootHints = rootHints;
                    connection.callbacks = callbacks;

                    connection.root =
                            MediaBrowserServiceCompat.this.onGetRoot(pkg, uid, rootHints);

                    // If they didn't return something, don't allow this client.
                    if (connection.root == null) {
                        Log.i(TAG, "No root for client " + pkg + " from service "
                                + getClass().getName());
                        try {
                            callbacks.onConnectFailed();
                        } catch (RemoteException ex) {
                            Log.w(TAG, "Calling onConnectFailed() failed. Ignoring. "
                                    + "pkg=" + pkg);
                        }
                    } else {
                        try {
                            mConnections.put(b, connection);
                            if (mSession != null) {
                                callbacks.onConnect(connection.root.getRootId(),
                                        mSession, connection.root.getExtras());
                            }
                        } catch (RemoteException ex) {
                            Log.w(TAG, "Calling onConnect() failed. Dropping client. "
                                    + "pkg=" + pkg);
                            mConnections.remove(b);
                        }
                    }
                }
            });
        }

        public void disconnect(final ServiceCallbacks callbacks) {
            mHandler.postOrRun(new Runnable() {
                @Override
                public void run() {
                    final IBinder b = callbacks.asBinder();

                    // Clear out the old subscriptions. We are getting new ones.
                    final ConnectionRecord old = mConnections.remove(b);
                    if (old != null) {
                        // TODO
                    }
                }
            });
        }

        public void addSubscription(final String id, final IBinder token, final Bundle options,
                final ServiceCallbacks callbacks) {
            mHandler.postOrRun(new Runnable() {
                @Override
                public void run() {
                    final IBinder b = callbacks.asBinder();

                    // Get the record for the connection
                    final ConnectionRecord connection = mConnections.get(b);
                    if (connection == null) {
                        Log.w(TAG, "addSubscription for callback that isn't registered id="
                                + id);
                        return;
                    }

                    MediaBrowserServiceCompat.this.addSubscription(id, connection, token, options);
                }
            });
        }

        public void removeSubscription(final String id, final IBinder token,
                final ServiceCallbacks callbacks) {
            mHandler.postOrRun(new Runnable() {
                @Override
                public void run() {
                    final IBinder b = callbacks.asBinder();

                    ConnectionRecord connection = mConnections.get(b);
                    if (connection == null) {
                        Log.w(TAG, "removeSubscription for callback that isn't registered id="
                                + id);
                        return;
                    }
                    if (!MediaBrowserServiceCompat.this.removeSubscription(
                            id, connection, token)) {
                        Log.w(TAG, "removeSubscription called for " + id
                                + " which is not subscribed");
                    }
                }
            });
        }

        public void getMediaItem(final String mediaId, final ResultReceiver receiver,
                final ServiceCallbacks callbacks) {
            if (TextUtils.isEmpty(mediaId) || receiver == null) {
                return;
            }

            mHandler.postOrRun(new Runnable() {
                @Override
                public void run() {
                    final IBinder b = callbacks.asBinder();

                    ConnectionRecord connection = mConnections.get(b);
                    if (connection == null) {
                        Log.w(TAG, "getMediaItem for callback that isn't registered id=" + mediaId);
                        return;
                    }
                    performLoadItem(mediaId, connection, receiver);
                }
            });
        }

        // Used when {@link MediaBrowserProtocol#EXTRA_MESSENGER_BINDER} is used.
        public void registerCallbacks(final ServiceCallbacks callbacks, final Bundle rootHints) {
            mHandler.postOrRun(new Runnable() {
                @Override
                public void run() {
                    final IBinder b = callbacks.asBinder();
                    // Clear out the old subscriptions. We are getting new ones.
                    mConnections.remove(b);

                    final ConnectionRecord connection = new ConnectionRecord();
                    connection.callbacks = callbacks;
                    connection.rootHints = rootHints;
                    mConnections.put(b, connection);
                }
            });
        }

        // Used when {@link MediaBrowserProtocol#EXTRA_MESSENGER_BINDER} is used.
        public void unregisterCallbacks(final ServiceCallbacks callbacks) {
            mHandler.postOrRun(new Runnable() {
                @Override
                public void run() {
                    final IBinder b = callbacks.asBinder();
                    mConnections.remove(b);
                }
            });
        }
    }

    private interface ServiceCallbacks {
        IBinder asBinder();
        void onConnect(String root, MediaSessionCompat.Token session, Bundle extras)
                throws RemoteException;
        void onConnectFailed() throws RemoteException;
        void onLoadChildren(String mediaId, List<MediaBrowserCompat.MediaItem> list, Bundle options)
                throws RemoteException;
    }

    private class ServiceCallbacksCompat implements ServiceCallbacks {
        final Messenger mCallbacks;

        ServiceCallbacksCompat(Messenger callbacks) {
            mCallbacks = callbacks;
        }

        @Override
        public IBinder asBinder() {
            return mCallbacks.getBinder();
        }

        @Override
        public void onConnect(String root, MediaSessionCompat.Token session, Bundle extras)
                throws RemoteException {
            if (extras == null) {
                extras = new Bundle();
            }
            extras.putInt(EXTRA_SERVICE_VERSION, SERVICE_VERSION_CURRENT);
            Bundle data = new Bundle();
            data.putString(DATA_MEDIA_ITEM_ID, root);
            data.putParcelable(DATA_MEDIA_SESSION_TOKEN, session);
            data.putBundle(DATA_ROOT_HINTS, extras);
            sendRequest(SERVICE_MSG_ON_CONNECT, data);
        }

        @Override
        public void onConnectFailed() throws RemoteException {
            sendRequest(SERVICE_MSG_ON_CONNECT_FAILED, null);
        }

        @Override
        public void onLoadChildren(String mediaId, List<MediaBrowserCompat.MediaItem> list,
                Bundle options) throws RemoteException {
            Bundle data = new Bundle();
            data.putString(DATA_MEDIA_ITEM_ID, mediaId);
            data.putBundle(DATA_OPTIONS, options);
            if (list != null) {
                data.putParcelableArrayList(DATA_MEDIA_ITEM_LIST,
                        list instanceof ArrayList ? (ArrayList) list : new ArrayList<>(list));
            }
            sendRequest(SERVICE_MSG_ON_LOAD_CHILDREN, data);
        }

        private void sendRequest(int what, Bundle data) throws RemoteException {
            Message msg = Message.obtain();
            msg.what = what;
            msg.arg1 = SERVICE_VERSION_CURRENT;
            msg.setData(data);
            mCallbacks.send(msg);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 24 || BuildCompat.isAtLeastN()) {
            mImpl = new MediaBrowserServiceImplApi24();
        } else if (Build.VERSION.SDK_INT >= 23) {
            mImpl = new MediaBrowserServiceImplApi23();
        } else if (Build.VERSION.SDK_INT >= 21) {
            mImpl = new MediaBrowserServiceImplApi21();
        } else {
            mImpl = new MediaBrowserServiceImplBase();
        }
        mImpl.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mImpl.onBind(intent);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
    }

    /**
     * Called to get the root information for browsing by a particular client.
     * <p>
     * The implementation should verify that the client package has permission
     * to access browse media information before returning the root id; it
     * should return null if the client is not allowed to access this
     * information.
     * </p>
     *
     * @param clientPackageName The package name of the application which is
     *            requesting access to browse media.
     * @param clientUid The uid of the application which is requesting access to
     *            browse media.
     * @param rootHints An optional bundle of service-specific arguments to send
     *            to the media browse service when connecting and retrieving the
     *            root id for browsing, or null if none. The contents of this
     *            bundle may affect the information returned when browsing.
     * @return The {@link BrowserRoot} for accessing this app's content or null.
     * @see BrowserRoot#EXTRA_RECENT
     * @see BrowserRoot#EXTRA_OFFLINE
     * @see BrowserRoot#EXTRA_SUGGESTED
     * @see BrowserRoot#EXTRA_SUGGESTION_KEYWORDS
     */
    public abstract @Nullable BrowserRoot onGetRoot(@NonNull String clientPackageName,
            int clientUid, @Nullable Bundle rootHints);

    /**
     * Called to get information about the children of a media item.
     * <p>
     * Implementations must call {@link Result#sendResult result.sendResult}
     * with the list of children. If loading the children will be an expensive
     * operation that should be performed on another thread,
     * {@link Result#detach result.detach} may be called before returning from
     * this function, and then {@link Result#sendResult result.sendResult}
     * called when the loading is complete.
     *
     * @param parentId The id of the parent media item whose children are to be
     *            queried.
     * @param result The Result to send the list of children to, or null if the
     *            id is invalid.
     */
    public abstract void onLoadChildren(@NonNull String parentId,
            @NonNull Result<List<MediaBrowserCompat.MediaItem>> result);

    /**
     * Called to get information about the children of a media item.
     * <p>
     * Implementations must call {@link Result#sendResult result.sendResult}
     * with the list of children. If loading the children will be an expensive
     * operation that should be performed on another thread,
     * {@link Result#detach result.detach} may be called before returning from
     * this function, and then {@link Result#sendResult result.sendResult}
     * called when the loading is complete.
     *
     * @param parentId The id of the parent media item whose children are to be
     *            queried.
     * @param result The Result to send the list of children to, or null if the
     *            id is invalid.
     * @param options A bundle of service-specific arguments sent from the media
     *            browse. The information returned through the result should be
     *            affected by the contents of this bundle.
     */
    public void onLoadChildren(@NonNull String parentId,
            @NonNull Result<List<MediaBrowserCompat.MediaItem>> result, @NonNull Bundle options) {
        // To support backward compatibility, when the implementation of MediaBrowserService doesn't
        // override onLoadChildren() with options, onLoadChildren() without options will be used
        // instead, and the options will be applied in the implementation of result.onResultSent().
        result.setFlags(RESULT_FLAG_OPTION_NOT_HANDLED);
        onLoadChildren(parentId, result);
    }

    /**
     * Called to get information about a specific media item.
     * <p>
     * Implementations must call {@link Result#sendResult result.sendResult}. If
     * loading the item will be an expensive operation {@link Result#detach
     * result.detach} may be called before returning from this function, and
     * then {@link Result#sendResult result.sendResult} called when the item has
     * been loaded.
     * <p>
     * The default implementation sends a null result.
     *
     * @param itemId The id for the specific {@link MediaBrowserCompat.MediaItem}.
     * @param result The Result to send the item to, or null if the id is
     *            invalid.
     */
    public void onLoadItem(String itemId, Result<MediaBrowserCompat.MediaItem> result) {
        result.sendResult(null);
    }

    /**
     * Call to set the media session.
     * <p>
     * This should be called as soon as possible during the service's startup.
     * It may only be called once.
     *
     * @param token The token for the service's {@link MediaSessionCompat}.
     */
    public void setSessionToken(MediaSessionCompat.Token token) {
        if (token == null) {
            throw new IllegalArgumentException("Session token may not be null.");
        }
        if (mSession != null) {
            throw new IllegalStateException("The session token has already been set.");
        }
        mSession = token;
        mImpl.setSessionToken(token);
    }

    /**
     * Gets the session token, or null if it has not yet been created
     * or if it has been destroyed.
     */
    public @Nullable MediaSessionCompat.Token getSessionToken() {
        return mSession;
    }

    /**
     * Gets the root hints sent from the currently connected {@link MediaBrowserCompat}.
     * The root hints are service-specific arguments included in an optional bundle sent to the
     * media browser service when connecting and retrieving the root id for browsing, or null if
     * none. The contents of this bundle may affect the information returned when browsing.
     * <p>
     * Note that this will return null when connected to {@link android.media.browse.MediaBrowser}
     * and running on API 23 or lower.
     *
     * @throws IllegalStateException If this method is called outside of {@link #onLoadChildren}
     *             or {@link #onLoadItem}
     * @see MediaBrowserServiceCompat.BrowserRoot#EXTRA_RECENT
     * @see MediaBrowserServiceCompat.BrowserRoot#EXTRA_OFFLINE
     * @see MediaBrowserServiceCompat.BrowserRoot#EXTRA_SUGGESTED
     * @see MediaBrowserServiceCompat.BrowserRoot#EXTRA_SUGGESTION_KEYWORDS
     */
    public final Bundle getBrowserRootHints() {
        return mImpl.getBrowserRootHints();
    }

    /**
     * Notifies all connected media browsers that the children of
     * the specified parent id have changed in some way.
     * This will cause browsers to fetch subscribed content again.
     *
     * @param parentId The id of the parent media item whose
     * children changed.
     */
    public void notifyChildrenChanged(@NonNull String parentId) {
        if (parentId == null) {
            throw new IllegalArgumentException("parentId cannot be null in notifyChildrenChanged");
        }
        mImpl.notifyChildrenChanged(parentId, null);
    }

    /**
     * Notifies all connected media browsers that the children of
     * the specified parent id have changed in some way.
     * This will cause browsers to fetch subscribed content again.
     *
     * @param parentId The id of the parent media item whose
     *            children changed.
     * @param options A bundle of service-specific arguments to send
     *            to the media browse. The contents of this bundle may
     *            contain the information about the change.
     */
    public void notifyChildrenChanged(@NonNull String parentId, @NonNull Bundle options) {
        if (parentId == null) {
            throw new IllegalArgumentException("parentId cannot be null in notifyChildrenChanged");
        }
        if (options == null) {
            throw new IllegalArgumentException("options cannot be null in notifyChildrenChanged");
        }
        mImpl.notifyChildrenChanged(parentId, options);
    }

    /**
     * Return whether the given package is one of the ones that is owned by the uid.
     */
    private boolean isValidPackage(String pkg, int uid) {
        if (pkg == null) {
            return false;
        }
        final PackageManager pm = getPackageManager();
        final String[] packages = pm.getPackagesForUid(uid);
        final int N = packages.length;
        for (int i=0; i<N; i++) {
            if (packages[i].equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Save the subscription and if it is a new subscription send the results.
     */
    private void addSubscription(String id, ConnectionRecord connection, IBinder token,
            Bundle options) {
        // Save the subscription
        List<Pair<IBinder, Bundle>> callbackList = connection.subscriptions.get(id);
        if (callbackList == null) {
            callbackList = new ArrayList<>();
        }
        for (Pair<IBinder, Bundle> callback : callbackList) {
            if (token == callback.first
                    && MediaBrowserCompatUtils.areSameOptions(options, callback.second)) {
                return;
            }
        }
        callbackList.add(new Pair<>(token, options));
        connection.subscriptions.put(id, callbackList);
        // send the results
        performLoadChildren(id, connection, options);
    }

    /**
     * Remove the subscription.
     */
    private boolean removeSubscription(String id, ConnectionRecord connection, IBinder token) {
        if (token == null) {
            return connection.subscriptions.remove(id) != null;
        }
        boolean removed = false;
        List<Pair<IBinder, Bundle>> callbackList = connection.subscriptions.get(id);
        if (callbackList != null) {
            Iterator<Pair<IBinder, Bundle>> iter = callbackList.iterator();
            while (iter.hasNext()){
                if (token == iter.next().first) {
                    removed = true;
                    iter.remove();
                }
            }
            if (callbackList.size() == 0) {
                connection.subscriptions.remove(id);
            }
        }
        return removed;
    }

    /**
     * Call onLoadChildren and then send the results back to the connection.
     * <p>
     * Callers must make sure that this connection is still connected.
     */
    private void performLoadChildren(final String parentId, final ConnectionRecord connection,
            final Bundle options) {
        final Result<List<MediaBrowserCompat.MediaItem>> result
                = new Result<List<MediaBrowserCompat.MediaItem>>(parentId) {
            @Override
            void onResultSent(List<MediaBrowserCompat.MediaItem> list, @ResultFlags int flags) {
                if (mConnections.get(connection.callbacks.asBinder()) != connection) {
                    if (DEBUG) {
                        Log.d(TAG, "Not sending onLoadChildren result for connection that has"
                                + " been disconnected. pkg=" + connection.pkg + " id=" + parentId);
                    }
                    return;
                }

                List<MediaBrowserCompat.MediaItem> filteredList =
                        (flags & RESULT_FLAG_OPTION_NOT_HANDLED) != 0
                                ? applyOptions(list, options) : list;
                try {
                    connection.callbacks.onLoadChildren(parentId, filteredList, options);
                } catch (RemoteException ex) {
                    // The other side is in the process of crashing.
                    Log.w(TAG, "Calling onLoadChildren() failed for id=" + parentId
                            + " package=" + connection.pkg);
                }
            }
        };

        mCurConnection = connection;
        if (options == null) {
            onLoadChildren(parentId, result);
        } else {
            onLoadChildren(parentId, result, options);
        }
        mCurConnection = null;

        if (!result.isDone()) {
            throw new IllegalStateException("onLoadChildren must call detach() or sendResult()"
                    + " before returning for package=" + connection.pkg + " id=" + parentId);
        }
    }

    private List<MediaBrowserCompat.MediaItem> applyOptions(List<MediaBrowserCompat.MediaItem> list,
            final Bundle options) {
        if (list == null) {
            return null;
        }
        int page = options.getInt(MediaBrowserCompat.EXTRA_PAGE, -1);
        int pageSize = options.getInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, -1);
        if (page == -1 && pageSize == -1) {
            return list;
        }
        int fromIndex = pageSize * page;
        int toIndex = fromIndex + pageSize;
        if (page < 0 || pageSize < 1 || fromIndex >= list.size()) {
            return Collections.EMPTY_LIST;
        }
        if (toIndex > list.size()) {
            toIndex = list.size();
        }
        return list.subList(fromIndex, toIndex);
    }

    private void performLoadItem(String itemId, ConnectionRecord connection,
            final ResultReceiver receiver) {
        final Result<MediaBrowserCompat.MediaItem> result =
                new Result<MediaBrowserCompat.MediaItem>(itemId) {
                    @Override
                    void onResultSent(MediaBrowserCompat.MediaItem item, @ResultFlags int flags) {
                        Bundle bundle = new Bundle();
                        bundle.putParcelable(KEY_MEDIA_ITEM, item);
                        receiver.send(0, bundle);
                    }
                };

        mCurConnection = connection;
        onLoadItem(itemId, result);
        mCurConnection = null;

        if (!result.isDone()) {
            throw new IllegalStateException("onLoadItem must call detach() or sendResult()"
                    + " before returning for id=" + itemId);
        }
    }

    /**
     * Contains information that the browser service needs to send to the client
     * when first connected.
     */
    public static final class BrowserRoot {
        /**
         * The lookup key for a boolean that indicates whether the browser service should return a
         * browser root for recently played media items.
         *
         * <p>When creating a media browser for a given media browser service, this key can be
         * supplied as a root hint for retrieving media items that are recently played.
         * If the media browser service can provide such media items, the implementation must return
         * the key in the root hint when {@link #onGetRoot(String, int, Bundle)} is called back.
         *
         * <p>The root hint may contain multiple keys.
         *
         * @see #EXTRA_OFFLINE
         * @see #EXTRA_SUGGESTED
         * @see #EXTRA_SUGGESTION_KEYWORDS
         */
        public static final String EXTRA_RECENT = "android.service.media.extra.RECENT";

        /**
         * The lookup key for a boolean that indicates whether the browser service should return a
         * browser root for offline media items.
         *
         * <p>When creating a media browser for a given media browser service, this key can be
         * supplied as a root hint for retrieving media items that are can be played without an
         * internet connection.
         * If the media browser service can provide such media items, the implementation must return
         * the key in the root hint when {@link #onGetRoot(String, int, Bundle)} is called back.
         *
         * <p>The root hint may contain multiple keys.
         *
         * @see #EXTRA_RECENT
         * @see #EXTRA_SUGGESTED
         * @see #EXTRA_SUGGESTION_KEYWORDS
         */
        public static final String EXTRA_OFFLINE = "android.service.media.extra.OFFLINE";

        /**
         * The lookup key for a boolean that indicates whether the browser service should return a
         * browser root for suggested media items.
         *
         * <p>When creating a media browser for a given media browser service, this key can be
         * supplied as a root hint for retrieving the media items suggested by the media browser
         * service. The list of media items passed in {@link android.support.v4.media.MediaBrowserCompat.SubscriptionCallback#onChildrenLoaded(String, List)}
         * is considered ordered by relevance, first being the top suggestion.
         * If the media browser service can provide such media items, the implementation must return
         * the key in the root hint when {@link #onGetRoot(String, int, Bundle)} is called back.
         *
         * <p>The root hint may contain multiple keys.
         *
         * @see #EXTRA_RECENT
         * @see #EXTRA_OFFLINE
         * @see #EXTRA_SUGGESTION_KEYWORDS
         */
        public static final String EXTRA_SUGGESTED = "android.service.media.extra.SUGGESTED";

        /**
         * The lookup key for a string that indicates specific keywords which will be considered
         * when the browser service suggests media items.
         *
         * <p>When creating a media browser for a given media browser service, this key can be
         * supplied as a root hint together with {@link #EXTRA_SUGGESTED} for retrieving suggested
         * media items related with the keywords. The list of media items passed in
         * {@link android.media.browse.MediaBrowser.SubscriptionCallback#onChildrenLoaded(String, List)}
         * is considered ordered by relevance, first being the top suggestion.
         * If the media browser service can provide such media items, the implementation must return
         * the key in the root hint when {@link #onGetRoot(String, int, Bundle)} is called back.
         *
         * <p>The root hint may contain multiple keys.
         *
         * @see #EXTRA_RECENT
         * @see #EXTRA_OFFLINE
         * @see #EXTRA_SUGGESTED
         */
        public static final String EXTRA_SUGGESTION_KEYWORDS
                = "android.service.media.extra.SUGGESTION_KEYWORDS";

        final private String mRootId;
        final private Bundle mExtras;

        /**
         * Constructs a browser root.
         * @param rootId The root id for browsing.
         * @param extras Any extras about the browser service.
         */
        public BrowserRoot(@NonNull String rootId, @Nullable Bundle extras) {
            if (rootId == null) {
                throw new IllegalArgumentException("The root id in BrowserRoot cannot be null. " +
                        "Use null for BrowserRoot instead.");
            }
            mRootId = rootId;
            mExtras = extras;
        }

        /**
         * Gets the root id for browsing.
         */
        public String getRootId() {
            return mRootId;
        }

        /**
         * Gets any extras about the browser service.
         */
        public Bundle getExtras() {
            return mExtras;
        }
    }
}
