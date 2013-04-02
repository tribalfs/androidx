/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.util.Log;

class ScriptIntrinsicLUTThunker extends ScriptIntrinsicLUT {
    android.renderscript.ScriptIntrinsicLUT mN;

    android.renderscript.ScriptIntrinsicLUT getNObj() {
        return mN;
    }

    private ScriptIntrinsicLUTThunker(int id, RenderScript rs) {
        super(id, rs);
    }

    public static ScriptIntrinsicLUTThunker create(RenderScript rs, Element e) {
        RenderScriptThunker rst = (RenderScriptThunker) rs;
        ElementThunker et = (ElementThunker) e;

        ScriptIntrinsicLUTThunker si = new ScriptIntrinsicLUTThunker(0, rs);
        si.mN = android.renderscript.ScriptIntrinsicLUT.create(rst.mN, et.getNObj());
        return si;
    }

    public void setRed(int index, int value) {
        mN.setRed(index, value);
    }

    public void setGreen(int index, int value) {
        mN.setGreen(index, value);
    }

    public void setBlue(int index, int value) {
        mN.setBlue(index, value);
    }

    public void setAlpha(int index, int value) {
        mN.setAlpha(index, value);
    }

    public void forEach(Allocation ain, Allocation aout) {
        AllocationThunker aint = (AllocationThunker)ain;
        AllocationThunker aoutt = (AllocationThunker)aout;
        mN.forEach(aint.getNObj(), aoutt.getNObj());
    }

    public Script.KernelID getKernelID() {
        Script.KernelID k = createKernelID(0, 3, null, null);
        k.mN = mN.getKernelID();
        return k;
    }
}

