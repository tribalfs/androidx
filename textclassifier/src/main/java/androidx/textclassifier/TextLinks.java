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

import static androidx.textclassifier.ConvertUtils.toPlatformEntityConfig;
import static androidx.textclassifier.ConvertUtils.unwrapLocalListCompat;

import android.app.PendingIntent;
import android.os.Bundle;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.core.os.LocaleListCompat;
import androidx.core.util.Preconditions;
import androidx.textclassifier.TextClassifier.EntityConfig;
import androidx.textclassifier.TextClassifier.EntityType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A collection of links, representing subsequences of text and the entity types (phone number,
 * address, url, etc) they may be.
 */
public final class TextLinks {

    private static final String LOG_TAG = "TextLinks";

    private static final String EXTRA_FULL_TEXT = "text";
    private static final String EXTRA_LINKS = "links";

    private final CharSequence mFullText;
    private final List<TextLink> mLinks;

    static final Executor sWorkerExecutor = Executors.newFixedThreadPool(1);
    static final MainThreadExecutor sMainThreadExecutor = new MainThreadExecutor();

    /** Status unknown. */
    public static final int STATUS_UNKNOWN = -1;
    /** Links were successfully applied to the text. */
    public static final int STATUS_LINKS_APPLIED = 0;
    /** No links exist to apply to text. Links count is zero. */
    public static final int STATUS_NO_LINKS_FOUND = 1;
    /** No links applied to text. The links were filtered out. */
    public static final int STATUS_NO_LINKS_APPLIED = 2;
    /** The specified text does not match the text used to generate the links. */
    public static final int STATUS_DIFFERENT_TEXT = 3;

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            STATUS_UNKNOWN,
            STATUS_LINKS_APPLIED,
            STATUS_NO_LINKS_FOUND,
            STATUS_NO_LINKS_APPLIED,
            STATUS_DIFFERENT_TEXT
    })
    public @interface Status {}

    /** Do not replace {@link ClickableSpan}s that exist where the {@link TextLinkSpan} needs to
     * be applied to. Do not apply the TextLinkSpan. **/
    public static final int APPLY_STRATEGY_IGNORE = 0;
    /** Replace any {@link ClickableSpan}s that exist where the {@link TextLinkSpan} needs to be
     * applied to. **/
    public static final int APPLY_STRATEGY_REPLACE = 1;

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({APPLY_STRATEGY_IGNORE, APPLY_STRATEGY_REPLACE})
    public @interface ApplyStrategy {}

    TextLinks(CharSequence fullText, List<TextLink> links) {
        mFullText = fullText;
        mLinks = Collections.unmodifiableList(links);
    }

    /**
     * Returns the text that was used to generate these links.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @NonNull
    public CharSequence getText() {
        return mFullText;
    }

    /**
     * Returns an unmodifiable Collection of the links.
     */
    @NonNull
    public Collection<TextLink> getLinks() {
        return mLinks;
    }

    @Override
    @NonNull
    public String toString() {
        return String.format(Locale.US, "TextLinks{fullText=%s, links=%s}", mFullText, mLinks);
    }

    /**
     * Adds a TextLinks object to a Bundle that can be read back with the same parameters
     * to {@link #createFromBundle(Bundle)}.
     */
    @NonNull
    public Bundle toBundle() {
        final Bundle bundle = new Bundle();
        bundle.putString(EXTRA_FULL_TEXT, mFullText.toString());
        BundleUtils.putTextLinkList(bundle, EXTRA_LINKS, mLinks);
        return bundle;
    }

    /**
     * Extracts an TextLinks object from a bundle that was added using {@link #toBundle()}.
     */
    @NonNull
    public static TextLinks createFromBundle(@NonNull Bundle bundle) {
        return new TextLinks(
                bundle.getString(EXTRA_FULL_TEXT),
                BundleUtils.getTextLinkListOrThrow(bundle, EXTRA_LINKS));
    }

    /**
     * A link, identifying a substring of text and possible entity types for it.
     */
    public static final class TextLink {

        private static final String EXTRA_ENTITY_SCORES = "scores";
        private static final String EXTRA_START = "start";
        private static final String EXTRA_END = "end";

        private final EntityConfidence mEntityScores;
        private final int mStart;
        private final int mEnd;
        // Allows us to fallback to legacy Linkify if necessary. Not parcelled.
        @Nullable private final URLSpan mUrlSpan;

        /**
         * Create a new TextLink.
         *
         * @throws IllegalArgumentException if entityScores is null or empty.
         * @hide
         */
        @VisibleForTesting
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        TextLink(
                int start, int end,
                @NonNull Map<String, Float> entityScores, @Nullable URLSpan urlSpan) {
            Preconditions.checkNotNull(entityScores);
            Preconditions.checkArgument(!entityScores.isEmpty());
            Preconditions.checkArgument(start <= end);
            mStart = start;
            mEnd = end;
            mEntityScores = new EntityConfidence(entityScores);
            mUrlSpan = urlSpan;
        }

        /**
         * Returns the start index of this link in the original text.
         *
         * @return the start index.
         */
        public int getStart() {
            return mStart;
        }

        /**
         * Returns the end index of this link in the original text.
         *
         * @return the end index.
         */
        public int getEnd() {
            return mEnd;
        }

        /**
         * Returns the number of entity types that have confidence scores.
         *
         * @return the entity count.
         */
        public int getEntityCount() {
            return mEntityScores.getEntities().size();
        }

        /**
         * Returns the entity type at a given index. Entity types are sorted by confidence.
         *
         * @return the entity type at the provided index.
         */
        @NonNull public @EntityType String getEntity(int index) {
            return mEntityScores.getEntities().get(index);
        }

        /**
         * Returns the confidence score for a particular entity type.
         *
         * @param entityType the entity type.
         */
        public @FloatRange(from = 0.0, to = 1.0) float getConfidenceScore(
                @EntityType String entityType) {
            return mEntityScores.getConfidenceScore(entityType);
        }

        /**
         * @hide
         */
        @Nullable
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        public URLSpan getUrlSpan() {
            return mUrlSpan;
        }

        @Override
        @NonNull
        public String toString() {
            return String.format(Locale.US,
                    "TextLink{start=%s, end=%s, entityScores=%s, urlSpan=%s}",
                    mStart, mEnd, mEntityScores, mUrlSpan);
        }

        /**
         * Adds this TextLink to a Bundle that can be read back with the same parameters
         * to {@link #createFromBundle(Bundle)}.
         */
        @NonNull
        public Bundle toBundle() {
            final Bundle bundle = new Bundle();
            BundleUtils.putMap(bundle, EXTRA_ENTITY_SCORES, mEntityScores.getConfidenceMap());
            bundle.putInt(EXTRA_START, mStart);
            bundle.putInt(EXTRA_END, mEnd);
            return bundle;
        }

        /**
         * Extracts a TextLink from a bundle that was added using {@link #toBundle()}.
         */
        @NonNull
        public static TextLink createFromBundle(@NonNull Bundle bundle) {
            return new TextLink(
                    bundle.getInt(EXTRA_START),
                    bundle.getInt(EXTRA_END),
                    BundleUtils.getFloatStringMapOrThrow(bundle, EXTRA_ENTITY_SCORES),
                    null /* urlSpan */);
        }
    }

    /**
     * A request object for generating TextLinks.
     */
    public static final class Request {

        private static final String EXTRA_TEXT = "text";
        private static final String EXTRA_DEFAULT_LOCALES = "locales";
        private static final String EXTRA_ENTITY_CONFIG = "entity_config";

        private final CharSequence mText;
        @Nullable private final LocaleListCompat mDefaultLocales;
        @NonNull private final EntityConfig mEntityConfig;

        Request(
                @NonNull CharSequence text,
                @Nullable LocaleListCompat defaultLocales,
                @Nullable EntityConfig entityConfig) {
            mText = text;
            mDefaultLocales = defaultLocales;
            mEntityConfig = entityConfig == null
                    ? new TextClassifier.EntityConfig.Builder().build()
                    : entityConfig;
        }

        /**
         * Returns the text to generate links for.
         */
        @NonNull
        public CharSequence getText() {
            return mText;
        }

        /**
         * @return ordered list of locale preferences that can be used to disambiguate
         *      the provided text
         */
        @Nullable
        public LocaleListCompat getDefaultLocales() {
            return mDefaultLocales;
        }

        /**
         * @return The config representing the set of entities to look for
         * @see Builder#setEntityConfig(EntityConfig)
         */
        @NonNull
        public EntityConfig getEntityConfig() {
            return mEntityConfig;
        }

        /**
         * A builder for building TextLinks requests.
         */
        public static final class Builder {

            private final CharSequence mText;

            @Nullable private LocaleListCompat mDefaultLocales;
            @Nullable private EntityConfig mEntityConfig;

            public Builder(@NonNull CharSequence text) {
                mText = Preconditions.checkNotNull(text);
            }

            /**
             * @param defaultLocales ordered list of locale preferences that may be used to
             *                       disambiguate the provided text. If no locale preferences exist,
             *                       set this to null or an empty locale list.
             * @return this builder
             */
            @NonNull
            public Builder setDefaultLocales(@Nullable LocaleListCompat defaultLocales) {
                mDefaultLocales = defaultLocales;
                return this;
            }

            /**
             * Sets the entity configuration to use. This determines what types of entities the
             * TextClassifier will look for.
             * Set to {@code null} for the default entity config and the TextClassifier will
             * automatically determine what links to generate.
             *
             * @return this builder
             */
            @NonNull
            public Builder setEntityConfig(@Nullable EntityConfig entityConfig) {
                mEntityConfig = entityConfig;
                return this;
            }

            /**
             * Builds and returns the request object.
             */
            @NonNull
            public Request build() {
                return new Request(mText, mDefaultLocales, mEntityConfig);
            }

        }

        /**
         * Adds this Request to a Bundle that can be read back with the same parameters
         * to {@link #createFromBundle(Bundle)}.
         */
        @NonNull
        public Bundle toBundle() {
            final Bundle bundle = new Bundle();
            bundle.putCharSequence(EXTRA_TEXT, mText);
            bundle.putBundle(EXTRA_ENTITY_CONFIG, mEntityConfig.toBundle());
            BundleUtils.putLocaleList(bundle, EXTRA_DEFAULT_LOCALES, mDefaultLocales);
            return bundle;
        }

        /**
         * Extracts a Request from a bundle that was added using {@link #toBundle()}.
         */
        @NonNull
        public static Request createFromBundle(@NonNull Bundle bundle) {
            return new Builder(bundle.getCharSequence(EXTRA_TEXT))
                    .setDefaultLocales(BundleUtils.getLocaleList(bundle, EXTRA_DEFAULT_LOCALES))
                    .setEntityConfig(
                            EntityConfig.createFromBundle(bundle.getBundle(EXTRA_ENTITY_CONFIG)))
                    .build();
        }

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        @RequiresApi(28)
        @NonNull
        android.view.textclassifier.TextLinks.Request toPlatform() {
            return new android.view.textclassifier.TextLinks.Request.Builder(getText())
                    .setDefaultLocales(unwrapLocalListCompat(getDefaultLocales()))
                    .setEntityConfig(toPlatformEntityConfig(getEntityConfig()))
                    .build();
        }
    }

    /**
     * A function to create spans from TextLinks.
     *
     * Hidden until we convinced we want it to be part of the public API.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public interface SpanFactory {

        /** Creates a span from a text link. */
        TextLinkSpan createSpan(@NonNull TextLinkSpanData textLinkSpanData);
    }

    /**
     * Contains necessary data for {@link TextLinkSpan}.
     */
    public static class TextLinkSpanData {
        @NonNull
        private final TextLink mTextLink;
        @NonNull
        private final TextClassifier mTextClassifier;
        @Nullable
        private final Calendar mReferenceTime;

        /**
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        TextLinkSpanData(
                @NonNull TextLink textLink,
                @NonNull TextClassifier textClassifier,
                @Nullable Calendar referenceTime) {
            mTextLink = Preconditions.checkNotNull(textLink);
            mTextClassifier = Preconditions.checkNotNull(textClassifier);
            mReferenceTime = referenceTime;
        }

        @NonNull
        public TextLink getTextLink() {
            return mTextLink;
        }

        /**
         * TODO: Make it public once we confirm how should we represent a datetime.
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        public Calendar getReferenceTime() {
            return mReferenceTime;
        }

        /**
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        TextClassifier getTextClassifier() {
            return mTextClassifier;
        }
    }

    /**
     * A ClickableSpan for a TextLink.
     */
    public static class TextLinkSpan extends ClickableSpan {

        @NonNull
        private final TextLinkSpanData mTextLinkSpanData;

        public TextLinkSpan(@NonNull TextLinkSpanData textLinkSpanData) {
            mTextLinkSpanData = Preconditions.checkNotNull(textLinkSpanData);
        }

        @Override
        public void onClick(View widget) {
            if (!(widget instanceof TextView)) {
                return;
            }

            final TextView textView = (TextView) widget;
            final CharSequence text = textView.getText();

            if (!(text instanceof Spanned)) {
                return;
            }
            final Spanned spanned = (Spanned) text;
            final int start = spanned.getSpanStart(this);
            final int end = spanned.getSpanEnd(this);
            final TextClassification.Request request =
                    new TextClassification.Request.Builder(text, start, end)
                            .setReferenceTime(mTextLinkSpanData.getReferenceTime())
                            .setDefaultLocales(getLocales(textView))
                            .build();
            final TextClassifier classifier = mTextLinkSpanData.getTextClassifier();

            // TODO: Truncate the text.
            sWorkerExecutor.execute(new Runnable() {
                @Override
                public void run() {

                    final TextClassification classification = classifier.classifyText(request);
                    sMainThreadExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (!classification.getActions().isEmpty()) {
                                // TODO: Show the toolbar instead.
                                try {
                                    classification.getActions().get(0).getActionIntent().send();
                                } catch (PendingIntent.CanceledException e) {
                                    Log.e(LOG_TAG, "Error handling TextLinkSpan click", e);
                                }
                            }
                        }
                    });
                }
            });
        }

        private LocaleListCompat getLocales(TextView textView) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                return LocaleListCompat.wrap(textView.getTextLocales());
            } else {
                return LocaleListCompat.create(textView.getTextLocale());
            }
        }

        @NonNull
        public final TextLinkSpanData getTextLinkSpanData() {
            return mTextLinkSpanData;
        }
    }

    /**
     * A builder to construct a TextLinks instance.
     */
    public static final class Builder {
        private final CharSequence mFullText;
        private final ArrayList<TextLink> mLinks;

        /**
         * Create a new TextLinks.Builder.
         *
         * @param fullText The full text to annotate with links.
         */
        // TODO: Change API to take a CharSequence instead.
        public Builder(@NonNull String fullText) {
            mFullText = Preconditions.checkNotNull(fullText);
            mLinks = new ArrayList<>();
        }

        /**
         * Adds a TextLink.
         *
         * @return this instance.
         *
         * @throws IllegalArgumentException if entityScores is null or empty.
         */
        @NonNull
        public Builder addLink(int start, int end, @NonNull Map<String, Float> entityScores) {
            mLinks.add(new TextLink(start, end, Preconditions.checkNotNull(entityScores), null));
            return this;
        }

        /**
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        public Builder addLink(
                int start, int end, @NonNull Map<String, Float> entityScores,
                @Nullable URLSpan urlSpan) {
            mLinks.add(new TextLink(start, end, Preconditions.checkNotNull(entityScores), urlSpan));
            return this;
        }

        @NonNull
        Builder addLink(TextLink link) {
            mLinks.add(Preconditions.checkNotNull(link));
            return this;
        }

        /**
         * Removes all {@link TextLink}s.
         */
        // TODO: Hide.
        @NonNull
        public Builder clearTextLinks() {
            mLinks.clear();
            return this;
        }

        /**
         * Constructs a TextLinks instance.
         *
         * @return the constructed TextLinks.
         */
        @NonNull
        public TextLinks build() {
            return new TextLinks(mFullText, mLinks);
        }
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @RequiresApi(28)
    @NonNull
    // TODO: In Q, we should make getText public and use it here.
    static TextLinks fromPlatform(
            @NonNull android.view.textclassifier.TextLinks textLinks,
            @NonNull CharSequence requestText) {
        Preconditions.checkNotNull(textLinks);
        Preconditions.checkNotNull(requestText);

        Collection<android.view.textclassifier.TextLinks.TextLink> links = textLinks.getLinks();
        TextLinks.Builder builder = new TextLinks.Builder(requestText.toString());
        for (android.view.textclassifier.TextLinks.TextLink link : links) {
            builder.addLink(link.getStart(), link.getEnd(),
                    ConvertUtils.createFloatMapFromTextLinks(link));
        }
        return builder.build();
    }
}
