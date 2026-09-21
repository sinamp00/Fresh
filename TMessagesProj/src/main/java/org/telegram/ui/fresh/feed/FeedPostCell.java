package org.telegram.ui.fresh.feed;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
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

public class FeedPostCell extends FrameLayout {

    private final BackupImageView avatarView;
    private final TextView usernameTextView;
    private final TextView timeTextView;
    private final BackupImageView postImageView;
    private final ImageView likeButton;
    private final TextView likesCountTextView;
    private final ImageView commentButton;
    private final ImageView shareButton;
    private final TextView captionTextView;
    private final View bottomDivider;

    private boolean isLiked = false;
    private int likesCount = 0;
    private String currentPostLink = "";
    private String currentCaption = "";
    private BaseFragment parentFragment;

    public FeedPostCell(@NonNull Context context) {
        super(context);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        // 1. Header Row (Avatar, Username, Time)
        LinearLayout headerLayout = new LinearLayout(context);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(10));
        addView(headerLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP));

        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(18));
        headerLayout.addView(avatarView, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        LinearLayout userMetaLayout = new LinearLayout(context);
        userMetaLayout.setOrientation(LinearLayout.VERTICAL);
        headerLayout.addView(userMetaLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));

        usernameTextView = new TextView(context);
        usernameTextView.setTypeface(AndroidUtilities.bold());
        usernameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        usernameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        userMetaLayout.addView(usernameTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        timeTextView = new TextView(context);
        timeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        timeTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        userMetaLayout.addView(timeTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        // 2. Post Media (Image)
        postImageView = new BackupImageView(context);
        postImageView.getImageReceiver().setCrossfadeByScale(1);
        addView(postImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 340, Gravity.TOP, 0, 56, 0, 0));

        // 3. Action Bar (Like, Comment, Telegram Share)
        LinearLayout actionsLayout = new LinearLayout(context);
        actionsLayout.setOrientation(LinearLayout.HORIZONTAL);
        actionsLayout.setGravity(Gravity.CENTER_VERTICAL);
        actionsLayout.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(4));
        addView(actionsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.TOP, 0, 396, 0, 0));

        likeButton = new ImageView(context);
        likeButton.setImageResource(R.drawable.msg_reactions);
        likeButton.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        likeButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
        likeButton.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
        likeButton.setOnClickListener(v -> toggleLike());
        actionsLayout.addView(likeButton, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        commentButton = new ImageView(context);
        commentButton.setImageResource(R.drawable.menu_comments);
        commentButton.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        commentButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
        commentButton.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
        actionsLayout.addView(commentButton, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        shareButton = new ImageView(context);
        shareButton.setImageResource(R.drawable.msg_share);
        shareButton.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        shareButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
        shareButton.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
        shareButton.setOnClickListener(v -> shareToTelegram());
        actionsLayout.addView(shareButton, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL));

        // 4. Details (Likes count & Caption)
        LinearLayout detailsLayout = new LinearLayout(context);
        detailsLayout.setOrientation(LinearLayout.VERTICAL);
        detailsLayout.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        addView(detailsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 440, 0, 0));

        likesCountTextView = new TextView(context);
        likesCountTextView.setTypeface(AndroidUtilities.bold());
        likesCountTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        likesCountTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        detailsLayout.addView(likesCountTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        captionTextView = new TextView(context);
        captionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        captionTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        captionTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        detailsLayout.addView(captionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        bottomDivider = new View(context);
        bottomDivider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        addView(bottomDivider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM));
    }

    public void setParentFragment(BaseFragment fragment) {
        this.parentFragment = fragment;
    }

    public void bindPost(
            String username,
            String avatarUrl,
            String timeAgo,
            String imageUrl,
            int likes,
            String caption,
            String postUrl,
            boolean userLiked
    ) {
        this.likesCount = likes;
        this.isLiked = userLiked;
        this.currentPostLink = postUrl != null ? postUrl : "https://instagram.com";
        this.currentCaption = caption != null ? caption : "";

        usernameTextView.setText(username);
        timeTextView.setText(timeAgo);

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            avatarView.setImage(avatarUrl, "36_36", null);
        } else {
            avatarView.clearImage();
        }

        if (imageUrl != null && !imageUrl.isEmpty()) {
            postImageView.setImage(imageUrl, null, null);
        } else {
            postImageView.clearImage();
        }

        updateLikeState(false);

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        ssb.append(username).append(" ");
        ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, username.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.append(currentCaption);
        captionTextView.setText(ssb);
    }

    private void toggleLike() {
        isLiked = !isLiked;
        likesCount += isLiked ? 1 : -1;
        updateLikeState(true);
    }

    private void updateLikeState(boolean animated) {
        if (isLiked) {
            likeButton.setImageResource(R.drawable.msg_reactions_filled);
            likeButton.setColorFilter(0xFFED4956); // Instagram vibrant red
        } else {
            likeButton.setImageResource(R.drawable.msg_reactions);
            likeButton.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        }

        likesCountTextView.setText(String.format("%,d پسند", Math.max(0, likesCount)));

        if (animated) {
            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                    ObjectAnimator.ofFloat(likeButton, View.SCALE_X, 1.0f, 1.35f, 1.0f),
                    ObjectAnimator.ofFloat(likeButton, View.SCALE_Y, 1.0f, 1.35f, 1.0f)
            );
            set.setDuration(280);
            set.start();
        }
    }

    private void shareToTelegram() {
        if (parentFragment != null && parentFragment.getParentActivity() != null) {
            String forwardText = currentCaption.length() > 120 ? currentCaption.substring(0, 117) + "..." : currentCaption;
            String shareMessage = forwardText + "\n\n" + currentPostLink;
            parentFragment.showDialog(ShareAlert.createShareAlert(
                    parentFragment.getParentActivity(),
                    null,
                    shareMessage,
                    false,
                    currentPostLink,
                    false
            ));
        }
    }

    public void updateColors() {
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        usernameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        timeTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        likesCountTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        captionTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        commentButton.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        shareButton.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        bottomDivider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        if (!isLiked) {
            likeButton.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        }
    }
}
