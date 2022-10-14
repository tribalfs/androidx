/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.car.app;

import static androidx.lifecycle.Lifecycle.State.DESTROYED;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.internal.DoNotInstrument;
import org.robolectric.shadows.ShadowActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Tests for {@link CarAppPermissionActivity}. */
@RunWith(RobolectricTestRunner.class)
@DoNotInstrument
public class CarAppPermissionActivityTest {
    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock
    private OnRequestPermissionsListener mMockListener;

    private final List<String> mPermissionsRequested = new ArrayList<>();

    private ActivityScenario<CarAppPermissionActivity> mActivity;
    private Application mApplication;

    @Before
    public void setUp() throws RemoteException {
        mApplication = ApplicationProvider.getApplicationContext();

        mPermissionsRequested.add("foo");
        mPermissionsRequested.add("bar");
    }

    @Test
    @Config(sdk = 23) // Note that while the app module minSDK is 21, the minSDK for hosts is 23
    public void onCreate_requestPermissionAction_requestsPermissions() {
        setupActivity(CarContext.REQUEST_PERMISSIONS_ACTION);

        mActivity.onActivity(activity -> {
            ShadowActivity shadowActivity = shadowOf(activity);
            ShadowActivity.PermissionsRequest request = shadowActivity.getLastRequestedPermission();
            assertThat(request.requestedPermissions).isEqualTo(
                    mPermissionsRequested.toArray(new String[0]));
        });
    }

    @Test
    public void onCreate_notARequestPermissionAction_finishes() {
        setupActivity("foo");

        assertThat(mActivity.getState()).isEqualTo(DESTROYED);
    }

    private Intent createLaunchIntent(String action) {
        Bundle extras = new Bundle(2);
        extras.putStringArray(CarContext.EXTRA_PERMISSIONS_KEY,
                mPermissionsRequested.toArray(new String[0]));
        extras.putBinder(CarContext.EXTRA_ON_REQUEST_PERMISSIONS_RESULT_LISTENER_KEY,
                new IOnRequestPermissionsListener.Stub() {
                    @SuppressWarnings("unckecked")
                    @Override
                    public void onRequestPermissionsResult(String[] approvedPermissions,
                            String[] rejectedPermissions) {
                        mMockListener.onRequestPermissionsResult(
                                Arrays.asList(approvedPermissions),
                                Arrays.asList(rejectedPermissions));
                    }
                }.asBinder());

        return new Intent(action).setComponent(
                new ComponentName(mApplication, CarAppPermissionActivity.class)).putExtras(extras);
    }

    private void setupActivity(String action) {
        mActivity = ActivityScenario.launch(createLaunchIntent(action));
    }
}
