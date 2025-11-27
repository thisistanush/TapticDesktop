import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class AppConfig {

    private AppConfig() {}

    // Global notification behavior
    public static volatile boolean playSound = true;
    public static volatile boolean flashEmergency = true;
    public static volatile double notifyThreshold = 0.20;
    public static volatile String notificationSound = "System beep";
    public static volatile String emergencyNotificationSound = "Alarm pulse";

    // User-selected emergency labels
    private static final Set<String> emergencyLabels =
            Collections.synchronizedSet(new HashSet<>());

    // Broadcast label sets
    private static final Set<String> broadcastSendLabels =
            Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> broadcastListenLabels =
            Collections.synchronizedSet(new HashSet<>());

    // Per-label notification colors (CSS color strings like "#FF5252")
    private static final Map<String, String> notificationColors =
            Collections.synchronizedMap(new HashMap<>());

    private static String normalizeLabel(String label) {
        if (label == null) return null;
        String trimmed = label.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.toLowerCase();
    }

    public static void seedEmergencyLabel(String label, boolean emergency) {
        if (!emergency) return; // only seed positives
        String key = normalizeLabel(label);
        if (key != null) {
            emergencyLabels.add(key);
        }
    }

    public static void setEmergencyLabel(String label, boolean emergency) {
        String key = normalizeLabel(label);
        if (key == null) return;
        if (emergency) {
            emergencyLabels.add(key);
        } else {
            emergencyLabels.remove(key);
        }
    }

    public static boolean isEmergencyLabel(String label) {
        String key = normalizeLabel(label);
        if (key == null) return false;
        if (emergencyLabels.contains(key)) {
            return true;
        }

        if (isEmergencyHeuristic(key)) {
            emergencyLabels.add(key); // seed so future checks stay consistent
            return true;
        }
        return false;
    }

    public static Set<String> getEmergencyLabels() {
        return Collections.unmodifiableSet(new HashSet<>(emergencyLabels));
    }

    public static boolean isEmergencyHeuristic(String label) {
        String key = normalizeLabel(label);
        if (key == null) return false;
        return isEmergencyHeuristicNormalized(key);
    }

    private static boolean isEmergencyHeuristicNormalized(String normalizedLabel) {
        String s = normalizedLabel.toLowerCase();
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

    public static void setBroadcastSendEnabled(String label, boolean enabled) {
        if (label == null) return;
        if (enabled) {
            broadcastSendLabels.add(label);
        } else {
            broadcastSendLabels.remove(label);
        }
    }

    public static boolean isBroadcastSendEnabled(String label) {
        if (label == null) return false;
        return broadcastSendLabels.contains(label);
    }

    public static void setBroadcastListenEnabled(String label, boolean enabled) {
        if (label == null) return;
        if (enabled) {
            broadcastListenLabels.add(label);
        } else {
            broadcastListenLabels.remove(label);
        }
    }

    public static boolean isBroadcastListenEnabled(String label) {
        if (label == null) return false;
        return broadcastListenLabels.contains(label);
    }

    public static void setNotificationColor(String label, String cssColor) {
        if (label == null || cssColor == null || cssColor.isEmpty()) return;
        String key = normalizeLabel(label);
        if (key != null) {
            notificationColors.put(key, cssColor);
        }
    }

    public static String getNotificationColor(String label) {
        String key = normalizeLabel(label);
        if (key == null) return "#8AB4FF";
        String c = notificationColors.get(key);
        if (c != null) return c;

        // Default: emergencies are red, others blue
        if (Interpreter.isEmergency(label)) {
            return "#FF5252";
        }
        return "#8AB4FF";
    }
}
