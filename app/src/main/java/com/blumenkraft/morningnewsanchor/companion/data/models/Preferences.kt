package com.blumenkraft.morningnewsanchor.companion.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Row of `public.preferences` — one per user, created by the `on_auth_user_created`
 * trigger at first sign-in. The companion app never inserts one; it only updates.
 *
 * Note there is no `ai_provider` / `source_mode` / `ai_model` column and no API-key
 * columns. Provider API keys live in `user_provider_keys`, encrypted, and are not
 * readable from a client — see [UserProviderKey].
 */
@Serializable
data class Preferences(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("anchor_name") val anchorName: String = "",
    /** Postgres `time` rendered as `HH:MM:SS`, interpreted in [timezone]. */
    @SerialName("delivery_local_time") val deliveryLocalTime: String = "08:00:00",
    /** IANA zone id, e.g. `Europe/Sofia`. */
    val timezone: String = "UTC",
    val tone: String = "",
    @SerialName("narration_style_prompt") val narrationStylePrompt: String = "",
    val interests: List<String> = emptyList(),
    @SerialName("skip_topics") val skipTopics: List<String> = emptyList(),
    @SerialName("voice_provider") val voiceProvider: String = "",
    @SerialName("elevenlabs_voice_id") val elevenlabsVoiceId: String? = null,
    /** When false the daily cron skips this user entirely. */
    @SerialName("auto_send") val autoSend: Boolean = false,
    @SerialName("backup_to_drive") val backupToDrive: Boolean = false,
    @SerialName("drive_folder_name") val driveFolderName: String = "",
    @SerialName("drive_root_folder_id") val driveRootFolderId: String? = null,
    @SerialName("telegram_chat_id") val telegramChatId: String? = null,
    @SerialName("onboarding_done") val onboardingDone: Boolean = false,
    @SerialName("onboarding_step") val onboardingStep: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * Partial update payload. Postgrest serializes only the non-null fields when
 * `explicitNulls = false` is set on the Json instance (see SupabaseModule), so every
 * field left null is untouched by the PATCH.
 */
@Serializable
data class PreferencesUpdate(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("anchor_name") val anchorName: String? = null,
    @SerialName("delivery_local_time") val deliveryLocalTime: String? = null,
    val timezone: String? = null,
    val tone: String? = null,
    @SerialName("narration_style_prompt") val narrationStylePrompt: String? = null,
    val interests: List<String>? = null,
    @SerialName("skip_topics") val skipTopics: List<String>? = null,
    @SerialName("voice_provider") val voiceProvider: String? = null,
    @SerialName("elevenlabs_voice_id") val elevenlabsVoiceId: String? = null,
    @SerialName("auto_send") val autoSend: Boolean? = null,
    @SerialName("backup_to_drive") val backupToDrive: Boolean? = null,
)

/**
 * Row of `public.user_provider_keys`. `api_key_encrypted` is deliberately absent from this
 * model: the app shows only which providers have a key on file and lets the user replace
 * one. Never select the ciphertext into the client.
 */
@Serializable
data class UserProviderKey(
    val id: String,
    @SerialName("user_id") val userId: String,
    val provider: String,
    val label: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * Values accepted in [Preferences.voiceProvider], mirroring the web app's TTS registry
 * (`src/lib/tts/index.ts`) and the picker in its preferences screen.
 */
enum class VoiceProvider(val id: String, val label: String, val serverSide: Boolean) {
    ELEVENLABS("elevenlabs", "ElevenLabs (Chloe by default)", serverSide = true),
    CARTESIA("cartesia", "Cartesia.ai (Sonic)", serverSide = true),
    HUME("hume", "Hume.ai (Octave)", serverSide = true),
    OPENAI("openai", "OpenAI (gpt-4o-mini-tts)", serverSide = true),
    AZURE("azure", "Azure", serverSide = true),

    /** Placeholder in the web app — selectable, but synthesis is not implemented. */
    GOOGLE("google", "Google (Gemini TTS)", serverSide = true),

    /**
     * Browser-local synthesis. The server refuses to synthesize for this provider, so a
     * briefing generated while it is selected will have no audio for the companion app to
     * play. Surfaced in the picker with a warning rather than hidden, to stay consistent
     * with the web app.
     */
    POCKET_TTS("pocket-tts", "Pocket-TTS (local, in-browser)", serverSide = false),
    ;

    companion object {
        val default = ELEVENLABS

        fun fromId(id: String?): VoiceProvider =
            entries.firstOrNull { it.id == id } ?: default
    }
}
