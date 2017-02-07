/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.support.v4.graphics;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Build.VERSION_CODES;

/**
 * Helper for accessing features in {@link android.graphics.Bitmap}
 * introduced after API level 4 in a backwards compatible fashion.
 */
public final class BitmapCompat {
    static class BitmapCompatBaseImpl {
        public boolean hasMipMap(Bitmap bitmap) {
            return false;
        }

        public void setHasMipMap(Bitmap bitmap, boolean hasMipMap) {
        }

        public int getAllocationByteCount(Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    }

    @TargetApi(VERSION_CODES.JELLY_BEAN_MR2)
    static class BitmapCompatApi18Impl extends BitmapCompatBaseImpl {
        @Override
        public boolean hasMipMap(Bitmap bitmap){
            return bitmap.hasMipMap();
        }

        @Override
        public void setHasMipMap(Bitmap bitmap, boolean hasMipMap) {
            bitmap.setHasMipMap(hasMipMap);
        }
    }

    @TargetApi(VERSION_CODES.KITKAT)
    static class BitmapCompatApi19Impl extends BitmapCompatApi18Impl {
        @Override
        public int getAllocationByteCount(Bitmap bitmap) {
            return bitmap.getAllocationByteCount();
        }
    }

    /**
     * Select the correct implementation to use for the current platform.
     */
    static final BitmapCompatBaseImpl IMPL;
    static {
        final int version = Build.VERSION.SDK_INT;
        if (version >= 19) {
            IMPL = new BitmapCompatApi19Impl();
        } else if (version >= 18) {
            IMPL = new BitmapCompatApi18Impl();
        } else {
            IMPL = new BitmapCompatBaseImpl();
        }
    }

    public static boolean hasMipMap(Bitmap bitmap) {
        return IMPL.hasMipMap(bitmap);
    }

    public static void setHasMipMap(Bitmap bitmap, boolean hasMipMap) {
        IMPL.setHasMipMap(bitmap, hasMipMap);
    }

    /**
     * Returns the size of the allocated memory used to store this bitmap's pixels in a backwards
     * compatible way.
     *
     * @param bitmap the bitmap in which to return it's allocation size
     * @return the allocation size in bytes
     */
    public static int getAllocationByteCount(Bitmap bitmap) {
        return IMPL.getAllocationByteCount(bitmap);
    }

    private BitmapCompat() {}
}