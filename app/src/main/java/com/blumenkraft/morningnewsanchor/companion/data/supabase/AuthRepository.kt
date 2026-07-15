package com.blumenkraft.morningnewsanchor.companion.data.supabase

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** What the UI needs to know about sign-in state. */
sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: UserInfo) : AuthState
}

/**
 * Owns sign-in/sign-out and exposes the current session.
 *
 * Sessions are persisted and refreshed by supabase-kt itself, so this is a thin wrapper —
 * its real job is translating [SessionStatus] into something the UI can consume and giving
 * repositories a reliable `currentUserId`.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: Auth,
) {
    val authState: Flow<AuthState> = auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> AuthState.SignedIn(status.session.user!!)
            is SessionStatus.NotAuthenticated -> AuthState.SignedOut
            // Both of these are transient states while the SDK loads or refreshes a token.
            is SessionStatus.Initializing -> AuthState.Loading
            is SessionStatus.RefreshFailure -> AuthState.Loading
        }
    }

    /** Null when signed out. Repositories use this to scope every query to the user. */
    val currentUserId: String?
        get() = auth.currentUserOrNull()?.id

    val currentUser: UserInfo?
        get() = auth.currentUserOrNull()

    /**
     * Throwing accessor for repository code, where a null user is a programming error
     * (the nav graph gates every data screen behind [AuthState.SignedIn]).
     */
    fun requireUserId(): String = currentUserId
        ?: error("No authenticated user — a repository was called from an unauthenticated screen.")

    /**
     * Completes sign-in from a Google ID token obtained via Credential Manager.
     *
     * This is the native path: Google hands us an ID token, Supabase verifies it against the
     * configured web client ID and mints its own session. No browser involved.
     *
     * @param idToken the raw `id_token` from GoogleIdTokenCredential.
     * @param rawNonce the *unhashed* nonce that was passed to Credential Manager. Supabase
     *   hashes it and compares against the `nonce` claim, so passing the hashed value here
     *   fails verification.
     */
    suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String?) {
        auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = Google
            this.nonce = rawNonce
        }
    }

    /**
     * Browser-based fallback for devices without Play Services, and the escape hatch if the
     * native token exchange is misconfigured. Redirects back via the `morningnews://auth-callback`
     * deep link registered in the manifest.
     */
    suspend fun signInWithGoogleOAuth() {
        auth.signInWith(Google)
    }

    suspend fun signOut() {
        auth.signOut()
    }

    /**
     * The current access token, for the rare call that has to leave the Supabase SDK —
     * e.g. authenticating against the web app's own endpoints.
     */
    suspend fun accessToken(): String? = auth.currentAccessTokenOrNull()
}
