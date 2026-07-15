package com.blumenkraft.morningnewsanchor.companion.data.remote

import com.blumenkraft.morningnewsanchor.companion.BuildConfig
import com.blumenkraft.morningnewsanchor.companion.data.supabase.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the signed-in user's Supabase access token to requests aimed at our own backend.
 *
 * This is the only credential the app sends. It is short-lived, scoped to one user, and
 * verifiable server-side — the opposite of a shared static secret.
 */
class AuthInterceptor @Inject constructor(
    private val auth: AuthRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // OkHttp interceptors are blocking by contract; the token read is a cheap in-memory
        // lookup, so bridging with runBlocking here is safe.
        val token = runBlocking { auth.accessToken() }

        val request = chain.request().newBuilder()
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .header("Accept", "application/json")
            .build()

        return chain.proceed(request)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    // BASIC, not BODY: request bodies here carry bearer tokens and a BODY-level
                    // log would write them to logcat.
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
                    )
                }
            }
            // Generating a briefing is slow (LLM + TTS). The trigger endpoint should return
            // as soon as the run is queued, but leave headroom.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.WEB_APP_BASE_URL.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideBriefingTriggerApi(retrofit: Retrofit): BriefingTriggerApi =
        retrofit.create(BriefingTriggerApi::class.java)
}

/** Retrofit rejects a baseUrl without a trailing slash. */
private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"
