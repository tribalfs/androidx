/*
 * Copyright 2023 The Android Open Source Project
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

package org.chromium.android_webview.js_sandbox.common;
/* @hide */
interface IJsSandboxConsoleCallback {
  void consoleMessage(int contextGroupId, int level, String message, String source, int line, int column, String trace) = 0;
  void consoleClear(int contextGroupId) = 1;
  const int CONSOLE_MESSAGE_LEVEL_LOG = (1 << 0) /* 1 */;
  const int CONSOLE_MESSAGE_LEVEL_DEBUG = (1 << 1) /* 2 */;
  const int CONSOLE_MESSAGE_LEVEL_INFO = (1 << 2) /* 4 */;
  const int CONSOLE_MESSAGE_LEVEL_ERROR = (1 << 3) /* 8 */;
  const int CONSOLE_MESSAGE_LEVEL_WARNING = (1 << 4) /* 16 */;
}
