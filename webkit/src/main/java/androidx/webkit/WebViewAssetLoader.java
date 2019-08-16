/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.webkit;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.webkit.internal.AssetHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to load local files including application's static assets and resources using
 * http(s):// URLs inside a {@link android.webkit.WebView} class.
 * Loading local files using web-like URLs instead of {@code "file://"} is desirable as it is
 * compatible with the Same-Origin policy.
 *
 * <p>
 * For more context about application's assets and resources and how to normally access them please
 * refer to <a href="https://developer.android.com/guide/topics/resources/providing-resources">
 * Android Developer Docs: App resources overview</a>.
 *
 * <p class='note'>
 * This class is expected to be used within
 * {@link android.webkit.WebViewClient#shouldInterceptRequest}, which is invoked on a different
 * thread than application's main thread. Although instances are themselves thread-safe (and may be
 * safely constructed on the application's main thread), exercise caution when accessing private
 * data or the view system.
 * <p>
 * Using http(s):// URLs to access local resources may conflict with a real website. This means
 * that local files should only be hosted on domains your organization owns (at paths reserved
 * for this purpose) or the default domain reserved for this: {@code appassets.androidplatform.net}.
 * <p>
 * A typical usage would be like:
 * <pre class="prettyprint">
 *     final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
 *              .addPathHandler("/assets/", new AssetsPathHandler(this))
 *              .addPathHandler("/res/", new ResourcesPathHandler(this))
 *              .build();
 *
 *     webView.setWebViewClient(new WebViewClient() {
 *         {@literal @}Override
 *         public WebResourceResponse shouldInterceptRequest(WebView view,
 *                                          WebResourceRequest request) {
 *             return assetLoader.shouldInterceptRequest(request.getUrl());
 *         }
 *     });
 *     // Assets are hosted under http(s)://appassets.androidplatform.net/assets/... .
 *     // If the application's assets are in the "main/assets" folder this will read the file
 *     // from "main/assets/www/index.html" and load it as if it were hosted on:
 *     // https://appassets.androidplatform.net/assets/www/index.html
 *     webview.loadUrl("https://appassets.androidplatform.net/assets/www/index.html");
 *
 * </pre>
 */
public final class WebViewAssetLoader {
    private static final String TAG = "WebViewAssetLoader";

    /**
     * An unused domain reserved for Android applications to intercept requests for app assets.
     * <p>
     * It is used by default unless the user specified a different domain.
     */
    public static final String DEFAULT_DOMAIN = "appassets.androidplatform.net";

    private final List<PathMatcher> mMatchers;

    /**
     * A handler that produces responses for a registered path.
     *
     * <p>
     * Implement this interface to handle other use-cases according to your app's needs.
     * <p>
     * Methods of this handler will be invoked on a background thread and care must be taken to
     * correctly synchronize access to any shared state.
     * <p>
     * On Android KitKat and above these methods may be called on more than one thread. This thread
     * may be different than the thread on which the shouldInterceptRequest method was invoked.
     * This means that on Android KitKat and above it is possible to block in this method without
     * blocking other resources from loading. The number of threads used to parallelize loading
     * is an internal implementation detail of the WebView and may change between updates which
     * means that the amount of time spent blocking in this method should be kept to an absolute
     * minimum.
     */
    public interface PathHandler {
        /**
         * Handles the requested URL by returning the appropriate response.
         *
         * @param path the suffix path to be handled.
         * @return {@link WebResourceResponse} for the requested path or {@code null} if it can't
         *                                     handle this path.
         */
        @WorkerThread
        @Nullable
        WebResourceResponse handle(@NonNull String path);
    }

    /**
     * Handler class to open a file from assets directory in the application APK.
     */
    public static final class AssetsPathHandler implements PathHandler {
        private AssetHelper mAssetHelper;

        /**
         * @param context {@link Context} used to resolve assets.
         */
        public AssetsPathHandler(@NonNull Context context) {
            mAssetHelper = new AssetHelper(context);
        }

        @VisibleForTesting
        /*package*/ AssetsPathHandler(@NonNull AssetHelper assetHelper) {
            mAssetHelper = assetHelper;
        }

        /**
         * Opens the requested file from the application's assets directory.
         * <p>
         * The matched prefix path used shouldn't be a prefix of a real web path. Thus, if the
         * requested file cannot be found a {@link WebResourceResponse} object with a {@code null}
         * {@link InputStream} will be returned instead of {@code null}. This saves the time of
         * falling back to network and trying to resolve a path that doesn't exist. A
         * {@link WebResourceResponse} with {@code null} {@link InputStream} will be received as an
         * HTTP response with status code {@code 404} and no body.
         * <p class="note">
         * The MIME type for the file will be determined from the file's extension using
         * {@link java.net.URLConnection#guessContentTypeFromName}. Developers should ensure that
         * asset files are named using standard file extensions. If the file does not have a
         * recognised extension, {@code "text/plain"} will be used by default.
         *
         * @param path the suffix path to be handled.
         * @return {@link WebResourceResponse} for the requested file.
         */
        @Override
        @WorkerThread
        @Nullable
        public WebResourceResponse handle(@NonNull String path) {
            InputStream is = mAssetHelper.openAsset(path);
            String mimeType = AssetHelper.guessMimeType(path);
            return new WebResourceResponse(mimeType, null, is);
        }
    }

    /**
     * Handler class to open a file from resources directory in the application APK.
     */
    public static final class ResourcesPathHandler implements PathHandler {
        private AssetHelper mAssetHelper;

        /**
         * @param context {@link Context} used to resolve resources.
         */
        public ResourcesPathHandler(@NonNull Context context) {
            mAssetHelper = new AssetHelper(context);
        }

        @VisibleForTesting
        /*package*/ ResourcesPathHandler(@NonNull AssetHelper assetHelper) {
            mAssetHelper = assetHelper;
        }

        /**
         * Opens the requested file from application's resources directory.
         * <p>
         * The matched prefix path used shouldn't be a prefix of a real web path. Thus, if the
         * requested file cannot be found a {@link WebResourceResponse} object with a {@code null}
         * {@link InputStream} will be returned instead of {@code null}. This saves the time of
         * falling back to network and trying to resolve a path that doesn't exist. A
         * {@link WebResourceResponse} with {@code null} {@link InputStream} will be received as an
         * HTTP response with status code {@code 404} and no body.
         * <p class="note">
         * The MIME type for the file will be determined from the file's extension using
         * {@link java.net.URLConnection#guessContentTypeFromName}. Developers should ensure that
         * resource files are named using standard file extensions. If the file does not have a
         * recognised extension, {@code "text/plain"} will be used by default.
         *
         * @param path the suffix path to be handled.
         * @return {@link WebResourceResponse} for the requested file.
         */
        @Override
        @WorkerThread
        @Nullable
        public WebResourceResponse handle(@NonNull String path) {
            InputStream is = mAssetHelper.openResource(path);
            String mimeType = AssetHelper.guessMimeType(path);
            return new WebResourceResponse(mimeType, null, is);
        }

    }

    /**
     * Handler class to open files from application internal storage.
     * For more information about android storage please refer to
     * <a href="https://developer.android.com/guide/topics/data/data-storage">Android Developers
     * Docs: Data and file storage overview</a>.
     * <p class="note">
     * To avoid leaking user or app data to the web, make sure to choose {@code directory}
     * carefully, and assume any file under this directory could be accessed by any web page subject
     * to same-origin rules.
     * <p>
     * A typical usage would be like:
     * <pre class="prettyprint">
     *     File publicDir = new File(context.getFilesDir(), "public");
     *     // Host "files/public/" in app's data directory under:
     *     // http://appassets.androidplatform.net/public/...
     *     WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
     *              .addPathHandler("/public/", new InternalStoragePathHandler(context, publicDir))
     *              .build();
     * </pre>
     */
    public static final class InternalStoragePathHandler implements PathHandler {
        /**
         * Forbidden subdirectories of {@link Context#getDataDir} that cannot be exposed by this
         * handler. They are forbidden as they often contain sensitive information.
         * <p class="note">
         * Note: Any future addition to this list will be considered breaking changes to the API.
         */
        private static final String[] FORBIDDEN_DATA_DIRS =
                new String[] {"app_webview/", "databases/", "lib/", "shared_prefs/", "code_cache/"};

        @NonNull private final File mDirectory;

        /**
         * Creates PathHandler for app's internal storage.
         * The directory to be exposed must be inside either the application's internal data
         * directory {@link Context#getDataDir} or cache directory {@link Context#getCacheDir}.
         * External storage is not supported for security reasons, as other apps with
         * {@link android.Manifest.permission#WRITE_EXTERNAL_STORAGE} may be able to modify the
         * files.
         * <p>
         * Exposing the entire data or cache directory is not permitted, to avoid accidentally
         * exposing sensitive application files to the web. Certain existing subdirectories of
         * {@link Context#getDataDir} are also not permitted as they are often sensitive.
         * These files are ({@code "app_webview/"}, {@code "databases/"}, {@code "lib/"},
         * {@code "shared_prefs/"} and {@code "code_cache/"}).
         * <p>
         * The application should typically use a dedicated subdirectory for the files it intends to
         * expose and keep them separate from other files.
         *
         * @param context {@link Context} that is used to access app's internal storage.
         * @param directory the absolute path of the exposed app internal storage directory from
         *                  which files can be loaded.
         * @throws IllegalArgumentException if the directory is not allowed.
         */
        public InternalStoragePathHandler(@NonNull Context context, @NonNull File directory) {
            if (!isAllowedInternalStorageDir(context, directory)) {
                throw new IllegalArgumentException("The given directory \"" + directory
                        + "\" doesn't exist under an allowed app internal storage directory");
            }
            mDirectory = directory;
        }

        private static boolean isAllowedInternalStorageDir(@NonNull Context context,
                @NonNull File dir) {
            try {
                String dirPath = AssetHelper.getCanonicalPath(dir);
                String cacheDirPath = AssetHelper.getCanonicalPath(context.getCacheDir());
                String dataDirPath = AssetHelper.getCanonicalPath(AssetHelper.getDataDir(context));
                // dir has to be a subdirectory of data or cache dir.
                if (!dirPath.startsWith(cacheDirPath) && !dirPath.startsWith(dataDirPath)) {
                    return false;
                }
                // dir cannot be the entire cache or data dir.
                if (dirPath.equals(cacheDirPath) || dirPath.equals(dataDirPath)) {
                    return false;
                }
                // dir cannot be a subdirectory of any forbidden data dir.
                for (String forbiddenPath : FORBIDDEN_DATA_DIRS) {
                    if (dirPath.startsWith(dataDirPath + forbiddenPath)) return false;
                }
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        /**
         * Opens the requested file from the exposed data directory.
         * <p>
         * The matched prefix path used shouldn't be a prefix of a real web path. Thus, if the
         * requested file cannot be found or is outside the mounted directory a
         * {@link WebResourceResponse} object with a {@code null} {@link InputStream} will be
         * returned instead of {@code null}. This saves the time of falling back to network and
         * trying to resolve a path that doesn't exist. A {@link WebResourceResponse} with
         * {@code null} {@link InputStream} will be received as an HTTP response with status code
         * {@code 404} and no body.
         * <p class="note">
         * The MIME type for the file will be determined from the file's extension using
         * {@link java.net.URLConnection#guessContentTypeFromName}. Developers should ensure that
         * files are named using standard file extensions. If the file does not have a
         * recognised extension, {@code "text/plain"} will be used by default.
         *
         * @param path the suffix path to be handled.
         * @return {@link WebResourceResponse} for the requested file.
         */
        @Override
        @WorkerThread
        @NonNull
        public WebResourceResponse handle(@NonNull String path) {
            File file = new File(mDirectory, path);
            InputStream is = null;
            if (AssetHelper.isCanonicalChildOf(mDirectory, file)) {
                is = AssetHelper.openFile(file);
            } else {
                Log.e(TAG, "The requested file: " + path + " is outside the mounted directory: "
                         + mDirectory);
            }
            String mimeType = AssetHelper.guessMimeType(path);
            return new WebResourceResponse(mimeType, null, is);
        }
    }


    /**
     * Matches URIs on the form: {@code "http(s)://authority/path/**"}, HTTPS is always enabled.
     *
     * <p>
     * Methods of this class will be invoked on a background thread and care must be taken to
     * correctly synchronize access to any shared state.
     * <p>
     * On Android KitKat and above these methods may be called on more than one thread. This thread
     * may be different than the thread on which the shouldInterceptRequest method was invoked.
     * This means that on Android KitKat and above it is possible to block in this method without
     * blocking other resources from loading. The number of threads used to parallelize loading
     * is an internal implementation detail of the WebView and may change between updates which
     * means that the amount of time spent blocking in this method should be kept to an absolute
     * minimum.
     */
    @VisibleForTesting
    /*package*/ static class PathMatcher {
        static final String HTTP_SCHEME = "http";
        static final String HTTPS_SCHEME = "https";

        final boolean mHttpEnabled;
        @NonNull final String mAuthority;
        @NonNull final String mPath;
        @NonNull final PathHandler mHandler;

        /**
         * @param authority the authority to match (For instance {@code "example.com"})
         * @param path the prefix path to match, it should start and end with a {@code "/"}.
         * @param httpEnabled enable hosting under the HTTP scheme, HTTPS is always enabled.
         * @param handler the {@link PathHandler} the handler class for this URI.
         */
        PathMatcher(@NonNull final String authority, @NonNull final String path,
                            boolean httpEnabled, @NonNull final PathHandler handler) {
            if (path.isEmpty() || path.charAt(0) != '/') {
                throw new IllegalArgumentException("Path should start with a slash '/'.");
            }
            if (!path.endsWith("/")) {
                throw new IllegalArgumentException("Path should end with a slash '/'");
            }
            mAuthority = authority;
            mPath = path;
            mHttpEnabled = httpEnabled;
            mHandler = handler;
        }

        /**
         * Match against registered scheme, authority and path prefix.
         *
         * Match happens when:
         * <ul>
         *      <li>Scheme is "https" <b>or</b> the scheme is "http" and http is enabled.</li>
         *      <li>Authority exact matches the given URI's authority.</li>
         *      <li>Path is a prefix of the given URI's path.</li>
         * </ul>
         *
         * @param uri the URI whose path we will match against.
         *
         * @return {@code PathHandler} if a match happens, {@code null} otherwise.
         */
        @WorkerThread
        @Nullable
        public PathHandler match(@NonNull Uri uri) {
            // Only match HTTP_SCHEME if caller enabled HTTP matches.
            if (uri.getScheme().equals(HTTP_SCHEME) && !mHttpEnabled) {
                return null;
            }
            // Don't match non-HTTP(S) schemes.
            if (!uri.getScheme().equals(HTTP_SCHEME) && !uri.getScheme().equals(HTTPS_SCHEME)) {
                return null;
            }
            if (!uri.getAuthority().equals(mAuthority)) {
                return null;
            }
            if (!uri.getPath().startsWith(mPath)) {
                return null;
            }
            return mHandler;
        }

        /**
         * Utility method to get the suffix path of a matched path.
         *
         * @param path the full received path.
         * @return the suffix path.
         */
        @WorkerThread
        @NonNull
        public String getSuffixPath(@NonNull String path) {
            return path.replaceFirst(mPath, "");
        }
    }

    /**
     * A builder class for constructing {@link WebViewAssetLoader} objects.
     */
    public static final class Builder {
        private boolean mHttpAllowed;
        private String mDomain;
        @NonNull private List<PathMatcher> mBuilderMatcherList;

        public Builder() {
            mHttpAllowed = false;
            mDomain = DEFAULT_DOMAIN;
            mBuilderMatcherList = new ArrayList<>();
        }

        /**
         * Set the domain under which app assets can be accessed.
         * The default domain is {@code "appassets.androidplatform.net"}
         *
         * @param domain the domain on which app assets should be hosted.
         * @return {@link Builder} object.
         */
        @NonNull
        public Builder setDomain(@NonNull String domain) {
            mDomain = domain;
            return this;
        }

        /**
         * Allow using the HTTP scheme in addition to HTTPS.
         * The default is to not allow HTTP.
         *
         * @return {@link Builder} object.
         */
        @NonNull
        public Builder setHttpAllowed(boolean httpAllowed) {
            mHttpAllowed = httpAllowed;
            return this;
        }

        /**
         * Register a {@link PathHandler} for a specific path.
         * <p>
         * The path should start and end with a {@code "/"} and it shouldn't collide with a real web
         * path.
         *
         * @param path the prefix path where this handler should be register.
         * @param handler {@link PathHandler} that handles requests for this path.
         * @return {@link Builder} object.
         * @throws IllegalArgumentException if the path is invalid.
         */
        @NonNull
        public Builder addPathHandler(@NonNull String path, @NonNull PathHandler handler) {
            mBuilderMatcherList.add(new PathMatcher(mDomain, path, mHttpAllowed, handler));
            return this;
        }

        /**
         * Build and return a {@link WebViewAssetLoader} object.
         *
         * @return immutable {@link WebViewAssetLoader} object.
         */
        @NonNull
        public WebViewAssetLoader build() {
            return new WebViewAssetLoader(mBuilderMatcherList);
        }
    }

    /*package*/ WebViewAssetLoader(@NonNull List<PathMatcher> pathMatchers) {
        mMatchers = pathMatchers;
    }

    /**
     * Attempt to resolve the {@code url} to an application resource or asset, and return
     * a {@link WebResourceResponse} for the content.
     * <p>
     * This method should be invoked from within
     * {@link android.webkit.WebViewClient#shouldInterceptRequest(android.webkit.WebView, String)}.
     *
     * @param url the URL to process.
     * @return {@link WebResourceResponse} if the request URL matches a registered URL,
     *         {@code null} otherwise.
     */
    @WorkerThread
    @Nullable
    public WebResourceResponse shouldInterceptRequest(@NonNull Uri url) {
        for (PathMatcher matcher : mMatchers) {
            PathHandler handler = matcher.match(url);
            // The requested URL doesn't match the URL where this handler has been registered.
            if (handler == null) continue;
            String suffixPath = matcher.getSuffixPath(url.getPath());
            WebResourceResponse response = handler.handle(suffixPath);
            // Handler doesn't want to intercept this request, try next handler.
            if (response == null) continue;

            return response;
        }
        return null;
    }
}
