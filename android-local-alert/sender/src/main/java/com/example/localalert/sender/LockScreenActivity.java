package com.example.localalert.sender;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

/**
 * The sender's lock-screen surface. It is a real Activity shown over the
 * Android keyguard, not a notification action.
 */
public class LockScreenActivity extends Activity {
    private boolean launchedForLockScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        launchedForLockScreen = isKeyguardShowing();

        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.setShowWhenLocked(true);
            window.setTurnScreenOn(false);
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        hideSystemBars();

        if (!launchedForLockScreen) {
            finish();
            return;
        }

        setContentView(R.layout.activity_lock_screen);
        Button callButton = findViewById(R.id.lock_screen_alert_button);
        TextView status = findViewById(R.id.lock_screen_status);
        callButton.setOnClickListener(view -> {
            status.setText(R.string.lock_screen_sending);
            startSenderService();
        });
    }

    private boolean isKeyguardShowing() {
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return keyguard != null && keyguard.isKeyguardLocked();
    }

    private void startSenderService() {
        Intent intent = new Intent(this, SenderService.class);
        intent.setAction(SenderService.ACTION_LOCK_SCREEN_ALERT);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (RuntimeException error) {
            TextView status = findViewById(R.id.lock_screen_status);
            status.setText(R.string.lock_screen_service_error);
        }
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && launchedForLockScreen && !isKeyguardShowing()) {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (launchedForLockScreen && !isKeyguardShowing()) {
            finish();
        }
    }
}