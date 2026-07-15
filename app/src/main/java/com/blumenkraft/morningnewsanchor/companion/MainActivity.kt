package com.blumenkraft.morningnewsanchor.companion

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.blumenkraft.morningnewsanchor.companion.ui.CompanionApp
import com.blumenkraft.morningnewsanchor.companion.ui.theme.MorningNewsTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var supabase: SupabaseClient

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Consumes `morningnews://auth-callback` from the browser OAuth fallback and
        // completes the PKCE exchange. Harmless for launches that aren't auth callbacks.
        supabase.handleDeeplinks(intent)

        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }

        setContent {
            MorningNewsTheme {
                CompanionApp(onReady = { keepSplash = false })
            }
        }
    }

    /**
     * The activity is `singleTask`, so a deep link arriving while it's already running comes
     * through here rather than onCreate.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        supabase.handleDeeplinks(intent)
    }
}
