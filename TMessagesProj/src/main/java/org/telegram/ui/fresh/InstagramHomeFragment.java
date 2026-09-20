package org.telegram.ui.fresh;

/**
 * Pure OLED (#000000) Instagram Home Feed & Stories.
 * Connected live to Meta's official Instagram servers with real login, real feed, and direct chat switch.
 */
public class InstagramHomeFragment extends FreshInstagramBaseFragment {
    public InstagramHomeFragment() {
        targetUrl = "https://www.instagram.com/?theme=dark";
        pageTitle = "Fresh";
        showHeader = true;
    }
}
