package com.example.localalert.sender;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    public static final String PREFERENCES = "local_alert_sender";
    public static final String KEY_RECEIVER_IP = "receiver_ip";
    public static final String KEY_ACK_RECEIVED = "ack_received";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 10;

    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private SharedPreferences preferences;
    private EditText receiverIp;
    private TextView status;
    private final BroadcastReceiver ackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showStatus("تم الرد", true);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        receiverIp = findViewById(R.id.receiver_ip);
        status = findViewById(R.id.status);
        receiverIp.setText(preferences.getString(KEY_RECEIVER_IP, ""));

        TextView localIp = findViewById(R.id.local_ip);
        localIp.setText(getString(R.string.local_ip_format, NetworkUtils.getLocalIpv4(this)));

        Button save = findViewById(R.id.save_ip);
        save.setOnClickListener(view -> saveReceiverIp());
        Button alertButton = findViewById(R.id.alert_button);
        alertButton.setOnClickListener(view -> sendAlert());

        requestNotificationPermission();
        startSenderService();
        requestBatteryExemption();
        if (preferences.getBoolean(KEY_ACK_RECEIVED, false)) {
            showStatus("تم الرد", true);
        }
        registerAckReceiver();
    }

    private void registerAckReceiver() {
        IntentFilter filter = new IntentFilter(SenderService.ACTION_ACK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(ackReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(ackReceiver, filter);
        }
    }

    private void saveReceiverIp() {
        String ip = receiverIp.getText().toString().trim();
        if (!NetworkUtils.isPrivateIpv4(ip)) {
            showStatus("اكتب عنوان IPv4 محلياً صحيحاً، مثل 192.168.1.100", false);
            return;
        }
        preferences.edit()
                .putString(KEY_RECEIVER_IP, ip)
                .putBoolean(KEY_ACK_RECEIVED, false)
                .apply();
        showStatus("تم حفظ العنوان", false);
        Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show();
    }

    private void sendAlert() {
        String ip = receiverIp.getText().toString().trim();
        if (!NetworkUtils.isPrivateIpv4(ip)) {
            showStatus("اكتب عنوان IPv4 محلياً صحيحاً، مثل 192.168.1.100", false);
            return;
        }
        preferences.edit()
                .putString(KEY_RECEIVER_IP, ip)
                .putBoolean(KEY_ACK_RECEIVED, false)
                .apply();
        showStatus("جارٍ إرسال النداء…", false);
        network.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("http://" + ip + ":8080/alert");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(3500);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                byte[] payload = "alert".getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(payload.length);
                connection.getOutputStream().write(payload);
                int responseCode = connection.getResponseCode();
                runOnUiThread(() -> showStatus(
                        responseCode >= 200 && responseCode < 300
                                ? "تم إرسال النداء"
                                : "تعذر إرسال النداء (" + responseCode + ")",
                        false));
            } catch (Exception error) {
                runOnUiThread(() -> showStatus(
                        "تعذر الاتصال. تأكد من الشبكة وعنوان IP", false));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void showStatus(String message, boolean acknowledged) {
        status.setText(message);
        status.setTextColor(getColor(
                acknowledged ? android.R.color.holo_green_light : android.R.color.white));
    }

    private void startSenderService() {
        Intent intent = new Intent(this, SenderService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (RuntimeException error) {
            showStatus("تعذر تشغيل خدمة الخلفية. فعّل إشعارات التطبيق.", false);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private void requestBatteryExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager == null || powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception ignored) {
            // Some manufacturers do not expose this settings screen.
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(ackReceiver);
        network.shutdownNow();
        super.onDestroy();
    }
}