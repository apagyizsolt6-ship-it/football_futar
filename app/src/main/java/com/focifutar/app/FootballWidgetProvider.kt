package com.focifutar.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

class FootballWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_football)
            
            // Alapértelmezett szöveg beállítása a widgetre
            views.setTextViewText(R.id.widget_title, "FOOTBALL FUTÁR")
            views.setTextViewText(R.id.widget_match_text, "Kattints az app megnyitásához")

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
