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

package androidx.media;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.os.Build;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.os.BuildCompat;

/**
 * @hide
 * Provides support for interacting with {@link MediaSessionCompat media sessions} that
 * applications have published to express their ongoing media playback state.
 *
 * @see MediaSessionCompat
 * @see MediaControllerCompat
 */
@RestrictTo(LIBRARY_GROUP)
public final class MediaSessionManager {
    static final String TAG = "MediaSessionManager";
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static MediaSessionManager sSessionManager;

    MediaSessionManagerImpl mImpl;

    /**
     * Gets an instance of the media session manager associated with the context.
     *
     * @return The MediaSessionManager instance for this context.
     */
    public static synchronized MediaSessionManager getSessionManager(Context context) {
        if (sSessionManager == null) {
            sSessionManager = new MediaSessionManager(context.getApplicationContext());
        }
        return sSessionManager;
    }

    private MediaSessionManager(Context context) {
        if (BuildCompat.isAtLeastP()) {
            mImpl = new MediaSessionManagerImplApi28(context);
        } else if (Build.VERSION.SDK_INT >= 21) {
            mImpl = new MediaSessionManagerImplApi21(context);
        } else {
            mImpl = new MediaSessionManagerImplBase(context);
        }
    }

    /**
     * Checks whether the remote user is a trusted app.
     * <p>
     * An app is trusted if the app holds the android.Manifest.permission.MEDIA_CONTENT_CONTROL
     * permission or has an enabled notification listener.
     *
     * @param userInfo The remote user info from either
     *            {@link MediaSessionCompat} and {@link MediaBrowserServiceCompat}.
     * @return {@code true} if the remote user is trusted and its package name matches with the UID.
     *            {@code false} otherwise.
     */
    public boolean isTrustedForMediaControl(@NonNull RemoteUserInfo userInfo) {
        if (userInfo == null) {
            throw new IllegalArgumentException("userInfo should not be null");
        }
        return mImpl.isTrustedForMediaControl(userInfo.mImpl);
    }

    Context getContext() {
        return mImpl.getContext();
    }

    interface MediaSessionManagerImpl {
        Context getContext();
        boolean isTrustedForMediaControl(RemoteUserInfoImpl userInfo);
    }

    interface RemoteUserInfoImpl {
        String getPackageName();
        int getPid();
        int getUid();
    }

    /**
     * Information of a remote user of {@link android.support.v4.media.session.MediaSessionCompat}
     * or {@link MediaBrowserServiceCompat}.
     * This can be used to decide whether the remote user is trusted app.
     *
     * @see #isTrustedForMediaControl(RemoteUserInfo)
     */
    public static final class RemoteUserInfo {
        RemoteUserInfoImpl mImpl;

        public RemoteUserInfo(String packageName, int pid, int uid) {
            if (BuildCompat.isAtLeastP()) {
                mImpl = new MediaSessionManagerImplApi28.RemoteUserInfo(packageName, pid, uid);
            } else {
                mImpl = new MediaSessionManagerImplBase.RemoteUserInfo(packageName, pid, uid);
            }
        }

        /**
         * @return package name of the controller
         */
        public String getPackageName() {
            return mImpl.getPackageName();
        }

        /**
         * @return pid of the controller
         */
        public int getPid() {
            return mImpl.getPid();
        }

        /**
         * @return uid of the controller
         */
        public int getUid() {
            return mImpl.getUid();
        }

        @Override
        public boolean equals(Object obj) {
            return mImpl.equals(obj);
        }

        @Override
        public int hashCode() {
            return mImpl.hashCode();
        }
    }
}
