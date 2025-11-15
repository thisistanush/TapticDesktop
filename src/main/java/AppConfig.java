import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class AppConfig {

    private AppConfig() {}

    // Global notification behavior
    public static volatile boolean playSound = true;
    public static volatile boolean flashEmergency = true;
    public static volatile double notifyThreshold = 0.30;
    public static volatile String notificationSound = "System beep";

    // Broadcast label sets
    private static final Set<String> broadcastSendLabels =
            Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> broadcastListenLabels =
            Collections.synchronizedSet(new HashSet<>());

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
}
