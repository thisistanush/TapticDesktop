import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class Interpreter {

    private static BroadcastSender sender;
    private static MainViewController mainController;

    // EMA smoothing (higher = more responsive, less laggy)
    private static boolean firstFrame = true;
    private static double smooth1, smooth2, smooth3;
    private static final double ALPHA = 0.7;

    // notification cooldown per label (ms)
    private static final long COOLDOWN_MS = 5000;
    private static final Map<String, Long> lastNotify = new HashMap<>();

    private Interpreter() {}

    /**
     * Called once from TapticFxApp after everything is created.
     */
    public static void init(BroadcastSender s,
                            MainViewController c,
                            String[] allLabels) {
        sender = s;
        mainController = c;
        if (mainController != null) {
            mainController.initMonitoredLists(allLabels);
        }
    }

    /**
     * Called from YamnetMic worker thread for every ~0.5s frame.
     */
    public static void onFrame(float[] scores, String[] labels) {
        if (mainController == null || scores == null || scores.length == 0) {
            return;
        }

        int n = scores.length;
        if (n < 3) {
            double s1 = scores[0];
            String l1 = labelAt(labels, 0);
            mainController.updateTop3(l1, s1, null, 0.0, null, 0.0);
            maybeNotify(l1, s1);
            return;
        }

        // Find top-3 indices by raw score
        int best1 = 0, best2 = 1, best3 = 2;
        for (int i = 0; i < n; i++) {
            float v = scores[i];
            if (v > scores[best1]) {
                best3 = best2;
                best2 = best1;
                best1 = i;
            } else if (i != best1 && v > scores[best2]) {
                best3 = best2;
                best2 = i;
            } else if (i != best1 && i != best2 && v > scores[best3]) {
                best3 = i;
            }
        }

        String l1 = labelAt(labels, best1);
        String l2 = labelAt(labels, best2);
        String l3 = labelAt(labels, best3);

        double s1 = scores[best1];
        double s2 = scores[best2];
        double s3 = scores[best3];

        // Smooth rankings so the progress bars feel less laggy
        if (firstFrame) {
            smooth1 = s1;
            smooth2 = s2;
            smooth3 = s3;
            firstFrame = false;
        } else {
            smooth1 = smooth1 + ALPHA * (s1 - smooth1);
            smooth2 = smooth2 + ALPHA * (s2 - smooth2);
            smooth3 = smooth3 + ALPHA * (s3 - smooth3);
        }

        mainController.updateTop3(l1, smooth1, l2, smooth2, l3, smooth3);

        // Notification logic uses RAW scores, not smoothed
        maybeNotify(l1, s1);
        maybeNotify(l2, s2);
        maybeNotify(l3, s3);
    }

    private static String labelAt(String[] labels, int idx) {
        if (labels == null || idx < 0 || idx >= labels.length) {
            return "class_" + idx;
        }
        String l = labels[idx];
        return (l == null || l.isEmpty()) ? ("class_" + idx) : l;
    }

    private static void maybeNotify(String label, double rawScore) {
        if (mainController == null || label == null) return;
        if (!mainController.isMonitored(label)) return;
        if (!mainController.isNotifyEnabled(label)) return;

        double threshold = AppConfig.notifyThreshold;
        if (rawScore < threshold) return;

        long now = System.currentTimeMillis();
        long last = lastNotify.getOrDefault(label, 0L);
        if (now - last < COOLDOWN_MS) return;
        lastNotify.put(label, now);

        boolean emergency = isEmergency(label);

        // Broadcast to other desktops if user wants to send this label
        if (sender != null && AppConfig.isBroadcastSendEnabled(label)) {
            try {
                sender.sendEvent(label);
            } catch (IOException e) {
                System.err.println("Broadcast error: " + e.getMessage());
            }
        }

        mainController.handleNotification(label, rawScore, emergency);
    }

    /**
     * A rough heuristic: treat fire/smoke/siren/alarm/glass breaking as emergencies.
     */
    static boolean isEmergency(String label) {
        if (label == null) return false;
        String s = label.toLowerCase();
        return s.contains("fire")
                || s.contains("smoke")
                || s.contains("siren")
                || s.contains("alarm")
                || s.contains("glass")
                || s.contains("gunshot")
                || s.contains("explosion")
                || s.contains("emergency")
                || s.contains("screaming");
    }

    /**
     * Called from BroadcastListener when another machine broadcasts a JSON event.
     */
    public static void handleBroadcastJson(String jsonText) {
        if (mainController == null || jsonText == null || jsonText.isEmpty()) {
            return;
        }
        try {
            JSONObject obj = new JSONObject(jsonText);
            String label = obj.optString("type", null);
            String host = obj.optString("host", "remote");
            if (label == null || label.isEmpty()) {
                return;
            }

            boolean emergency = isEmergency(label);
            mainController.handleRemoteNotification(label, host, emergency);

        } catch (Exception e) {
            System.err.println("Bad broadcast JSON: " + e.getMessage());
        }
    }
}
