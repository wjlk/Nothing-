package com.example.localalert.sender;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

public class SenderWidgetProvider extends AppWidgetProvider {
    private static final int REQUEST_CODE = 7001;

    @Override
    public void onUpdate(
            Context context,
            AppWidgetManager appWidgetManager,
            int[] appWidgetIds) {
        Intent intent = new Intent(context, SenderService.class);
        intent.setAction(SenderService.ACTION_WIDGET_ALERT);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent action = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? PendingIntent.getForegroundService(context, REQUEST_CODE, intent, flags)
                : PendingIntent.getService(context, REQUEST_CODE, intent, flags);

        RemoteViews views = new RemoteViews(
                context.getPackageName(),
                R.layout.widget_sender);
        views.setOnClickPendingIntent(R.id.widget_button, action);
        appWidgetManager.updateAppWidget(appWidgetIds, views);
    }
}