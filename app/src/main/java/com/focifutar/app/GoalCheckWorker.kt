package com.focifutar.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
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

            leagues.flatMap { it.match }.forEach { match ->
                val matchId = match.mainId ?: "${match.home?.name}-${match.away?.name}"
                if (favoriteIds.contains(matchId) && isLiveMatch(match)) {
                    val homeGoals = match.home?.goals ?: "0"
                    val awayGoals = match.away?.goals ?: "0"
                    
                    val goalText = "⚽ GÓL! ${match.home?.name} $homeGoals - $awayGoals ${match.away?.name}"
                    sendNotification(context, "Élő Gól Értesítés", goalText)
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
                "Élő Gól Értesítések",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
