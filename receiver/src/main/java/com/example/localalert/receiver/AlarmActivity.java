package com.example.localalert.receiver;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class AlarmActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeatures(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.activity_alarm);

        TextView title = findViewById(R.id.alarm_title);
        title.setText(R.string.alert_title);
        Button confirm = findViewById(R.id.confirm_button);
        confirm.setOnClickListener(view -> confirmAndClose());
    }

    private void confirmAndClose() {
        Intent intent = new Intent(this, ReceiverService.class);
        intent.setAction(ReceiverService.ACTION_CONFIRM);
        startService(intent);
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onBackPressed() {
        // Confirmation is intentionally required before the alarm can be dismissed.
    }
}