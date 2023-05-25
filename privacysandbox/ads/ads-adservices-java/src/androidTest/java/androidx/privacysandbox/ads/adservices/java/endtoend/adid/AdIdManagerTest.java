/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.privacysandbox.ads.adservices.java.endtoend.adid;

import static com.google.common.truth.Truth.assertThat;

import androidx.privacysandbox.ads.adservices.adid.AdId;
import androidx.privacysandbox.ads.adservices.internal.AdServicesInfo;
import androidx.privacysandbox.ads.adservices.java.adid.AdIdManagerFutures;
import androidx.privacysandbox.ads.adservices.java.endtoend.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
@SdkSuppress(minSdkVersion = 28) // API 28 required for device_config used by this test
public class AdIdManagerTest {
    private static final String TAG = "AdIdManagerTest";
    private TestUtil mTestUtil = new TestUtil(InstrumentationRegistry.getInstrumentation(),
            TAG);

    @Before
    public void setup() throws Exception {
        mTestUtil.overrideAdIdKillSwitch(true);
        mTestUtil.overrideKillSwitches(true);
        mTestUtil.overrideConsentManagerDebugMode(true);
        mTestUtil.overrideAllowlists(true);

        // Put in a short sleep to make sure the updated config propagates
        // before starting the tests
        Thread.sleep(100);
    }

    @After
    public void teardown() {
        mTestUtil.overrideAdIdKillSwitch(false);
        mTestUtil.overrideKillSwitches(false);
        mTestUtil.overrideConsentManagerDebugMode(false);
        mTestUtil.overrideAllowlists(false);
    }

    @Test
    public void testAdId() throws Exception {
        // Skip the test if SDK extension 4 is not present.
        Assume.assumeTrue(AdServicesInfo.INSTANCE.version() >= 4);

        AdIdManagerFutures adIdManager =
                AdIdManagerFutures.from(ApplicationProvider.getApplicationContext());
        AdId adId = adIdManager.getAdIdAsync().get();
        assertThat(adId.getAdId()).isNotEmpty();
        assertThat(adId.isLimitAdTrackingEnabled()).isNotNull();
    }
}
