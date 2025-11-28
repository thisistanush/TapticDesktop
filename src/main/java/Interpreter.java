import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Interpreter connects the audio model (YamnetMic), the UI
 * (MainViewController),
 * and network broadcasting (BroadcastSender/Listener).
 * 
 * This class receives audio classification results and decides:
 * - Which sounds to show on the UI
 * - When to send notifications
 * - Whether to broadcast sounds to other devices on the network
 */
public final class Interpreter {

    // References to other components
    private static BroadcastSender sender;
    private static MainViewController mainController;

    // Smoothing variables for UI progress bars (makes them less jumpy)
    // EMA = Exponential Moving Average
    private static boolean firstFrame = true;
    private static double smooth1;
    private static double smooth2;
    private static double smooth3;
    private static final double ALPHA = 0.7; // Higher = more responsive, less smooth

    // Notification cooldown: don't send same notification more than once per 5
    // seconds
    private static final long COOLDOWN_MS = 5000;
    private static final Map<String, Long> lastNotify = new HashMap<>();

    // Private constructor - this class is only for static methods
    private Interpreter() {
    }

    /**
     * Initialize the Interpreter with required components.
     * Called once from TapticFxApp when the application starts.
     * 
     * @param s         The broadcast sender for network communication
     * @param c         The main view controller for UI updates
     * @param allLabels All possible sound labels from the AI model
     */
    public static void init(BroadcastSender s, MainViewController c, String[] allLabels) {
        sender = s;
        mainController = c;

        // Set up the monitored/notify checkboxes in the UI
        if (mainController != null && allLabels != null) {
            mainController.initMonitoredLists(allLabels);
        }
    }

    /**
     * Process one frame of audio results from the AI model.
     * Called continuously by YamnetMic as audio is processed.
     * 
     * @param scores Confidence scores for each sound class (0.0 to 1.0)
     * @param labels Names of the sound classes
     * @param level  Raw audio level (0.0 to 1.0) for the meter display
     */
    public static void onFrame(float[] scores, String[] labels, double level) {
        // Safety check: need valid controller and scores
        if (mainController == null || scores == null || scores.length == 0) {
            return;
        }

        // Update sound level meter in UI
        mainController.updateSoundLevel(level);

        int numScores = scores.length;

        // Edge case: if we have fewer than 3 results
        if (numScores < 3) {
            String label = getLabelAt(labels, 0);
            double score = scores[0];
            boolean emergency = isEmergency(label);

            // Add to history (not marked as important yet)
            mainController.addHistory(label, score, emergency, true, null, false);

            // Update the top 3 display
            mainController.updateTop3(label, score, null, 0.0, null, 0.0);

            // Check if we should notify
            maybeNotify(label, score, true, null);
            return;
        }

        // Find the top 3 highest scoring sounds
        // We do this manually to avoid sorting the entire array
        int best1 = 0;
        int best2 = 1;
        int best3 = 2;

        for (int i = 0; i < numScores; i++) {
            float currentScore = scores[i];

            if (currentScore > scores[best1]) {
                // New best! Shift everything down
                best3 = best2;
                best2 = best1;
                best1 = i;
            } else if (i != best1 && currentScore > scores[best2]) {
                // New second best
                best3 = best2;
                best2 = i;
            } else if (i != best1 && i != best2 && currentScore > scores[best3]) {
                // New third best
                best3 = i;
            }
        }

        // Get the labels and scores for top 3
        String label1 = getLabelAt(labels, best1);
        String label2 = getLabelAt(labels, best2);
        String label3 = getLabelAt(labels, best3);

        double score1 = scores[best1];
        double score2 = scores[best2];
        double score3 = scores[best3];

        boolean emergency1 = isEmergency(label1);
        boolean emergency2 = isEmergency(label2);
        boolean emergency3 = isEmergency(label3);

        // Add all top 3 sounds to history (not marked as important yet)
        mainController.addHistory(label1, score1, emergency1, true, null, false);
        mainController.addHistory(label2, score2, emergency2, true, null, false);
        mainController.addHistory(label3, score3, emergency3, true, null, false);

        // Apply smoothing to make the progress bars less jumpy
        if (firstFrame) {
            // First time: just use the raw scores
            smooth1 = score1;
            smooth2 = score2;
            smooth3 = score3;
            firstFrame = false;
        } else {
            // Apply exponential moving average
            smooth1 = smooth1 + ALPHA * (score1 - smooth1);
            smooth2 = smooth2 + ALPHA * (score2 - smooth2);
            smooth3 = smooth3 + ALPHA * (score3 - smooth3);
        }

        // Update the UI with smoothed scores for display
        mainController.updateTop3(label1, smooth1, label2, smooth2, label3, smooth3);

        // Check if we should send notifications (uses RAW scores, not smoothed)
        maybeNotify(label1, score1, true, null);
        maybeNotify(label2, score2, true, null);
        maybeNotify(label3, score3, true, null);
    }

    /**
     * Get a label from the array, with safety checks.
     * 
     * @param labels Array of labels
     * @param index  Index to get
     * @return The label, or a fallback like "class_0" if index is invalid
     */
    private static String getLabelAt(String[] labels, int index) {
        // Check if index is valid
        if (labels == null || index < 0 || index >= labels.length) {
            return "class_" + index;
        }

        String label = labels[index];

        // Check if label is empty
        if (label == null || label.isEmpty()) {
            return "class_" + index;
        }

        return label;
    }

    /**
     * Decide whether to send a notification for a detected sound.
     * Checks several conditions before notifying:
     * - Is the sound being monitored?
     * - Are notifications enabled for this sound?
     * - Does the confidence score meet the threshold?
     * - Has enough time passed since the last notification?
     * 
     * @param label    The sound label
     * @param rawScore The AI confidence score (0.0 to 1.0)
     * @param local    True if detected locally, false if received from network
     * @param host     The hostname if received from network (null if local)
     */
    private static void maybeNotify(String label, double rawScore, boolean local, String host) {
        // Safety checks
        if (mainController == null || label == null) {
            return;
        }

        // Check if this sound is being monitored
        if (!mainController.isMonitored(label)) {
            return;
        }

        // Check if notifications are enabled for this sound
        if (!mainController.isNotifyEnabled(label)) {
            return;
        }

        // Check if confidence score meets the threshold
        double threshold = AppConfig.notifyThreshold;
        if (rawScore < threshold) {
            return;
        }

        // Check cooldown: don't spam notifications
        long now = System.currentTimeMillis();
        Long lastTime = lastNotify.get(label);
        if (lastTime != null) {
            long timeSinceLast = now - lastTime;
            if (timeSinceLast < COOLDOWN_MS) {
                return; // Too soon, skip notification
            }
        }
        lastNotify.put(label, now);

        // Determine if this is an emergency sound
        boolean emergency = isEmergency(label);

        // If this is a local detection and broadcasting is enabled, send to network
        if (local && sender != null && AppConfig.isBroadcastSendEnabled(label)) {
            try {
                sender.sendEvent(label);
            } catch (IOException e) {
                System.err.println("Broadcast error: " + e.getMessage());
            }
        }

        // Trigger the notification in the UI
        if (local) {
            // Local detection
            mainController.handleNotification(label, rawScore, emergency);
            mainController.addHistory(label, rawScore, emergency, true, null, true);
        } else {
            // Remote detection (received from network)
            mainController.handleRemoteNotification(label, host, emergency);
            mainController.addHistory(label, rawScore, emergency, false, host, true);
        }
    }

    /**
     * Check if a sound should be treated as an emergency.
     * Emergency sounds trigger special alerts (screen flash, louder notification
     * sound).
     * 
     * @param label The sound label
     * @return True if this is an emergency sound
     */
    static boolean isEmergency(String label) {
        return AppConfig.isEmergencyLabel(label);
    }

    /**
     * Handle a sound detection broadcast from another device on the network.
     * Called by BroadcastListener when it receives a JSON message.
     * 
     * @param jsonText The JSON message from the network
     */
    public static void handleBroadcastJson(String jsonText) {
        if (mainController == null || jsonText == null || jsonText.isEmpty()) {
            return;
        }

        try {
            // Parse the JSON message
            JSONObject obj = new JSONObject(jsonText);
            String label = obj.optString("type", null);
            String host = obj.optString("host", "remote");

            if (label == null || label.isEmpty()) {
                return;
            }

            // Treat remote events as full-confidence detections
            maybeNotify(label, 1.0, false, host);

        } catch (Exception e) {
            System.err.println("Bad broadcast JSON: " + e.getMessage());
        }
    }

    /**
     * Report a microphone error to the UI.
     * Called by YamnetMic if the microphone fails to initialize.
     * 
     * @param msg The error message
     */
    public static void reportMicError(String msg) {
        if (mainController != null) {
            mainController.showMicError(msg);
        }
    }
}
