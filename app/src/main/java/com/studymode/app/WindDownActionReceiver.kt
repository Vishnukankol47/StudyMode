package com.studymode.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WindDownActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            "ACTION_SESSION_PAUSE" -> {
                WindDownSessionManager.pauseSession(context)
            }
            "ACTION_SESSION_RESUME" -> {
                WindDownSessionManager.resumeSession(context)
            }
            "ACTION_SESSION_END" -> {
                WindDownSessionManager.endSession(context)
            }
        }
    }
}
