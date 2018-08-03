/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.os.AsyncTask;
import android.os.CancellationSignal;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.UiThread;
import androidx.core.util.Preconditions;
import androidx.core.util.Supplier;

import java.util.concurrent.Executor;

/**
 * Provides {@link android.text.util.Linkify} functionality using a {@link TextClassifier}.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class SmartLinkify {

    private SmartLinkify() {}

    /**
     * Scans the text of the provided TextView and turns all occurrences of the entity types
     * specified by {@code options} into clickable links. If links are found, this method
     * removes any pre-existing {@link TextLinks.TextLinkSpan} attached to the text (to avoid
     * problems if you call it repeatedly on the same text) and sets the movement method for the
     * TextView to LinkMovementMethod.
     *
     * <p><strong>Note:</strong> This method returns immediately but generates the links with
     * a text classifier created via
     * {@link TextClassificationManager#createTextClassifier(TextClassificationContext)}
     * on a background thread. The generated links are applied on the UI thread.
     *
     * @param textView TextView whose text is to be marked-up with links
     * @param params optional parameters to specify how to generate the links
     */
    public void addLinksAsync(
            @NonNull TextView textView,
            @Nullable TextLinksParams params) {
        addLinksAsync(textView, null /* classifierFactory */, params,
                null /* cancel */, null /* executor */, null /* callback */);
    }

    /**
     * Scans the text of the provided TextView and turns all occurrences of the entity types
     * specified by {@code options} into clickable links. If links are found, this method
     * removes any pre-existing {@link TextLinks.TextLinkSpan} attached to the text (to avoid
     * problems if you call it repeatedly on the same text) and sets the movement method for the
     * TextView to LinkMovementMethod.
     *
     * <p><strong>Note:</strong> This method returns immediately but generates the links with
     * the specified text classifier on a background thread. The generated links are applied on the
     * UI thread.
     *
     * @param textView TextView whose text is to be marked-up with links
     * @param classifierFactory factory to create the TextClassifier used to generate and handle
     *      the links. If null, a text classifier created via
     *      {@link TextClassificationManager#createTextClassifier(TextClassificationContext)}
     *      is used
     * @param params optional parameters to specify how to generate the links
     * @param cancel Cancellation signal to cancel the task
     * @param executor Executor that runs the background task
     * @param callback Callback that receives the final status of the background task execution
     */
    public static void addLinksAsync(
            @NonNull final TextView textView,
            @Nullable TextClassifierFactory classifierFactory,
            @Nullable TextLinksParams params,
            @Nullable CancellationSignal cancel,
            @Nullable Executor executor,
            @Nullable final Callback callback) {
        Preconditions.checkNotNull(textView);
        final Supplier<Spannable> textSupplier = new Supplier<Spannable>() {
            @Override
            public Spannable get() {
                final CharSequence text = textView.getText();
                return (text instanceof Spannable)
                        ? (Spannable) text : SpannableString.valueOf(text);
            }
        };
        final String widgetType = getWidgetType(textView);
        final Callback callbackWrapper = new Callback() {
            @Override
            public void onLinkify(Spannable text, int status) {
                if (status == TextLinks.STATUS_LINKS_APPLIED) {
                    final MovementMethod method = textView.getMovementMethod();
                    if (!(method instanceof LinkMovementMethod)) {
                        if (textView.getLinksClickable()) {
                            textView.setMovementMethod(LinkMovementMethod.getInstance());
                        }
                    }

                    if (text != textView.getText()) {
                        textView.setText(text);
                    }
                }

                if (callback != null) {
                    callback.onLinkify(text, status);
                }
            }
        };
        addLinksAsync(textSupplier, textView.getContext(), widgetType,
                classifierFactory, params, cancel, executor, callbackWrapper);
    }

    private static String getWidgetType(TextView textView) {
        final String widgetType;
        if (isTextEditable(textView)) {
            widgetType = TextClassifier.WIDGET_TYPE_EDITTEXT;
        } else if (textView.isTextSelectable()) {
            widgetType = TextClassifier.WIDGET_TYPE_TEXTVIEW;
        } else {
            widgetType = TextClassifier.WIDGET_TYPE_UNSELECTABLE_TEXTVIEW;
        }
        return widgetType;
    }

    private static boolean isTextEditable(TextView textView) {
        return textView.getText() instanceof Editable
                && textView.onCheckIsTextEditor()
                && textView.isEnabled();
    }

    /**
     * Scans the text of the provided TextView and turns all occurrences of the entity types
     * specified by {@code options} into clickable links. If links are found, this method
     * removes any pre-existing {@link TextLinks.TextLinkSpan} attached to the text to avoid
     * problems if you call it repeatedly on the same text.
     *
     * <p><strong>Note:</strong> This method returns immediately but generates the links with
     * a text classifier created via
     * {@link TextClassificationManager#createTextClassifier(TextClassificationContext)}
     * on a background thread. The generated links are applied on the UI thread.
     *
     * @param text Spannable whose text is to be marked-up with links
     * @param context the current context
     * @param params optional parameters to specify how to generate the links
     */
    public static void addLinksAsync(
            @NonNull Spannable text, @NonNull Context context, @Nullable TextLinksParams params) {
        addLinksAsync(text, context, null /* classifierFactory */,
                params, null /* cancel */, null /* executor */, null /* callback */);
    }

    /**
     * Scans the text of the provided TextView and turns all occurrences of the entity types
     * specified by {@code options} into clickable links. If links are found, this method
     * removes any pre-existing {@link TextLinks.TextLinkSpan} attached to the text to avoid
     * problems if you call it repeatedly on the same text.
     *
     * <p><strong>Note:</strong> This method returns immediately but generates the links with
     * the specified text classifier on a background thread. The generated links are applied on the
     * UI thread.
     *
     * @param text Spannable whose text is to be marked-up with links
     * @param context the current context
     * @param classifierFactory factory to create the TextClassifier used to generate and handle
     *      the links. If null, a text classifier created via
     *      {@link TextClassificationManager#createTextClassifier(TextClassificationContext)}
     *      is used
     * @param params optional parameters to specify how to generate the links
     * @param cancel Cancellation signal to cancel the task
     * @param executor Executor that runs the background task
     * @param callback Callback that receives the final status of the background task execution
     */
    public static void addLinksAsync(
            @NonNull final Spannable text,
            @NonNull final Context context,
            @Nullable TextClassifierFactory classifierFactory,
            @Nullable TextLinksParams params,
            @Nullable CancellationSignal cancel,
            @Nullable Executor executor,
            @Nullable Callback callback) {
        final Supplier<Spannable> textSupplier = new Supplier<Spannable>() {
            @Override
            public Spannable get() {
                return text;
            }
        };
        addLinksAsync(textSupplier, context, TextClassifier.WIDGET_TYPE_UNKNOWN,
                classifierFactory, params, cancel, executor, callback);
    }

    private static void addLinksAsync(
            @NonNull Supplier<Spannable> textSupplier,
            @NonNull Context context,
            @NonNull String widgetType,
            @Nullable TextClassifierFactory classifierFactory,
            @Nullable TextLinksParams params,
            @Nullable CancellationSignal cancel,
            @Nullable Executor executor,
            @Nullable Callback callback) {
        final LinkifyTask task = new LinkifyTask(
                textSupplier, context, widgetType, classifierFactory, params, callback);
        if (cancel != null) {
            cancel.setOnCancelListener(task);
        }
        if (executor != null) {
            task.executeOnExecutor(executor);
        } else {
            task.execute();
        }
    }

    /**
     * Callback for when a SmartLinkify call completes.
     */
    public interface Callback {

        /**
         * Notifies when SmartLinkify completes.
         * This is called on the UI thread.
         *
         * @param text the text that was linkified
         * @param status see {@link TextLinks} for statuses reported
         */
        // TODO: Decide whether or not we want to guarantee what thread the links are applied on
        // and this callback is called on. Ideally, we'd only run on the UI thread for text that is
        // currently attached to the UI.
        void onLinkify(Spannable text, @TextLinks.Status int status);
    }

    private static final class LinkifyTask extends AsyncTask<Void, Void, TextLinks>
            implements CancellationSignal.OnCancelListener {

        private static final Callback NO_OP_CALLBACK = new Callback() {
            @Override
            public void onLinkify(Spannable text, int status) {}
        };

        private final Supplier<Spannable> mTextSupplier;
        private final Spannable mText;
        private final CharSequence mTruncatedText;
        private final TextClassifier mClassifier;
        private final TextLinksParams mParams;
        private final Callback mCallback;
        private final TextLinks.Request mRequest;

        @Nullable
        private final TextClassifierFactory mClassifierFactory;

        @TextLinks.Status
        private int mStatus = TextLinks.STATUS_UNKNOWN;

        LinkifyTask(@NonNull Supplier<Spannable> textSupplier,
                    @NonNull Context context,
                    @NonNull String widgetType,
                    @Nullable TextClassifierFactory classifierFactory,
                    @Nullable TextLinksParams params,
                    @Nullable Callback callback) {
            mTextSupplier = Preconditions.checkNotNull(textSupplier);
            mText = mTextSupplier.get();
            mClassifierFactory = classifierFactory;
            final TextClassificationContext classificationContext =
                    new TextClassificationContext.Builder(context.getPackageName(), widgetType)
                            .build();
            if (mClassifierFactory != null) {
                mClassifier = classifierFactory.create(classificationContext);
            } else {
                mClassifier = TextClassificationManager.of(context)
                        .createTextClassifier(classificationContext);
            }
            mParams = params != null ? params : new TextLinksParams.Builder().build();
            // TODO: If text is longer than the supported length,
            // break it down and process in parallel.
            mTruncatedText = mText.subSequence(
                    0, Math.min(mText.length(), mClassifier.getMaxGenerateLinksTextLength()));
            mCallback = callback != null ? callback : NO_OP_CALLBACK;
            mRequest = new TextLinks.Request.Builder(mTruncatedText)
                    .setEntityConfig(mParams.getEntityConfig())
                    .setDefaultLocales(mParams.getDefaultLocales())
                    .build();
        }

        @Override
        protected TextLinks doInBackground(Void... nil) {
            final TextLinks textLinks = mClassifier.generateLinks(mRequest);
            textLinks.setClassifierFactory(mClassifierFactory);
            return textLinks;
        }

        @Override
        @UiThread
        protected void onPostExecute(TextLinks links) {
            if (links.getLinks().isEmpty()) {
                mStatus = TextLinks.STATUS_NO_LINKS_FOUND;
                mCallback.onLinkify(mText, mStatus);
                return;
            }

            final Spannable text = mTextSupplier.get();
            if (mParams.canApply(text, links)) {
                // Remove old spans only for the part of the text we generated links for.
                final TextLinks.TextLinkSpan[] old =
                        text.getSpans(0, mTruncatedText.length(), TextLinks.TextLinkSpan.class);
                for (int i = old.length - 1; i >= 0; i--) {
                    text.removeSpan(old[i]);
                }
            }
            mStatus = mParams.apply(text, links);
            mCallback.onLinkify(text, mStatus);
        }

        @Override
        public void onCancel() {
            cancel(true);
        }
    }
}
