package com.example.kompasblocker

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class BlockerAccessibilityService : AccessibilityService() {
    private var lastRedirectAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val foregroundPackage = event.packageName?.toString() ?: return
        if (foregroundPackage == packageName) return

        val blocked = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getStringSet(BLOCKED_PACKAGES, emptySet())
            ?.contains(foregroundPackage) == true
        if (!blocked) return

        val now = SystemClock.elapsedRealtime()
        if (lastRedirectAt != 0L && now - lastRedirectAt < REDIRECT_COOLDOWN_MS) return
        lastRedirectAt = now
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() = Unit

    companion object {
        const val PREFS = "kompas_blocker_prefs"
        const val BLOCKED_PACKAGES = "blocked_packages"
        private const val REDIRECT_COOLDOWN_MS = 1200L
    }
}
