package org.telegram.ui.fresh.feed;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.fresh.instagram.client.InstagramApiClient;
import org.fresh.instagram.model.feed.TimelineFeedResponse;
import org.fresh.instagram.session.InstagramSessionManager;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.MainTabsActivity;

import java.util.ArrayList;
import java.util.List;

public class InstagramFeedActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate {

    private static final int MENU_REFRESH = 1;

    private RecyclerListView listView;
    private FeedAdapter adapter;
    private LinearLayoutManager layoutManager;
    private final List<PostModel> posts = new ArrayList<>();
    private boolean isLoading = false;

    private InstagramApiClient apiClient;
    private InstagramSessionManager sessionManager;

    public static class PostModel {
        public String username;
        public String avatarUrl;
        public String timeAgo;
        public String imageUrl;
        public int likes;
        public String caption;
        public String postUrl;
        public boolean userLiked;

        public PostModel(String username, String avatarUrl, String timeAgo, String imageUrl, int likes, String caption, String postUrl) {
            this.username = username;
            this.avatarUrl = avatarUrl;
            this.timeAgo = timeAgo;
            this.imageUrl = imageUrl;
            this.likes = likes;
            this.caption = caption;
            this.postUrl = postUrl;
            this.userLiked = false;
        }
    }

    public InstagramFeedActivity() {
        super();
    }

    public InstagramFeedActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        Context context = ApplicationLoader.applicationContext;
        sessionManager = new InstagramSessionManager(context);
        apiClient = new InstagramApiClient(sessionManager);
        populateInitialFeed();
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(0); // Root tab fragment, no back arrow
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("فید");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == MENU_REFRESH) {
                    loadFeed();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem refreshItem = menu.addItem(MENU_REFRESH, R.drawable.msg_retry);
        refreshItem.setContentDescription("تازه سازی");

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = contentView;

        listView = new RecyclerListView(context);
        layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setLayoutManager(layoutManager);
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 12));

        adapter = new FeedAdapter();
        listView.setAdapter(adapter);

        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Load feed asynchronously
        loadFeed();

        return fragmentView;
    }

    private void populateInitialFeed() {
        posts.clear();
        posts.add(new PostModel(
                "fresh_official",
                "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150",
                "۲ ساعت پیش",
                "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=800",
                1248,
                "به سوپراپ Fresh خوش آمدید! ترکیب بدون نقص تلگرام پرسرعت و پروتکل بومی اینستاگرام بدون قطعی و پارازیت.",
                "https://fresh.org"
        ));
        posts.add(new PostModel(
                "tehran_architecture",
                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
                "۴ ساعت پیش",
                "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=800",
                3490,
                "نگاهی به زیباترین معماری‌های مدرن و نورپردازی شبانه در قلب شهر.",
                "https://instagram.com"
        ));
        posts.add(new PostModel(
                "tech_trends_iran",
                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
                "۶ ساعت پیش",
                "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800",
                892,
                "فناوری تکه‌تکه‌سازی پکت‌های TLS (Fragmentation) چگونه به عبور روان جریان داده در شبکه‌های تحت سانسور کمک می‌کند؟",
                "https://instagram.com"
        ));
        posts.add(new PostModel(
                "nature_persia",
                "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150",
                "۱۰ ساعت پیش",
                "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=800",
                5620,
                "غروب دل‌انگیز پاییز در دامنه‌های البرز سرسبز.",
                "https://instagram.com"
        ));
    }

    private void loadFeed() {
        if (isLoading) return;
        isLoading = true;

        new Thread(() -> {
            try {
                if (sessionManager != null && sessionManager.isLoggedIn()) {
                    TimelineFeedResponse response = apiClient.getTimelineFeedBlocking(null);
                    if (response != null && response.getFeedItems() != null && !response.getFeedItems().isEmpty()) {
                        List<PostModel> fetched = new ArrayList<>();
                        for (org.fresh.instagram.model.feed.TimelineFeedItem item : response.getFeedItems()) {
                            org.fresh.instagram.model.feed.MediaItem media = item.getMediaOrAd();
                            if (media != null) {
                                String user = media.getUser() != null ? media.getUser().getUsername() : "instagram_user";
                                String avatar = media.getUser() != null ? media.getUser().getProfilePicUrl() : "";
                                String image = media.getBestImageUrl();
                                String caption = media.getCaption() != null ? media.getCaption().getText() : "";
                                int likes = media.getLikeCount();
                                String url = "https://instagram.com/p/" + (media.getCode() != null ? media.getCode() : "");
                                fetched.add(new PostModel(user, avatar, "به‌تازگی", image, likes, caption, url));
                            }
                        }
                        if (!fetched.isEmpty()) {
                            AndroidUtilities.runOnUIThread(() -> {
                                posts.clear();
                                posts.addAll(fetched);
                                if (adapter != null) {
                                    adapter.notifyDataSetChanged();
                                }
                                isLoading = false;
                            });
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            AndroidUtilities.runOnUIThread(() -> {
                isLoading = false;
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            });
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onParentScrollToTop() {
        if (listView != null) {
            listView.smoothScrollToPosition(0);
        }
    }

    private class FeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FeedPostCell cell = new FeedPostCell(parent.getContext());
            cell.setParentFragment(InstagramFeedActivity.this);
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            FeedPostCell cell = (FeedPostCell) holder.itemView;
            PostModel post = posts.get(position);
            cell.bindPost(
                    post.username,
                    post.avatarUrl,
                    post.timeAgo,
                    post.imageUrl,
                    post.likes,
                    post.caption,
                    post.postUrl,
                    post.userLiked
            );
        }

        @Override
        public int getItemCount() {
            return posts.size();
        }
    }
}
