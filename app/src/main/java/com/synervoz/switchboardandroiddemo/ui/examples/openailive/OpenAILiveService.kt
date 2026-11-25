package com.synervoz.switchboardandroiddemo.ui.examples.openailive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Microphone foreground service, running while the conversation does. Android cuts a
 * background app off from the microphone, so without it the assistant stops hearing
 * the user when the screen locks or another app comes to the front.
 */
class OpenAILiveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Runs after the start command: stopping before startForeground crashes the app.
        if (intent?.action == ACTION_STOP) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification())
            }
        } catch (e: Exception) {
            // Not allowed from the background, or no microphone permission. The
            // conversation still runs while the app is in front.
            Log.w(TAG, "Couldn't start the foreground service", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Voice conversation", NotificationManager.IMPORTANCE_LOW)
            )
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("OpenAI Live")
            .setContentText("Conversation in progress")
            .setOngoing(true)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setContentIntent(PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE))
        }
        return builder.build()
    }

    companion object {
        /** Stops the service, after its start. */
        const val ACTION_STOP = "com.synervoz.switchboardandroiddemo.openailive.STOP"

        private const val TAG = "OpenAILiveService"
        private const val CHANNEL_ID = "openai_live"
        private const val NOTIFICATION_ID = 1
    }
}
