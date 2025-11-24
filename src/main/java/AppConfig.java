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

    // Broadcast label sets
    private static final Set<String> broadcastSendLabels =
            Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> broadcastListenLabels =
            Collections.synchronizedSet(new HashSet<>());

    // Per-label notification colors (CSS color strings like "#FF5252")
    private static final Map<String, String> notificationColors =
            Collections.synchronizedMap(new HashMap<>());

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
        notificationColors.put(label, cssColor);
    }

    public static String getNotificationColor(String label) {
        if (label == null) return "#8AB4FF";
        String c = notificationColors.get(label);
        if (c != null) return c;

        // Default: emergencies are red, others blue
        if (Interpreter.isEmergency(label)) {
            return "#FF5252";
        }
        return "#8AB4FF";
    }
}
