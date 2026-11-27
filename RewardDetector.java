package com.efar.autoreward;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class RewardDetector {

    public interface DetectionCallback {
        void onTargetFound(float clickX, float clickY);
        void onScanFinished();
    }

    private final TextRecognizer recognizer;
    private volatile boolean isBusy = false;

    public RewardDetector() {
        this.recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    public void analyzeScreen(Bitmap bitmap, DetectionCallback callback) {
        if (bitmap == null || bitmap.isRecycled()) {
            callback.onScanFinished();
            return;
        }
        if (isBusy) {
            callback.onScanFinished();
            return;
        }
        isBusy = true;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int cropTop = (int) (height * 0.22);
        int cropHeight = Math.max(1, Math.min(height - cropTop, (int) (height * 0.60)));
        Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, cropTop, width, cropHeight);

        InputImage image = InputImage.fromBitmap(croppedBitmap, 0);

        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    boolean hasTargetAmount = false;
                    Rect okBounds = null;

                    for (Text.TextBlock block : visionText.getTextBlocks()) {
                        for (Text.Line line : block.getLines()) {
                            String raw = line.getText();
                            if (raw == null) continue;

                            String normalized = normalizeDigits(raw);
                            if (hasTargetNumber(normalized)) {
                                hasTargetAmount = true;
                            }

                            String compact = raw.replace(" ", "").trim();
                            if (compact.equalsIgnoreCase("OK")) {
                                okBounds = line.getBoundingBox();
                            }
                        }
                    }

                    if (hasTargetAmount && okBounds != null) {
                        float realX = okBounds.exactCenterX();
                        float realY = okBounds.exactCenterY() + cropTop;
                        callback.onTargetFound(realX, realY);
                    }

                    safeRecycle(croppedBitmap);
                    isBusy = false;
                    callback.onScanFinished();
                })
                .addOnFailureListener(e -> {
                    safeRecycle(croppedBitmap);
                    isBusy = false;
                    callback.onScanFinished();
                });
    }

    public static String normalizeDigits(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= '\u0660' && c <= '\u0669') {
                sb.append((char) ('0' + (c - '\u0660')));
            } else if (c >= '\u06F0' && c <= '\u06F9') {
                sb.append((char) ('0' + (c - '\u06F0')));
            } else if (Character.isDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static boolean hasTargetNumber(String normalizedDigits) {
        if (normalizedDigits == null || normalizedDigits.isEmpty()) return false;
        return normalizedDigits.equals("3000") || normalizedDigits.equals("5000")
                || normalizedDigits.contains("3000") || normalizedDigits.contains("5000");
    }

    private void safeRecycle(Bitmap bmp) {
        if (bmp != null && !bmp.isRecycled()) {
            bmp.recycle();
        }
    }

    public void close() {
        recognizer.close();
    }
}
