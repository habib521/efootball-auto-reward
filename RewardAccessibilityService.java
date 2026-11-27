package com.efar.autoreward;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.NonNull;

public class RewardAccessibilityService extends AccessibilityService {

    public static final String ACTION_TOGGLE_SCAN = "com.efar.autoreward.ACTION_TOGGLE_SCAN";
    private static final long SCAN_INTERVAL_MS = 110L;
    private static final long CLICK_COOLDOWN_MS = 650L;

    private RewardDetector detector;
    private Handler handler;
    private boolean isServiceConnected = false;
    private boolean isScanningActive = true;
    private long lastClickTimeMs = 0L;

    private final BroadcastReceiver toggleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_TOGGLE_SCAN.equals(intent.getAction())) {
                boolean enabled = intent.getBooleanExtra(MainActivity.KEY_SCANNING_ENABLED, true);
                setScanningActive(enabled);
            }
        }
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        detector = new RewardDetector();
        handler = new Handler(Looper.getMainLooper());
        isServiceConnected = true;

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        isScanningActive = prefs.getBoolean(MainActivity.KEY_SCANNING_ENABLED, true);

        IntentFilter filter = new IntentFilter(ACTION_TOGGLE_SCAN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(toggleReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(toggleReceiver, filter);
        }

        if (isScanningActive) {
            startScanningLoop();
        }
    }

    private void setScanningActive(boolean active) {
        if (this.isScanningActive == active) return;
        this.isScanningActive = active;
        handler.removeCallbacksAndMessages(null);
        if (active && isServiceConnected) {
            startScanningLoop();
        }
    }

    private void startScanningLoop() {
        if (!isServiceConnected || !isScanningActive) return;

        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(@NonNull ScreenshotResult screenshot) {
                HardwareBuffer buffer = screenshot.getHardwareBuffer();
                Bitmap hardwareBitmap = null;
                Bitmap softwareBitmap = null;

                try {
                    hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
                    if (hardwareBitmap != null) {
                        softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false);
                    }
                } finally {
                    if (hardwareBitmap != null && !hardwareBitmap.isRecycled()) {
                        hardwareBitmap.recycle();
                    }
                    if (buffer != null) {
                        buffer.close();
                    }
                }

                if (softwareBitmap != null && isScanningActive) {
                    final Bitmap targetBitmap = softwareBitmap;
                    detector.analyzeScreen(targetBitmap, new RewardDetector.DetectionCallback() {
                        @Override
                        public void onTargetFound(float clickX, float clickY) {
                            long now = SystemClock.elapsedRealtime();
                            if (isScanningActive && (now - lastClickTimeMs) >= CLICK_COOLDOWN_MS) {
                                lastClickTimeMs = now;
                                performFastClick(clickX, clickY);
                            }
                        }

                        @Override
                        public void onScanFinished() {
                            if (!targetBitmap.isRecycled()) {
                                targetBitmap.recycle();
                            }
                            scheduleNextScan(SCAN_INTERVAL_MS);
                        }
                    });
                } else {
                    if (softwareBitmap != null && !softwareBitmap.isRecycled()) {
                        softwareBitmap.recycle();
                    }
                    scheduleNextScan(SCAN_INTERVAL_MS);
                }
            }

            @Override
            public void onFailure(int errorCode) {
                scheduleNextScan(220L);
            }
        });
    }

    private void scheduleNextScan(long delayMs) {
        if (isServiceConnected && isScanningActive) {
            handler.postDelayed(this::startScanningLoop, delayMs);
        }
    }

    private void performFastClick(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 10));
        dispatchGesture(builder.build(), null, null);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        isServiceConnected = false;
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        isServiceConnected = false;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (detector != null) {
            detector.close();
        }
        try {
            unregisterReceiver(toggleReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        super.onDestroy();
    }
}
