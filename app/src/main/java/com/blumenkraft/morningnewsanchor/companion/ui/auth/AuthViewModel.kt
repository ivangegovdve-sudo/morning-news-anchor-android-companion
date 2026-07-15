package com.blumenkraft.morningnewsanchor.companion.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blumenkraft.morningnewsanchor.companion.BuildConfig
import com.blumenkraft.morningnewsanchor.companion.data.supabase.AuthRepository
import com.blumenkraft.morningnewsanchor.companion.data.supabase.AuthState
import com.blumenkraft.morningnewsanchor.companion.data.supabase.StorageUrlResolver
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object SignedOut : AuthUiState
    data object SignedIn : AuthUiState
    data class Error(val message: String) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val urlResolver: StorageUrlResolver,
) : ViewModel() {

    private val transientError = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)

    val isBusy: StateFlow<Boolean> = busy.asStateFlow()

    val uiState: StateFlow<AuthUiState> =
        combine(authRepository.authState, transientError) { state, error ->
            when {
                state is AuthState.SignedIn -> AuthUiState.SignedIn
                state is AuthState.Loading -> AuthUiState.Loading
                error != null -> AuthUiState.Error(error)
                else -> AuthUiState.SignedOut
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthUiState.Loading)

    /**
     * Google sign-in via Credential Manager.
     *
     * The nonce dance matters and is easy to get wrong: Google signs a *hashed* nonce into
     * the token's `nonce` claim, but Supabase expects the *raw* value so it can hash and
     * compare. So we generate raw → pass SHA-256 to Google → pass raw to Supabase. Passing
     * the same value to both fails verification with an unhelpful error.
     *
     * @param context must be an Activity context — Credential Manager needs to show UI.
     */
    fun signInWithGoogle(context: Context) {
        if (busy.value) return

        viewModelScope.launch {
            busy.value = true
            transientError.value = null

            try {
                check(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
                    "GOOGLE_WEB_CLIENT_ID is not set — see local.properties.example."
                }

                val rawNonce = generateRawNonce()
                val hashedNonce = sha256(rawNonce)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    // false, so someone whose Google account isn't yet linked can still sign
                    // in — this app shares accounts with the web app but a user may arrive here first.
                    .setFilterByAuthorizedAccounts(false)
                    .setNonce(hashedNonce)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = CredentialManager.create(context).getCredential(context, request)
                val credential = GoogleIdTokenCredential.createFrom(result.credential.data)

                authRepository.signInWithGoogleIdToken(credential.idToken, rawNonce)
            } catch (e: GetCredentialCancellationException) {
                // User dismissed the sheet. Not an error worth showing.
            } catch (e: NoCredentialException) {
                transientError.value =
                    "No Google account available on this device. Add one in Settings, or use the browser sign-in."
            } catch (e: GetCredentialException) {
                transientError.value = e.message ?: "Google sign-in failed."
            } catch (e: Exception) {
                transientError.value = e.message ?: "Sign-in failed."
            } finally {
                busy.value = false
            }
        }
    }

    /** Browser/PKCE fallback. Returns via the `morningnews://auth-callback` deep link. */
    fun signInWithBrowser() {
        viewModelScope.launch {
            busy.value = true
            runCatching { authRepository.signInWithGoogleOAuth() }
                .onFailure { transientError.value = it.message ?: "Sign-in failed." }
            busy.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            // Signed URLs are per-user; leaving them cached would let the next account reuse them.
            urlResolver.clear()
        }
    }

    fun dismissError() { transientError.value = null }

    private fun generateRawNonce(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
