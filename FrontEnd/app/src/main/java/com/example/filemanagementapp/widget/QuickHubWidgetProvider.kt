package com.example.filemanagementapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.filemanagementapp.R
import com.example.filemanagementapp.main.MainActivity
import com.example.filemanagementapp.search.SearchActivity

class QuickHubWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawSummary = prefs.getString(KEY_STORAGE_SUMMARY, null)
        val username = prefs.getString(KEY_USERNAME, "") ?: ""

        val storageDisplay = formatStorageDisplay(context, rawSummary)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_hub)
            views.setTextViewText(R.id.widgetStorageText, storageDisplay)

            // 1. Header & Container -> Mở ứng dụng chính
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.filemanagementapp.ACTION_WIDGET_MAIN"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val mainPendingIntent = PendingIntent.getActivity(
                context,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetHeader, mainPendingIntent)

            // 2. Action Search -> Mở SearchActivity
            val searchIntent = Intent(context, SearchActivity::class.java).apply {
                action = "com.example.filemanagementapp.ACTION_WIDGET_SEARCH"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(SearchActivity.EXTRA_USERNAME, username)
            }
            val searchPendingIntent = PendingIntent.getActivity(
                context,
                1,
                searchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetActionSearch, searchPendingIntent)

            // 3. Action Upload -> Mở MainActivity và kích hoạt tải lên
            val uploadIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.filemanagementapp.ACTION_WIDGET_UPLOAD"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAVIGATE_UPLOAD, true)
            }
            val uploadPendingIntent = PendingIntent.getActivity(
                context,
                2,
                uploadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetActionUpload, uploadPendingIntent)

            // 4. Action AI -> Mở MainActivity và kích hoạt trợ lý AI
            val aiIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.filemanagementapp.ACTION_WIDGET_AI"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAVIGATE_AI, true)
            }
            val aiPendingIntent = PendingIntent.getActivity(
                context,
                3,
                aiIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetActionAi, aiPendingIntent)

            // 5. Action Google Drive -> Mở MainActivity và điều hướng tới Google Drive
            val driveIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.filemanagementapp.ACTION_WIDGET_DRIVE"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAVIGATE_DRIVE, true)
            }
            val drivePendingIntent = PendingIntent.getActivity(
                context,
                4,
                driveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetActionDrive, drivePendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun formatStorageDisplay(context: Context, summary: String?): String {
        if (summary.isNullOrBlank()) {
            return context.getString(R.string.widget_quick_hub_used_default)
        }
        val cleaned = summary.replace("used", "", ignoreCase = true).trim()
        return context.getString(R.string.widget_quick_hub_used_format, cleaned)
    }

    companion object {
        const val PREFS_NAME = "quick_hub_widget_prefs"
        const val KEY_STORAGE_SUMMARY = "key_storage_summary"
        const val KEY_USERNAME = "key_username"

        const val EXTRA_NAVIGATE_AI = "com.example.filemanagementapp.EXTRA_NAVIGATE_AI"
        const val EXTRA_NAVIGATE_DRIVE = "com.example.filemanagementapp.EXTRA_NAVIGATE_DRIVE"
        const val EXTRA_NAVIGATE_UPLOAD = "com.example.filemanagementapp.EXTRA_NAVIGATE_UPLOAD"

        fun updateStorageSummary(context: Context, storageSummary: String, username: String? = null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit().putString(KEY_STORAGE_SUMMARY, storageSummary)
            if (!username.isNullOrBlank()) {
                editor.putString(KEY_USERNAME, username)
            }
            editor.apply()
            updateAllWidgets(context)
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, QuickHubWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, QuickHubWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
