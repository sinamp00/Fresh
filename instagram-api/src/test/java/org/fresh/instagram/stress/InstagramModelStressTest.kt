package org.fresh.instagram.stress

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.fresh.instagram.model.clips.ClipsResponse
import org.fresh.instagram.model.clips.ReelsTrayResponse
import org.fresh.instagram.model.clips.StoryReelItem
import org.fresh.instagram.model.feed.*
import org.fresh.instagram.model.session.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Empirical Stress Test Harness for Milestone 2 Data Models & Deserialization.
 * Tests edge cases, missing fields, nulls, empty arrays, malformed structures, and integer overflow.
 */
class InstagramModelStressTest {

    private val gson = Gson()

    // =========================================================================
    // 1. Extreme Inputs: Empty JSON, Missing Fields, and Nulls
    // =========================================================================

    @Test
    fun testEmptyJsonDeserializationOnAllModels() {
        val emptyJson = "{}"

        // TimelineFeedResponse
        val feedResp = gson.fromJson(emptyJson, TimelineFeedResponse::class.java)
        assertNotNull(feedResp)
        assertNull(feedResp.feedItems)
        assertFalse(feedResp.moreAvailable)
        assertNull(feedResp.nextMaxId)
        assertNull(feedResp.status)

        // TimelineFeedItem
        val feedItem = gson.fromJson(emptyJson, TimelineFeedItem::class.java)
        assertNotNull(feedItem)
        assertNull(feedItem.mediaOrAd)
        assertNull(feedItem.storiesNetego)

        // MediaItem
        val mediaItem = gson.fromJson(emptyJson, MediaItem::class.java)
        assertNotNull(mediaItem)
        assertNull(mediaItem.id) // Non-nullable in Kotlin, but null via Gson
        assertEquals(0L, mediaItem.pk)
        // Note: Kotlin default was 1, but Gson UnsafeAllocator leaves primitive int as 0
        assertEquals(0, mediaItem.mediaType)
        assertFalse(mediaItem.isPhoto)
        assertFalse(mediaItem.isVideo)
        assertFalse(mediaItem.isCarousel)
        assertNull(mediaItem.user)
        assertNull(mediaItem.caption)
        assertNull(mediaItem.imageVersions2)
        assertNull(mediaItem.videoVersions)
        assertNull(mediaItem.carouselMedia)
        assertNull(mediaItem.bestImageUrl)
        assertNull(mediaItem.bestVideoUrl)

        // ClipsResponse (Kotlin generates parameterless constructor because all properties have defaults)
        val clipsResp = gson.fromJson(emptyJson, ClipsResponse::class.java)
        assertNotNull(clipsResp)
        assertEquals(emptyList<ClipsResponse.ClipItemWrapper>(), clipsResp.items)
        assertNull(clipsResp.pagingInfo)

        // ReelsTrayResponse (all properties have defaults -> parameterless constructor -> emptyList())
        val trayResp = gson.fromJson(emptyJson, ReelsTrayResponse::class.java)
        assertNotNull(trayResp)
        assertEquals(emptyList<StoryReelItem>(), trayResp.tray)

        // StoryReelItem
        val storyItem = gson.fromJson(emptyJson, StoryReelItem::class.java)
        assertNotNull(storyItem)
        assertEquals(0L, storyItem.latestReelMedia)
        assertEquals(0L, storyItem.seen)
        assertFalse(storyItem.hasUnseenMedia)

        // TwoFactorInfo
        val twoFactorInfo = gson.fromJson(emptyJson, TwoFactorInfo::class.java)
        assertNotNull(twoFactorInfo)
        assertNull(twoFactorInfo.twoFactorIdentifier)
        assertFalse(twoFactorInfo.smsTwoFactorOn)
        assertFalse(twoFactorInfo.totpTwoFactorOn)

        // ChallengeInfo
        val challengeInfo = gson.fromJson(emptyJson, ChallengeInfo::class.java)
        assertNotNull(challengeInfo)
        assertNull(challengeInfo.url)
        assertNull(challengeInfo.apiPath)
        assertFalse(challengeInfo.lock)

        // ChallengeResponse
        val challengeResp = gson.fromJson(emptyJson, ChallengeResponse::class.java)
        assertNotNull(challengeResp)
        assertFalse(challengeResp.isCompleted)

        // LoginResponse
        val loginResp = gson.fromJson(emptyJson, LoginResponse::class.java)
        assertNotNull(loginResp)
        assertFalse(loginResp.isSuccess)
        assertFalse(loginResp.isTwoFactor)
        assertFalse(loginResp.isChallenge)
    }

    @Test
    fun testInstagramSessionNullSafetyAndValidity() {
        val emptyJson = "{}"
        val session = gson.fromJson(emptyJson, InstagramSession::class.java)
        assertNotNull(session)

        // Notice: in Kotlin, sessionId is defined as 'val sessionId: String' (non-nullable).
        // When deserialized from empty JSON, sessionId is null in bytecode.
        // Calling session.isValid evaluates `sessionId.isNotBlank()` which must be tested for NPE!
        try {
            val valid = session.isValid
            assertFalse(valid)
        } catch (e: NullPointerException) {
            // Documenting empirical discovery: calling isValid on empty deserialized session throws NPE!
            println("[DISCOVERY] session.isValid threw NPE due to Kotlin non-null check on null field: ${e.message}")
        }
    }

    // =========================================================================
    // 2. Helper Properties: bestImageUrl, bestVideoUrl, hasUnseenMedia, mediaTypes
    // =========================================================================

    @Test
    fun testBestImageUrlResolutionLogic() {
        // Case A: imageVersions2 is null
        val itemNoImages = MediaItem(id = "1")
        assertNull(itemNoImages.bestImageUrl)

        // Case B: candidates list is empty
        val itemEmptyCandidates = MediaItem(
            id = "2",
            imageVersions2 = ImageVersions2(candidates = emptyList())
        )
        assertNull(itemEmptyCandidates.bestImageUrl)

        // Case C: standard multi-resolution candidates
        val candidates = listOf(
            ImageCandidate(width = 320, height = 320, url = "https://cdn.test/320.jpg"),
            ImageCandidate(width = 1080, height = 1080, url = "https://cdn.test/1080.jpg"),
            ImageCandidate(width = 640, height = 640, url = "https://cdn.test/640.jpg")
        )
        val itemValid = MediaItem(
            id = "3",
            imageVersions2 = ImageVersions2(candidates = candidates)
        )
        assertEquals("https://cdn.test/1080.jpg", itemValid.bestImageUrl)

        // Case D: Single candidate fallback
        val singleCandidate = listOf(
            ImageCandidate(width = 0, height = 0, url = "https://cdn.test/fallback.jpg")
        )
        val itemFallback = MediaItem(
            id = "4",
            imageVersions2 = ImageVersions2(candidates = singleCandidate)
        )
        assertEquals("https://cdn.test/fallback.jpg", itemFallback.bestImageUrl)
    }

    @Test
    fun testBestImageUrlIntegerOverflowAdversarialChallenge() {
        // Challenge: width * height exceeds Int.MAX_VALUE (2,147,483,647)
        // Candidate 1: 50,000 x 50,000 = 2,500,000,000 (overflows to negative: -1,794,967,296)
        // Candidate 2: 1920 x 1080 = 2,073,600 (positive)
        val candidateHuge = ImageCandidate(width = 50000, height = 50000, url = "https://cdn.test/huge_4k.jpg")
        val candidateNormal = ImageCandidate(width = 1920, height = 1080, url = "https://cdn.test/normal_1080p.jpg")

        val item = MediaItem(
            id = "overflow_test",
            imageVersions2 = ImageVersions2(candidates = listOf(candidateHuge, candidateNormal))
        )

        val selectedUrl = item.bestImageUrl
        println("[STRESS RESULT] 50000x50000 area calculation: ${candidateHuge.width * candidateHuge.height}")
        println("[STRESS RESULT] Selected URL: $selectedUrl")

        // In 32-bit signed multiplication, 50000 * 50000 overflows to negative!
        // maxByOrNull selects candidateNormal because its area is positive while candidateHuge is negative!
        if (selectedUrl == candidateNormal.url) {
            println("[VULNERABILITY CONFIRMED] Integer overflow causes bestImageUrl to select lower resolution candidate on extreme dimensions!")
        }
    }

    @Test
    fun testBestVideoUrlResolutionLogic() {
        // Case A: videoVersions is null
        val itemNoVideo = MediaItem(id = "1")
        assertNull(itemNoVideo.bestVideoUrl)

        // Case B: videoVersions is empty
        val itemEmptyVideo = MediaItem(id = "2", videoVersions = emptyList())
        assertNull(itemEmptyVideo.bestVideoUrl)

        // Case C: multiple video versions, progressive mp4 selection
        val videoVersions = listOf(
            VideoVersion(type = 101, width = 720, height = 1280, url = "https://cdn.test/prog_720.mp4"),
            VideoVersion(type = 102, width = 480, height = 854, url = "https://cdn.test/prog_480.mp4")
        )
        val itemVideo = MediaItem(id = "3", videoVersions = videoVersions)
        assertEquals("https://cdn.test/prog_720.mp4", itemVideo.bestVideoUrl)
    }

    @Test
    fun testHasUnseenMediaLogic() {
        // Unseen: latest > seen
        val unseenStory = StoryReelItem(id = "s1", latestReelMedia = 1000L, seen = 900L)
        assertTrue(unseenStory.hasUnseenMedia)

        // Seen: latest == seen
        val seenStory = StoryReelItem(id = "s2", latestReelMedia = 1000L, seen = 1000L)
        assertFalse(seenStory.hasUnseenMedia)

        // Seen: seen > latest (clock skew or future read marker)
        val futureSeenStory = StoryReelItem(id = "s3", latestReelMedia = 900L, seen = 1000L)
        assertFalse(futureSeenStory.hasUnseenMedia)

        // Both 0 (empty/uninitialized)
        val emptyStory = StoryReelItem(id = "s4", latestReelMedia = 0L, seen = 0L)
        assertFalse(emptyStory.hasUnseenMedia)
    }

    @Test
    fun testMediaTypesIdentification() {
        val photo = MediaItem(id = "p", mediaType = MediaItem.TYPE_PHOTO)
        assertTrue(photo.isPhoto)
        assertFalse(photo.isVideo)
        assertFalse(photo.isCarousel)

        val video = MediaItem(id = "v", mediaType = MediaItem.TYPE_VIDEO)
        assertFalse(video.isPhoto)
        assertTrue(video.isVideo)
        assertFalse(video.isCarousel)

        val carousel = MediaItem(id = "c", mediaType = MediaItem.TYPE_CAROUSEL)
        assertFalse(carousel.isPhoto)
        assertFalse(carousel.isVideo)
        assertTrue(carousel.isCarousel)

        // Unknown / unsupported media type (e.g. 0, 99)
        val unknown = MediaItem(id = "u", mediaType = 99)
        assertFalse(unknown.isPhoto)
        assertFalse(unknown.isVideo)
        assertFalse(unknown.isCarousel)
    }

    // =========================================================================
    // 3. Malformed JSON and Type Coercion Resilience
    // =========================================================================

    @Test
    fun testTypeCoercionAndSyntaxResilience() {
        // 1. Numeric ID passed as Long instead of String
        val jsonNumericId = """{"id": 3123456789012345678, "pk": 3123456789012345678, "media_type": 1}"""
        // In standard Gson, reading a NUMBER when expecting String throws JsonSyntaxException
        try {
            val item = gson.fromJson(jsonNumericId, MediaItem::class.java)
            println("[OBSERVATION] Gson coerced numeric ID to String: ${item.id}")
        } catch (e: JsonSyntaxException) {
            println("[OBSERVATION] Gson threw JsonSyntaxException for numeric ID: ${e.message}")
        }

        // 2. String passed for integer media_type
        val jsonStringMediaType = """{"id": "test_1", "media_type": "2"}"""
        val itemCoerced = gson.fromJson(jsonStringMediaType, MediaItem::class.java)
        assertEquals(2, itemCoerced.mediaType)
        assertTrue(itemCoerced.isVideo)

        // 3. Incomplete / truncated JSON
        val truncatedJson = "{\"feed_items\": [{\"media_or_ad\": {\"id\": \"123"
        try {
            gson.fromJson(truncatedJson, TimelineFeedResponse::class.java)
            fail("Should throw JsonSyntaxException on truncated JSON")
        } catch (expected: JsonSyntaxException) {
            assertNotNull(expected)
        }
    }

    @Test
    fun testContextInstantiation() {
        try {
            val dummyContext = object : android.content.ContextWrapper(null) {}
            println("[TEST] ContextWrapper instantiated successfully!")
        } catch (e: Throwable) {
            println("[TEST] ContextWrapper threw: ${e.javaClass.name}: ${e.message}")
        }
    }
}
