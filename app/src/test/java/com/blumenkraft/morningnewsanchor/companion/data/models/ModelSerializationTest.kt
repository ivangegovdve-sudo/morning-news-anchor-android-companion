package com.blumenkraft.morningnewsanchor.companion.data.models

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the column-name mapping between these models and the live Postgres schema.
 *
 * The JSON below is shaped exactly like a Postgrest row for each table, copied from the web
 * app's generated `types.ts`. If someone renames a column in a migration and doesn't update
 * the `@SerialName`, these tests fail — which is much better than a null field at runtime.
 */
class ModelSerializationTest {

    /** Must match SupabaseModule.provideJson(). The `explicitNulls` behaviour is load-bearing. */
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun `briefing decodes a full row`() {
        val row = """
            {
              "id": "b1e7c0de-0000-4000-8000-000000000001",
              "user_id": "u1e7c0de-0000-4000-8000-000000000002",
              "briefing_date": "2026-07-15",
              "status": "ready",
              "intro_text": "Good morning.",
              "outro_text": "That's all.",
              "narrative_script": "…",
              "intro_audio_url": "u1e7c0de/b1e7c0de/intro.mp3",
              "sent_at": null,
              "error": null,
              "drive_folder_id": null,
              "drive_backup_at": null,
              "created_at": "2026-07-15T05:30:00Z",
              "updated_at": "2026-07-15T05:35:00Z"
            }
        """.trimIndent()

        val briefing = json.decodeFromString<Briefing>(row)

        assertEquals("2026-07-15", briefing.briefingDate)
        assertEquals("u1e7c0de/b1e7c0de/intro.mp3", briefing.introAudioUrl)
        assertTrue(briefing.isReady)
        assertFalse(briefing.hasFailed)
    }

    @Test
    fun `briefing tolerates a column the app does not know about`() {
        // The web app owns this schema; adding a column must not break the client.
        val row = """
            {
              "id": "b1", "user_id": "u1", "briefing_date": "2026-07-15",
              "status": "pending", "created_at": "2026-07-15T05:30:00Z",
              "updated_at": "2026-07-15T05:30:00Z",
              "some_future_column": {"nested": true}
            }
        """.trimIndent()

        val briefing = json.decodeFromString<Briefing>(row)

        assertEquals(Briefing.STATUS_PENDING, briefing.status)
        assertFalse(briefing.isReady)
    }

    @Test
    fun `briefing story maps audio_url and position`() {
        val row = """
            {
              "id": "s1", "briefing_id": "b1", "user_id": "u1",
              "position": 3,
              "headline": "Something happened",
              "summary": "A summary.",
              "narration_text": "As read aloud.",
              "audio_url": "u1/b1/story-03.mp3",
              "image_url": null,
              "source_name": "Reuters",
              "source_url": "https://example.com/a",
              "category": "world",
              "edited": false,
              "drive_file_id": null,
              "created_at": "2026-07-15T05:31:00Z",
              "updated_at": "2026-07-15T05:31:00Z"
            }
        """.trimIndent()

        val story = json.decodeFromString<BriefingStory>(row)

        assertEquals(3, story.position)
        assertEquals("u1/b1/story-03.mp3", story.audioUrl)
        assertEquals("Reuters", story.sourceName)
    }

    @Test
    fun `preferences decodes the real column set`() {
        val row = """
            {
              "user_id": "u1",
              "display_name": "Ivan",
              "anchor_name": "Chloe",
              "delivery_local_time": "08:00:00",
              "timezone": "Europe/Sofia",
              "tone": "warm",
              "narration_style_prompt": "Be brief.",
              "interests": ["ai", "cycling"],
              "skip_topics": ["celebrity"],
              "voice_provider": "elevenlabs",
              "elevenlabs_voice_id": "xNtG3W2oqJs0cJZuTyBc",
              "auto_send": true,
              "backup_to_drive": false,
              "drive_folder_name": "Briefings",
              "drive_root_folder_id": null,
              "telegram_chat_id": null,
              "onboarding_done": true,
              "onboarding_step": 5,
              "created_at": "2026-07-01T00:00:00Z",
              "updated_at": "2026-07-15T00:00:00Z"
            }
        """.trimIndent()

        val prefs = json.decodeFromString<Preferences>(row)

        assertEquals("Europe/Sofia", prefs.timezone)
        assertEquals("08:00:00", prefs.deliveryLocalTime)
        assertEquals(listOf("ai", "cycling"), prefs.interests)
        assertTrue(prefs.autoSend)
        assertTrue(prefs.onboardingDone)
        assertEquals(VoiceProvider.ELEVENLABS, VoiceProvider.fromId(prefs.voiceProvider))
    }

    /**
     * The whole partial-update strategy rests on this: a null field must be *absent* from the
     * PATCH body. If it serialized as `"timezone": null`, saving one preference would wipe
     * every other column on the row.
     */
    @Test
    fun `preferences update omits null fields entirely`() {
        val update = PreferencesUpdate(autoSend = false)

        val encoded = json.encodeToString(PreferencesUpdate.serializer(), update)
        val keys = json.parseToJsonElement(encoded).jsonObject.keys

        assertEquals(setOf("auto_send"), keys)
        assertFalse(encoded.contains("timezone"))
        assertFalse(encoded.contains("null"))
    }

    @Test
    fun `feed decodes with tags array and kind`() {
        val row = """
            {
              "id": "f1", "user_id": "u1",
              "name": "Hacker News",
              "url": "https://news.ycombinator.com/rss",
              "kind": "rss",
              "tags": ["tech"],
              "enabled": true,
              "created_at": "2026-07-01T00:00:00Z",
              "updated_at": "2026-07-01T00:00:00Z"
            }
        """.trimIndent()

        val feed = json.decodeFromString<Feed>(row)

        assertEquals(FeedKind.RSS, feed.kind)
        assertEquals(listOf("tech"), feed.tags)
        assertTrue(feed.enabled)
    }

    /** `finished_at`, not `completed_at`; `kind`, not `type`; no `items_ingested`. */
    @Test
    fun `cron run decodes finished_at and json details`() {
        val row = """
            {
              "id": "c1",
              "user_id": "u1",
              "kind": "morning-briefing",
              "status": "ok",
              "started_at": "2026-07-15T05:30:00Z",
              "finished_at": "2026-07-15T05:36:00Z",
              "error": null,
              "details": {"briefingId": "b1", "stories": 7}
            }
        """.trimIndent()

        val run = json.decodeFromString<CronRun>(row)

        assertEquals("morning-briefing", run.kind)
        assertEquals("2026-07-15T05:36:00Z", run.finishedAt)
        assertFalse(run.isRunning)
    }

    @Test
    fun `cron run with null user_id is the fleet-wide row`() {
        val row = """
            {
              "id": "c2", "user_id": null, "kind": "sweep", "status": "running",
              "started_at": "2026-07-15T05:30:00Z"
            }
        """.trimIndent()

        val run = json.decodeFromString<CronRun>(row)

        assertNull(run.userId)
        assertTrue(run.isRunning)
    }

    @Test
    fun `ingested item decodes external_id and used_in_briefing_id`() {
        val row = """
            {
              "id": "i1", "user_id": "u1",
              "external_id": "https://example.com/a",
              "source": "rss",
              "title": "Headline",
              "url": "https://example.com/a",
              "sender": null,
              "raw_text": "body",
              "image_url": null,
              "classification": "news",
              "catalog_id": null,
              "used_in_briefing_id": "b1",
              "archived": false,
              "published_at": "2026-07-15T04:00:00Z",
              "created_at": "2026-07-15T05:00:00Z"
            }
        """.trimIndent()

        val item = json.decodeFromString<IngestedItem>(row)

        assertEquals("https://example.com/a", item.externalId)
        assertEquals("b1", item.usedInBriefingId)
        assertFalse(item.archived)
    }

    @Test
    fun `newsletter catalog and subscription decode`() {
        val catalogRow = """
            {
              "id": "n1", "slug": "stratechery", "name": "Stratechery",
              "publisher": "Ben Thompson", "description": null, "category": "tech",
              "logo_url": null, "subscribe_url": null,
              "sender_email_pattern": "%@stratechery.com",
              "is_featured": true, "sort_order": 1,
              "created_at": "2026-07-01T00:00:00Z", "updated_at": "2026-07-01T00:00:00Z"
            }
        """.trimIndent()

        val subRow = """
            {
              "id": "s1", "user_id": "u1", "catalog_id": "n1",
              "status": "active", "paused": false,
              "added_at": "2026-07-01T00:00:00Z", "updated_at": "2026-07-01T00:00:00Z"
            }
        """.trimIndent()

        val catalog = json.decodeFromString<NewsletterCatalogEntry>(catalogRow)
        val sub = json.decodeFromString<NewsletterSubscription>(subRow)
        val joined = CatalogItemWithSubscription(catalog, sub)

        assertTrue(catalog.isFeatured)
        assertEquals("%@stratechery.com", catalog.senderEmailPattern)
        assertTrue(joined.isSubscribed)
        assertFalse(joined.isPaused)
    }

    @Test
    fun `unsubscribed catalog item reports not subscribed`() {
        val catalog = NewsletterCatalogEntry(id = "n1", slug = "s", name = "N")
        val removed = NewsletterSubscription(
            id = "s1", userId = "u1", catalogId = "n1",
            status = SubscriptionStatus.REMOVED,
        )

        assertFalse(CatalogItemWithSubscription(catalog, null).isSubscribed)
        assertFalse(CatalogItemWithSubscription(catalog, removed).isSubscribed)
    }

    @Test
    fun `archive log decodes`() {
        val row = """
            {
              "id": "a1", "user_id": "u1",
              "gmail_message_id": "18f0a",
              "reason": "older_than_30d",
              "sender": "news@example.com",
              "subject": "Weekly digest",
              "archived_at": "2026-07-14T09:00:00Z"
            }
        """.trimIndent()

        val entry = json.decodeFromString<ArchiveLogEntry>(row)

        assertEquals("18f0a", entry.gmailMessageId)
        assertEquals("older_than_30d", entry.reason)
    }

    @Test
    fun `voice provider falls back to elevenlabs for unknown ids`() {
        assertEquals(VoiceProvider.ELEVENLABS, VoiceProvider.fromId(null))
        assertEquals(VoiceProvider.ELEVENLABS, VoiceProvider.fromId("something-new"))
        assertEquals(VoiceProvider.POCKET_TTS, VoiceProvider.fromId("pocket-tts"))
        // pocket-tts is the one provider the server refuses to synthesize for.
        assertFalse(VoiceProvider.POCKET_TTS.serverSide)
        assertTrue(VoiceProvider.CARTESIA.serverSide)
    }
}
