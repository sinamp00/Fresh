package org.fresh.instagram.model.clips

import com.google.gson.annotations.SerializedName
import org.fresh.instagram.model.feed.InstagramUser
import org.fresh.instagram.model.feed.MediaItem

/**
 * Response model for Reels / Clips discovery: POST /api/v1/clips/discover/
 */
data class ClipsResponse(
    @SerializedName("items") val items: List<ClipItemWrapper> = emptyList(),
    @SerializedName("paging_info") val pagingInfo: PagingInfo? = null,
    @SerializedName("status") val status: String? = null
) {
    data class ClipItemWrapper(
        @SerializedName("media") val media: MediaItem
    )

    data class PagingInfo(
        @SerializedName("max_id") val maxId: String? = null,
        @SerializedName("more_available") val moreAvailable: Boolean = false
    )
}

/**
 * Alias for discover response matching interface contract.
 */
typealias ClipsDiscoverResponse = ClipsResponse

/**
 * Response model for Stories Tray: POST /api/v1/feed/reels_tray/
 */
data class ReelsTrayResponse(
    @SerializedName("tray") val tray: List<StoryReelItem> = emptyList(),
    @SerializedName("status") val status: String? = null
)

/**
 * Single Story Reel Item representing a user's active story bundle.
 */
data class StoryReelItem(
    @SerializedName("id") val id: String,
    @SerializedName("latest_reel_media") val latestReelMedia: Long = 0L,
    @SerializedName("seen") val seen: Long = 0L,
    @SerializedName("user") val user: InstagramUser? = null,
    @SerializedName("items") val items: List<MediaItem>? = null,
    @SerializedName("can_reply") val canReply: Boolean? = null,
    @SerializedName("can_reshare") val canReshare: Boolean? = null,
    @SerializedName("is_muted") val isMuted: Boolean? = null,
    @SerializedName("has_besties_media") val hasBestiesMedia: Boolean? = null,
    @SerializedName("media_count") val mediaCount: Int = 0
) {
    val hasUnseenMedia: Boolean
        get() = latestReelMedia > seen
}

/**
 * Response model for individual user's reels: POST /api/v1/clips/user/
 */
data class UserClipsResponse(
    @SerializedName("items") val items: List<ClipsResponse.ClipItemWrapper> = emptyList(),
    @SerializedName("paging_info") val pagingInfo: ClipsResponse.PagingInfo? = null,
    @SerializedName("status") val status: String? = null
)
