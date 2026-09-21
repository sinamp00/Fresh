package org.telegram.ui.fresh.reels;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.VideoPlayer;

public class ReelItemCell extends FrameLayout {

    private final TextureView textureView;
    private final BackupImageView previewImageView;
    private final BackupImageView avatarView;
    private final TextView usernameTextView;
    private final TextView captionTextView;
    private final TextView musicTextView;

    private final ImageView likeButton;
    private final TextView likeCountTextView;
    private final ImageView commentButton;
    private final ImageView shareButton;

    private VideoPlayer player;
    private boolean isPlaying = false;
    private boolean isLiked = false;
    private int likesCount = 0;
    private String currentVideoUrl;
    private String currentReelLink = "";
    private String currentCaption = "";
    private BaseFragment parentFragment;

    public ReelItemCell(@NonNull Context context) {
        super(context);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setBackgroundColor(0xFF000000); // Black video background

        // 1. TextureView for Video Rendering
        textureView = new TextureView(context);
        addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // 2. Poster / Preview Image
        previewImageView = new BackupImageView(context);
        addView(previewImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // 3. Tap overlay to pause / play
        View tapOverlay = new View(context);
        tapOverlay.setOnClickListener(v -> togglePlayPause());
        addView(tapOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // 4. Right Side Actions (Like, Comment, Share)
        LinearLayout actionsLayout = new LinearLayout(context);
        actionsLayout.setOrientation(LinearLayout.VERTICAL);
        actionsLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        actionsLayout.setPadding(0, 0, AndroidUtilities.dp(16), AndroidUtilities.dp(80));
        addView(actionsLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT));

        // Like Button
        likeButton = new ImageView(context);
        likeButton.setImageResource(R.drawable.msg_reactions);
        likeButton.setColorFilter(Color.WHITE);
        likeButton.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        likeButton.setOnClickListener(v -> toggleLike());
        actionsLayout.addView(likeButton, LayoutHelper.createLinear(44, 44, Gravity.CENTER_HORIZONTAL));

        likeCountTextView = new TextView(context);
        likeCountTextView.setTextColor(Color.WHITE);
        likeCountTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        likeCountTextView.setTypeface(AndroidUtilities.bold());
        likeCountTextView.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(1), 0x99000000);
        actionsLayout.addView(likeCountTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        // Comment Button
        commentButton = new ImageView(context);
        commentButton.setImageResource(R.drawable.menu_comments);
        commentButton.setColorFilter(Color.WHITE);
        commentButton.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        actionsLayout.addView(commentButton, LayoutHelper.createLinear(44, 44, Gravity.CENTER_HORIZONTAL));

        TextView commentCountTextView = new TextView(context);
        commentCountTextView.setTextColor(Color.WHITE);
        commentCountTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        commentCountTextView.setText("۳۲");
        commentCountTextView.setTypeface(AndroidUtilities.bold());
        commentCountTextView.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(1), 0x99000000);
        actionsLayout.addView(commentCountTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        // Telegram Share Button
        shareButton = new ImageView(context);
        shareButton.setImageResource(R.drawable.msg_share);
        shareButton.setColorFilter(Color.WHITE);
        shareButton.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        shareButton.setOnClickListener(v -> shareToTelegram());
        actionsLayout.addView(shareButton, LayoutHelper.createLinear(44, 44, Gravity.CENTER_HORIZONTAL));

        // 5. Bottom Overlay (Author & Caption)
        LinearLayout bottomInfoLayout = new LinearLayout(context);
        bottomInfoLayout.setOrientation(LinearLayout.VERTICAL);
        bottomInfoLayout.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(80), AndroidUtilities.dp(70));
        addView(bottomInfoLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT));

        LinearLayout userRow = new LinearLayout(context);
        userRow.setOrientation(LinearLayout.HORIZONTAL);
        userRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomInfoLayout.addView(userRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(18));
        userRow.addView(avatarView, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        usernameTextView = new TextView(context);
        usernameTextView.setTextColor(Color.WHITE);
        usernameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        usernameTextView.setTypeface(AndroidUtilities.bold());
        usernameTextView.setShadowLayer(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(1), 0xAA000000);
        userRow.addView(usernameTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        captionTextView = new TextView(context);
        captionTextView.setTextColor(Color.WHITE);
        captionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        captionTextView.setMaxLines(3);
        captionTextView.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(1), 0xAA000000);
        bottomInfoLayout.addView(captionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        musicTextView = new TextView(context);
        musicTextView.setTextColor(0xDDFFFFFF);
        musicTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        musicTextView.setText("♫ صدای اصلی • Original Audio");
        musicTextView.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(1), 0xAA000000);
        bottomInfoLayout.addView(musicTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
    }

    public void setParentFragment(BaseFragment fragment) {
        this.parentFragment = fragment;
    }

    public void bindReel(
            String username,
            String avatarUrl,
            String previewUrl,
            String videoUrl,
            int likes,
            String caption,
            String reelLink
    ) {
        this.currentVideoUrl = videoUrl;
        this.likesCount = likes;
        this.currentCaption = caption != null ? caption : "";
        this.currentReelLink = reelLink != null ? reelLink : "https://instagram.com/reels";

        usernameTextView.setText(username);
        captionTextView.setText(currentCaption);
        likeCountTextView.setText(String.format("%,d", likesCount));

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            avatarView.setImage(avatarUrl, "36_36", null);
        } else {
            avatarView.clearImage();
        }

        if (previewUrl != null && !previewUrl.isEmpty()) {
            previewImageView.setImage(previewUrl, null, null);
            previewImageView.setVisibility(VISIBLE);
        }
    }

    public void play() {
        if (currentVideoUrl == null || currentVideoUrl.isEmpty()) return;

        if (player == null) {
            player = new VideoPlayer();
            player.setTextureView(textureView);
            player.preparePlayer(Uri.parse(currentVideoUrl), "other");
            player.setPlayWhenReady(true);
            player.setLooping(true);
        } else {
            player.play();
        }
        isPlaying = true;
        AndroidUtilities.runOnUIThread(() -> previewImageView.setVisibility(GONE), 300);
    }

    public void pause() {
        if (player != null) {
            player.pause();
        }
        isPlaying = false;
    }

    public void release() {
        if (player != null) {
            player.releasePlayer(true);
            player = null;
        }
        isPlaying = false;
        previewImageView.setVisibility(VISIBLE);
    }

    private void togglePlayPause() {
        if (isPlaying) {
            pause();
        } else {
            play();
        }
    }

    private void toggleLike() {
        isLiked = !isLiked;
        likesCount += isLiked ? 1 : -1;
        if (isLiked) {
            likeButton.setImageResource(R.drawable.msg_reactions_filled);
            likeButton.setColorFilter(0xFFED4956);
        } else {
            likeButton.setImageResource(R.drawable.msg_reactions);
            likeButton.setColorFilter(Color.WHITE);
        }
        likeCountTextView.setText(String.format("%,d", Math.max(0, likesCount)));

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(likeButton, View.SCALE_X, 1.0f, 1.35f, 1.0f),
                ObjectAnimator.ofFloat(likeButton, View.SCALE_Y, 1.0f, 1.35f, 1.0f)
        );
        set.setDuration(280);
        set.start();
    }

    private void shareToTelegram() {
        if (parentFragment != null && parentFragment.getParentActivity() != null) {
            String forwardText = currentCaption.length() > 100 ? currentCaption.substring(0, 97) + "..." : currentCaption;
            String shareMessage = "🎥 ریلز اینستاگرام:\n" + forwardText + "\n\n" + currentReelLink;
            parentFragment.showDialog(ShareAlert.createShareAlert(
                    parentFragment.getParentActivity(),
                    null,
                    shareMessage,
                    false,
                    currentReelLink,
                    false
            ));
        }
    }
}
