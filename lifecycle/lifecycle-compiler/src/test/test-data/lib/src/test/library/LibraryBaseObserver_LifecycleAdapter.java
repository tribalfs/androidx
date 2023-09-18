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

package test.library;

import androidx.lifecycle.GenericLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import java.lang.Override;
import javax.annotation.processing.Generated;

@SuppressWarnings("deprecation")
@Generated("androidx.lifecycle.LifecycleProcessor")
public class LibraryBaseObserver_LifecycleAdapter implements GenericLifecycleObserver {
    final LibraryBaseObserver mReceiver;

    LibraryBaseObserver_LifecycleAdapter(LibraryBaseObserver receiver) {
        this.mReceiver = receiver;
    }

    @Override
    public void onStateChanged(LifecycleOwner owner, Lifecycle.Event event) {
        // fake adapter
    }

    public static void __synthetic_doOnPause(LibraryBaseObserver receiver, LifecycleOwner owner) {
        receiver.doOnPause(owner);
    }
}
