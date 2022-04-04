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

package androidx.webkit;

import android.webkit.WebSettings;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresFeature;
import androidx.annotation.RestrictTo;
import androidx.webkit.internal.WebSettingsAdapter;
import androidx.webkit.internal.WebViewFeatureInternal;
import androidx.webkit.internal.WebViewGlueCommunicator;

import org.chromium.support_lib_boundary.WebSettingsBoundaryInterface;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compatibility version of {@link android.webkit.WebSettings}
 */
public class WebSettingsCompat {
    private WebSettingsCompat() {}

    /**
     * Sets whether this WebView should raster tiles when it is
     * offscreen but attached to a window. Turning this on can avoid
     * rendering artifacts when animating an offscreen WebView on-screen.
     * Offscreen WebViews in this mode use more memory. The default value is
     * false.<br>
     * Please follow these guidelines to limit memory usage:
     * <ul>
     * <li> WebView size should be not be larger than the device screen size.
     * <li> Limit use of this mode to a small number of WebViews. Use it for
     *   visible WebViews and WebViews about to be animated to visible.
     * </ul>
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#OFF_SCREEN_PRERASTER}.
     */
    @RequiresFeature(name = WebViewFeature.OFF_SCREEN_PRERASTER,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static void setOffscreenPreRaster(@NonNull WebSettings settings, boolean enabled) {
        WebViewFeatureInternal feature = WebViewFeatureInternal.OFF_SCREEN_PRERASTER;
        if (feature.isSupportedByFramework()) {
            settings.setOffscreenPreRaster(enabled);
        } else if (feature.isSupportedByWebView()) {
            getAdapter(settings).setOffscreenPreRaster(enabled);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Gets whether this WebView should raster tiles when it is
     * offscreen but attached to a window.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#OFF_SCREEN_PRERASTER}.
     *
     * @return {@code true} if this WebView will raster tiles when it is
     * offscreen but attached to a window.
     */
    @RequiresFeature(name = WebViewFeature.OFF_SCREEN_PRERASTER,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static boolean getOffscreenPreRaster(@NonNull WebSettings settings) {
        WebViewFeatureInternal feature = WebViewFeatureInternal.OFF_SCREEN_PRERASTER;
        if (feature.isSupportedByFramework()) {
            return settings.getOffscreenPreRaster();
        } else if (feature.isSupportedByWebView()) {
            return getAdapter(settings).getOffscreenPreRaster();
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Sets whether Safe Browsing is enabled. Safe Browsing allows WebView to
     * protect against malware and phishing attacks by verifying the links.
     *
     * <p>
     * Safe Browsing can be disabled for all WebViews using a manifest tag (read <a
     * href="{@docRoot}reference/android/webkit/WebView.html">general Safe Browsing info</a>). The
     * manifest tag has a lower precedence than this API.
     *
     * <p>
     * Safe Browsing is enabled by default for devices which support it.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#SAFE_BROWSING_ENABLE}.
     *
     * @param enabled Whether Safe Browsing is enabled.
     */
    @RequiresFeature(name = WebViewFeature.SAFE_BROWSING_ENABLE,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static void setSafeBrowsingEnabled(@NonNull WebSettings settings, boolean enabled) {
        WebViewFeatureInternal feature = WebViewFeatureInternal.SAFE_BROWSING_ENABLE;
        if (feature.isSupportedByFramework()) {
            settings.setSafeBrowsingEnabled(enabled);
        } else if (feature.isSupportedByWebView()) {
            getAdapter(settings).setSafeBrowsingEnabled(enabled);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Gets whether Safe Browsing is enabled.
     * See {@link #setSafeBrowsingEnabled}.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#SAFE_BROWSING_ENABLE}.
     *
     * @return {@code true} if Safe Browsing is enabled and {@code false} otherwise.
     */
    @RequiresFeature(name = WebViewFeature.SAFE_BROWSING_ENABLE,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static boolean getSafeBrowsingEnabled(@NonNull WebSettings settings) {
        WebViewFeatureInternal feature = WebViewFeatureInternal.SAFE_BROWSING_ENABLE;
        if (feature.isSupportedByFramework()) {
            return settings.getSafeBrowsingEnabled();
        } else if (feature.isSupportedByWebView()) {
            return getAdapter(settings).getSafeBrowsingEnabled();
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @IntDef(flag = true, value = {
            WebSettings.MENU_ITEM_NONE,
            WebSettings.MENU_ITEM_SHARE,
            WebSettings.MENU_ITEM_WEB_SEARCH,
            WebSettings.MENU_ITEM_PROCESS_TEXT
    })
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    public @interface MenuItemFlags {}

    /**
     * Disables the action mode menu items according to {@code menuItems} flag.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#DISABLED_ACTION_MODE_MENU_ITEMS}.
     *
     * @param menuItems an integer field flag for the menu items to be disabled.
     */
    @RequiresFeature(name = WebViewFeature.DISABLED_ACTION_MODE_MENU_ITEMS,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static void setDisabledActionModeMenuItems(@NonNull WebSettings settings,
            @MenuItemFlags int menuItems) {
        WebViewFeatureInternal feature =
                WebViewFeatureInternal.DISABLED_ACTION_MODE_MENU_ITEMS;
        if (feature.isSupportedByFramework()) {
            settings.setDisabledActionModeMenuItems(menuItems);
        } else if (feature.isSupportedByWebView()) {
            getAdapter(settings).setDisabledActionModeMenuItems(menuItems);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Gets the action mode menu items that are disabled, expressed in an integer field flag.
     * The default value is {@link WebSettings#MENU_ITEM_NONE}
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#DISABLED_ACTION_MODE_MENU_ITEMS}.
     *
     * @return all the disabled menu item flags combined with bitwise OR.
     */
    @RequiresFeature(name = WebViewFeature.DISABLED_ACTION_MODE_MENU_ITEMS,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static @MenuItemFlags int getDisabledActionModeMenuItems(@NonNull WebSettings settings) {
        WebViewFeatureInternal feature =
                WebViewFeatureInternal.DISABLED_ACTION_MODE_MENU_ITEMS;
        if (feature.isSupportedByFramework()) {
            return settings.getDisabledActionModeMenuItems();
        } else if (feature.isSupportedByWebView()) {
            return getAdapter(settings).getDisabledActionModeMenuItems();
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Sets whether the WebView’s internal error page should be suppressed or displayed
     * for bad navigations. True means suppressed (not shown), false means it will be
     * displayed.
     * The default value is false.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#SUPPRESS_ERROR_PAGE}.
     *
     * @param suppressed whether the WebView should suppress its internal error page
     *
     * TODO(cricke): unhide
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @RequiresFeature(name = WebViewFeature.SUPPRESS_ERROR_PAGE,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static void setWillSuppressErrorPage(@NonNull WebSettings settings,
            boolean suppressed) {
        WebViewFeatureInternal feature = WebViewFeatureInternal.SUPPRESS_ERROR_PAGE;
        if (feature.isSupportedByWebView()) {
            getAdapter(settings).setWillSuppressErrorPage(suppressed);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }


    /**
     * Gets whether the WebView’s internal error page will be suppressed or displayed
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#SUPPRESS_ERROR_PAGE}.
     *
     * @return true if the WebView will suppress its internal error page
     * @see #setWillSuppressErrorPage
     *
     * TODO(cricke): unhide
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @RequiresFeature(name = WebViewFeature.SUPPRESS_ERROR_PAGE,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static boolean willSuppressErrorPage(@NonNull WebSettings settings) {
        WebViewFeatureInternal feature = WebViewFeatureInternal.SUPPRESS_ERROR_PAGE;
        if (feature.isSupportedByWebView()) {
            return getAdapter(settings).willSuppressErrorPage();
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Disable force dark, irrespective of the force dark mode of the WebView parent. In this mode,
     * WebView content will always be rendered as-is, regardless of whether native views are being
     * automatically darkened.
     *
     * @see #setForceDark
     * @deprecated refer to {@link #setForceDark}
     */
    @Deprecated
    public static final int FORCE_DARK_OFF = WebSettings.FORCE_DARK_OFF;

    /**
     * Enable force dark dependent on the state of the WebView parent view. If the WebView parent
     * view is being automatically force darkened
     * (@see android.view.View#setForceDarkAllowed), then WebView content will be rendered
     * so as to emulate a dark theme. WebViews that are not attached to the view hierarchy will not
     * be inverted.
     *
     * <p class="note"> If your app uses a dark theme, WebView will not be inverted. Similarly, if
     * your app's theme inherits from a {@code DayNight} theme, WebView will not be inverted.
     * In either of these cases, you should control the mode manually with
     * {@link ForceDark#FORCE_DARK_ON} or {@link ForceDark#FORCE_DARK_OFF}.
     *
     * <p> See <a href="https://developer.android.com/guide/topics/ui/look-and-feel/darktheme#force_dark">
     * Force Dark documentation</a> for more information.
     *
     * @see #setForceDark
     * @deprecated refer to {@link #setForceDark}
     */
    @Deprecated
    public static final int FORCE_DARK_AUTO = WebSettings.FORCE_DARK_AUTO;

    /**
     * Unconditionally enable force dark. In this mode WebView content will always be rendered so
     * as to emulate a dark theme.
     *
     * @see #setForceDark
     * @deprecated refer to {@link #setForceDark}
     */
    @Deprecated
    public static final int FORCE_DARK_ON = WebSettings.FORCE_DARK_ON;

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @IntDef(value = {
            FORCE_DARK_OFF,
            FORCE_DARK_AUTO,
            FORCE_DARK_ON,
    })
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    public @interface ForceDark {}

    /**
     * Set the force dark mode for this WebView.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#FORCE_DARK}.
     *
     * <p>
     * If equals to {@link ForceDark#FORCE_DARK_ON} then {@link #setForceDarkStrategy} is used to
     * specify darkening strategy.
     *
     * @param forceDarkMode the force dark mode to set.
     * @see #getForceDark
     * @deprecated The "force dark" model previously implemented by WebView was complex
     * and didn't interoperate well with current Web standards for
     * {@code prefers-color-scheme} and {@code color-scheme}. In apps with
     * {@code targetSdkVersion} &ge; {@link android.os.Build.VERSION_CODES#TIRAMISU}
     * this API is a no-op and WebView will always use the dark style defined by web content
     * authors if the app's theme is dark. To customize the behavior, refer to
     * {@link #setAlgorithmicDarkeningAllowed}.
     */
    @Deprecated
    @RequiresFeature(name = WebViewFeature.FORCE_DARK,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static void setForceDark(@NonNull WebSettings settings,
            @ForceDark int forceDarkMode) {
        WebViewFeatureInternal feature = WebViewFeatureInternal.FORCE_DARK;
        if (feature.isSupportedByFramework()) {
            settings.setForceDark(forceDarkMode);
        } else if (feature.isSupportedByWebView()) {
            getAdapter(settings).setForceDark(forceDarkMode);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Get the force dark mode for this WebView.
     *
     * <p>
     * The default force dark mode is {@link #FORCE_DARK_AUTO}.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#FORCE_DARK}.
     *
     * @return the currently set force dark mode.
     * @see #setForceDark
     * @deprecated refer to {@link #setForceDark}
     */
    @Deprecated
    @RequiresFeature(name = WebViewFeature.FORCE_DARK,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static @ForceDark int getForceDark(@NonNull WebSettings settings) {
        WebViewFeatureInternal feature = WebViewFeatureInternal.FORCE_DARK;
        if (feature.isSupportedByFramework()) {
            return settings.getForceDark();
        } else if (feature.isSupportedByWebView()) {
            return getAdapter(settings).getForceDark();
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Control whether algorithmic darkening is allowed.
     *
     * <p class="note">
     * <b>Note:</b> This API and the behaviour described only apply to apps with
     * {@code targetSdkVersion} &ge; {@link android.os.Build.VERSION_CODES#TIRAMISU}.
     *
     * <p>
     * WebView always sets the media query {@code prefers-color-scheme} according to the app's
     * theme attribute {@link android.R.styleable#Theme_isLightTheme isLightTheme}, i.e.
     * {@code prefers-color-scheme} is {@code light} if isLightTheme is true or not specified,
     * otherwise it is {@code dark}. This means that the web content's light or dark style will
     * be applied automatically to match the app's theme if the content supports it.
     *
     * <p>
     * Algorithmic darkening is disallowed by default.
     * <p>
     * If the app's theme is dark and it allows algorithmic darkening, WebView will attempt to
     * darken web content using an algorithm, if the content doesn't define its own dark styles
     * and doesn't explicitly disable darkening.
     *
     * <p>
     * If Android is applying Force Dark to WebView then WebView will ignore the value of
     * this setting and behave as if it were set to true.
     *
     * <p>
     * The deprecated {@link #setForceDark} and related API are no-ops in apps with
     * {@code targetSdkVersion} &ge; {@link android.os.Build.VERSION_CODES#TIRAMISU},
     * but they still apply to apps with
     * {@code targetSdkVersion} &lt; {@link android.os.Build.VERSION_CODES#TIRAMISU}.
     *
     * <p>
     * The below table summarizes how APIs work with different apps.
     *
     * <table border="2" width="85%" align="center" cellpadding="5">
     *     <thead>
     *         <tr>
     *             <th>App</th>
     *             <th>Web content which uses {@code prefers-color-scheme}</th>
     *             <th>Web content which does not use {@code prefers-color-scheme}</th>
     *         </tr>
     *     </thead>
     *     <tbody>
     *     <tr>
     *         <td>App with {@code isLightTheme} True or not set</td>
     *         <td>Renders with the light theme defined by the content author.</td>
     *         <td>Renders with the default styling defined by the content author.</td>
     *     </tr>
     *     <tr>
     *         <td>App with Android forceDark in effect</td>
     *         <td>Renders with the dark theme defined by the content author.</td>
     *         <td>Renders with the styling modified to dark colors by an algorithm
     *             if allowed by the content author.</td>
     *     </tr>
     *     <tr>
     *         <td>App with {@code isLightTheme} False,
     *            {@code targetSdkVersion} &lt; {@link android.os.Build.VERSION_CODES#TIRAMISU},
     *             and has {@code FORCE_DARK_AUTO}</td>
     *         <td>Renders with the dark theme defined by the content author.</td>
     *         <td>Renders with the default styling defined by the content author.</td>
     *     </tr>
     *     <tr>
     *         <td>App with {@code isLightTheme} False,
     *            {@code targetSdkVersion} &ge; {@link android.os.Build.VERSION_CODES#TIRAMISU},
     *             and {@code setAlgorithmicDarkening(false)}</td>
     *         <td>Renders with the dark theme defined by the content author.</td>
     *         <td>Renders with the default styling defined by the content author.</td>
     *     </tr>
     *     <tr>
     *         <td>App with {@code isLightTheme} False,
     *            {@code targetSdkVersion} &ge; {@link android.os.Build.VERSION_CODES#TIRAMISU},
     *             and {@code setAlgorithmicDarkening(true)}</td>
     *         <td>Renders with the dark theme defined by the content author.</td>
     *         <td>Renders with the styling modified to dark colors by an algorithm if allowed
     *             by the content author.</td>
     *     </tr>
     *     </tbody>
     * </table>
     * </p>
     *
     * @param allow allow algorithmic darkening or not.
     */
    @RequiresFeature(name = WebViewFeature.ALGORITHMIC_DARKENING,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static void setAlgorithmicDarkeningAllowed(@NonNull WebSettings settings,
            boolean allow) {
        WebViewFeatureInternal feature = WebViewFeatureInternal.ALGORITHMIC_DARKENING;
        if (feature.isSupportedByWebView()) {
            getAdapter(settings).setAlgorithmicDarkeningAllowed(allow);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Get if algorithmic darkening is allowed or not for this WebView.
     * The default is false.
     *
     * @return if the algorithmic darkening is allowed or not.
     * @see #setAlgorithmicDarkeningAllowed
     */
    @RequiresFeature(name = WebViewFeature.ALGORITHMIC_DARKENING,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static boolean isAlgorithmicDarkeningAllowed(@NonNull WebSettings settings) {
        WebViewFeatureInternal feature = WebViewFeatureInternal.ALGORITHMIC_DARKENING;
        if (feature.isSupportedByWebView()) {
            return getAdapter(settings).isAlgorithmicDarkeningAllowed();
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * In this mode WebView content will be darkened by a user agent and it will ignore the
     * web page's dark theme if it exists. To avoid mixing two different darkening strategies,
     * the {@code prefers-color-scheme} media query will evaluate to light.
     *
     * <p> See <a href="https://drafts.csswg.org/css-color-adjust-1/">specification</a>
     * for more information.
     *
     * @see #setForceDarkStrategy
     * @deprecated refer to {@link #setForceDark}
     */
    @Deprecated
    public static final int DARK_STRATEGY_USER_AGENT_DARKENING_ONLY =
            WebSettingsBoundaryInterface.ForceDarkBehavior.FORCE_DARK_ONLY;

    /**
     * In this mode WebView content will always be darkened using dark theme provided by web page.
     * If web page does not provide dark theme support WebView content will be rendered with a
     * default theme.
     *
     * <p> See <a href="https://drafts.csswg.org/css-color-adjust-1/">specification</a>
     * for more information.
     *
     * @see #setForceDarkStrategy
     * @deprecated refer to {@link #setForceDark}
     */
    @Deprecated
    public static final int DARK_STRATEGY_WEB_THEME_DARKENING_ONLY =
            WebSettingsBoundaryInterface.ForceDarkBehavior.MEDIA_QUERY_ONLY;

    /**
     * In this mode WebView content will be darkened by a user agent unless web page supports dark
     * theme. WebView determines whether web pages supports dark theme by the presence of
     * {@code color-scheme} metadata containing "dark" value. For example,
     * {@code <meta name="color-scheme" content="dark light">}.
     * If the metadata is not presented WebView content will be darkened by a user agent and
     * {@code prefers-color-scheme} media query will evaluate to light.
     *
     * <p> See <a href="https://drafts.csswg.org/css-color-adjust-1/">specification</a>
     * for more information.
     *
     * @see #setForceDarkStrategy
     * @deprecated refer to {@link #setForceDark}
     */
    @Deprecated
    public static final int DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING =
            WebSettingsBoundaryInterface.ForceDarkBehavior.PREFER_MEDIA_QUERY_OVER_FORCE_DARK;

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @IntDef(value = {
            DARK_STRATEGY_USER_AGENT_DARKENING_ONLY,
            DARK_STRATEGY_WEB_THEME_DARKENING_ONLY,
            DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING,
    })
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    public @interface ForceDarkStrategy {}

    /**
     * Set how WebView content should be darkened.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#FORCE_DARK_STRATEGY}.
     *
     * <p>
     * The specified strategy is only used if force dark mode is on.
     * See {@link #setForceDark}.
     *
     * @param forceDarkBehavior the force dark strategy to set.
     * @see #getForceDarkStrategy
     * @deprecated refer to {@link #setForceDark}
     */
    @RequiresFeature(name = WebViewFeature.FORCE_DARK_STRATEGY,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    @Deprecated
    public static void setForceDarkStrategy(@NonNull WebSettings settings,
            @ForceDarkStrategy int forceDarkBehavior) {
        WebViewFeatureInternal feature = WebViewFeatureInternal.FORCE_DARK_STRATEGY;
        if (feature.isSupportedByWebView()) {
            getAdapter(settings).setForceDarkStrategy(forceDarkBehavior);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Get how content is darkened for this WebView.
     *
     * <p>
     * The default force dark strategy is
     * {@link #DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING}
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#FORCE_DARK_STRATEGY}.
     *
     * @return the currently set force dark strategy.
     * @see #setForceDarkStrategy
     * @deprecated refer to {@link #setForceDark}
     */
    @RequiresFeature(name = WebViewFeature.FORCE_DARK_STRATEGY,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    @Deprecated
    public static @ForceDarkStrategy int getForceDarkStrategy(@NonNull WebSettings settings) {
        WebViewFeatureInternal feature = WebViewFeatureInternal.FORCE_DARK_STRATEGY;
        if (feature.isSupportedByWebView()) {
            return getAdapter(settings).getForceDark();
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * In this mode the WebView will not add an X-Requested-With header on HTTP
     * requests automatically.
     *
     * @see #setRequestedWithHeaderMode(WebSettings, int)
     * @see ServiceWorkerWebSettingsCompat#setRequestedWithHeaderMode(int)
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static final int REQUESTED_WITH_HEADER_MODE_NO_HEADER =
            WebSettingsBoundaryInterface.RequestedWithHeaderMode.NO_HEADER;
    /**
     * In this mode the WebView automatically add an X-Requested-With header to outgoing
     * requests, if the application or the loaded webpage has not already set a header value.
     * The value of this automatically added header will be the package name of the app.
     *
     * This is the default mode.
     *
     * @see #setRequestedWithHeaderMode(WebSettings, int)
     * @see ServiceWorkerWebSettingsCompat#setRequestedWithHeaderMode(int)
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static final int REQUESTED_WITH_HEADER_MODE_APP_PACKAGE_NAME =
            WebSettingsBoundaryInterface.RequestedWithHeaderMode.APP_PACKAGE_NAME;

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @IntDef(value = {
            REQUESTED_WITH_HEADER_MODE_NO_HEADER,
            REQUESTED_WITH_HEADER_MODE_APP_PACKAGE_NAME
    })
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.PARAMETER, ElementType.METHOD})
    public @interface RequestedWithHeaderMode {}

    /**
     * Sets how the WebView will set the X-Requested-With header on requests.
     *
     * If you are calling this method, you may also want to call
     * {@link ServiceWorkerWebSettingsCompat#setRequestedWithHeaderMode(int)} with the same
     * parameter value to configure ServiceWorker requests.
     *
     * The default behavior may vary depending on the WebView implementation.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#REQUESTED_WITH_HEADER_CONTROL}.
     *
     * @param requestedWithHeaderMode The {@code REQUESTED_WITH_HEADER_MODE to use}
     * @see ServiceWorkerWebSettingsCompat#setRequestedWithHeaderMode(int)
     * @see #getRequestedWithHeaderMode(WebSettings)
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @RequiresFeature(name = WebViewFeature.REQUESTED_WITH_HEADER_CONTROL,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static void setRequestedWithHeaderMode(@NonNull WebSettings settings,
            @RequestedWithHeaderMode int requestedWithHeaderMode) {
        WebViewFeatureInternal feature = WebViewFeatureInternal.REQUESTED_WITH_HEADER_CONTROL;
        if (feature.isSupportedByWebView()) {
            getAdapter(settings).setRequestedWithHeaderMode(requestedWithHeaderMode);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Gets how the WebView will set the X-Requested-With header on HTTP requests.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#REQUESTED_WITH_HEADER_CONTROL}.
     *
     * @return the currently configured {@code REQUESTED_WITH_HEADER_MODE}
     * @see #setRequestedWithHeaderMode(WebSettings, int)
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @RequiresFeature(name = WebViewFeature.REQUESTED_WITH_HEADER_CONTROL,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    @RequestedWithHeaderMode
    public static int getRequestedWithHeaderMode(@NonNull WebSettings settings) {
        WebViewFeatureInternal feature = WebViewFeatureInternal.REQUESTED_WITH_HEADER_CONTROL;
        if (feature.isSupportedByWebView()) {
            return getAdapter(settings).getRequestedWithHeaderMode();
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    private static WebSettingsAdapter getAdapter(WebSettings settings) {
        return WebViewGlueCommunicator.getCompatConverter().convertSettings(settings);
    }
}

