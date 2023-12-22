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

package androidx.appcompat.view;

import android.graphics.Insets;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import java.util.List;

@RequiresApi(api = 30)
public class SeslResizeViewInsetsCallback extends WindowInsetsAnimation.Callback {
    private final int mDeferInsetTypes;
    private final int mOldOrientation;
    private int mOriginalHeight;
    private int mOriginalHeightSpec;
    private final int mPersistentInsetTypes;
    private boolean mSkipProgress;
    private final View mView;

    public SeslResizeViewInsetsCallback(@NonNull View view, int persistentInsetTypes, int deferInsetTypes) {
        this(view, persistentInsetTypes, deferInsetTypes, DISPATCH_MODE_STOP);
    }

    @Override
    public void onEnd(@NonNull WindowInsetsAnimation anim) {
        if (mSkipProgress) {
            mSkipProgress = false;
            return;
        }
        ViewGroup.LayoutParams layoutParams = mView.getLayoutParams();
        layoutParams.height = mOriginalHeightSpec;
        mView.setLayoutParams(layoutParams);
    }

    @Override
    public void onPrepare(@NonNull WindowInsetsAnimation anim) {
        super.onPrepare(anim);
        if (mOldOrientation != mView.getResources().getConfiguration().orientation) {
            mSkipProgress = true;
        } else {
            mOriginalHeightSpec = mView.getLayoutParams().height;
        }
    }

    @Override
    @NonNull
    public WindowInsets onProgress(@NonNull WindowInsets windowInsets,
            @NonNull List<WindowInsetsAnimation> list) {
        Insets differInsets;
        Insets persistInsets;
        Insets targetInset;

        if (mSkipProgress) {
            return windowInsets;
        }

        differInsets = windowInsets.getInsets(mDeferInsetTypes);
        persistInsets = windowInsets.getInsets(mPersistentInsetTypes);
        targetInset = Insets.subtract(differInsets, persistInsets);
        Insets max = Insets.max(targetInset, Insets.NONE);

        int targetHeight = max.top - max.bottom;
        if ((mOriginalHeight == 0 || mOriginalHeight == -1) && targetHeight == 0) {
            mOriginalHeight = mView.getHeight();
        }
        ViewGroup.LayoutParams lp = mView.getLayoutParams();
        lp.height = mOriginalHeight + targetHeight;
        mView.setLayoutParams(lp);
        return windowInsets;
    }

    public SeslResizeViewInsetsCallback(@NonNull View view, int persistentInsetTypes, int deferInsetTypes,
            int dispatchMode) {
        super(dispatchMode);
        mOriginalHeight = -1;
        mOriginalHeightSpec = -1;
        mView = view;
        mPersistentInsetTypes = persistentInsetTypes;
        mDeferInsetTypes = deferInsetTypes;
        mOldOrientation = view.getResources().getConfiguration().orientation;
    }
}