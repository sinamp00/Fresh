package org.telegram.ui.fresh.reels;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.fresh.instagram.client.InstagramApiClient;
import org.fresh.instagram.model.clips.ClipsResponse;
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
import org.telegram.ui.MainTabsActivity;

import java.util.ArrayList;
import java.util.List;

public class InstagramReelsActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate {

    private static final int MENU_REFRESH = 1;

    private RecyclerListView listView;
    private ReelsAdapter adapter;
    private LinearLayoutManager layoutManager;
    private final List<ReelModel> reels = new ArrayList<>();

    private int currentPlayingPosition = -1;
    private InstagramApiClient apiClient;
    private InstagramSessionManager sessionManager;

    public static class ReelModel {
        public String username;
        public String avatarUrl;
        public String previewUrl;
        public String videoUrl;
        public int likes;
        public String caption;
        public String reelLink;

        public ReelModel(String username, String avatarUrl, String previewUrl, String videoUrl, int likes, String caption, String reelLink) {
            this.username = username;
            this.avatarUrl = avatarUrl;
            this.previewUrl = previewUrl;
            this.videoUrl = videoUrl;
            this.likes = likes;
            this.caption = caption;
            this.reelLink = reelLink;
        }
    }

    public InstagramReelsActivity() {
        super();
    }

    public InstagramReelsActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        Context context = ApplicationLoader.applicationContext;
        sessionManager = new InstagramSessionManager(context);
        apiClient = new InstagramApiClient(sessionManager);
        populateInitialReels();
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(0);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("ریلز");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == MENU_REFRESH) {
                    loadReels();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem refreshItem = menu.addItem(MENU_REFRESH, R.drawable.msg_retry);
        refreshItem.setContentDescription("تازه سازی");

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(0xFF000000);
        fragmentView = contentView;

        listView = new RecyclerListView(context);
        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        listView.setLayoutManager(layoutManager);
        listView.setVerticalScrollBarEnabled(false);

        PagerSnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(listView);

        adapter = new ReelsAdapter();
        listView.setAdapter(adapter);

        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int pos = layoutManager.findFirstCompletelyVisibleItemPosition();
                    if (pos >= 0 && pos != currentPlayingPosition) {
                        playPosition(pos);
                    }
                }
            }
        });

        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.post(() -> playPosition(0));

        loadReels();

        return fragmentView;
    }

    private void populateInitialReels() {
        reels.clear();
        reels.add(new ReelModel(
                "fresh_creators",
                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
                "https://images.unsplash.com/photo-1518791841217-8f162f1e1131?w=800",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                4380,
                "سرعت پخش ریلزها در محیط بومی سوپراپ فرش بدون هیچ افت فریم و تاخیر.",
                "https://instagram.com/reel/1"
        ));
        reels.add(new ReelModel(
                "iran_drone_view",
                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
                "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=800",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                9120,
                "تصاویر هوایی خیره‌کننده از کویر لوت و شب پرستاره ایران.",
                "https://instagram.com/reel/2"
        ));
        reels.add(new ReelModel(
                "motion_graphics_pro",
                "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150",
                "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                3210,
                "طراحی انیمیشن‌های روان و مدرن با رابط کاربری یکپارچه تلگرام و اینستاگرام.",
                "https://instagram.com/reel/3"
        ));
    }

    private void playPosition(int position) {
        if (listView == null || position < 0 || position >= reels.size()) return;

        // Pause previous
        if (currentPlayingPosition >= 0) {
            RecyclerView.ViewHolder prevHolder = listView.findViewHolderForAdapterPosition(currentPlayingPosition);
            if (prevHolder != null && prevHolder.itemView instanceof ReelItemCell) {
                ((ReelItemCell) prevHolder.itemView).pause();
            }
        }

        // Play new
        currentPlayingPosition = position;
        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(position);
        if (holder != null && holder.itemView instanceof ReelItemCell) {
            ((ReelItemCell) holder.itemView).play();
        }
    }

    private void loadReels() {
        new Thread(() -> {
            try {
                if (sessionManager != null && sessionManager.isLoggedIn()) {
                    ClipsResponse clipsResponse = apiClient.getClipsDiscoverBlocking(null);
                    if (clipsResponse != null && clipsResponse.getItems() != null && !clipsResponse.getItems().isEmpty()) {
                        List<ReelModel> fetched = new ArrayList<>();
                        for (ClipsResponse.ClipItemWrapper item : clipsResponse.getItems()) {
                            if (item.getMedia() != null) {
                                org.fresh.instagram.model.feed.MediaItem media = item.getMedia();
                                String user = media.getUser() != null ? media.getUser().getUsername() : "instagram_user";
                                String avatar = media.getUser() != null ? media.getUser().getProfilePicUrl() : "";
                                String videoUrl = media.getBestVideoUrl();
                                String preview = media.getBestImageUrl();
                                String caption = media.getCaption() != null ? media.getCaption().getText() : "";
                                int likes = media.getLikeCount();
                                String link = "https://instagram.com/reel/" + (media.getCode() != null ? media.getCode() : "");
                                if (videoUrl != null && !videoUrl.isEmpty()) {
                                    fetched.add(new ReelModel(user, avatar, preview, videoUrl, likes, caption, link));
                                }
                            }
                        }
                        if (!fetched.isEmpty()) {
                            AndroidUtilities.runOnUIThread(() -> {
                                reels.clear();
                                reels.addAll(fetched);
                                if (adapter != null) {
                                    adapter.notifyDataSetChanged();
                                }
                                playPosition(0);
                            });
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }).start();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (currentPlayingPosition >= 0 && listView != null) {
            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(currentPlayingPosition);
            if (holder != null && holder.itemView instanceof ReelItemCell) {
                ((ReelItemCell) holder.itemView).pause();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (currentPlayingPosition >= 0 && listView != null) {
            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(currentPlayingPosition);
            if (holder != null && holder.itemView instanceof ReelItemCell) {
                ((ReelItemCell) holder.itemView).play();
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (listView != null) {
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                if (child instanceof ReelItemCell) {
                    ((ReelItemCell) child).release();
                }
            }
        }
    }

    @Override
    public void onParentScrollToTop() {
        if (listView != null) {
            listView.smoothScrollToPosition(0);
        }
    }

    private class ReelsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ReelItemCell cell = new ReelItemCell(parent.getContext());
            cell.setParentFragment(InstagramReelsActivity.this);
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ReelItemCell cell = (ReelItemCell) holder.itemView;
            ReelModel reel = reels.get(position);
            cell.bindReel(
                    reel.username,
                    reel.avatarUrl,
                    reel.previewUrl,
                    reel.videoUrl,
                    reel.likes,
                    reel.caption,
                    reel.reelLink
            );
        }

        @Override
        public int getItemCount() {
            return reels.size();
        }
    }
}
