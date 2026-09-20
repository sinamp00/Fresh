package org.telegram.ui.fresh;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.MainTabsActivity;

/**
 * Pure OLED (#000000) Real Live Instagram WebView Integration.
 * Directly connects to Meta's official Instagram servers with real login, real session, and real feed.
 */
public class FreshInstagramBaseFragment extends BaseFragment implements MainTabsActivity.TabFragmentDelegate {

    protected WebView webView;
    protected ProgressBar progressBar;
    protected FrameLayout rootLayout;
    protected LinearLayout errorLayout;
    protected String targetUrl = "https://www.instagram.com/";
    protected String pageTitle = "Fresh";
    protected boolean showHeader = true;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View createView(Context context) {
        rootLayout = new FrameLayout(context);
        rootLayout.setBackgroundColor(0xFF000000); // Pure OLED Black

        FrameLayout contentContainer = new FrameLayout(context);
        contentContainer.setBackgroundColor(0xFF000000);

        int topMargin = 0;

        if (showHeader) {
            topMargin = dp(52);
            FrameLayout headerLayout = new FrameLayout(context);
            headerLayout.setBackgroundColor(0xFF000000);

            TextView titleView = new TextView(context);
            titleView.setText(pageTitle);
            titleView.setTextColor(0xFFFFFFFF);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), Typeface.BOLD);
            titleView.setGravity(Gravity.CENTER_VERTICAL);
            headerLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

            LinearLayout actionsLayout = new LinearLayout(context);
            actionsLayout.setOrientation(LinearLayout.HORIZONTAL);
            actionsLayout.setGravity(Gravity.CENTER_VERTICAL);

            // Refresh button
            ImageView refreshBtn = new ImageView(context);
            refreshBtn.setImageResource(R.drawable.msg_retry);
            refreshBtn.setColorFilter(new PorterDuffColorFilter(0xFFCCCCCC, PorterDuff.Mode.SRC_IN));
            refreshBtn.setScaleType(ImageView.ScaleType.CENTER);
            refreshBtn.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, 1));
            refreshBtn.setOnClickListener(v -> {
                if (webView != null) {
                    webView.reload();
                }
            });
            actionsLayout.addView(refreshBtn, LayoutHelper.createLinear(44, 44, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));

            // Direct message button (switches directly to Telegram chats tab)
            ImageView directBtn = new ImageView(context);
            directBtn.setImageResource(R.drawable.send_plane_24);
            directBtn.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
            directBtn.setScaleType(ImageView.ScaleType.CENTER);
            directBtn.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, 1));
            directBtn.setOnClickListener(v -> {
                if (parentLayout != null) {
                    for (BaseFragment f : parentLayout.getFragmentStack()) {
                        if (f instanceof MainTabsActivity) {
                            ((MainTabsActivity) f).switchToTab(MainTabsActivity.POSITION_CHATS);
                            break;
                        }
                    }
                }
            });
            actionsLayout.addView(directBtn, LayoutHelper.createLinear(44, 44, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

            View headerDivider = new View(context);
            headerDivider.setBackgroundColor(0x1FFFFFFF);
            headerLayout.addView(headerDivider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM));

            headerLayout.addView(actionsLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
            rootLayout.addView(headerLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 52, Gravity.TOP));
        }

        // WebView setup
        webView = new WebView(context);
        webView.setBackgroundColor(0xFF000000);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                WebSettingsCompat.setForceDarkStrategy(settings, WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING);
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON);
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false);
            }
        } catch (Throwable ignored) {}

        // Horizontal Progress Bar
        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        if (progressBar.getProgressDrawable() != null) {
            progressBar.getProgressDrawable().setColorFilter(new PorterDuffColorFilter(0xFF00F5A0, PorterDuff.Mode.SRC_IN));
        }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) {
                    if (newProgress < 100) {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(newProgress);
                    } else {
                        progressBar.setVisibility(View.GONE);
                    }
                }
                if (newProgress >= 30) {
                    injectOledDarkMode(view);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (progressBar != null) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(15);
                }
                if (errorLayout != null) {
                    errorLayout.setVisibility(View.GONE);
                }
                injectOledDarkMode(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        CookieManager.getInstance().flush();
                    } catch (Throwable ignored) {}
                }
                injectOledDarkMode(view);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    if (errorLayout != null) {
                        errorLayout.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (!TextUtils.isEmpty(url) && (url.contains("instagram.com") || url.contains("facebook.com") || url.contains("meta.com") || url.contains("cdninstagram.com"))) {
                    return false;
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    String url = request.getUrl().toString();
                    if (!TextUtils.isEmpty(url) && (url.contains("instagram.com") || url.contains("facebook.com") || url.contains("meta.com") || url.contains("cdninstagram.com"))) {
                        return false;
                    }
                }
                return false;
            }
        });

        contentContainer.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Error Retry View
        errorLayout = new LinearLayout(context);
        errorLayout.setOrientation(LinearLayout.VERTICAL);
        errorLayout.setGravity(Gravity.CENTER);
        errorLayout.setBackgroundColor(0xFF000000);
        errorLayout.setVisibility(View.GONE);

        TextView errTitle = new TextView(context);
        errTitle.setText("اتصال به سرورهای اینستاگرام برقرار نشد");
        errTitle.setTextColor(0xFFFFFFFF);
        errTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        errTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), Typeface.BOLD);
        errTitle.setGravity(Gravity.CENTER);
        errorLayout.addView(errTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 8));

        TextView errSubtitle = new TextView(context);
        errSubtitle.setText("لطفاً اتصال اینترنت خود را بررسی نمایید");
        errSubtitle.setTextColor(0xFFAAAAAA);
        errSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        errSubtitle.setGravity(Gravity.CENTER);
        errorLayout.addView(errSubtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 20));

        TextView retryBtn = new TextView(context);
        retryBtn.setText("تلاش مجدد");
        retryBtn.setTextColor(0xFF000000);
        retryBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        retryBtn.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), Typeface.BOLD);
        retryBtn.setGravity(Gravity.CENTER);
        retryBtn.setPadding(dp(24), dp(10), dp(24), dp(10));
        retryBtn.setBackground(Theme.createRoundRectDrawable(dp(8), 0xFF00F5A0));
        retryBtn.setOnClickListener(v -> {
            if (errorLayout != null) {
                errorLayout.setVisibility(View.GONE);
            }
            if (webView != null) {
                webView.loadUrl(targetUrl);
            }
        });
        errorLayout.addView(retryBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        contentContainer.addView(errorLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Progress bar at top
        contentContainer.addView(progressBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2.5f, Gravity.TOP));

        rootLayout.addView(contentContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, topMargin, 0, 0));

        webView.loadUrl(targetUrl);
        fragmentView = rootLayout;
        return fragmentView;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (webView != null) {
            try {
                ViewParent parent = webView.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(webView);
                }
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.destroy();
                webView = null;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (webView != null && webView.canGoBack()) {
            if (invoked) {
                webView.goBack();
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public void onParentScrollToTop() {
        if (webView != null) {
            webView.scrollTo(0, 0);
        }
    }

    protected void injectOledDarkMode(WebView view) {
        if (view == null) {
            return;
        }
        String js = "(function() {" +
                "  try {" +
                "    document.documentElement.classList.add('__ig-dark-mode');" +
                "    if (document.body) { document.body.classList.add('__ig-dark-mode'); }" +
                "    var style = document.getElementById('fresh-oled-style');" +
                "    if (!style) {" +
                "      style = document.createElement('style');" +
                "      style.id = 'fresh-oled-style';" +
                "      style.innerHTML = '" +
                "        :root, html, body, .__ig-dark-mode {" +
                "          color-scheme: dark !important;" +
                "          --ig-primary-background: 0, 0, 0 !important;" +
                "          --ig-elevated-separator: #262626 !important;" +
                "          --fds-primary-icon: #f5f5f5 !important;" +
                "          --fds-primary-text: #f5f5f5 !important;" +
                "        }" +
                "        html, body {" +
                "          background-color: #000000 !important;" +
                "        }" +
                "        input, textarea, select {" +
                "          background-color: #1a1a1a !important;" +
                "          color: #f5f5f5 !important;" +
                "          border: 1px solid #333333 !important;" +
                "        }" +
                "        input::placeholder, textarea::placeholder {" +
                "          color: #8e8e8e !important;" +
                "        }" +
                "        svg[fill=\"#262626\"], svg[fill=\"#000000\"], svg[fill=\"#121212\"] {" +
                "          fill: #f5f5f5 !important;" +
                "        }" +
                "        svg[stroke=\"#262626\"], svg[stroke=\"#000000\"] {" +
                "          stroke: #f5f5f5 !important;" +
                "        }" +
                "        a[href*=\"play.google.com\"], a[href*=\"itunes.apple.com\"], div[class*=\"AppBanner\"], div[class*=\"InstallAppBanner\"], div[class*=\"NativeAppBanner\"] {" +
                "          display: none !important;" +
                "        }';" +
                "      (document.head || document.documentElement).appendChild(style);" +
                "    }" +
                "  } catch(e) {}" +
                "})();";
        view.evaluateJavascript(js, null);
    }
}
