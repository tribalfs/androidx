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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media2.MediaSession2.ControllerInfo;

/**
 * Base class for media session services, which is the service containing {@link MediaSession2}.
 * <p>
 * It's highly recommended for an app to use this if it wants to keep media playback in the
 * background.
 * <p>
 * Here are the benefits of using {@link MediaSessionService2}.
 * <ul>
 * <li>Another app can know that your app supports {@link MediaSession2} even when your app
 * isn't running.
 * <li>Another app can start playback of your app even when your app isn't running.
 * </ul>
 * For example, user's voice command can start playback of your app even when it's not running.
 * <p>
 * To extend this class, adding followings directly to your {@code AndroidManifest.xml}.
 * <pre>
 * &lt;service android:name="component_name_of_your_implementation" &gt;
 *   &lt;intent-filter&gt;
 *     &lt;action android:name="android.media.MediaSessionService2" /&gt;
 *   &lt;/intent-filter&gt;
 * &lt;/service&gt;</pre>
 * <p>
 * It's recommended for an app to have a single {@link MediaSessionService2} declared in the
 * manifest. Otherwise, your app might be shown twice in the list of the Auto/Wearable, or another
 * app fails to pick the right session service when it wants to start the playback this app.
 * <p>
 * Topic covered here:
 * <ol>
 * <li><a href="#ServiceLifecycle">Service Lifecycle</a>
 * <li><a href="#Permissions">Permissions</a>
 * </ol>
 * <div class="special reference">
 * <a name="ServiceLifecycle"></a>
 * <h3>Service Lifecycle</h3>
 * <p>
 * Session service is bound service. When a {@link MediaController2} is created for the
 * session service, the controller binds to the session service. {@link #onCreateSession()}
 * may be called after the {@link #onCreate} if the service hasn't created yet.
 * <p>
 * After the binding, session's
 * {@link MediaSession2.SessionCallback#onConnect(MediaSession2, ControllerInfo)}
 * will be called to accept or reject connection request from a controller. If the connection is
 * rejected, the controller will unbind. If it's accepted, the controller will be available to use
 * and keep binding.
 * <p>
 * When playback is started for this session service, {@link #onUpdateNotification()}
 * is called and service would become a foreground service. It's needed to keep playback after the
 * controller is destroyed. The session service becomes background service when the playback is
 * stopped. Apps targeting API {@link android.os.Build.VERSION_CODES#P} or later must request
 * the permission {@link android.Manifest.permission#FOREGROUND_SERVICE} in order to make the
 * service as foreground.
 * <p>
 * The service is destroyed when the session is closed, or no media controller is binding to the
 * session while the service is not running as a foreground service.
 * <a name="Permissions"></a>
 * <h3>Permissions</h3>
 * <p>
 * Any app can bind to the session service with controller, but the controller can be used only if
 * the session service accepted the connection request through
 * {@link MediaSession2.SessionCallback#onConnect(MediaSession2, ControllerInfo)}.
 */
public abstract class MediaSessionService2 extends Service {
    /**
     * The {@link Intent} that must be declared as handled by the service.
     */
    public static final String SERVICE_INTERFACE = "android.media.MediaSessionService2";

    private final MediaSessionService2Impl mImpl;

    public MediaSessionService2() {
        super();
        // Note: This service doesn't have valid context at this moment.
        mImpl = createImpl();
    }

    MediaSessionService2Impl createImpl() {
        return new MediaSessionService2ImplBase();
    }

    /**
     * Default implementation for {@link MediaSessionService2} to initialize session service.
     * <p>
     * Override this method if you need your own initialization. Derived classes MUST call through
     * to the super class's implementation of this method.
     */
    @CallSuper
    @Override
    public void onCreate() {
        super.onCreate();
        mImpl.onCreate(this);
    }

    /**
     * Called when another app requested to start this service to get {@link MediaSession2}.
     * <p>
     * Session service will accept or reject the connection with the
     * {@link MediaSession2.SessionCallback} in the created session.
     * <p>
     * This method will be called on the main thread.
     *
     * @return a new session
     * @see MediaSession2.Builder
     * @see #getSession()
     */
    public @NonNull abstract MediaSession2 onCreateSession();

    /**
     * Called when notification UI needs update. Override this method to show or cancel your own
     * notification UI.
     * <p>
     * This would be called when player state is changed.
     * <p>
     * With the notification returned here, the service becomes foreground service when the playback
     * is started. Apps targeting API {@link android.os.Build.VERSION_CODES#P} or later must request
     * the permission {@link android.Manifest.permission#FOREGROUND_SERVICE} in order to use
     * this API. It becomes background service after the playback is stopped.
     *
     * @return a {@link MediaNotification}. If it's {@code null}, notification wouldn't be shown.
     */
    public @Nullable MediaNotification onUpdateNotification() {
        return mImpl.onUpdateNotification();
    }

    /**
     * Get instance of the {@link MediaSession2} that you've previously created with the
     * {@link #onCreateSession} for this service.
     * <p>
     * This may be {@code null} before the {@link #onCreate()} is finished.
     *
     * @return created session
     */
    public final @Nullable MediaSession2 getSession() {
        return mImpl.getSession();
    }

    /**
     * Default implementation for {@link MediaSessionService2} to handle incoming binding
     * request. If the request is for getting the session, the intent will have action
     * {@link #SERVICE_INTERFACE}.
     * <p>
     * Override this method if this service also needs to handle binder requests other than
     * {@link #SERVICE_INTERFACE}. Derived classes MUST call through to the super class's
     * implementation of this method.
     *
     * @param intent
     * @return Binder
     */
    @CallSuper
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mImpl.onBind(intent);
    }

    /**
     * Returned by {@link #onUpdateNotification()} for making session service foreground service
     * to keep playback running in the background. It's highly recommended to show media style
     * notification here.
     */
    public static class MediaNotification {
        private final int mNotificationId;
        private final Notification mNotification;

        /**
         * Default constructor
         *
         * @param notificationId notification id to be used for
         *      {@link NotificationManager#notify(int, Notification)}.
         * @param notification a notification to make session service foreground service. Media
         *      style notification is recommended here.
         */
        public MediaNotification(int notificationId, @NonNull Notification notification) {
            if (notification == null) {
                throw new IllegalArgumentException("notification shouldn't be null");
            }
            mNotificationId = notificationId;
            mNotification = notification;
        }

        /**
         * Gets the id of the id.
         *
         * @return the notification id
         */
        public int getNotificationId() {
            return mNotificationId;
        }

        /**
         * Gets the notification.
         *
         * @return the notification
         */
        public @NonNull Notification getNotification() {
            return mNotification;
        }
    }

    interface MediaSessionService2Impl {
        void onCreate(MediaSessionService2 service);
        IBinder onBind(Intent intent);
        MediaNotification onUpdateNotification();
        MediaSession2 getSession();
    }
}
