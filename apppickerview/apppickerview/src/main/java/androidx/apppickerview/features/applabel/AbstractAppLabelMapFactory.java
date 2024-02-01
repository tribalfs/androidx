/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.apppickerview.features.applabel;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.Map;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */
@RequiresApi(api = Build.VERSION_CODES.O)
public abstract class AbstractAppLabelMapFactory {

    public interface KeyFormatter {
        String getKey(String str, String str2);
    }

    public abstract Map<String, String> getLabelMap();

    public static AbstractAppLabelMapFactory getFactory(Context context, boolean z) {
        return new AppLabelMapSCSFactory(context, getKeyFormatter(z));
    }

    public static KeyFormatter getKeyFormatter(boolean z) {
        if (z) {
            return new KeyFormatter() {
                @Override
                public String getKey(String str, String str2) {
                    return str + "/" + str2;
                }
            };
        }
        return new KeyFormatter() {
            @Override
            public String getKey(String str, String str2) {
                return str;
            }
        };
    }

}