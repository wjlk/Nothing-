package com.example.localalert.sender;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.net.wifi.WifiManager;

import java.io.IOException;

public class SenderService extends Service {
    public static final String ACTION_ACK = "com.example.localalert.sender.ACTION_ACK";
    private static final String CHANNEL_ID = "sender_status";
    private static final int NOTIFICATION_ID = 1001;
    private SimpleHttpServer server;
    private WifiManager.WifiLock wifiLock;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            createNotificationChannel();
            startForeground(NOTIFICATION_ID, buildNotification("جاهز لاستقبال تأكيد الرد"));
            server = new SimpleHttpServer(8081, (method, path) -> {
                if ("GET".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)) {
                    getSharedPreferences(MainActivity.PREFERENCES, MODE_PRIVATE)
                            .edit()
                            .putBoolean(MainActivity.KEY_ACK_RECEIVED, true)
                            .apply();
                    NotificationManager manager =
                            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if (manager != null) {
                        manager.notify(NOTIFICATION_ID, buildNotification("تم الرد"));
                    }
                    sendBroadcast(new Intent(ACTION_ACK));
                }
            server.start();
        } catch (IOException | RuntimeException error) {
            stopSelf();
        }
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(getString(com.example.localalert.sender.R.string.app_name))
                .setContentText(text)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void acquireWifiLock() {
        WifiManager manager = (WifiManager) getApplicationContext()
                .getSystemService(WIFI_SERVICE);
        if (manager == null) {
            return;
        }
        wifiLock = manager.createWifiLock(
                WifiManager.WIFI_MODE_FULL,
                "localalert:sender");
        wifiLock.setReferenceCounted(false);
        try {
            wifiLock.acquire();
        } catch (SecurityException ignored) {
            // Some Android builds restrict Wi-Fi locks; the service can still receive ACKs.
            wifiLock = null;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "حالة الاتصال المحلي",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("يبقي خادم تأكيد الرد فعالاً في الخلفية");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) {
            server.stop();
        }
        try {
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
            }
        } catch (RuntimeException ignored) {
            // The Wi-Fi lock may already be released by the device.
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}