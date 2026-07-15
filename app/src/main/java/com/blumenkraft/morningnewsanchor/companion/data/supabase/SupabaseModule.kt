package com.blumenkraft.morningnewsanchor.companion.data.supabase

import com.blumenkraft.morningnewsanchor.companion.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.googleNativeLogin
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/**
 * Builds the single [SupabaseClient] the whole app shares.
 *
 * The client is pointed at the same project as the web app (ref `ghsmnpqigzocbiatpjid`), so
 * a user signs in here with the account they already have. All access is as the
 * `authenticated` role under RLS — the app holds no service-role key and cannot bypass it.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    /**
     * `explicitNulls = false` is what makes the partial `*Update` payloads work: a null
     * field is omitted from the PATCH body rather than written as SQL NULL. Without it,
     * updating one preference would blank every other column.
     *
     * `ignoreUnknownKeys = true` keeps the app from crashing when the web app adds a column.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideSupabaseClient(json: Json): SupabaseClient {
        check(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "SUPABASE_URL is empty — copy local.properties.example to local.properties and fill it in."
        }
        check(BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            "SUPABASE_ANON_KEY is empty — copy local.properties.example to local.properties and fill it in."
        }

        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            defaultSerializer = KotlinXSerializer(json)

            install(Auth) {
                // Must match the intent-filter in AndroidManifest.xml and the redirect URL
                // allow-list in the Supabase dashboard (Auth > URL Configuration).
                scheme = "morningnews"
                host = "auth-callback"
                flowType = FlowType.PKCE
                // supabase-kt persists the session to encrypted SharedPreferences and
                // refreshes it in the background by default on Android.
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }

            install(Postgrest)
            install(Storage)
            install(Realtime)

            install(ComposeAuth) {
                // One-tap / Credential Manager path. Requires the *Web* OAuth client ID —
                // the same one configured in Supabase's Google provider settings.
                googleNativeLogin(serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID)
            }
        }
    }

    @Provides
    @Singleton
    fun provideAuth(client: SupabaseClient) = client.auth

    @Provides
    @Singleton
    fun providePostgrest(client: SupabaseClient) = client.postgrest

    @Provides
    @Singleton
    fun provideStorage(client: SupabaseClient) = client.storage

    @Provides
    @Singleton
    fun provideRealtime(client: SupabaseClient) = client.realtime
}

/** Table names, kept in one place so a rename in the web app is a one-line change here. */
object Tables {
    const val PREFERENCES = "preferences"
    const val BRIEFINGS = "briefings"
    const val BRIEFING_STORIES = "briefing_stories"
    const val FEEDS = "feeds"
    const val INGESTED_ITEMS = "ingested_items"
    const val CRON_RUNS = "cron_runs"
    const val NEWSLETTER_CATALOG = "newsletter_catalog"
    const val USER_NEWSLETTER_SUBSCRIPTIONS = "user_newsletter_subscriptions"
    const val ARCHIVE_LOG = "archive_log"
    const val USER_PROVIDER_KEYS = "user_provider_keys"
    const val WEEKLY_HIGHLIGHTS = "weekly_highlights"
    const val TELEGRAM_LINKS = "telegram_links"
}

/** Storage buckets. */
object Buckets {
    /**
     * The audio bucket is private. Its RLS policy is
     * `bucket_id = 'audio' AND (storage.foldername(name))[1] = auth.uid()::text`,
     * so a user may only read objects under their own `{userId}/` prefix — which is
     * exactly what lets this app sign its own URLs without a server round-trip.
     */
    const val AUDIO = "audio"
}
