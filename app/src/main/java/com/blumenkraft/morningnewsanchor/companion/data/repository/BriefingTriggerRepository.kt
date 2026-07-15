package com.blumenkraft.morningnewsanchor.companion.data.repository

import com.blumenkraft.morningnewsanchor.companion.data.models.CronRun
import com.blumenkraft.morningnewsanchor.companion.data.remote.BriefingTriggerApi
import com.blumenkraft.morningnewsanchor.companion.data.remote.RunNowRequest
import com.blumenkraft.morningnewsanchor.companion.data.supabase.AuthRepository
import com.blumenkraft.morningnewsanchor.companion.data.supabase.Tables
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a manual trigger attempt. */
sealed interface TriggerResult {
    data class Started(val runId: String?, val briefingId: String?) : TriggerResult
    data class Failed(val message: String) : TriggerResult

    /**
     * The backend endpoint isn't deployed yet. Distinct from [Failed] so the UI can explain
     * *why* rather than showing a generic error — see [BriefingTriggerApi] for the rationale.
     */
    data object NotAvailable : TriggerResult
}

/**
 * Manual "Run now" trigger, plus read access to `cron_runs` so the dashboard can show what
 * the backend last did.
 *
 * [runNow] is gated behind [TRIGGER_ENDPOINT_DEPLOYED]. Flip it to true only once the
 * authenticated endpoint described in [BriefingTriggerApi] actually exists. Do not "unblock"
 * this by pointing it at the cron endpoint with a shared secret.
 */
@Singleton
class BriefingTriggerRepository @Inject constructor(
    private val api: BriefingTriggerApi,
    private val postgrest: Postgrest,
    private val auth: AuthRepository,
) {
    suspend fun runNow(skipDelivery: Boolean = false): TriggerResult {
        if (!TRIGGER_ENDPOINT_DEPLOYED) return TriggerResult.NotAvailable

        return runCatching { api.runNow(RunNowRequest(skipDelivery = skipDelivery)) }
            .fold(
                onSuccess = { response ->
                    val error = response.error
                    if (error != null) TriggerResult.Failed(error)
                    else TriggerResult.Started(response.runId, response.briefingId)
                },
                onFailure = { t -> TriggerResult.Failed(t.message ?: "Trigger failed") },
            )
    }

    /** Recent backend runs for this user, newest first. */
    suspend fun listRuns(limit: Long = 20): List<CronRun> =
        postgrest.from(Tables.CRON_RUNS)
            .select {
                filter { eq("user_id", auth.requireUserId()) }
                order("started_at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList()

    suspend fun latestRun(): CronRun? = listRuns(limit = 1).firstOrNull()

    companion object {
        /**
         * Set to true when `POST /api/briefings/run-now` (JWT-authenticated, user id derived
         * from the token, rate-limited) is live. Until then Run Now stays disabled rather
         * than failing at runtime.
         */
        const val TRIGGER_ENDPOINT_DEPLOYED = false
    }
}
