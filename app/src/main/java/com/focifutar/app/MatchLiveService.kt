package com.focifutar.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class MatchLiveService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())

        serviceScope.launch {
            while (true) {
                var hasActiveLiveFavorites = false
                try {
                    val context = applicationContext
                    val apiKey = getApiKey(context)
                    val favoriteIds = getFavoriteMatchIds(context)

                    if (apiKey.isNotBlank() && favoriteIds.isNotEmpty()) {
                        val response = StatPalClient.service.getDailyMatches(apiKey, 0)
                        val leagues = response.liveMatches?.league.orEmpty()
                        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

                        leagues.flatMap { it.match }.forEach { m ->
                            val id = m.mainId ?: "${m.home?.name}-${m.away?.name}"
                            if (favoriteIds.contains(id)) {
                                if (isLiveMatch(m)) {
                                    hasActiveLiveFavorites = true
                                }

                                val scoreStr = "${m.home?.goals ?: "0"}-${m.away?.goals ?: "0"}"
                                val rawStatus = (m.status ?: m.time ?: "").uppercase()
                                val isHtNow = rawStatus.contains("HT") || rawStatus.contains("FÉLIDŐ")
                                val isFinishedNow = rawStatus == "FT" || rawStatus == "FINISHED" || rawStatus == "VÉGE" || rawStatus == "ENDED"

                                val savedScoreKey = "saved_score_$id"
                                val oldScore = prefs.getString(savedScoreKey, null)

                                // 1. Gól ellenőrzés globális szűréssel
                                if (oldScore != null && oldScore != scoreStr && isLiveMatch(m)) {
                                    val goalEventKey = "goal_${id}_${scoreStr}"
                                    if (!isEventAlreadyProcessed(context, goalEventKey)) {
                                        markEventAsProcessed(context, goalEventKey)
                                        sendSystemNotification(context, "⚽ Élő Gól Értesítés", "${m.home?.name} $scoreStr ${m.away?.name}")
                                    }
                                }
                                prefs.edit().putString(savedScoreKey, scoreStr).apply()

                                // 2. Félidő ellenőrzés
                                val htEventKey = "ht_$id"
                                if (isHtNow && !isEventAlreadyProcessed(context, htEventKey)) {
                                    markEventAsProcessed(context, htEventKey)
                                    sendSystemNotification(context, "⏱️ Félidő", "Félidő a mérkőzésen: ${m.home?.name} $scoreStr ${m.away?.name}")
                                }

                                // 3. Meccs vége ellenőrzés
                                val ftEventKey = "ft_$id"
                                if (isFinishedNow && !isEventAlreadyProcessed(context, ftEventKey)) {
                                    markEventAsProcessed(context, ftEventKey)
                                    sendSystemNotification(context, "🏁 Mérkőzés Vége", "Vége a meccsnek: ${m.home?.name} $scoreStr ${m.away?.name}")
                                }

                                // 4. Sárga és piros lapok ellenőrzése
                                val events = m.events?.event.orEmpty()
                                events.forEach { event ->
                                    val type = event.type?.lowercase() ?: ""
                                    if (type.contains("yellow") || type.contains("red")) {
                                        val eventKey = "card_${id}_${event.minute}_${event.player}_${event.type}"
                                        if (!isEventAlreadyProcessed(context, eventKey)) {
                                            markEventAsProcessed(context, eventKey)
                                            val isRed = type.contains("red")
                                            val cardIcon = if (isRed) "🟥" else "🟨"
                                            val cardName = if (isRed) "Piros lap" else "Sárga lap"
                                            sendSystemNotification(context, "⚠️ Lap esemény", "$cardIcon $cardName (${event.minute}'): ${event.player} (${m.home?.name} - ${m.away?.name})")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                if (hasActiveLiveFavorites) {
                    delay(15000L)
                } else {
                    delay(30000L)
                }
            }
        }

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "football_futar_service"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Futár Élő Háttérszolgáltatás", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Football Futár")
            .setContentText("Élő meccsek és események figyelése a háttérben...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
