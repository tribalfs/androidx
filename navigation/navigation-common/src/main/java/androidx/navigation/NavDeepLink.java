/*
 * Copyright (C) 2017 The Android Open Source Project
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

package androidx.navigation;

import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NavDeepLink encapsulates the parsing and matching of a navigation deep link.
 *
 * This should be added to a {@link NavDestination} using
 * {@link NavDestination#addDeepLink(NavDeepLink)}.
 */
public final class NavDeepLink {
    private static final Pattern SCHEME_PATTERN = Pattern.compile("^[a-zA-Z]+[+\\w\\-.]*:");

    private final ArrayList<String> mArguments = new ArrayList<>();
    private final Map<String, ParamQuery> mParamArgMap = new HashMap<>();

    private Pattern mPattern = null;
    private boolean mExactDeepLink = false;
    private boolean mIsParameterizedQuery = false;

    private final String mUri;
    private final String mAction;

    private Pattern mMimeTypePattern = null;
    private final String mMimeType;

    NavDeepLink(@Nullable String uri, @Nullable String action, @Nullable String mimeType) {
        mUri = uri;
        mAction = action;
        mMimeType = mimeType;
        if (uri != null) {
            Uri parameterizedUri = Uri.parse(uri);
            mIsParameterizedQuery = parameterizedUri.getQuery() != null;
            StringBuilder uriRegex = new StringBuilder("^");

            if (!SCHEME_PATTERN.matcher(uri).find()) {
                uriRegex.append("http[s]?://");
            }
            Pattern fillInPattern = Pattern.compile("\\{(.+?)\\}");
            if (mIsParameterizedQuery) {
                Matcher matcher = Pattern.compile("(\\?)").matcher(uri);
                if (matcher.find()) {
                    buildPathRegex(uri.substring(0, matcher.start()), uriRegex, fillInPattern);
                }
                mExactDeepLink = false;
                for (String paramName : parameterizedUri.getQueryParameterNames()) {
                    StringBuilder argRegex = new StringBuilder();
                    String queryParam = parameterizedUri.getQueryParameter(paramName);
                    matcher = fillInPattern.matcher(queryParam);
                    int appendPos = 0;
                    ParamQuery param = new ParamQuery();
                    // Build the regex for each query param
                    while (matcher.find()) {
                        param.addArgumentName(matcher.group(1));
                        argRegex.append(Pattern.quote(queryParam.substring(appendPos,
                                matcher.start())));
                        argRegex.append("(.+?)?");
                        appendPos = matcher.end();
                    }
                    if (appendPos < queryParam.length()) {
                        argRegex.append(Pattern.quote(queryParam.substring(appendPos)));
                    }
                    // Save the regex with wildcards unquoted, and add the param to the map with its
                    // name as the key
                    param.setParamRegex(argRegex.toString().replace(".*", "\\E.*\\Q"));
                    mParamArgMap.put(paramName, param);
                }
            } else {
                mExactDeepLink = buildPathRegex(uri, uriRegex, fillInPattern);
            }
            // Since we've used Pattern.quote() above, we need to
            // specifically escape any .* instances to ensure
            // they are still treated as wildcards in our final regex
            String finalRegex = uriRegex.toString().replace(".*", "\\E.*\\Q");
            mPattern = Pattern.compile(finalRegex);
        }

        if (mimeType != null) {
            Pattern mimeTypePattern = Pattern.compile("^[\\s\\S]+/[\\s\\S]+$");
            Matcher mimeTypeMatcher = mimeTypePattern.matcher(mimeType);

            if (!mimeTypeMatcher.matches()) {
                throw new IllegalArgumentException("The given mimeType " + mimeType + " does "
                        + "not match to required \"type/subtype\" format");
            }

            // get the type and subtype of the mimeType
            MimeType splitMimeType = new MimeType(mimeType);

            // the matching pattern can have the exact name or it can be wildcard literal (*)
            String mimeTypeRegex =
                    "^(" + splitMimeType.mType + "|[*]+)/(" + splitMimeType.mSubType + "|[*]+)$";

            // if the deep link type or subtype is wildcard, allow anything
            String finalRegex = mimeTypeRegex.replace("*|[*]", "[\\s\\S]");
            mMimeTypePattern = Pattern.compile(finalRegex);
        }
    }

    NavDeepLink(@NonNull String uri) {
        this(uri, null, null);
    }

    private boolean buildPathRegex(@NonNull String uri, StringBuilder uriRegex,
            Pattern fillInPattern) {
        Matcher matcher = fillInPattern.matcher(uri);
        int appendPos = 0;
        // Track whether this is an exact deep link
        boolean exactDeepLink = !uri.contains(".*");
        while (matcher.find()) {
            String argName = matcher.group(1);
            mArguments.add(argName);
            // Use Pattern.quote() to treat the input string as a literal
            uriRegex.append(Pattern.quote(uri.substring(appendPos, matcher.start())));
            uriRegex.append("(.+?)");
            appendPos = matcher.end();
            exactDeepLink = false;
        }
        if (appendPos < uri.length()) {
            // Use Pattern.quote() to treat the input string as a literal
            uriRegex.append(Pattern.quote(uri.substring(appendPos)));
        }
        // Match either the end of string if all params are optional or match the
        // question mark and 0 or more characters after it
        // We do not use '.*' here because the finalregex would replace it with a quoted
        // version below.
        uriRegex.append("($|(\\?(.)*))");
        return exactDeepLink;
    }

    boolean matches(@NonNull Uri uri) {
        return matches(new NavDeepLinkRequest(uri, null, null));
    }

    boolean matches(@NonNull NavDeepLinkRequest deepLinkRequest) {
        if (!matchUri(deepLinkRequest.getUri())) {
            return false;
        }

        if (!matchAction(deepLinkRequest.getAction())) {
            return false;
        }

        return matchMimeType(deepLinkRequest.getMimeType());
    }

    private boolean matchUri(Uri uri) {
        // If the null status of both are not the same return false.
        if ((uri == null) == (mPattern != null)) {
            return false;
        }
        // If both are null return true, otherwise see if they match
        return uri == null || mPattern.matcher(uri.toString()).matches();
    }

    private boolean matchAction(String action) {
        // If the null status of both are not the same return false.
        if ((action == null) == (mAction != null)) {
            return false;
        }
        // If both are null return true, otherwise see if they match
        return action == null || mAction.equals(action);
    }

    private boolean matchMimeType(String mimeType) {
        // If the null status of both are not the same return false.
        if ((mimeType == null) == (mMimeType != null)) {
            return false;
        }

        // If both are null return true, otherwise see if they match
        return mimeType == null || mMimeTypePattern.matcher(mimeType).matches();
    }


    boolean isExactDeepLink() {
        return mExactDeepLink;
    }

    /**
     * Get the uri pattern from the NavDeepLink.
     *
     * @return the uri pattern for the deep link.
     * @see NavDeepLinkRequest#getUri()
     */
    @Nullable
    public String getUriPattern() {
        return mUri;
    }

    /**
     * Get the action from the NavDeepLink.
     *
     * @return the action for the deep link.
     * @see NavDeepLinkRequest#getAction()
     */
    @Nullable
    public String getAction() {
        return mAction;
    }

    /**
     * Get the mimeType from the NavDeepLink.
     *
     * @return the mimeType of the deep link.
     * @see NavDeepLinkRequest#getMimeType()
     */
    @Nullable
    public String getMimeType() {
        return mMimeType;
    }

    int getMimeTypeMatchRating(@NonNull String mimeType) {
        if (mMimeType == null || !mMimeTypePattern.matcher(mimeType).matches()) {
            return -1;
        }

        return new MimeType(mMimeType).compareTo(new MimeType(mimeType));
    }

    @Nullable
    Bundle getMatchingArguments(@NonNull Uri deepLink,
            @NonNull Map<String, NavArgument> arguments) {
        Matcher matcher = mPattern.matcher(deepLink.toString());
        if (!matcher.matches()) {
            return null;
        }
        Bundle bundle = new Bundle();
        int size = mArguments.size();
        for (int index = 0; index < size; index++) {
            String argumentName = mArguments.get(index);
            String value = Uri.decode(matcher.group(index + 1));
            NavArgument argument = arguments.get(argumentName);
            if (parseArgument(bundle, argumentName, value, argument)) {
                return null;
            }
        }
        if (mIsParameterizedQuery) {
            for (String paramName : mParamArgMap.keySet()) {
                Matcher argMatcher = null;
                ParamQuery storedParam = mParamArgMap.get(paramName);
                String inputParams = deepLink.getQueryParameter(paramName);
                if (inputParams != null) {
                    // Match the input arguments with the saved regex
                    argMatcher = Pattern.compile(storedParam.getParamRegex()).matcher(inputParams);
                    if (!argMatcher.matches()) {
                        return null;
                    }
                }
                // Params could have multiple arguments, we need to handle them all
                for (int index = 0; index < storedParam.size(); index++) {
                    String value = null;
                    if (argMatcher != null) {
                        value = Uri.decode(argMatcher.group(index + 1));
                    }
                    String argName = storedParam.getArgumentName(index);
                    NavArgument argument = arguments.get(argName);
                    if (value != null
                            && !value.replaceAll("[{}]", "").equals(argName)
                            && parseArgument(bundle, argName, value, argument)) {
                        return null;
                    }
                }
            }
        }
        return bundle;
    }

    private boolean parseArgument(Bundle bundle, String name, String value, NavArgument argument) {
        if (argument != null) {
            NavType<?> type = argument.getType();
            try {
                type.parseAndPut(bundle, name, value);
            } catch (IllegalArgumentException e) {
                // Failed to parse means this isn't a valid deep link
                // for the given URI - i.e., the URI contains a non-integer
                // value for an integer argument
                return true;
            }
        } else {
            bundle.putString(name, value);
        }
        return false;
    }

    /**
     * Used to maintain query parameters and the mArguments they match with.
     */
    private static class ParamQuery {
        private String mParamRegex;
        private ArrayList<String> mArguments;

        ParamQuery() {
            mArguments = new ArrayList<>();
        }

        void setParamRegex(String paramRegex) {
            this.mParamRegex = paramRegex;
        }

        String getParamRegex() {
            return mParamRegex;
        }

        void addArgumentName(String name) {
            mArguments.add(name);
        }

        String getArgumentName(int index) {
            return mArguments.get(index);
        }

        public int size() {
            return mArguments.size();
        }
    }

    private static class MimeType implements Comparable<MimeType> {
        String mType;
        String mSubType;

        MimeType(@NonNull String mimeType) {
            // Using split with a limit of -1 to avoid errorprone issues
            // https://errorprone.info/bugpattern/StringSplitter
            String[] typeAndSubType = mimeType.split("/", -1);
            mType = typeAndSubType[0];
            mSubType = typeAndSubType[1];
        }

        @Override
        public int compareTo(@NonNull MimeType o) {
            int result = 0;
            // matching just subtypes is 1
            // matching just types is 2
            // matching both is 3

            if (mType.equals(o.mType)) {
                result += 2;
            }

            if (mSubType.equals(o.mSubType)) {
                result++;
            }
            return result;
        }
    }

    /**
     * A builder for constructing {@link NavDeepLink} instances.
     */
    public static final class Builder {
        private String mUriPattern;
        private String mAction;
        private String mMimeType;

        Builder() {}

        /**
         * Creates a {@link NavDeepLink.Builder} with a set uri pattern.
         *
         * @param uriPattern The uri pattern to add to the NavDeepLink
         * @return a {@link Builder} instance
         */
        @NonNull
        public static Builder fromUriPattern(@NonNull String uriPattern) {
            Builder builder = new Builder();
            builder.setUriPattern(uriPattern);
            return builder;
        }

        /**
         * Creates a {@link NavDeepLink.Builder} with a set action.
         *
         * @throws IllegalArgumentException if the action is empty.
         *
         * @param action the intent action for the NavDeepLink
         * @return a {@link Builder} instance
         */
        @NonNull
        public static Builder fromAction(@NonNull String action) {
            // if the action given at runtime is empty we should throw
            if (action.isEmpty()) {
                throw new IllegalArgumentException("The NavDeepLink cannot have an empty action.");
            }
            Builder builder = new Builder();
            builder.setAction(action);
            return builder;
        }

        /**
         * Creates a {@link NavDeepLink.Builder} with a set mimeType.
         *
         * @param mimeType the mimeType for the NavDeepLink
         * @return a {@link Builder} instance
         */
        @NonNull
        public static Builder fromMimeType(@NonNull String mimeType) {
            Builder builder = new Builder();
            builder.setMimeType(mimeType);
            return builder;
        }

        /**
         * Set the uri pattern for the {@link NavDeepLink}.
         *
         * @param uriPattern The uri pattern to add to the NavDeepLink
         *
         * @return This builder.
         */
        @NonNull
        public Builder setUriPattern(@NonNull String uriPattern) {
            mUriPattern = uriPattern;
            return this;
        }

        /**
         * Set the action for the {@link NavDeepLink}.
         *
         * @throws IllegalArgumentException if the action is empty.
         *
         * @param action the intent action for the NavDeepLink
         *
         * @return This builder.
         */
        @NonNull
        public Builder setAction(@NonNull String action) {
            // if the action given at runtime is empty we should throw
            if (action.isEmpty()) {
                throw new IllegalArgumentException("The NavDeepLink cannot have an empty action.");
            }
            mAction = action;
            return this;
        }

        /**
         * Set the mimeType for the {@link NavDeepLink}.
         *
         * @param mimeType the mimeType for the NavDeepLink
         *
         * @return This builder.
         */
        @NonNull
        public Builder setMimeType(@NonNull String mimeType) {
            mMimeType = mimeType;
            return this;
        }

        /**
         * Build the {@link NavDeepLink} specified by this builder.
         *
         * @return the newly constructed NavDeepLink.
         */
        @NonNull
        public NavDeepLink build() {
            return new NavDeepLink(mUriPattern, mAction, mMimeType);
        }
    }
}
