/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.textclassifier;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.util.Preconditions;

/**
 * Provides a {@link androidx.textclassifier.TextClassifier} interface for a
 * {@link android.view.textclassifier.TextClassifier} object.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@RequiresApi(Build.VERSION_CODES.O)
public class PlatformTextClassifierWrapper extends TextClassifier {
    private final android.view.textclassifier.TextClassifier mPlatformTextClassifier;
    private final Context mContext;
    private final TextClassifier mFallback;

    @VisibleForTesting
    PlatformTextClassifierWrapper(
            @NonNull Context context,
            @NonNull android.view.textclassifier.TextClassifier platformTextClassifier,
            @NonNull SessionStrategy sessionStrategy) {
        super(sessionStrategy);
        mContext = Preconditions.checkNotNull(context);
        mPlatformTextClassifier = Preconditions.checkNotNull(platformTextClassifier);
        mFallback = LegacyTextClassifier.of(context);
    }

    /**
     * Returns a newly create instance of PlatformTextClassifierWrapper.
     */
    @NonNull
    public static PlatformTextClassifierWrapper create(
            @NonNull Context context,
            @NonNull TextClassificationContext textClassificationContext) {

        android.view.textclassifier.TextClassificationManager textClassificationManager =
                (android.view.textclassifier.TextClassificationManager)
                        context.getSystemService(Context.TEXT_CLASSIFICATION_SERVICE);

        android.view.textclassifier.TextClassifier platformTextClassifier;
        SessionStrategy sessionStrategy;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            platformTextClassifier =
                    textClassificationManager.createTextClassificationSession(
                            textClassificationContext.toPlatform());
            sessionStrategy = new ProxySessionStrategy(platformTextClassifier);
        } else {
            // No session handling before P.
            platformTextClassifier = textClassificationManager.getTextClassifier();
            sessionStrategy = SessionStrategy.NO_OP;
        }

        return new PlatformTextClassifierWrapper(
                context, platformTextClassifier, sessionStrategy);
    }

    /** @inheritDoc */
    @NonNull
    @WorkerThread
    @Override
    public TextSelection suggestSelection(@NonNull TextSelection.Request request) {
        Preconditions.checkNotNull(request);
        ensureNotOnMainThread();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return TextSelection.fromPlatform(
                    mPlatformTextClassifier.suggestSelection(request.toPlatform()));
        }
        return TextSelection.fromPlatform(
                mPlatformTextClassifier.suggestSelection(
                        request.getText(),
                        request.getStartIndex(),
                        request.getEndIndex(),
                        ConvertUtils.unwrapLocalListCompat(request.getDefaultLocales())));
    }

    /** @inheritDoc */
    @NonNull
    @WorkerThread
    @Override
    public TextClassification classifyText(@NonNull TextClassification.Request request) {
        Preconditions.checkNotNull(request);
        ensureNotOnMainThread();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return TextClassification.fromPlatform(mContext,
                    mPlatformTextClassifier.classifyText(request.toPlatform()));
        }
        TextClassification textClassification = TextClassification.fromPlatform(mContext,
                mPlatformTextClassifier.classifyText(
                        request.getText(),
                        request.getStartIndex(),
                        request.getEndIndex(),
                        ConvertUtils.unwrapLocalListCompat(request.getDefaultLocales())));
        return textClassification;
    }

    /** @inheritDoc */
    @NonNull
    @WorkerThread
    @Override
    public TextLinks generateLinks(@NonNull TextLinks.Request request) {
        Preconditions.checkNotNull(request);
        ensureNotOnMainThread();
        if (Build.VERSION.SDK_INT >=  Build.VERSION_CODES.P) {
            return TextLinks.fromPlatform(mPlatformTextClassifier.generateLinks(
                    request.toPlatform()), request.getText());
        }
        return mFallback.generateLinks(request);
    }

    /** @inheritDoc */
    @Override
    @WorkerThread
    public int getMaxGenerateLinksTextLength() {
        ensureNotOnMainThread();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return mPlatformTextClassifier.getMaxGenerateLinksTextLength();
        }
        return super.getMaxGenerateLinksTextLength();
    }

    /**
     * Delegates session handling to {@link android.view.textclassifier.TextClassifier}.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private static class ProxySessionStrategy implements SessionStrategy {
        private final android.view.textclassifier.TextClassifier mPlatformTextClassifier;

        ProxySessionStrategy(
                @NonNull android.view.textclassifier.TextClassifier textClassifier) {
            Preconditions.checkNotNull(textClassifier);
            mPlatformTextClassifier = textClassifier;
        }

        @Override
        public void destroy() {
            mPlatformTextClassifier.destroy();
        }

        @Override
        public void reportSelectionEvent(@NonNull SelectionEvent event) {
            Preconditions.checkNotNull(event);
            mPlatformTextClassifier.onSelectionEvent(event.toPlatform());
        }

        @Override
        public boolean isDestroyed() {
            return mPlatformTextClassifier.isDestroyed();
        }
    }
}
