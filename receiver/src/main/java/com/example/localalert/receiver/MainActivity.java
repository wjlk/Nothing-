package com.example.localalert.receiver;

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
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    public static final String PREFERENCES = "local_alert_receiver";
    public static final String KEY_SENDER_IP = "sender_ip";
    public static final String KEY_ALARM_ACTIVE = "alarm_active";
    public static final String ACTION_ACK_SENT =
            "com.example.localalert.receiver.ACTION_ACK_SENT";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 11;

    private SharedPreferences preferences;
    private EditText senderIp;
    private TextView status;
    private final BroadcastReceiver eventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ReceiverService.ACTION_ALERT.equals(intent.getAction())) {
                showStatus("وصل نداء", false);
            } else if (ACTION_ACK_SENT.equals(intent.getAction())) {
                showStatus("تم الرد", true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        senderIp = findViewById(R.id.sender_ip);
        status = findViewById(R.id.status);
        senderIp.setText(preferences.getString(KEY_SENDER_IP, ""));

        TextView localIp = findViewById(R.id.local_ip);
        localIp.setText(getString(R.string.local_ip_format, NetworkUtils.getLocalIpv4(this)));

        Button save = findViewById(R.id.save_ip);
        save.setOnClickListener(view -> saveSenderIp());
        Button test = findViewById(R.id.test_alarm);
        test.setOnClickListener(view -> ReceiverServiceTest.start(this));

        requestNotificationPermission();
        startReceiverService();
        requestBatteryExemption();
        if (preferences.getBoolean(KEY_ALARM_ACTIVE, false)) {
            showStatus("يوجد نداء معلّق", false);
        }
        registerEvents();
    }

    private void registerEvents() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ReceiverService.ACTION_ALERT);
        filter.addAction(ACTION_ACK_SENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(eventReceiver, filter);
        }
    }

    private void saveSenderIp() {
        String ip = senderIp.getText().toString().trim();
        if (!NetworkUtils.isPrivateIpv4(ip)) {
            showStatus("اكتب عنوان IPv4 محلياً صحيحاً، مثل 192.168.1.101", false);
            return;
        }
        preferences.edit().putString(KEY_SENDER_IP, ip).apply();
        showStatus("تم حفظ العنوان", false);
        Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show();
    }

    private void showStatus(String message, boolean acknowledged) {
        status.setText(message);
        status.setTextColor(getColor(
                acknowledged ? android.R.color.holo_green_dark : android.R.color.black));
    }

    private void startReceiverService() {
        Intent intent = new Intent(this, ReceiverService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
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
        unregisterReceiver(eventReceiver);
        super.onDestroy();
    }

    /**
     * Small adapter used only by the local "اختبار الإنذار" button.
     */
    private static final class ReceiverServiceTest {
        private static void start(Context context) {
            Intent intent = new Intent(context, ReceiverService.class);
            intent.setAction(ReceiverService.ACTION_ALERT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            Toast.makeText(context, "للاختبار استخدم طلب /alert من هاتف الجد", Toast.LENGTH_SHORT)
                    .show();
        }
    }
}