package id.walt.walletdemo.compose.android

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

internal object AndroidCredentialStatusNotifications : Application.ActivityLifecycleCallbacks {
    const val OPEN_HOME = "id.walt.walletdemo.compose.action.OPEN_CREDENTIAL_STATUS_HOME"
    private const val CHANNEL = "credential_status"
    private const val NOTICE = 1
    private val visibleActivities = AtomicInteger(0)

    fun requestPermissionOnce(activity: Activity, request: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < 33 || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val preferences = activity.getSharedPreferences(CHANNEL, Context.MODE_PRIVATE)
        if (!preferences.getBoolean("permissionRequested", false)) {
            preferences.edit().putBoolean("permissionRequested", true).apply()
            request(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun postIfAllowed(context: Context) {
        if (visibleActivities.get() > 0 || (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.credential_status_notification_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, MainActivity::class.java).setAction(OPEN_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, NOTICE, intent, PendingIntent.FLAG_IMMUTABLE)
        manager.notify(NOTICE, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_credential_status)
            .setContentTitle(context.getString(R.string.credential_status_notification_title))
            .setContentText(context.getString(R.string.credential_status_notification_message))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build())
    }

    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTICE)
    }

    override fun onActivityStarted(activity: Activity) { visibleActivities.incrementAndGet() }
    override fun onActivityStopped(activity: Activity) { visibleActivities.decrementAndGet() }
    override fun onActivityCreated(activity: Activity, state: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
