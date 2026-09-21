package org.fresh.instagram.model.feed

import com.google.gson.annotations.SerializedName
import org.fresh.instagram.model.clips.StoryReelItem

/**
 * Root response model for POST /api/v1/feed/timeline/
 */
data class TimelineFeedResponse(
    @SerializedName("feed_items") val feedItems: List<TimelineFeedItem>? = null,
    @SerializedName("more_available") val moreAvailable: Boolean = false,
    @SerializedName("next_max_id") val nextMaxId: String? = null,
    @SerializedName("auto_load_more_enabled") val autoLoadMoreEnabled: Boolean? = null,
    @SerializedName("status") val status: String? = null
)

/**
 * Wrapper item in timeline feed containing either a media post or an injected stories netego tray.
 */
data class TimelineFeedItem(
    @SerializedName("media_or_ad") val mediaOrAd: MediaItem? = null,
    @SerializedName("stories_netego") val storiesNetego: StoriesNetego? = null
)

/**
 * Instagram Media Item (Photo, Video, Carousel, or Reel).
 */
data class MediaItem(
    @SerializedName("id") val id: String,
    @SerializedName("pk") val pk: Long = 0L,
    @SerializedName("media_type") val mediaType: Int = 1, // 1: Photo, 2: Video, 8: Carousel
    @SerializedName("code") val code: String? = null, // Shortcode (e.g. "C-123456")
    @SerializedName("taken_at") val takenAt: Long = 0L,
    @SerializedName("user") val user: InstagramUser? = null,
    @SerializedName("caption") val caption: MediaCaption? = null,
    @SerializedName("like_count") val likeCount: Int = 0,
    @SerializedName("has_liked") val hasLiked: Boolean = false,
    @SerializedName("comment_count") val commentCount: Int = 0,
    @SerializedName("view_count") val viewCount: Long? = null,
    @SerializedName("play_count") val playCount: Long? = null,
    @SerializedName("image_versions2") val imageVersions2: ImageVersions2? = null,
    @SerializedName("video_versions") val videoVersions: List<VideoVersion>? = null,
    @SerializedName("carousel_media") val carouselMedia: List<CarouselItem>? = null,
    @SerializedName("carousel_media_count") val carouselMediaCount: Int? = null,
    @SerializedName("clips_metadata") val clipsMetadata: ClipsMetadata? = null,
    @SerializedName("video_duration") val videoDuration: Double? = null,
    @SerializedName("has_audio") val hasAudio: Boolean? = null,
    @SerializedName("original_width") val originalWidth: Int = 0,
    @SerializedName("original_height") val originalHeight: Int = 0
) {
    companion object {
        const val TYPE_PHOTO = 1
        const val TYPE_VIDEO = 2
        const val TYPE_CAROUSEL = 8
    }

    val isPhoto: Boolean get() = mediaType == TYPE_PHOTO
    val isVideo: Boolean get() = mediaType == TYPE_VIDEO
    val isCarousel: Boolean get() = mediaType == TYPE_CAROUSEL

    /**
     * Resolves the highest resolution image candidate available.
     */
    val bestImageUrl: String?
        get() = imageVersions2?.candidates?.maxByOrNull { it.width * it.height }?.url
            ?: imageVersions2?.candidates?.firstOrNull()?.url

    /**
     * Resolves progressive MP4 video URL for playback.
     */
    val bestVideoUrl: String?
        get() = videoVersions?.firstOrNull()?.url
}

/**
 * Basic Instagram User representation embedded in posts, stories, and comments.
 */
data class InstagramUser(
    @SerializedName("pk") val pk: Long,
    @SerializedName("username") val username: String,
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("profile_pic_url") val profilePicUrl: String? = null,
    @SerializedName("profile_pic_id") val profilePicId: String? = null,
    @SerializedName("is_verified") val isVerified: Boolean = false,
    @SerializedName("is_private") val isPrivate: Boolean = false
)

/**
 * Post caption metadata.
 */
data class MediaCaption(
    @SerializedName("pk") val pk: Long = 0L,
    @SerializedName("user_id") val userId: Long = 0L,
    @SerializedName("text") val text: String = "",
    @SerializedName("created_at") val createdAt: Long = 0L,
    @SerializedName("created_at_utc") val createdAtUtc: Long = 0L
)

/**
 * Collection of image candidates in various resolutions.
 */
data class ImageVersions2(
    @SerializedName("candidates") val candidates: List<ImageCandidate> = emptyList()
)

/**
 * Single image resolution candidate.
 */
data class ImageCandidate(
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int,
    @SerializedName("url") val url: String,
    @SerializedName("scans_profile") val scansProfile: String? = null
)

/**
 * Single video resolution candidate (progressive MP4 stream).
 */
data class VideoVersion(
    @SerializedName("type") val type: Int = 101, // 101, 102
    @SerializedName("width") val width: Int = 0,
    @SerializedName("height") val height: Int = 0,
    @SerializedName("url") val url: String,
    @SerializedName("id") val id: String? = null
)

/**
 * Child item within an album/carousel post.
 */
data class CarouselItem(
    @SerializedName("id") val id: String,
    @SerializedName("pk") val pk: Long = 0L,
    @SerializedName("media_type") val mediaType: Int = 1, // 1: Photo, 2: Video
    @SerializedName("carousel_parent_id") val carouselParentId: String? = null,
    @SerializedName("image_versions2") val imageVersions2: ImageVersions2? = null,
    @SerializedName("video_versions") val videoVersions: List<VideoVersion>? = null,
    @SerializedName("original_width") val originalWidth: Int = 0,
    @SerializedName("original_height") val originalHeight: Int = 0
) {
    val bestImageUrl: String?
        get() = imageVersions2?.candidates?.maxByOrNull { it.width * it.height }?.url
            ?: imageVersions2?.candidates?.firstOrNull()?.url

    val bestVideoUrl: String?
        get() = videoVersions?.firstOrNull()?.url
}

/**
 * In-feed Stories Netego Tray.
 */
data class StoriesNetego(
    @SerializedName("tracking_token") val trackingToken: String? = null,
    @SerializedName("tray") val tray: List<StoryReelItem>? = null
)

/**
 * Metadata for Reels & audio tracks.
 */
data class ClipsMetadata(
    @SerializedName("music_info") val musicInfo: MusicInfo? = null,
    @SerializedName("original_sound_info") val originalSoundInfo: OriginalSoundInfo? = null,
    @SerializedName("audio_type") val audioType: String? = null
) {
    data class MusicInfo(
        @SerializedName("music_asset_info") val musicAssetInfo: MusicAssetInfo? = null
    )

    data class MusicAssetInfo(
        @SerializedName("id") val id: String? = null,
        @SerializedName("title") val title: String? = null,
        @SerializedName("display_artist") val displayArtist: String? = null,
        @SerializedName("progressive_download_url") val progressiveDownloadUrl: String? = null,
        @SerializedName("cover_artwork_uri") val coverArtworkUri: String? = null
    )

    data class OriginalSoundInfo(
        @SerializedName("audio_asset_id") val audioAssetId: Long = 0L,
        @SerializedName("original_audio_title") val originalAudioTitle: String? = null,
        @SerializedName("progressive_download_url") val progressiveDownloadUrl: String? = null,
        @SerializedName("ig_artist") val igArtist: InstagramUser? = null
    )
}
