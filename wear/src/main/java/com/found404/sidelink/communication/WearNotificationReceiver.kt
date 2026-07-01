package com.found404.sidelink.communication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.found404.sidelink.shared.CommunicationConstants

class WearNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val id = intent.getStringExtra("notification_id")
        val commManager = BluetoothCommunicationManager.getInstance(context)

        try {
            when (action) {
                "com.found404.sidelink.ACTION_REPLY" -> {
                    if (id != null) {
                        val remoteInput = RemoteInput.getResultsFromIntent(intent)
                        val replyText = remoteInput?.getCharSequence("extra_reply")?.toString()
                        if (replyText != null) {
                            Log.d("WearReceiver", "Replying to $id: $replyText")
                            commManager.sendReply(id, replyText)
                        } else {
                            Log.w("WearReceiver", "Reply text is null")
                        }
                    }
                }
                "com.found404.sidelink.ACTION_DISMISS" -> {
                    if (id != null) {
                        Log.d("WearReceiver", "Dismissing $id")
                        commManager.sendDismiss(id)
                    }
                }
                "com.found404.sidelink.ACTION_TRIGGER" -> {
                    if (id != null) {
                        val actionIndex = intent.getIntExtra("action_index", -1)
                        if (actionIndex >= 0) {
                            Log.d("WearReceiver", "Action trigger $actionIndex on $id")
                            commManager.sendActionTrigger(id, actionIndex)
                        }
                    }
                }
                "com.found404.sidelink.CMD_MEDIA_PLAY" -> commManager.sendMediaAction(CommunicationConstants.ACTION_PLAY)
                "com.found404.sidelink.CMD_MEDIA_PAUSE" -> commManager.sendMediaAction(CommunicationConstants.ACTION_PAUSE)
                "com.found404.sidelink.CMD_MEDIA_NEXT" -> commManager.sendMediaAction(CommunicationConstants.ACTION_NEXT)
                "com.found404.sidelink.CMD_MEDIA_PREV" -> commManager.sendMediaAction(CommunicationConstants.ACTION_PREVIOUS)
                Intent.ACTION_MEDIA_BUTTON -> {
                    // Forward media button events to the session
                    commManager.getSessionToken() // Ensure session is initialized
                    androidx.media.session.MediaButtonReceiver.handleIntent(commManager.mediaSession, intent)
                }
            }
        } catch (e: Exception) {
            Log.e("WearReceiver", "Action failed", e)
        }
    }
}
