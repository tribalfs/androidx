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

package foo;

import android.arch.lifecycle.GeneratedAdapter;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.MethodCallsLogger;
import java.lang.Override;
import javax.annotation.Generated;

@Generated("android.arch.lifecycle.LifecycleProcessor")
public class InterfaceOk2Derived_LifecycleAdapter implements GeneratedAdapter {
  final InterfaceOk2Derived mReceiver;

  InterfaceOk2Derived_LifecycleAdapter(InterfaceOk2Derived receiver) {
    this.mReceiver = receiver;
  }

  @Override
  public void callMethods(LifecycleOwner owner, Lifecycle.Event event, boolean onAny,
      MethodCallsLogger logger) {
    boolean hasLogger = logger != null;
    if (onAny) {
      return;
    }
    if (event == Lifecycle.Event.ON_STOP) {
      if (!hasLogger || logger.approveCall("onStop1", 2)) {
        mReceiver.onStop1(owner);
      }
      if (!hasLogger || logger.approveCall("onStop2", 2)) {
        mReceiver.onStop2(owner);
      }
      if (!hasLogger || logger.approveCall("onStop3", 2)) {
        mReceiver.onStop3(owner);
      }
      return;
    }
  }
}
