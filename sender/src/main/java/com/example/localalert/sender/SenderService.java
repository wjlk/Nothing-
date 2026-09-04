package com.example.localalert.sender;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.net.wifi.WifiManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SenderService extends Service {
    public static final String ACTION_ACK = "com.example.localalert.sender.ACTION_ACK";
    public static final String ACTION_WIDGET_ALERT =
            "com.example.localalert.sender.ACTION_WIDGET_ALERT";
    private static final String CHANNEL_ID = "sender_status";
    private static final int NOTIFICATION_ID = 1001;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
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
            });
            server.start();
        } catch (IOException | RuntimeException error) {
            stopSelf();
        }
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Intent callIntent = new Intent(this, SenderService.class);
        callIntent.setAction(ACTION_WIDGET_ALERT);
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent callAction = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? PendingIntent.getForegroundService(
                        this, 7002, callIntent, pendingIntentFlags)
                : PendingIntent.getService(this, 7002, callIntent, pendingIntentFlags);
        return builder
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(getString(com.example.localalert.sender.R.string.app_name))
                .setContentText(text)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_call,
                        "إضغط للنداء",
                        callAction).build())
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
        if (intent != null && ACTION_WIDGET_ALERT.equals(intent.getAction())) {
            sendAlertFromWidget();
        }
        return START_STICKY;
    }

    private void sendAlertFromWidget() {
        String receiverIp = getSharedPreferences(MainActivity.PREFERENCES, MODE_PRIVATE)
                .getString(MainActivity.KEY_RECEIVER_IP, "");
        if (!NetworkUtils.isPrivateIpv4(receiverIp)) {
            updateServiceNotification("افتح التطبيق واحفظ عنوان الاستقبال أولاً");
            return;
        }

        updateServiceNotification("جارٍ إرسال النداء…");
        network.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("http://" + receiverIp + ":8080/alert");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(3500);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                byte[] payload = "alert".getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(payload);
                }
                int responseCode = connection.getResponseCode();
                updateServiceNotification(
                        responseCode >= 200 && responseCode < 300
                                ? "تم إرسال النداء من الشاشة الرئيسية"
                                : "تعذر إرسال النداء (" + responseCode + ")");
            } catch (Exception error) {
                updateServiceNotification("تعذر الاتصال بهاتف الاستقبال");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void updateServiceNotification(String text) {
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
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
        network.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}