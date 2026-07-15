package com.blumenkraft.morningnewsanchor.companion

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MorningNewsCompanionApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BRIEFING_READY,
                getString(R.string.channel_briefing_ready),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = getString(R.string.channel_briefing_ready_desc) }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PLAYBACK,
                getString(R.string.channel_playback),
                // LOW: the media notification is a transport control, not an alert.
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.channel_playback_desc) }
        )
    }

    companion object {
        const val CHANNEL_BRIEFING_READY = "briefing_ready"
        const val CHANNEL_PLAYBACK = "playback"
    }
}
