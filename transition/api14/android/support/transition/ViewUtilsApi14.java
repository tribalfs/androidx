/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.support.transition;

import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.view.View;

@RequiresApi(14)
class ViewUtilsApi14 implements ViewUtilsImpl {

    @Override
    public ViewOverlayImpl getOverlay(@NonNull View view) {
        return ViewOverlayApi14.createFrom(view);
    }

    @Override
    public WindowIdImpl getWindowId(@NonNull View view) {
        return new WindowIdApi14(view.getWindowToken());
    }

    @Override
    public void setTransitionAlpha(@NonNull View view, float alpha) {
        view.setAlpha(alpha);
    }

    @Override
    public float getTransitionAlpha(@NonNull View view) {
        return view.getAlpha();
    }

}
