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

package bar;

import com.android.support.lifecycle.GenericLifecycleObserver;
import com.android.support.lifecycle.LifecycleProvider;
import java.lang.Object;
import java.lang.Override;

public class DifferentPackagesDerived2_LifecycleAdapter implements GenericLifecycleObserver {
    final DifferentPackagesDerived2 mReceiver;

    DifferentPackagesDerived2_LifecycleAdapter(DifferentPackagesDerived2 receiver) {
        this.mReceiver = receiver;
    }

    @Override
    public void onStateChanged(LifecycleProvider provider, int event) {
        if ((event & 8192) != 0) {
            mReceiver.onStop(provider, event);
            mReceiver.onStop2(provider, event);
        }
    }

    public Object getReceiver() {
        return mReceiver;
    }
}
