package com.example.localalert.sender;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Places the lock-screen Activity in front of the normal launcher when the
 * phone wakes while the keyguard is still showing.
 */
public class ScreenStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent broadcastIntent) {
        String action = broadcastIntent.getAction();
        if (!Intent.ACTION_SCREEN_ON.equals(action)
                && !Intent.ACTION_SCREEN_OFF.equals(action)) {
            return;
        }

        if (!context.getSharedPreferences(MainActivity.PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(MainActivity.KEY_LOCK_SCREEN_ENABLED, false)) {
            return;
        }

        KeyguardManager keyguard =
                (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguard == null || !keyguard.isKeyguardLocked()) {
            return;
        }

        Intent lockScreen = new Intent(context, LockScreenActivity.class);
        lockScreen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(lockScreen);
        } catch (RuntimeException ignored) {
            // Android may block background activity launches on some releases.
            // The foreground service remains available as a fallback.
        }
    }
}