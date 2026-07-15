package com.blumenkraft.morningnewsanchor.companion.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * The "Run now" endpoint.
 *
 * ─────────────────────────────────────────────────────────────────────────────────────
 * THIS ENDPOINT DOES NOT EXIST YET. It must be built on the backend before the Run Now
 * button can be enabled. Read this before wiring it to anything.
 * ─────────────────────────────────────────────────────────────────────────────────────
 *
 * The obvious move is to call the web app's existing cron endpoint, which already supports
 * a forced single-user run:
 *
 *     POST /api/cron/morning-briefing
 *     Authorization: Bearer <CRON_SECRET>
 *     { "force": true, "userId": "<uuid>", "skipDelivery": false }
 *
 * That must not be done from this app. Two reasons, both fatal:
 *
 *  1. `CRON_SECRET` is a shared, static secret. Anything shipped in an APK is public —
 *     `apktool` on a downloaded build recovers it in seconds. There is no obfuscation that
 *     changes this; it is a property of shipping the secret at all.
 *
 *  2. The endpoint takes `userId` from the request *body* and does not check it against any
 *     caller identity. Whoever holds the secret can generate briefings for arbitrary user
 *     ids. Leaking it doesn't just let someone trigger their own briefing early — it lets
 *     them burn every user's LLM and TTS budget.
 *
 * The fix is a thin authenticated endpoint (a Supabase Edge Function is the natural home,
 * since it gets JWT verification for free) that:
 *
 *  - verifies the caller's Supabase JWT;
 *  - derives the user id **from the verified token**, never from the body;
 *  - rate-limits per user (the pipeline is expensive — one manual run per N minutes);
 *  - holds `CRON_SECRET`/`SERVICE_ROLE_KEY` server-side and calls the pipeline itself;
 *  - returns the `cron_runs.id` so the client can follow progress.
 *
 * Until that exists, DashboardViewModel keeps Run Now disabled.
 */
interface BriefingTriggerApi {

    /**
     * Authorization is attached by AuthInterceptor as the user's Supabase access token —
     * not a shared secret. The body carries no user id by design.
     */
    @POST(RUN_NOW_PATH)
    suspend fun runNow(@Body request: RunNowRequest = RunNowRequest()): RunNowResponse

    companion object {
        /** Relative to WEB_APP_BASE_URL. Adjust once the endpoint is agreed with the backend. */
        const val RUN_NOW_PATH = "api/briefings/run-now"
    }
}

@Serializable
data class RunNowRequest(
    /**
     * Whether to also deliver (Telegram / Drive) or only generate.
     * The cron endpoint defaults forced runs to `skipDelivery = true`; a user tapping
     * "Run now" in the app almost certainly wants the briefing delivered, so this defaults
     * the other way.
     */
    @SerialName("skip_delivery") val skipDelivery: Boolean = false,
)

@Serializable
data class RunNowResponse(
    @SerialName("briefing_id") val briefingId: String? = null,
    /** Row id in `cron_runs`, for progress polling. */
    @SerialName("run_id") val runId: String? = null,
    val status: String? = null,
    val error: String? = null,
)
