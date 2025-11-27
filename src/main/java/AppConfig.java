import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AppConfig {

    private AppConfig() {}

    // Global notification behavior
    public static volatile boolean playSound = true;
    public static volatile boolean flashEmergency = true;
    public static volatile double notifyThreshold = 0.20;
    public static volatile String notificationSound = "System beep";
    public static volatile String emergencyNotificationSound = "Alarm pulse";

    // User-selected emergency labels
    private static final List<String> emergencyLabels = new ArrayList<>();

    // Broadcast label sets
    private static final List<String> broadcastSendLabels = new ArrayList<>();
    private static final List<String> broadcastListenLabels = new ArrayList<>();

    // Per-label notification colors (CSS color strings like "#FF5252")
    private static final List<LabelColor> notificationColors = new ArrayList<>();

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
            addIfMissing(emergencyLabels, key);
        }
    }

    public static void setEmergencyLabel(String label, boolean emergency) {
        String key = normalizeLabel(label);
        if (key == null) return;
        if (emergency) {
            addIfMissing(emergencyLabels, key);
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
            addIfMissing(emergencyLabels, key); // seed so future checks stay consistent
            return true;
        }
        return false;
    }

    public static List<String> getEmergencyLabels() {
        return Collections.unmodifiableList(new ArrayList<>(emergencyLabels));
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
            addIfMissing(broadcastSendLabels, label);
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
            addIfMissing(broadcastListenLabels, label);
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
            boolean updated = false;
            for (LabelColor lc : notificationColors) {
                if (lc.label.equals(key)) {
                    lc.cssColor = cssColor;
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                notificationColors.add(new LabelColor(key, cssColor));
            }
        }
    }

    public static String getNotificationColor(String label) {
        String key = normalizeLabel(label);
        if (key == null) return "#8AB4FF";
        for (LabelColor lc : notificationColors) {
            if (lc.label.equals(key)) {
                return lc.cssColor;
            }
        }

        // Default: emergencies are red, others blue
        if (Interpreter.isEmergency(label)) {
            return "#FF5252";
        }
        return "#8AB4FF";
    }

    private static void addIfMissing(List<String> list, String value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    private static final class LabelColor {
        private final String label;
        private String cssColor;

        private LabelColor(String label, String cssColor) {
            this.label = label;
            this.cssColor = cssColor;
        }
    }
}
