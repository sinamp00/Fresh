package org.telegram.ui.fresh;

/**
 * Pure OLED (#000000) Instagram Explore & Search.
 * Connected live to Meta's official Instagram Explore servers.
 */
public class InstagramExploreFragment extends FreshInstagramBaseFragment {
    public InstagramExploreFragment() {
        targetUrl = "https://www.instagram.com/explore/?theme=dark";
        pageTitle = "Explore";
        showHeader = false;
    }
}
