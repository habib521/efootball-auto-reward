package com.efar.autoreward;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "efar_prefs";
    public static final String KEY_SCANNING_ENABLED = "scanning_enabled";

    private TextView tvStatus;
    private Button btnToggleScan;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        tvStatus = findViewById(R.id.tvStatus);
        Button btnEnable = findViewById(R.id.btnEnableService);
        btnToggleScan = findViewById(R.id.btnToggleScan);

        btnEnable.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        btnToggleScan.setOnClickListener(v -> {
            boolean current = prefs.getBoolean(KEY_SCANNING_ENABLED, true);
            boolean next = !current;
            prefs.edit().putBoolean(KEY_SCANNING_ENABLED, next).apply();

            Intent broadcast = new Intent(RewardAccessibilityService.ACTION_TOGGLE_SCAN);
            broadcast.setPackage(getPackageName());
            broadcast.putExtra(KEY_SCANNING_ENABLED, next);
            sendBroadcast(broadcast);

            updateUI();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void updateUI() {
        boolean serviceEnabled = isAccessibilityServiceEnabled(this, RewardAccessibilityService.class);
        boolean scanningEnabled = prefs.getBoolean(KEY_SCANNING_ENABLED, true);

        if (!serviceEnabled) {
            tvStatus.setText("حالة الخدمة: غير مفعلة ❌");
            tvStatus.setTextColor(0xFFFF5252);
            btnToggleScan.setEnabled(false);
            btnToggleScan.setAlpha(0.5f);
            btnToggleScan.setText("إيقاف / تشغيل الفحص");
        } else if (scanningEnabled) {
            tvStatus.setText("حالة الخدمة: مفعلة وتعمل الآن ✅");
            tvStatus.setTextColor(0xFF4CAF50);
            btnToggleScan.setEnabled(true);
            btnToggleScan.setAlpha(1.0f);
            btnToggleScan.setText("إيقاف الفحص مؤقتاً ⏸️");
            btnToggleScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF9800));
        } else {
            tvStatus.setText("حالة الخدمة: متوقفة مؤقتاً ⏸️");
            tvStatus.setTextColor(0xFFFFB74D);
            btnToggleScan.setEnabled(true);
            btnToggleScan.setAlpha(1.0f);
            btnToggleScan.setText("استئناف الفحص السريع ▶️");
            btnToggleScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
        }
    }

    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> service) {
        String expectedComponentName = context.getPackageName() + "/" + service.getName();
        String enabledServicesSetting = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServicesSetting == null) return false;
        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServicesSetting);
        while (colonSplitter.hasNext()) {
            String componentName = colonSplitter.next();
            if (componentName.equalsIgnoreCase(expectedComponentName)) {
                return true;
            }
        }
        return false;
    }
}
