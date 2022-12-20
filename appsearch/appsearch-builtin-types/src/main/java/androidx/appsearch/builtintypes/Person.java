/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.appsearch.builtintypes;

import android.net.Uri;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.appsearch.annotation.Document;
import androidx.appsearch.app.AppSearchSchema.StringPropertyConfig;
import androidx.appsearch.app.AppSearchSession;
import androidx.core.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AppSearch document representing a Person entity modeled after
 * <a href="http://schema.org/Person">Person</a>.
 *
 * <p>The {@link Person} document includes commonly searchable properties such as name,
 * organization, and notes, as well as contact information such as phone numbers, email
 * addresses, etc, grouped by their label. The labeled contact information is present in a nested
 * {@link ContactPoint} document.
 */
@Document(name = "builtin:Person")
public class Person {
    /** Holds type information for additional names for Person. */
    public static class AdditionalName {
        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        @IntDef({
                TYPE_UNKNOWN,
                TYPE_NICKNAME,
                TYPE_PHONETIC_NAME
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface NameType {
        }

        /** The additional name is unknown. */
        public static final int TYPE_UNKNOWN = 0;
        /** The additional name is a nickname. */
        public static final int TYPE_NICKNAME = 1;
        /** The additional name is a phonetic name. */
        public static final int TYPE_PHONETIC_NAME = 2;

        @NameType
        private final int mType;
        private final String mValue;

        /**
         * Constructs an {@link AdditionalName}.
         */
        public AdditionalName(@NameType int type,
                @NonNull String value) {
            mType = Preconditions.checkArgumentInRange(type, TYPE_UNKNOWN, TYPE_PHONETIC_NAME,
                    "type");
            mValue = value;
        }

        @NameType
        public int getType() {
            return mType;
        }

        @NonNull
        public String getValue() {
            return mValue;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof AdditionalName)) {
                return false;
            }

            return mType == ((AdditionalName) other).mType && mValue.equals(
                    ((AdditionalName) other).mValue);
        }

        @Override
        public int hashCode() {
            String str = mType + mValue;
            return str.hashCode();
        }
    }

    @Document.Namespace
    private final String mNamespace;

    @Document.Id
    private final String mId;

    @Document.Score
    private final int mDocumentScore;

    @Document.CreationTimestampMillis
    private final long mCreationTimestampMillis;

    @Document.TtlMillis
    private final long mDocumentTtlMillis;

    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_PREFIXES)
    private final String mName;

    @Document.StringProperty
    private final String mGivenName;

    @Document.StringProperty
    private final String mMiddleName;

    @Document.StringProperty
    private final String mFamilyName;

    @Document.StringProperty
    final String mExternalUri;

    @Document.StringProperty
    final String mImageUri;

    @Document.BooleanProperty
    final boolean mIsImportant;

    @Document.BooleanProperty
    final boolean mIsBot;

    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_PREFIXES)
    private final List<String> mNotes;

    @Document.LongProperty
    final List<Long> mAdditionalNameTypes;

    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_PREFIXES)
    final List<String> mAdditionalNames;

    @Document.StringProperty(indexingType = StringPropertyConfig.INDEXING_TYPE_PREFIXES)
    private final List<String> mAffiliations;

    @Document.StringProperty
    private final List<String> mRelations;

    @Document.DocumentProperty(indexNestedProperties = true)
    private final List<ContactPoint> mContactPoints;

    private final List<AdditionalName> mTypedAdditionalNames;

    Person(@NonNull String namespace,
            @NonNull String id,
            int documentScore,
            long creationTimestampMillis,
            long documentTtlMillis,
            @NonNull String name,
            @Nullable String givenName,
            @Nullable String middleName,
            @Nullable String familyName,
            @Nullable String externalUri,
            @Nullable String imageUri,
            boolean isImportant,
            boolean isBot,
            @NonNull List<String> notes,
            @NonNull @AdditionalName.NameType List<Long> additionalNameTypes,
            @NonNull List<String> additionalNames,
            @NonNull List<String> affiliations,
            @NonNull List<String> relations,
            @NonNull List<ContactPoint> contactPoints) {
        mNamespace = namespace;
        mId = id;
        mDocumentScore = documentScore;
        mCreationTimestampMillis = creationTimestampMillis;
        mDocumentTtlMillis = documentTtlMillis;
        mName = name;
        mGivenName = givenName;
        mMiddleName = middleName;
        mFamilyName = familyName;
        mExternalUri = externalUri;
        mImageUri = imageUri;
        mIsImportant = isImportant;
        mIsBot = isBot;
        mNotes = Collections.unmodifiableList(notes);
        mAdditionalNameTypes = Collections.unmodifiableList(additionalNameTypes);
        mAdditionalNames = Collections.unmodifiableList(additionalNames);
        mAffiliations = Collections.unmodifiableList(affiliations);
        mRelations = Collections.unmodifiableList(relations);
        mContactPoints = Collections.unmodifiableList(contactPoints);

        // For the additionalNames to to returned in the getter.
        List<AdditionalName> names = new ArrayList<>(mAdditionalNameTypes.size());
        for (int i = 0; i < mAdditionalNameTypes.size(); ++i) {
            names.add(new AdditionalName(mAdditionalNameTypes.get(i).intValue(),
                    mAdditionalNames.get(i)));
        }
        mTypedAdditionalNames = Collections.unmodifiableList(names);
    }

    /** Returns the namespace of this {@link Person}. */
    @NonNull
    public String getNamespace() {
        return mNamespace;
    }

    /** Returns the unique identifier for this {@link Person} within its namespace. */
    @NonNull
    public String getId() {
        return mId;
    }

    /**
     * Returns the user-provided opaque document score of the {@link Person}, which can be
     * used for ranking using
     * {@link androidx.appsearch.app.SearchSpec.RankingStrategy#RANKING_STRATEGY_DOCUMENT_SCORE}.
     */
    public int getDocumentScore() {
        return mDocumentScore;
    }

    /**
     * Returns the creation timestamp for the current AppSearch entity, in milliseconds using the
     * {@link System#currentTimeMillis()} time base.
     *
     * <p>This timestamp refers to the creation time of the AppSearch entity, not when the
     * document is written into AppSearch.
     *
     * <p>See {@link androidx.appsearch.annotation.Document.CreationTimestampMillis} for more
     * information on creation timestamp.
     */
    public long getCreationTimestampMillis() {
        return mCreationTimestampMillis;
    }

    /**
     * Gets the TTL (time-to-live) of the {@link Person}, in milliseconds.
     *
     * <p>The TTL is measured against {@link #getCreationTimestampMillis}. At the timestamp of
     * {@code creationTimestampMillis + ttlMillis} time, measured in the
     * {@link System#currentTimeMillis} time base, the document will be auto-deleted.
     *
     * <p>The default value is 0, which means the document is permanent and won't be
     * auto-deleted until the app is uninstalled or {@link AppSearchSession#remove} is
     * called.
     */
    public long getDocumentTtlMillis() {
        return mDocumentTtlMillis;
    }

    /**
     * Returns a full name associated with the {@link Person}.
     *
     * <p>It is a full name consisting of first, middle and family names. E.g.
     * "Larry Edward Page", or "Page, Larry Edward".
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /** Returns the given (or first) name for this {@link Person}. */
    @Nullable
    public String getGivenName() {
        return mGivenName;
    }

    /**
     * Returns the middle name(s) for this {@link Person}.
     *
     * <p>For a Person with multiple middle names, this method returns a flattened and whitespace
     * separated list. For example, "middle1 middle2 ..."
     */
    @Nullable
    public String getMiddleName() {
        return mMiddleName;
    }

    /** Returns the family (or last) name for this {@link Person}. */
    @Nullable
    public String getFamilyName() {
        return mFamilyName;
    }

    /**
     * Returns an external uri for this {@link Person}. Or {@code null} if no {@link Uri} is
     * provided. A {@link Uri} can be any of the following:
     *
     * <li>A {@link android.provider.ContactsContract.Contacts#CONTENT_LOOKUP_URI}.
     * <li>A {@code mailto:} schema*
     * <li>A {@code tel:} schema*
     *
     * <p>For mailto: and tel: URI schemes, it is recommended that the path portion
     * refers to a valid contact in the Contacts Provider.
     */
    @Nullable
    public Uri getExternalUri() {
        if (mExternalUri != null) {
            return Uri.parse(mExternalUri);
        }
        return null;
    }

    /** Returns {@link Uri} of the profile image for this {@link Person}. */
    @Nullable
    public Uri getImageUri() {
        if (mImageUri != null) {
            return Uri.parse(mImageUri);
        }
        return null;
    }

    /**
     * Returns whether this {@link Person} is important to the user of this device with
     * regards to how frequently they interact.
     */
    public boolean isImportant() {
        return mIsImportant;
    }

    /** Returns whether this {@link Person} is a machine rather than a human. */
    public boolean isBot() {
        return mIsBot;
    }

    /** Returns the notes about this {@link Person}. */
    @NonNull
    public List<String> getNotes() {
        return mNotes;
    }

    /**
     * Returns a list of additional names for this {@link Person}.
     *
     * <p>Additional names can include something like phonetic names, or nicknames.
     *
     * <p>Different from {@link #getTypedAdditionalNames()}, the return value doesn't include
     * type information for the additional names.
     */
    @NonNull
    public List<String> getAdditionalNames() {
        return mAdditionalNames;
    }

    /**
     * Returns a list of {@link AdditionalName} for this {@link Person}.
     *
     * <p>Additional names can include something like phonetic names, or nicknames.
     *
     * <p>Each {@link AdditionalName} contains type information for the additional name.
     */
    @NonNull
    public List<AdditionalName> getTypedAdditionalNames() {
        return mTypedAdditionalNames;
    }

    /**
     * Returns a list of affiliation for this {@link Person}. Like company, school, etc.
     *
     * <p>For a contact with the title "Software Engineer" in a department "Engineering" at a
     * company "Cloud Company", this can include a flattened value of "Software Engineer,
     * Engineering, Cloud Company".
     */
    @NonNull
    public List<String> getAffiliations() {
        return mAffiliations;
    }

    /** Returns a list of relations for this {@link Person}, like "Father" or "Mother". */
    @NonNull
    public List<String> getRelations() {
        return mRelations;
    }

    /**
     * Returns a list of {@link ContactPoint}.
     *
     * <p>More information can be found in {@link ContactPoint}.
     */
    @NonNull
    public List<ContactPoint> getContactPoints() {
        return mContactPoints;
    }

    /** Builder class for {@link Person}. */
    public static final class Builder extends BaseBuiltinTypeBuilder<Builder> {
        private String mName;
        private String mGivenName;
        private String mMiddleName;
        private String mFamilyName;
        private Uri mExternalUri;
        private Uri mImageUri;
        boolean mIsImportant;
        boolean mIsBot;
        // Make sure the lists are not null.
        private List<String> mNotes = Collections.emptyList();
        @AdditionalName.NameType
        private List<Long> mAdditionalNameTypes = Collections.emptyList();
        private List<String> mAdditionalNames = Collections.emptyList();
        private List<String> mAffiliations = Collections.emptyList();
        private List<String> mRelations = Collections.emptyList();
        private List<ContactPoint> mContactPoints = Collections.emptyList();

        /**
         * Constructor for {@link Person.Builder}.
         *
         * @param namespace Namespace for the {@link Person} Document. See
         *                  {@link Document.Namespace}.
         * @param id        Unique identifier for the {@link Person} Document. See
         *                  {@link Document.Id}.
         * @param name      The searchable full name of this {@link Person}. E.g. "Larry Page", or
         *                  "Page, Larry".
         */
        public Builder(@NonNull String namespace, @NonNull String id, @NonNull String name) {
            super(namespace, id);
            mName = Preconditions.checkNotNull(name);
        }

        /**
         * Constructor for {@link Builder} with all the existing values of an {@link Person}.
         */
        public Builder(@NonNull Person person) {
            this(Preconditions.checkNotNull(person).getNamespace(),
                    person.getId(),
                    person.getName());
            mDocumentScore = person.getDocumentScore();
            mCreationTimestampMillis =
                    person.getCreationTimestampMillis();
            mDocumentTtlMillis = person.getDocumentTtlMillis();
            mGivenName = person.getGivenName();
            mMiddleName = person.getMiddleName();
            mFamilyName = person.getFamilyName();
            mExternalUri = person.getExternalUri();
            mImageUri = person.getImageUri();
            mIsImportant = person.isImportant();
            mIsBot = person.isBot();
            mNotes = person.getNotes();
            mAffiliations = person.getAffiliations();
            mRelations = person.getRelations();
            mContactPoints = person.getContactPoints();
            setAdditionalNames(person.getTypedAdditionalNames());
        }

        /** Sets the given name of this {@link Person}. */
        @NonNull
        public Builder setGivenName(@NonNull String givenName) {
            mGivenName = Preconditions.checkNotNull(givenName);
            return this;
        }

        /**
         * Sets the middle name of this {@link Person}.
         *
         * <p>For {@link Person} with multiple middle names, they can all be set in this
         * single string. Each middle name could be separated by a whitespace like "middleName1
         * middleName2 middleName3".
         */
        @NonNull
        public Builder setMiddleName(@NonNull String middleName) {
            mMiddleName = Preconditions.checkNotNull(middleName);
            return this;
        }

        /** Sets the family name of this {@link Person}. */
        @NonNull
        public Builder setFamilyName(@NonNull String familyName) {
            mFamilyName = Preconditions.checkNotNull(familyName);
            return this;
        }

        /**
         * Sets an external {@link Uri} for this {@link Person}. Or {@code null} if no
         * {@link Uri} is provided. A {@link Uri} can be any of the following:
         *
         * <li>A {@link android.provider.ContactsContract.Contacts#CONTENT_LOOKUP_URI}.
         * <li>A {@code mailto:} schema*
         * <li>A {@code tel:} schema*
         *
         * <p>For mailto: and tel: URI schemes, it is recommended that the path
         * portion refers to a valid contact in the Contacts Provider.
         */
        @NonNull
        public Builder setExternalUri(@NonNull Uri externalUri) {
            mExternalUri = Preconditions.checkNotNull(externalUri);
            return this;
        }

        /** Sets the {@link Uri} of the profile image for the {@link Person}. */
        @NonNull
        public Builder setImageUri(@NonNull Uri imageUri) {
            mImageUri = Preconditions.checkNotNull(imageUri);
            return this;
        }

        /** Sets whether this {@link Person} is important. */
        @NonNull
        public Builder setImportant(boolean isImportant) {
            mIsImportant = isImportant;
            return this;
        }

        /** Sets whether this {@link Person} is a bot. */
        @NonNull
        public Builder setBot(boolean isBot) {
            mIsBot = isBot;
            return this;
        }

        /** Sets the notes about this {@link Person}. */
        @NonNull
        public Builder setNotes(@NonNull List<String> notes) {
            mNotes = Preconditions.checkNotNull(notes);
            return this;
        }

        /**
         * Sets a list of {@link AdditionalName} for that {@link Person}.
         *
         * <p>Only types defined in {@link AdditionalName.NameType} are accepted.
         */
        @NonNull
        public Builder setAdditionalNames(@NonNull List<AdditionalName> additionalNames) {
            Preconditions.checkNotNull(additionalNames);
            int size = additionalNames.size();
            mAdditionalNameTypes = new ArrayList<>(size);
            mAdditionalNames = new ArrayList<>(size);
            for (int i = 0; i < additionalNames.size(); ++i) {
                long type = Preconditions.checkArgumentInRange(additionalNames.get(i).getType(),
                        AdditionalName.TYPE_UNKNOWN,
                        AdditionalName.TYPE_PHONETIC_NAME,
                        "type");
                mAdditionalNameTypes.add(type);
                mAdditionalNames.add(additionalNames.get(i).getValue());
            }
            return this;
        }

        /**
         * Sets a list of affiliations for this {@link Person}. Like company, school,
         * etc.
         */
        @NonNull
        public Builder setAffiliations(@NonNull List<String> affiliations) {
            mAffiliations = Preconditions.checkNotNull(affiliations);
            return this;
        }

        /** Sets a list of relations for this {@link Person}, like "Father" or "Mother". */
        @NonNull
        public Builder setRelations(@NonNull List<String> relations) {
            mRelations = Preconditions.checkNotNull(relations);
            return this;
        }

        /**
         * Sets a list of {@link ContactPoint} for the {@link Person}.
         *
         * <p>More information could be found in {@link ContactPoint}.
         */
        @NonNull
        public Builder setContactPoints(@NonNull List<ContactPoint> contactPoints) {
            mContactPoints = Preconditions.checkNotNull(contactPoints);
            return this;
        }

        /** Builds the {@link Person}. */
        @NonNull
        public Person build() {
            Preconditions.checkState(mAdditionalNameTypes.size() == mAdditionalNames.size());
            return new Person(
                    /*namespace=*/ mNamespace,
                    /*id=*/ mId,
                    /*documentScore=*/ mDocumentScore,
                    /*creationTimestampMillis=*/ mCreationTimestampMillis,
                    /*documentTtlMillis=*/ mDocumentTtlMillis,
                    /*name=*/ mName,
                    /*givenName=*/ mGivenName,
                    /*middleName=*/ mMiddleName,
                    /*familyName=*/ mFamilyName,
                    /*externalUri=*/ mExternalUri != null
                    ? mExternalUri.toString() : null,
                    /*imageUri=*/ mImageUri != null
                    ? mImageUri.toString() : null,
                    /*isImportant=*/ mIsImportant,
                    /*isBot=*/ mIsBot,
                    /*notes=*/ new ArrayList<>(mNotes),
                    /*additionalNameTypes=*/ new ArrayList<>(mAdditionalNameTypes),
                    /*additionalNames=*/ new ArrayList<>(mAdditionalNames),
                    /*affiliations=*/ new ArrayList<>(mAffiliations),
                    /*relations=*/ new ArrayList<>(mRelations),
                    /*contactPoints=*/ new ArrayList<>(mContactPoints));
        }
    }
}
