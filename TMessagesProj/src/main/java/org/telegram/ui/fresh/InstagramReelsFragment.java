package org.telegram.ui.fresh;

/**
 * Pure OLED (#000000) Fullscreen Instagram Reels.
 * Connected live to Meta's official Instagram Reels servers.
 */
public class InstagramReelsFragment extends FreshInstagramBaseFragment {
    public InstagramReelsFragment() {
        targetUrl = "https://www.instagram.com/reels/?theme=dark";
        pageTitle = "Reels";
        showHeader = false;
    }
}
