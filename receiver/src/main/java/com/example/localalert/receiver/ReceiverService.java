package com.example.localalert.receiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.net.wifi.WifiManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReceiverService extends Service {
    public static final String ACTION_CONFIRM =
            "com.example.localalert.receiver.ACTION_CONFIRM";
    public static final String ACTION_ALERT =
            "com.example.localalert.receiver.ACTION_ALERT";
    public static final String EXTRA_SENDER_IP = "sender_ip";
    private static final String SERVICE_CHANNEL_ID = "server_status";
    private static final String CHANNEL_ID = "local_alert";
    private static final int SERVICE_NOTIFICATION_ID = 2001;
    private static final int ALERT_NOTIFICATION_ID = 2002;

    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private SimpleHttpServer server;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private WifiManager.WifiLock wifiLock;
    private boolean alarming;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification());
        acquireWifiLock();
        vibrator = getVibrator();
        server = new SimpleHttpServer(8080, (method, path) -> {
            if ("/alert".equals(path)) {
                triggerAlarm();
            }
        });
        try {
            server.start();
        } catch (IOException error) {
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CONFIRM.equals(intent.getAction())) {
            stopAlarmAndAcknowledge();
        } else if (intent != null && ACTION_ALERT.equals(intent.getAction())) {
            triggerAlarm();
        }
        return START_STICKY;
    }

    private synchronized void triggerAlarm() {
        if (alarming) {
            return;
        }
        alarming = true;
        getSharedPreferences(MainActivity.PREFERENCES, MODE_PRIVATE)
                .edit()
                .putBoolean(MainActivity.KEY_ALARM_ACTIVE, true)
                .apply();
        playAlarmSound();
        vibrate();
        showAlarmNotification();
        sendBroadcast(new Intent(ACTION_ALERT));
    }

    private void playAlarmSound() {
        try {
            android.net.Uri alarmUri = RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mediaPlayer.setLooping(true);
            mediaPlayer.setVolume(1.0f, 1.0f);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception ignored) {
            mediaPlayer = null;
        }
    }

    private void vibrate() {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        long[] pattern = {0, 900, 250, 900, 250};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        } else {
            vibrator.vibrate(pattern, 0);
        }
    }

    private Vibrator getVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            return manager == null ? null : manager.getDefaultVibrator();
        }
        return (Vibrator) getSystemService(VIBRATOR_SERVICE);
    }

    private Notification buildServiceNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, SERVICE_CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.server_running))
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
                "localalert:receiver");
        wifiLock.setReferenceCounted(false);
        wifiLock.acquire();
    }

    private void showAlarmNotification() {
        Intent alarmIntent = new Intent(this, AlarmActivity.class);
        alarmIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                20,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(getString(R.string.alert_title))
                .setContentText(getString(R.string.alert_body))
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(ALERT_NOTIFICATION_ID, builder.build());
        }
    }

    public synchronized void stopAlarmAndAcknowledge() {
        if (!alarming) {
            return;
        }
        alarming = false;
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (IllegalStateException ignored) {
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(ALERT_NOTIFICATION_ID);
        }
        getSharedPreferences(MainActivity.PREFERENCES, MODE_PRIVATE)
                .edit()
                .putBoolean(MainActivity.KEY_ALARM_ACTIVE, false)
                .apply();
        sendBroadcast(new Intent(MainActivity.ACTION_ACK_SENT));

        String senderIp = getSharedPreferences(MainActivity.PREFERENCES, MODE_PRIVATE)
                .getString(MainActivity.KEY_SENDER_IP, "");
        if (!senderIp.isEmpty()) {
            network.execute(() -> sendAcknowledgement(senderIp));
        }
    }

    private void sendAcknowledgement(String senderIp) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("http://" + senderIp + ":8081/ack");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(3500);
            connection.setDoOutput(true);
            byte[] payload = "تم الرد".getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            connection.getResponseCode();
        } catch (Exception ignored) {
            // The local alarm is already acknowledged even if the reply cannot reach sender.
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel serviceChannel = new NotificationChannel(
                SERVICE_CHANNEL_ID,
                "حالة الخادم المحلي",
                NotificationManager.IMPORTANCE_LOW);
        serviceChannel.setDescription("يبقي خادم النداء المحلي فعالاً");
        manager.createNotificationChannel(serviceChannel);

        NotificationChannel alertChannel = new NotificationChannel(
                CHANNEL_ID,
                "الإنذار والاتصال المحلي",
                NotificationManager.IMPORTANCE_HIGH);
        alertChannel.setDescription("إنذار النداء وإبقاء الخادم المحلي فعالاً");
        alertChannel.setSound(null, null);
        manager.createNotificationChannel(alertChannel);
    }

    @Override
    public void onDestroy() {
        stopAlarmOnly();
        if (server != null) {
            server.stop();
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        network.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private synchronized void stopAlarmOnly() {
        alarming = false;
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (IllegalStateException ignored) {
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(ALERT_NOTIFICATION_ID);
        }
        getSharedPreferences(MainActivity.PREFERENCES, MODE_PRIVATE)
                .edit()
                .putBoolean(MainActivity.KEY_ALARM_ACTIVE, false)
                .apply();
    }
}