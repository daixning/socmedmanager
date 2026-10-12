package app.socmed.manager.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.socmed.core.Platform
import app.socmed.core.Status
import app.socmed.manager.MainActivity
import app.socmed.manager.R
import app.socmed.manager.data.AppData
import app.socmed.manager.data.Prefs
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Local reminders only (no server). A publishing reminder says "this is due" — it never marks anything Published.
 * Alarms are inexact on purpose so no special "exact alarm" permission is needed.
 */
object Reminders {
    const val CHANNEL = "reminders"
    private const val MAX = 50
    private const val STATE = "reminder_state"

    private data class R(val key: String, val at: Long, val title: String, val text: String, val route: String)

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Publishing & deadline reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun sync(ctx: Context, d: AppData) {
        if (!d.loaded) return
        val am = ctx.getSystemService(AlarmManager::class.java)
        val sp = ctx.getSharedPreferences(STATE, Context.MODE_PRIVATE)
        val old = sp.getStringSet("keys", emptySet()).orEmpty()
        val wanted = if (d.prefBool(Prefs.NOTIFS)) build(d) else emptyList()
        val newKeys = wanted.map { it.key }.toSet()
        for (k in old - newKeys) am.cancel(pending(ctx, k, "", "", ""))
        for (r in wanted) {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pending(ctx, r.key, r.title, r.text, r.route)) }
        }
        sp.edit().putStringSet("keys", newKeys).apply()
    }

    private fun build(d: AppData): List<R> {
        val now = System.currentTimeMillis()
        val lead = d.pref(Prefs.LEAD_MIN, "60").toLongOrNull() ?: 60L
        val zone = d.zone
        val out = ArrayList<R>()
        for (e in d.planEntries()) {
            val open = e.status != Status.PUBLISHED && e.status != Status.CANCELLED && e.status != Status.ON_HOLD
            val at = e.atMillis ?: continue
            if (!open) continue
            var t = at - lead * 60_000
            if (t < now) t = at
            if (t < now) continue
            val where = e.platforms.joinToString { it.label }.ifBlank { "your planned post" }
            out += R("pub:${e.content.id}:$at", t, "Time to publish: ${e.content.title.ifBlank { "Untitled" }}", "Planned for $where. Mark it Published yourself once it is live.", "post/${e.content.id}")
        }
        fun nineAm(day: Long) = ZonedDateTime.of(LocalDate.ofEpochDay(day), LocalTime.of(9, 0), zone).toInstant().toEpochMilli()
        for (c in d.contents) {
            val dl = c.deadlineDay ?: continue
            if (c.archived || !Status.isOpen(c.status) || c.status == Status.ON_HOLD) continue
            val t = nineAm(dl)
            if (t > now) out += R("dl:${c.id}:$dl", t, "Production deadline today", c.title.ifBlank { "Untitled" }, "post/${c.id}")
        }
        for (task in d.tasks) {
            val dl = task.dueDay ?: continue
            if (task.done || task.ownerType != "event") continue
            val ev = d.eventById[task.ownerId] ?: continue
            val t = nineAm(dl)
            if (t > now) out += R("task:${task.id}:$dl", t, "Event task due today", "${task.text} · ${ev.name}", "event/${ev.id}")
        }
        for (ev in d.events) {
            val st = ev.startAt ?: continue
            val t = st - 24 * 3_600_000L
            if (t > now && ev.status != "CANCELLED" && ev.status != "DONE") out += R("ev:${ev.id}:$st", t, "Tomorrow: ${ev.name}", ev.location, "event/${ev.id}")
        }
        return out.sortedBy { it.at }.take(MAX)
    }

    private fun pending(ctx: Context, key: String, title: String, text: String, route: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).apply {
            data = Uri.parse("socmed://reminder/${Uri.encode(key)}")
            putExtra("title", title); putExtra("text", text); putExtra("route", route); putExtra("id", key.hashCode())
        }
        return PendingIntent.getBroadcast(ctx, key.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        Reminders.ensureChannel(ctx)
        val open = Intent(ctx, MainActivity::class.java).apply {
            putExtra("route", intent.getStringExtra("route")); flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(ctx, intent.getIntExtra("id", 0), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = android.app.Notification.Builder(ctx, Reminders.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(intent.getStringExtra("title")).setContentText(intent.getStringExtra("text"))
            .setContentIntent(pi).setAutoCancel(true).build()
        ctx.getSystemService(NotificationManager::class.java).notify(intent.getIntExtra("id", 0), n)
    }
}

/** Starting the process on boot is enough: the app re-schedules all alarms as soon as its data loads. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {}
}
