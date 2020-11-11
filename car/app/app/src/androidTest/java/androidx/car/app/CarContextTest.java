/*
 * Copyright 2020 The Android Open Source Project
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

import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.DisplayMetrics;

import androidx.annotation.Nullable;
import androidx.car.app.navigation.NavigationManager;
import androidx.car.app.test.R;
import androidx.car.app.testing.TestLifecycleOwner;
import androidx.lifecycle.Lifecycle.Event;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Locale;

/** Tests for {@link CarContext}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class CarContextTest {
    private static final String APP_SERVICE = "app_manager";
    private static final String NAVIGATION_SERVICE = "navigation_manager";
    private static final String SCREEN_MANAGER_SERVICE = "screen_manager";

    @Mock
    private ICarHost mMockCarHost;
    @Mock
    private IStartCarApp mMockStartCarApp;
    @Mock
    private Screen mMockScreen1;
    @Mock
    private Screen mMockScreen2;

    private CarContext mCarContext;
    private Screen mScreen1;
    private Screen mScreen2;

    private Intent mIntentFromNotification;
    private final TestLifecycleOwner mLifecycleOwner =
            new TestLifecycleOwner();

    @Before
    @UiThreadTest
    public void setUp() throws RemoteException {
        MockitoAnnotations.initMocks(this);
        when(mMockCarHost.getHost(CarContext.APP_SERVICE))
                .thenReturn(
                        new IAppHost.Stub() {
                            @Override
                            public void invalidate() {
                            }

                            @Override
                            public void showToast(CharSequence text, int duration) {
                            }

                            @Override
                            public void setSurfaceListener(@Nullable ISurfaceListener listener) {
                            }
                        }.asBinder());

        TestStartCarAppStub startCarAppStub = new TestStartCarAppStub(mMockStartCarApp);

        Bundle extras = new Bundle(1);
        extras.putBinder(CarContext.START_CAR_APP_BINDER_KEY, startCarAppStub.asBinder());
        mIntentFromNotification = new Intent().putExtras(extras);

        mCarContext = CarContext.create(mLifecycleOwner.mRegistry);
        mCarContext.attachBaseContext(
                ApplicationProvider.getApplicationContext(),
                ApplicationProvider.getApplicationContext().getResources().getConfiguration());
        mCarContext.setCarHost(mMockCarHost);

        mScreen1 = new TestScreen(mCarContext, mMockScreen1);
        mScreen2 = new TestScreen(mCarContext, mMockScreen2);
    }

    @Test
    public void getCarService_app() {
        assertThat(mCarContext.getCarService(CarContext.APP_SERVICE))
                .isEqualTo(mCarContext.getCarService(AppManager.class));
        assertThat(mCarContext.getCarService(CarContext.APP_SERVICE)).isNotNull();
    }

    @Test
    public void getCarService_navigation() {
        assertThat(mCarContext.getCarService(CarContext.NAVIGATION_SERVICE))
                .isEqualTo(mCarContext.getCarService(NavigationManager.class));
        assertThat(mCarContext.getCarService(CarContext.NAVIGATION_SERVICE)).isNotNull();
    }

    @Test
    public void getCarService_screenManager() {
        assertThat(mCarContext.getCarService(CarContext.SCREEN_MANAGER_SERVICE))
                .isEqualTo(mCarContext.getCarService(ScreenManager.class));
        assertThat(mCarContext.getCarService(CarContext.SCREEN_MANAGER_SERVICE)).isNotNull();
    }

    @Test
    public void getCarService_unknown_throws() {
        assertThrows(IllegalArgumentException.class, () -> mCarContext.getCarService("foo"));
    }

    @Test
    public void getCarService_null_throws() {
        assertThrows(NullPointerException.class, () -> mCarContext.getCarService((String) null));
        assertThrows(NullPointerException.class,
                () -> mCarContext.getCarService((Class<Object>) null));
    }

    @Test
    public void getCarServiceName_app() {
        assertThat(mCarContext.getCarServiceName(AppManager.class)).isEqualTo(APP_SERVICE);
    }

    @Test
    public void getCarServiceName_navigation() {
        assertThat(mCarContext.getCarServiceName(NavigationManager.class)).isEqualTo(
                NAVIGATION_SERVICE);
    }

    @Test
    public void getCarServiceName_screenManager() {
        assertThat(mCarContext.getCarServiceName(ScreenManager.class)).isEqualTo(
                SCREEN_MANAGER_SERVICE);
    }

    @Test
    public void getCarServiceName_unexpectedClass_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mCarContext.getCarServiceName(CarAppService.class));
    }

    @Test
    public void getCarServiceName_null_throws() {
        assertThrows(NullPointerException.class, () -> mCarContext.getCarServiceName(null));
    }

    @Test
    public void startCarApp_callsICarHostStartCarApp() throws RemoteException {
        Intent foo = new Intent("foo");
        mCarContext.startCarApp(foo);

        verify(mMockCarHost).startCarApp(foo);
    }

    @Test
    public void startCarApp_nullIntent_throws() {
        assertThrows(NullPointerException.class, () -> CarContext.startCarApp(null, null));
        assertThrows(
                NullPointerException.class,
                () -> CarContext.startCarApp(mIntentFromNotification, null));
        assertThrows(NullPointerException.class, () -> CarContext.startCarApp(null, new Intent()));
    }

    @Test
    public void startCarApp_callsTheBinder() throws RemoteException {
        Intent startCarAppIntent = new Intent("foo");

        CarContext.startCarApp(mIntentFromNotification, startCarAppIntent);

        verify(mMockStartCarApp).startCarApp(startCarAppIntent);
    }

    @Test
    public void finishCarApp() throws RemoteException {
        mCarContext.finishCarApp();

        verify(mMockCarHost).finish();
    }

    @Test
    @UiThreadTest
    public void onConfigurationChanged_updatesTheConfiguration() {
        Configuration configuration = new Configuration();
        configuration.setToDefaults();
        configuration.setLocale(Locale.CANADA_FRENCH);

        mCarContext.onCarConfigurationChanged(configuration);

        assertThat(mCarContext.getResources().getConfiguration().getLocales().get(0))
                .isEqualTo(Locale.CANADA_FRENCH);
    }

    @Test
    @UiThreadTest
    public void onConfigurationChanged_loadsCorrectNewResource() {
        Configuration ldpiConfig = new Configuration(mCarContext.getResources().getConfiguration());
        ldpiConfig.densityDpi = 120;

        mCarContext.onCarConfigurationChanged(ldpiConfig);

        Drawable ldpiDrawable = mCarContext.getDrawable(R.drawable.banana);
        assertThat(ldpiDrawable.getIntrinsicHeight()).isEqualTo(48);

        Configuration mdpiConfig = new Configuration(mCarContext.getResources().getConfiguration());
        mdpiConfig.densityDpi = 160;

        mCarContext.onCarConfigurationChanged(mdpiConfig);

        Drawable mdpiDrawable = mCarContext.getDrawable(R.drawable.banana);
        assertThat(mdpiDrawable.getIntrinsicHeight()).isEqualTo(64);

        Configuration hdpiConfig = new Configuration(mCarContext.getResources().getConfiguration());
        hdpiConfig.densityDpi = 240;

        mCarContext.onCarConfigurationChanged(hdpiConfig);

        Drawable hdpiDrawable = mCarContext.getDrawable(R.drawable.banana);
        assertThat(hdpiDrawable.getIntrinsicHeight()).isEqualTo(96);
    }

    @Test
    @UiThreadTest
    // TODO(rampara): Investigate removing usage of deprecated updateConfiguration API
    @SuppressWarnings("deprecation")
    public void changingApplicationContextConfiguration_doesNotChangeTheCarContextConfiguration() {
        Configuration ldpiConfig = new Configuration(mCarContext.getResources().getConfiguration());
        ldpiConfig.densityDpi = 120;

        mCarContext.onCarConfigurationChanged(ldpiConfig);

        Drawable ldpiDrawable = mCarContext.getDrawable(R.drawable.banana);
        assertThat(ldpiDrawable.getIntrinsicHeight()).isEqualTo(48);

        Configuration mdpiConfig = new Configuration(mCarContext.getResources().getConfiguration());
        mdpiConfig.densityDpi = 160;

        Context applicationContext = mCarContext.getApplicationContext();
        applicationContext
                .getResources()
                .updateConfiguration(mdpiConfig,
                        applicationContext.getResources().getDisplayMetrics());

        Drawable carContextDrawable = mCarContext.getDrawable(R.drawable.banana);
        assertThat(carContextDrawable.getIntrinsicHeight()).isEqualTo(48);

        Drawable applicationContextDrawable = applicationContext.getDrawable(R.drawable.banana);
        assertThat(applicationContextDrawable.getIntrinsicHeight()).isEqualTo(64);
    }

    @Test
    @UiThreadTest
    // TODO(rampara): Investigate removing usage of deprecated updateConfiguration API
    @SuppressWarnings("deprecation")
    public void changingApplicationContextDisplayMetrics_doesNotChangeCarContextDisplayMetrics() {
        Configuration ldpiConfig = new Configuration(mCarContext.getResources().getConfiguration());
        ldpiConfig.densityDpi = 120;

        mCarContext.onCarConfigurationChanged(ldpiConfig);

        Drawable ldpiDrawable = mCarContext.getDrawable(R.drawable.banana);
        assertThat(ldpiDrawable.getIntrinsicHeight()).isEqualTo(48);

        Configuration mdpiConfig = new Configuration(mCarContext.getResources().getConfiguration());
        mdpiConfig.densityDpi = 160;

        Context applicationContext = mCarContext.getApplicationContext();

        VirtualDisplay display = applicationContext.getSystemService(DisplayManager.class)
                .createVirtualDisplay(
                        "CarAppService",
                        mdpiConfig.screenWidthDp,
                        mdpiConfig.screenHeightDp,
                        5,
                        null,
                        VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        DisplayMetrics newDisplayMetrics = new DisplayMetrics();
        display.getDisplay().getMetrics(newDisplayMetrics);

        applicationContext.getResources().updateConfiguration(mdpiConfig, newDisplayMetrics);

        assertThat(applicationContext.getResources().getConfiguration()).isEqualTo(mdpiConfig);
        // TODO(rampara): Investigate why DisplayMetrics isn't updated
//        assertThat(applicationContext.getResources().getDisplayMetrics()).isEqualTo(
//                newDisplayMetrics);

        assertThat(mCarContext.getResources().getConfiguration()).isNotEqualTo(mdpiConfig);
        assertThat(mCarContext.getResources().getDisplayMetrics()).isNotEqualTo(newDisplayMetrics);
    }

    @Test
    public void isDarkMode_returnsExpectedValue() {
        assertThat(mCarContext.isDarkMode()).isFalse();

        mCarContext.getResources().getConfiguration().uiMode = Configuration.UI_MODE_NIGHT_YES;

        assertThat(mCarContext.isDarkMode()).isTrue();
    }

    private static class TestStartCarAppStub extends IStartCarApp.Stub {
        private final IStartCarApp mMockableStub;

        private TestStartCarAppStub(IStartCarApp mockableStub) {
            this.mMockableStub = mockableStub;
        }

        @Override
        public void startCarApp(Intent startCarAppIntent) throws RemoteException {
            mMockableStub.startCarApp(startCarAppIntent);
        }
    }

    @Test
    public void getOnBackPressedDispatcher_noListeners_popsAScreen() {
        mCarContext.getCarService(ScreenManager.class).push(mScreen1);
        mCarContext.getCarService(ScreenManager.class).push(mScreen2);

        mCarContext.getOnBackPressedDispatcher().onBackPressed();

        verify(mMockScreen1, never()).dispatchLifecycleEvent(Event.ON_DESTROY);
        verify(mMockScreen2).dispatchLifecycleEvent(Event.ON_DESTROY);
    }

    // TODO(rampara): Investigate how to mock final methods
//    @Test
//    public void
//    getOnBackPressedDispatcher_withAListenerThatIsStarted_callsTheListenerAndDoesNotPop() {
//        mCarContext.getCarService(ScreenManager.class).push(mScreen1);
//        mCarContext.getCarService(ScreenManager.class).push(mScreen2);
//
//        OnBackPressedCallback callback = mock(OnBackPressedCallback.class);
//        when(callback.isEnabled()).thenReturn(true);
//        mLifecycleOwner.mRegistry.setCurrentState(State.STARTED);
//
//        mCarContext.getOnBackPressedDispatcher().addCallback(mLifecycleOwner, callback);
//        mCarContext.getOnBackPressedDispatcher().onBackPressed();
//
//        verify(callback).handleOnBackPressed();
//        verify(mMockScreen1, never()).dispatchLifecycleEvent(Event.ON_DESTROY);
//        verify(mMockScreen2, never()).dispatchLifecycleEvent(Event.ON_DESTROY);
//    }
//
//    @Test
//    public void getOnBackPressedDispatcher_withAListenerThatIsNotStarted_popsAScreen() {
//        mCarContext.getCarService(ScreenManager.class).push(mScreen1);
//        mCarContext.getCarService(ScreenManager.class).push(mScreen2);
//
//        OnBackPressedCallback callback = mock(OnBackPressedCallback.class);
//        when(callback.isEnabled()).thenReturn(true);
//        mLifecycleOwner.mRegistry.setCurrentState(State.CREATED);
//
//        mCarContext.getOnBackPressedDispatcher().addCallback(mLifecycleOwner, callback);
//        mCarContext.getOnBackPressedDispatcher().onBackPressed();
//
//        verify(callback, never()).handleOnBackPressed();
//        verify(mMockScreen1, never()).dispatchLifecycleEvent(Lifecycle.Event.ON_DESTROY);
//        verify(mMockScreen2).dispatchLifecycleEvent(Lifecycle.Event.ON_DESTROY);
//    }
//
//    @Test
//    public void getOnBackPressedDispatcher_callsDefaultListenerWheneverTheAddedOneIsNotSTARTED() {
//        mCarContext.getCarService(ScreenManager.class).push(mScreen1);
//        mCarContext.getCarService(ScreenManager.class).push(mScreen2);
//
//        OnBackPressedCallback callback = mock(OnBackPressedCallback.class);
//        when(callback.isEnabled()).thenReturn(true);
//        mLifecycleOwner.mRegistry.setCurrentState(State.CREATED);
//
//        mCarContext.getOnBackPressedDispatcher().addCallback(mLifecycleOwner, callback);
//        mCarContext.getOnBackPressedDispatcher().onBackPressed();
//
//        verify(callback, never()).handleOnBackPressed();
//        verify(mMockScreen2).dispatchLifecycleEvent(Event.ON_DESTROY);
//
//        mLifecycleOwner.mRegistry.setCurrentState(State.STARTED);
//        mCarContext.getOnBackPressedDispatcher().onBackPressed();
//
//        verify(callback).handleOnBackPressed();
//    }
}
