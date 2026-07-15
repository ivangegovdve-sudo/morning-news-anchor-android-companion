package com.blumenkraft.morningnewsanchor.companion.data.repository

import com.blumenkraft.morningnewsanchor.companion.data.models.Preferences
import com.blumenkraft.morningnewsanchor.companion.data.models.PreferencesUpdate
import com.blumenkraft.morningnewsanchor.companion.data.models.UserProviderKey
import com.blumenkraft.morningnewsanchor.companion.data.supabase.AuthRepository
import com.blumenkraft.morningnewsanchor.companion.data.supabase.Tables
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: AuthRepository,
) {
    /**
     * The row is created by the `on_auth_user_created` database trigger, so it should always
     * exist for a signed-in user. Null means the trigger didn't fire — a backend problem the
     * app should surface rather than paper over by inserting a row of its own.
     */
    suspend fun getPreferences(): Preferences? =
        postgrest.from(Tables.PREFERENCES)
            .select { filter { eq("user_id", auth.requireUserId()) } }
            .decodeSingleOrNull()

    /**
     * Partial update. Null fields are omitted from the payload rather than nulling columns —
     * that behaviour depends on `explicitNulls = false` in SupabaseModule's Json.
     */
    suspend fun updatePreferences(update: PreferencesUpdate): Preferences =
        postgrest.from(Tables.PREFERENCES)
            .update(update) {
                filter { eq("user_id", auth.requireUserId()) }
                select()
            }
            .decodeSingle()

    /**
     * Which providers the user has an API key on file for.
     *
     * Deliberately does not select `api_key_encrypted`. The column is readable under the
     * user's own RLS policy, but pulling ciphertext into the client buys nothing — the app
     * can't decrypt it (the key lives server-side) and it would end up in memory dumps and
     * logs for no reason.
     */
    suspend fun listProviderKeys(): List<UserProviderKey> =
        postgrest.from(Tables.USER_PROVIDER_KEYS)
            .select(io.github.jan.supabase.postgrest.query.Columns.list("id", "user_id", "provider", "label", "updated_at")) {
                filter { eq("user_id", auth.requireUserId()) }
            }
            .decodeList()
}
