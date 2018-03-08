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

import androidx.lifecycle.GeneratedAdapter;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MethodCallsLogger;
import java.lang.Override;
import javax.annotation.Generated;
import test.library.LibraryBaseObserver_LifecycleAdapter;

@Generated("androidx.lifecycle.LifecycleProcessor")
public class DerivedFromJar_LifecycleAdapter implements GeneratedAdapter {
  final DerivedFromJar mReceiver;

  DerivedFromJar_LifecycleAdapter(DerivedFromJar receiver) {
    this.mReceiver = receiver;
  }

  @Override
  public void callMethods(LifecycleOwner owner, Lifecycle.Event event, boolean onAny,
          MethodCallsLogger logger) {
    boolean hasLogger = logger != null;
    if (onAny) {
      return;
    }
    if (event == Lifecycle.Event.ON_START) {
      if (!hasLogger || logger.approveCall("doOnStart", 1)) {
        mReceiver.doOnStart();
      }
      if (!hasLogger || logger.approveCall("doAnother", 1)) {
        mReceiver.doAnother();
      }
      return;
    }
    if (event == Lifecycle.Event.ON_PAUSE) {
      if (!hasLogger || logger.approveCall("doOnPause", 2)) {
        LibraryBaseObserver_LifecycleAdapter.__synthetic_doOnPause(mReceiver,owner);
      }
      return;
    }
  }
}