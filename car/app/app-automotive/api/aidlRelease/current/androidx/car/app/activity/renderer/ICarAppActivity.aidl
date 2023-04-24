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
///////////////////////////////////////////////////////////////////////////////
// THIS FILE IS IMMUTABLE. DO NOT EDIT IN ANY CASE.                          //
///////////////////////////////////////////////////////////////////////////////

// This file is a snapshot of an AIDL file. Do not edit it manually. There are
// two cases:
// 1). this is a frozen version file - do not edit this in any case.
// 2). this is a 'current' file. If you make a backwards compatible change to
//     the interface (from the latest frozen version), the build system will
//     prompt you to update this file with `m <name>-update-api`.
//
// You must not make a backward incompatible change to any AIDL file built
// with the aidl_interface module type with versions property set. The module
// type is used to build AIDL files in a way that they can be used across
// independently updatable components of the system. If a device is shipped
// with such a backward incompatible change, it has a high risk of breaking
// later when a module using the interface is updated, e.g., Mainline modules.

package androidx.car.app.activity.renderer;
/* @hide */
interface ICarAppActivity {
  oneway void setSurfacePackage(in androidx.car.app.serialization.Bundleable surfacePackage) = 1;
  oneway void setSurfaceListener(androidx.car.app.activity.renderer.surface.ISurfaceListener listener) = 2;
  oneway void registerRendererCallback(androidx.car.app.activity.renderer.IRendererCallback callback) = 3;
  oneway void onStartInput() = 4;
  oneway void onStopInput() = 5;
  oneway void startCarApp(in android.content.Intent intent) = 6;
  oneway void finishCarApp() = 7;
  oneway void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd) = 8;
  oneway void setInsetsListener(androidx.car.app.activity.renderer.IInsetsListener listener) = 9;
  oneway void showAssist(in android.os.Bundle args) = 10;
}
