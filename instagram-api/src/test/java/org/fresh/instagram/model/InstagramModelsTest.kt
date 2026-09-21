package org.fresh.instagram.model

import com.google.gson.Gson
import org.fresh.instagram.model.clips.ClipsResponse
import org.fresh.instagram.model.clips.ReelsTrayResponse
import org.fresh.instagram.model.feed.*
import org.fresh.instagram.model.session.*
import org.junit.Assert.*
import org.junit.Test

class InstagramModelsTest {

    private val gson = Gson()

    @Test
    fun testTimelineFeedResponseSerialization() {
        val sampleJson = """
        {
            "feed_items": [
                {
                    "media_or_ad": {
                        "id": "3123456789012345678_123456",
                        "pk": 3123456789012345678,
                        "media_type": 1,
                        "code": "C_abcdef123",
                        "taken_at": 1726880000,
                        "user": {
                            "pk": 123456,
                            "username": "fresh_user",
                            "full_name": "Fresh User",
                            "profile_pic_url": "https://instagram.fhrk1-1.fna.fbcdn.net/v/avatar.jpg",
                            "is_verified": true
                        },
                        "caption": {
                            "pk": 987654321,
                            "text": "Hello Fresh SuperApp!"
                        },
                        "like_count": 4200,
                        "has_liked": false,
                        "comment_count": 1337,
                        "image_versions2": {
                            "candidates": [
                                {
                                    "width": 640,
                                    "height": 640,
                                    "url": "https://instagram.fhrk1-1.fna.fbcdn.net/v/640.jpg"
                                },
                                {
                                    "width": 1080,
                                    "height": 1080,
                                    "url": "https://instagram.fhrk1-1.fna.fbcdn.net/v/1080.jpg"
                                }
                            ]
                        }
                    }
                }
            ],
            "more_available": true,
            "next_max_id": "QVFBWEZYWXlGOGp...",
            "status": "ok"
        }
        """.trimIndent()

        val response = gson.fromJson(sampleJson, TimelineFeedResponse::class.java)
        assertNotNull(response)
        assertEquals("ok", response.status)
        assertTrue(response.moreAvailable)
        assertEquals("QVFBWEZYWXlGOGp...", response.nextMaxId)

        val item = response.feedItems?.firstOrNull()?.mediaOrAd
        assertNotNull(item)
        assertEquals("fresh_user", item?.user?.username)
        assertTrue(item?.isPhoto == true)
        assertFalse(item?.isVideo == true)
        assertEquals(4200, item?.likeCount)
        assertEquals("https://instagram.fhrk1-1.fna.fbcdn.net/v/1080.jpg", item?.bestImageUrl)
    }

    @Test
    fun testClipsDiscoverResponseSerialization() {
        val sampleJson = """
        {
            "items": [
                {
                    "media": {
                        "id": "3999999999999999999_99999",
                        "pk": 3999999999999999999,
                        "media_type": 2,
                        "code": "C_reel12345",
                        "user": {
                            "pk": 99999,
                            "username": "reels_creator"
                        },
                        "like_count": 89000,
                        "video_versions": [
                            {
                                "type": 101,
                                "width": 720,
                                "height": 1280,
                                "url": "https://instagram.fhrk1-1.fna.fbcdn.net/v/progressive_reel.mp4"
                            }
                        ],
                        "clips_metadata": {
                            "music_info": {
                                "music_asset_info": {
                                    "title": "Fresh Beat",
                                    "display_artist": "Fresh Audio"
                                }
                            }
                        }
                    }
                }
            ],
            "paging_info": {
                "max_id": "cursor_page_2",
                "more_available": true
            },
            "status": "ok"
        }
        """.trimIndent()

        val response = gson.fromJson(sampleJson, ClipsResponse::class.java)
        assertNotNull(response)
        assertEquals(1, response.items.size)
        assertEquals("cursor_page_2", response.pagingInfo?.maxId)

        val media = response.items[0].media
        assertTrue(media.isVideo)
        assertEquals("https://instagram.fhrk1-1.fna.fbcdn.net/v/progressive_reel.mp4", media.bestVideoUrl)
        assertEquals("Fresh Beat", media.clipsMetadata?.musicInfo?.musicAssetInfo?.title)
    }

    @Test
    fun testReelsTrayResponseSerialization() {
        val sampleJson = """
        {
            "tray": [
                {
                    "id": "user_story_12345",
                    "latest_reel_media": 1726885000,
                    "seen": 1726880000,
                    "user": {
                        "pk": 12345,
                        "username": "story_teller"
                    },
                    "media_count": 3
                }
            ],
            "status": "ok"
        }
        """.trimIndent()

        val response = gson.fromJson(sampleJson, ReelsTrayResponse::class.java)
        assertNotNull(response)
        assertEquals(1, response.tray.size)
        val reel = response.tray[0]
        assertEquals("story_teller", reel.user?.username)
        assertTrue(reel.hasUnseenMedia)
    }

    @Test
    fun testSessionModelsSerialization() {
        val session = InstagramSession(
            userId = 12345678L,
            username = "fresh_tester",
            fullName = "Fresh Tester",
            profilePicUrl = "https://instagram.com/pic.jpg",
            sessionId = "session_token_123",
            csrfToken = "csrf_token_abc",
            dsUserId = "12345678",
            deviceId = "android-1234567890abcdef",
            uuid = "uuid-1234",
            phoneId = "phone-1234",
            adid = "adid-1234"
        )

        assertTrue(session.isValid)
        val json = gson.toJson(session)
        val deserialized = gson.fromJson(json, InstagramSession::class.java)
        assertEquals(session.userId, deserialized.userId)
        assertEquals(session.username, deserialized.username)
        assertEquals(session.sessionId, deserialized.sessionId)
        assertEquals(session.csrfToken, deserialized.csrfToken)
    }

    @Test
    fun testTwoFactorAndChallengeLoginResponses() {
        val twoFactorJson = """
        {
            "message": "checkpoint_required",
            "two_factor_required": true,
            "two_factor_info": {
                "two_factor_identifier": "2fa_id_999",
                "sms_two_factor_on": true,
                "totp_two_factor_on": false,
                "obfuscated_phone_number": "1234"
            },
            "status": "fail"
        }
        """.trimIndent()

        val resp2Fa = gson.fromJson(twoFactorJson, LoginResponse::class.java)
        assertTrue(resp2Fa.isTwoFactor)
        assertEquals("2fa_id_999", resp2Fa.twoFactorInfo?.twoFactorIdentifier)
        assertEquals("1234", resp2Fa.twoFactorInfo?.obfuscatedPhoneNumber)

        val challengeJson = """
        {
            "message": "challenge_required",
            "challenge": {
                "url": "https://i.instagram.com/challenge/123/abc/",
                "api_path": "/challenge/123/abc/",
                "lock": true
            },
            "status": "fail"
        }
        """.trimIndent()

        val respChallenge = gson.fromJson(challengeJson, LoginResponse::class.java)
        assertTrue(respChallenge.isChallenge)
        assertEquals("/challenge/123/abc/", respChallenge.challenge?.apiPath)
    }
}
