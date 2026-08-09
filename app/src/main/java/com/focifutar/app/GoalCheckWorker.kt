package com.focifutar.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoalCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) return@withContext Result.success()

        val favoriteIds = getFavoriteMatchIds(context)
        if (favoriteIds.isEmpty()) return@withContext Result.success()

        try {
            val response = StatPalClient.service.getDailyMatches(apiKey, 0)
            val leagues = response.liveMatches?.league.orEmpty()
            val prefs = context.getSharedPreferences("goal_cache_prefs", Context.MODE_PRIVATE)

            leagues.flatMap { it.match }.forEach { match ->
                val matchId = match.mainId ?: "${match.home?.name}-${match.away?.name}"
                if (favoriteIds.contains(matchId)) {
                    val homeGoals = match.home?.goals ?: "0"
                    val awayGoals = match.away?.goals ?: "0"
                    val currentScoreStr = "$homeGoals-$awayGoals"
                    val rawStatus = (match.status ?: match.time ?: "").uppercase()

                    val savedScoreStr = prefs.getString("${matchId}_score", null)
                    val wasFinished = prefs.getBoolean("${matchId}_finished", false)
                    val wasHt = prefs.getBoolean("${matchId}_ht", false)

                    // 1. Gól ellenőrzés
                    if (isLiveMatch(match)) {
                        if (savedScoreStr != null && savedScoreStr != currentScoreStr) {
                            val goalText = "⚽ GÓL! ${match.home?.name} $currentScoreStr ${match.away?.name}"
                            sendNotification(context, "⚽ Élő Gól Értesítés", goalText)
                        }
                    }

                    // 2. Félidő ellenőrzés
                    val isHtNow = rawStatus.contains("HT") || rawStatus.contains("FÉLIDŐ")
                    if (isHtNow && !wasHt) {
                        val htText = "⏱️ Félidő van a mérkőzésen: ${match.home?.name} $currentScoreStr ${match.away?.name}"
                        sendNotification(context, "⏱️ Félidő", htText)
                        prefs.edit().putBoolean("${matchId}_ht", true).apply()
                    }

                    // 3. Meccs vége ellenőrzés
                    val isFinishedNow = rawStatus == "FT" || rawStatus == "FINISHED" || rawStatus == "VÉGE" || rawStatus == "ENDED"
                    if (isFinishedNow && !wasFinished) {
                        val endText = "🏁 VÉGE A MECCSNEK! ${match.home?.name} $currentScoreStr ${match.away?.name}"
                        sendNotification(context, "🏁 Mérkőzés Vége", endText)
                        prefs.edit().putBoolean("${matchId}_finished", true).apply()
                    }

                    // 4. Lapok (Sárga / Piros) ellenőrzése
                    val events = match.events?.event.orEmpty()
                    val processedEvents = prefs.getStringSet("${matchId}_processed_events", emptySet())?.toMutableSet() ?: mutableSetOf()
                    
                    events.forEach { event ->
                        val type = event.type?.lowercase() ?: ""
                        if (type.contains("yellow") || type.contains("red")) {
                            val eventKey = "${event.minute}_${event.player}_${event.type}"
                            if (!processedEvents.contains(eventKey)) {
                                processedEvents.add(eventKey)
                                val cardIcon = if (type.contains("red")) "🟥" else "🟨"
                                val cardName = if (type.contains("red")) "Piros lap" else "Sárga lap"
                                val cardText = "$cardIcon $cardName (${event.minute}'): ${event.player} (${match.home?.name} - ${match.away?.name})"
                                sendNotification(context, "⚠️ Lap esemény", cardText)
                            }
                        }
                    }
                    prefs.edit().putStringSet("${matchId}_processed_events", processedEvents).apply()

                    prefs.edit().putString("${matchId}_score", currentScoreStr).apply()
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun sendNotification(context: Context, title: String, message: String) {
        val channelId = "football_futar_goals"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Élő Gól & Meccs Értesítések",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI, null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
