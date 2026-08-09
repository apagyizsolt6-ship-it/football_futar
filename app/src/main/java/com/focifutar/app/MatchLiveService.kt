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
    private val previousScoresMap = mutableMapOf<String, String>()
    private val finishedMatchesMap = mutableSetOf<String>()
    private val halftimeMatchesMap = mutableSetOf<String>()
    private val processedEventsMap = mutableMapOf<String, MutableSet<String>>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())

        serviceScope.launch {
            while (true) {
                try {
                    val context = applicationContext
                    val apiKey = getApiKey(context)
                    val favoriteIds = getFavoriteMatchIds(context)

                    if (apiKey.isNotBlank() && favoriteIds.isNotEmpty()) {
                        val response = StatPalClient.service.getDailyMatches(apiKey, 0)
                        val leagues = response.liveMatches?.league.orEmpty()

                        leagues.flatMap { it.match }.forEach { m ->
                            val id = m.mainId ?: "${m.home?.name}-${m.away?.name}"
                            if (favoriteIds.contains(id)) {
                                val scoreStr = "${m.home?.goals ?: "0"}-${m.away?.goals ?: "0"}"
                                val rawStatus = (m.status ?: m.time ?: "").uppercase()
                                val isHtNow = rawStatus.contains("HT") || rawStatus.contains("FÉLIDŐ")
                                val isFinishedNow = rawStatus == "FT" || rawStatus == "FINISHED" || rawStatus == "VÉGE" || rawStatus == "ENDED"

                                // Gól ellenőrzés
                                if (previousScoresMap.containsKey(id) && isLiveMatch(m)) {
                                    val oldScore = previousScoresMap[id]
                                    if (oldScore != null && oldScore != scoreStr) {
                                        sendSystemNotification(context, "⚽ Élő Gól Értesítés", "${m.home?.name} $scoreStr ${m.away?.name}")
                                    }
                                }
                                previousScoresMap[id] = scoreStr

                                // Félidő ellenőrzés
                                if (isHtNow && !halftimeMatchesMap.contains(id)) {
                                    halftimeMatchesMap.add(id)
                                    sendSystemNotification(context, "⏱️ Félidő", "Félidő a mérkőzésen: ${m.home?.name} $scoreStr ${m.away?.name}")
                                }

                                // Meccs vége ellenőrzés
                                if (isFinishedNow && !finishedMatchesMap.contains(id)) {
                                    finishedMatchesMap.add(id)
                                    sendSystemNotification(context, "🏁 Mérkőzés Vége", "Vége a meccsnek: ${m.home?.name} $scoreStr ${m.away?.name}")
                                }

                                // Sárga és piros lapok ellenőrzése
                                val events = m.events?.event.orEmpty()
                                val currentProcessed = processedEventsMap.getOrPut(id) { mutableSetOf() }
                                events.forEach { event ->
                                    val type = event.type?.lowercase() ?: ""
                                    if (type.contains("yellow") || type.contains("red")) {
                                        val eventKey = "${event.minute}_${event.player}_${event.type}"
                                        if (!currentProcessed.contains(eventKey)) {
                                            currentProcessed.add(eventKey)
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

                delay(15000L) // 15 másodpercenként fut a háttérben is folyamatosan!
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
