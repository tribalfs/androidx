/*
 * Copyright (C) 2022 The Android Open Source Project
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

package androidx.reflect.os;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.os.Build;

import androidx.annotation.RestrictTo;
import androidx.reflect.DeviceInfo;
import androidx.reflect.SeslBaseReflector;

import java.lang.reflect.Method;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

@RestrictTo(LIBRARY_GROUP_PREFIX)
public class SeslSystemPropertiesReflector {
    private static String mClassName = "android.os.SemSystemProperties";

    private SeslSystemPropertiesReflector() {
    }

    public static String getStringProperties(String key) {
        if (!DeviceInfo.isOneUI()) return null;
        Method method;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            method = SeslBaseReflector.getDeclaredMethod(mClassName, "get", String.class);
        } else {
            method = SeslBaseReflector.getMethod(mClassName, "get", String.class);
        }

        if (method != null) {
            Object result = SeslBaseReflector.invoke(null, method, key);
            if (result instanceof String) {
                return (String) result;
            }
        }

        return null;
    }

    public static String getSalesCode() {
        if (!DeviceInfo.isOneUI()) return null;
        Method method;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            method = SeslBaseReflector.getDeclaredMethod(mClassName, "getSalesCode");
        } else {
            method = SeslBaseReflector.getMethod(mClassName, "getSalesCode");
        }

        if (method != null) {
            Object result = SeslBaseReflector.invoke(null, method);
            if (result instanceof String) {
                return (String) result;
            }
        }

        return null;
    }
}
