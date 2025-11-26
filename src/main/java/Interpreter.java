import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Glue between the audio model (Yamnet), the UI, and broadcasting.
 * Most methods are small so readers can skim the flow from onFrame → maybeNotify.
 */
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
     * Called once from TapticFxApp when everything is created.
     */
    public static void init(BroadcastSender s,
                            MainViewController c,
                            String[] allLabels) {
        sender = s;
        mainController = c;
        if (mainController != null && allLabels != null) {
            mainController.initMonitoredLists(allLabels);
        }
    }

    /**
     * Backwards-compat hook if anything still calls this.
     * (Not strictly needed, but harmless.)
     */
    public static void setUiController(MainViewController c) {
        mainController = c;
    }

    /**
     * Main entry from YamnetMic: scores + label list + raw level.
     */
    public static void onFrame(float[] scores, String[] labels, double level) {
        if (mainController == null || scores == null || scores.length == 0) {
            return;
        }

        // Sound level meter
        mainController.updateSoundLevel(level);

        int n = scores.length;
        if (n < 3) {
            String l = labelAt(labels, 0);
            double s = scores[0];

            boolean emergency = isEmergency(l);

            // history: all sounds (important = false)
            mainController.addHistory(l, s, emergency, true, null, false);

            mainController.updateTop3(l, s, null, 0.0, null, 0.0);
            maybeNotify(l, s, true, null);
            return;
        }

        // Find top-3 indices by raw score. A tiny hand-written loop keeps
        // dependencies low and is easy to trace with a debugger.
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

        boolean e1 = isEmergency(l1);
        boolean e2 = isEmergency(l2);
        boolean e3 = isEmergency(l3);

        // add all heard sounds to history (important = false)
        mainController.addHistory(l1, s1, e1, true, null, false);
        mainController.addHistory(l2, s2, e2, true, null, false);
        mainController.addHistory(l3, s3, e3, true, null, false);

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
        maybeNotify(l1, s1, true, null);
        maybeNotify(l2, s2, true, null);
        maybeNotify(l3, s3, true, null);
    }

    /**
     * Backwards-compat overload: old code calling onFrame(scores, labels)
     * still works – level just defaults to 0.
     */
    public static void onFrame(float[] scores, String[] labels) {
        onFrame(scores, labels, 0.0);
    }

    private static String labelAt(String[] labels, int idx) {
        if (labels == null || idx < 0 || idx >= labels.length) {
            return "class_" + idx;
        }
        String l = labels[idx];
        return (l == null || l.isEmpty()) ? ("class_" + idx) : l;
    }

    private static void maybeNotify(String label,
                                    double rawScore,
                                    boolean local,
                                    String host) {
        if (mainController == null || label == null) return;
        if (!mainController.isMonitored(label)) return;
        if (!mainController.isNotifyEnabled(label)) return;

        double threshold = AppConfig.notifyThreshold; // global slider
        if (rawScore < threshold) return;

        long now = System.currentTimeMillis();
        long last = lastNotify.getOrDefault(label, 0L);
        if (now - last < COOLDOWN_MS) return;
        lastNotify.put(label, now);

        boolean emergency = isEmergency(label);

        // Broadcast to other desktops if local + user wants to send this label
        if (local && sender != null && AppConfig.isBroadcastSendEnabled(label)) {
            try {
                sender.sendEvent(label);
            } catch (IOException e) {
                System.err.println("Broadcast error: " + e.getMessage());
            }
        }

        // Mark as important in history + trigger UI
        if (local) {
            mainController.handleNotification(label, rawScore, emergency);
            mainController.addHistory(label, rawScore, emergency, true, null, true);
        } else {
            mainController.handleRemoteNotification(label, host, emergency);
            mainController.addHistory(label, rawScore, emergency, false, host, true);
        }
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
                || s.contains("screaming")
                || s.contains("crying")
                || s.contains("baby");
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

            // remote events are treated as full-confidence important hits
            maybeNotify(label, 1.0, false, host);

        } catch (Exception e) {
            System.err.println("Bad broadcast JSON: " + e.getMessage());
        }
    }

    /**
     * Mic error reporting from YamnetMic.
     */
    public static void reportMicError(String msg) {
        if (mainController != null) {
            mainController.showMicError(msg);
        }
    }
}
