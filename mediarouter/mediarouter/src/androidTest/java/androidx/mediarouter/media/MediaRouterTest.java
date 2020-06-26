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

package androidx.mediarouter.media;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link MediaRouter}.
 */
@RunWith(AndroidJUnit4.class)
public class MediaRouterTest {
   // The maximum time to wait for an operation.
   private static final long TIME_OUT_MS = 3000L;
   private static final String SESSION_TAG = "test-session";
   private final Object mWaitLock = new Object();

   private Context mContext;
   private MediaRouter mRouter;
   private MediaSessionCompat mSession;
   private MediaSessionCallback mSessionCallback = new MediaSessionCallback();

   @Before
   public void setUp() throws Exception {
       getInstrumentation().runOnMainSync(new Runnable() {
           @Override
           public void run() {
               mContext = getApplicationContext();
               mRouter = MediaRouter.getInstance(mContext);
               mSession = new MediaSessionCompat(mContext, SESSION_TAG);
           }
       });
   }

   @After
   public void tearDown() throws Exception {
       mSession.release();
   }

   /**
    * This test checks whether the session callback work properly after setMediaSessionCompat() is
    * called.
    */
   @Test
   @SmallTest
   public void setMediaSessionCompat_receivesCallbacks() throws Exception {
       getInstrumentation().runOnMainSync(new Runnable() {
           @Override
           public void run() {
               mSession.setCallback(mSessionCallback);
               mRouter.setMediaSessionCompat(mSession);
           }
       });

       MediaControllerCompat controller = mSession.getController();
       MediaControllerCompat.TransportControls controls = controller.getTransportControls();
       synchronized (mWaitLock) {
           mSessionCallback.reset();
           controls.play();
           mWaitLock.wait(TIME_OUT_MS);
           assertTrue(mSessionCallback.mOnPlayCalled);

           mSessionCallback.reset();
           controls.pause();
           mWaitLock.wait(TIME_OUT_MS);
           assertTrue(mSessionCallback.mOnPauseCalled);
       }
   }

    @Test
    @SmallTest
    public void getRouteParams_afterSetRouterParams_returnsSetParams() {
        MediaRouterParams expectedParams = new MediaRouterParams();
        expectedParams.setDialogType(MediaRouterParams.DIALOG_TYPE_DYNAMIC_GROUP);
        Bundle paramExtras = new Bundle();
        paramExtras.putString("test_key", "test_value");
        expectedParams.setExtras(paramExtras);
        paramExtras.remove("test_key");
        mRouter.setRouterParams(expectedParams);

        MediaRouterParams actualParams = mRouter.getRouterParams();
        assertNotNull(actualParams);
        assertEquals(expectedParams.getDialogType(), actualParams.getDialogType());

        Bundle actualExtras = actualParams.getExtras();
        assertNotNull(actualExtras);
        assertEquals("test_value", actualExtras.getString("test_key"));
    }

   private class MediaSessionCallback extends MediaSessionCompat.Callback {
       private boolean mOnPlayCalled;
       private boolean mOnPauseCalled;

       public void reset() {
           mOnPlayCalled = false;
           mOnPauseCalled = false;
       }

       @Override
       public void onPlay() {
           synchronized (mWaitLock) {
               mOnPlayCalled = true;
               mWaitLock.notify();
           }
       }

       @Override
       public void onPause() {
           synchronized (mWaitLock) {
               mOnPauseCalled = true;
               mWaitLock.notify();
           }
       }
   }
}