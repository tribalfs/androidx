/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.support.v8.renderscript;

import android.support.v8.renderscript.RenderScript;

/**
 * Utility class for packing arguments and structures from Android system objects to
 * RenderScript objects.
 *
 * This class is only intended to be used to support the
 * reflected code generated by the RS tool chain.  It should not
 * be called directly.
 *
 **/
public class FieldPackerThunker {
    private android.renderscript.FieldPacker mN;
    private int mPos;

    public FieldPackerThunker(int len) {
        mN = new android.renderscript.FieldPacker(len);
        mPos = 0;
    }

    void align(int v) {
        mN.align(v);
        while ((mPos & (v - 1)) != 0) {
            mPos++;
        }
    }

    void reset() {
        mN.reset();
        mPos = 0;
    }

    void reset(int i) {
        mN.reset(i);
        mPos = i;
    }

    public void skip(int i) {
        mN.skip(i);
        mPos += i;
    }

    public void addI8(byte v) {
        mN.addI8(v);
        mPos++;
    }

    public void addI16(short v) {
        mN.addI16(v);
        mPos += 2;
    }

    public void addI32(int v) {
        mN.addI32(v);
        mPos += 4;
    }

    public void addI64(long v) {
        mN.addI64(v);
        mPos += 8;
    }

    public void addU8(short v) {
        mN.addU8(v);
        mPos++;
    }

    public void addU16(int v) {
        mN.addU16(v);
        mPos += 2;
    }

    public void addU32(long v) {
        mN.addU32(v);
        mPos += 4;
    }

    public void addU64(long v) {
        mN.addU64(v);
        mPos += 8;
    }

    public void addF32(float v) {
        mN.addF32(v);
        mPos += 4;
    }

    public void addF64(double v) {
        mN.addF64(v);
        mPos += 8;
    }

    public void addObj(BaseObj obj) {
        if (obj != null) {
            mN.addObj(obj.getNObj());
        } else {
            mN.addObj(null);
        }
        mPos += 4;  // Compat lib only works in 32-bit mode, so objects are 4 bytes.
    }

    public void addF32(Float2 v) {
        mN.addF32(new android.renderscript.Float2(v.x, v.y));
        mPos += 8;
    }
    public void addF32(Float3 v) {
        mN.addF32(new android.renderscript.Float3(v.x, v.y, v.z));
        mPos += 12;
    }
    public void addF32(Float4 v) {
        mN.addF32(new android.renderscript.Float4(v.x, v.y, v.z, v.w));
        mPos += 16;
    }

    public void addF64(Double2 v) {
        mN.addF64(new android.renderscript.Double2(v.x, v.y));
        mPos += 16;
    }
    public void addF64(Double3 v) {
        mN.addF64(new android.renderscript.Double3(v.x, v.y, v.z));
        mPos += 24;
    }
    public void addF64(Double4 v) {
        mN.addF64(new android.renderscript.Double4(v.x, v.y, v.z, v.w));
        mPos += 32;
    }

    public void addI8(Byte2 v) {
        mN.addI8(new android.renderscript.Byte2(v.x, v.y));
        mPos += 2;
    }
    public void addI8(Byte3 v) {
        mN.addI8(new android.renderscript.Byte3(v.x, v.y, v.z));
        mPos += 3;
    }
    public void addI8(Byte4 v) {
        mN.addI8(new android.renderscript.Byte4(v.x, v.y, v.z, v.w));
        mPos += 4;
    }

    public void addU8(Short2 v) {
        mN.addU8(new android.renderscript.Short2(v.x, v.y));
        mPos += 2;
    }
    public void addU8(Short3 v) {
        mN.addU8(new android.renderscript.Short3(v.x, v.y, v.z));
        mPos += 3;
    }
    public void addU8(Short4 v) {
        mN.addU8(new android.renderscript.Short4(v.x, v.y, v.z, v.w));
        mPos += 4;
    }

    public void addI16(Short2 v) {
        mN.addI16(new android.renderscript.Short2(v.x, v.y));
        mPos += 4;
    }
    public void addI16(Short3 v) {
        mN.addI16(new android.renderscript.Short3(v.x, v.y, v.z));
        mPos += 6;
    }
    public void addI16(Short4 v) {
        mN.addI16(new android.renderscript.Short4(v.x, v.y, v.z, v.w));
        mPos += 8;
    }

    public void addU16(Int2 v) {
        mN.addU16(new android.renderscript.Int2(v.x, v.y));
        mPos += 4;
    }
    public void addU16(Int3 v) {
        mN.addU16(new android.renderscript.Int3(v.x, v.y, v.z));
        mPos += 6;
    }
    public void addU16(Int4 v) {
        mN.addU16(new android.renderscript.Int4(v.x, v.y, v.z, v.w));
        mPos += 8;
    }

    public void addI32(Int2 v) {
        mN.addI32(new android.renderscript.Int2(v.x, v.y));
        mPos += 8;
    }
    public void addI32(Int3 v) {
        mN.addI32(new android.renderscript.Int3(v.x, v.y, v.z));
        mPos += 12;
    }
    public void addI32(Int4 v) {
        mN.addI32(new android.renderscript.Int4(v.x, v.y, v.z, v.w));
        mPos += 16;
    }

    public void addU32(Long2 v) {
        mN.addU32(new android.renderscript.Long2(v.x, v.y));
        mPos += 8;
    }
    public void addU32(Long3 v) {
        mN.addU32(new android.renderscript.Long3(v.x, v.y, v.z));
        mPos += 12;
    }
    public void addU32(Long4 v) {
        mN.addU32(new android.renderscript.Long4(v.x, v.y, v.z, v.w));
        mPos += 16;
    }

    public void addI64(Long2 v) {
        mN.addI64(new android.renderscript.Long2(v.x, v.y));
        mPos += 16;
    }
    public void addI64(Long3 v) {
        mN.addI64(new android.renderscript.Long3(v.x, v.y, v.z));
        mPos += 24;
    }
    public void addI64(Long4 v) {
        mN.addI64(new android.renderscript.Long4(v.x, v.y, v.z, v.w));
        mPos += 32;
    }

    public void addU64(Long2 v) {
        mN.addU64(new android.renderscript.Long2(v.x, v.y));
        mPos += 16;
    }
    public void addU64(Long3 v) {
        mN.addU64(new android.renderscript.Long3(v.x, v.y, v.z));
        mPos += 24;
    }
    public void addU64(Long4 v) {
        mN.addU64(new android.renderscript.Long4(v.x, v.y, v.z, v.w));
        mPos += 32;
    }

    public void addMatrix(Matrix4f v) {
        mN.addMatrix(new android.renderscript.Matrix4f(v.getArray()));
        mPos += (4 * 4 * 4);
    }

    public void addMatrix(Matrix3f v) {
        mN.addMatrix(new android.renderscript.Matrix3f(v.getArray()));
        mPos += (3 * 3 * 4);
    }

    public void addMatrix(Matrix2f v) {
        mN.addMatrix(new android.renderscript.Matrix2f(v.getArray()));
        mPos += (2 * 2 * 4);
    }

    public void addBoolean(boolean v) {
        mN.addBoolean(v);
        mPos++;
    }

    public final byte[] getData() {
        return mN.getData();
    }

    // We must compute our own mPos, since this API is not available in older target APIs.
    public int getPos() {
        return mPos;
    }
}


