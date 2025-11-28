import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Application configuration and settings storage.
 * Stores all user preferences including:
 * - Notification behavior (sound, flash, threshold)
 * - Emergency sound classifications
 * - Per-sound notification colors
 * - Network broadcast settings
 */
public final class AppConfig {

    // Private constructor - this class is only for static methods and fields
    private AppConfig() {
    }

    // ============ GLOBAL NOTIFICATION SETTINGS ============

    /**
     * Whether to play a sound when notifications appear.
     * User can toggle this in settings.
     */
    public static volatile boolean playSound = true;

    /**
     * Whether to flash the screen red for emergency sounds.
     * User can toggle this in settings.
     */
    public static volatile boolean flashEmergency = true;

    /**
     * Minimum confidence score (0.0 to 1.0) required to trigger notifications.
     * Lower = more sensitive (more notifications).
     * Higher = less sensitive (only high-confidence detections).
     * Default is 0.20 (20% confidence).
     */
    public static volatile double notifyThreshold = 0.20;

    /**
     * Sound file to play for normal notifications.
     * Example: "System beep"
     */
    public static volatile String notificationSound = "System beep";

    /**
     * Sound file to play for emergency notifications (louder/different).
     * Example: "Alarm pulse"
     */
    public static volatile String emergencyNotificationSound = "Alarm pulse";

    /**
     * Emoji to show in macOS notification titles.
     * Example: "🔵"
     */
    public static volatile String notificationEmoji = "🔵";

    // ============ NETWORK BROADCAST SETTINGS ============

    /**
     * List of sound labels that should be broadcast to other devices.
     * When this device detects these sounds, it sends them over the network.
     */
    private static final List<String> broadcastSendLabels = new ArrayList<>();

    /**
     * List of sound labels that we want to receive from other devices.
     * When another device broadcasts these sounds, we show notifications.
     */
    private static final List<String> broadcastListenLabels = new ArrayList<>();

    // ============ PER-SOUND SETTINGS ============

    /**
     * Custom notification colors for each sound.
     * Maps sound label (lowercase) to CSS color string like "#FF5252".
     * If a sound isn't in this map, it uses a default color.
     */
    private static final java.util.Map<String, String> notificationColors = new java.util.HashMap<>();

    /**
     * Set of sounds that should trigger emergency alerts.
     * Emergency sounds cause:
     * - Screen flash (red, 10 seconds)
     * - Louder/different notification sound
     * 
     * This includes both auto-detected emergencies (fire, smoke, alarm, etc.)
     * and user-selected custom emergency sounds.
     */
    private static final Set<String> emergencyLabels = new HashSet<>();

    // ============ HELPER METHODS ============

    /**
     * Normalize a sound label for consistent lookup.
     * Converts to lowercase and trims whitespace.
     * 
     * @param label The sound label to normalize
     * @return Normalized label, or null if input is invalid
     */
    private static String normalizeLabel(String label) {
        if (label == null) {
            return null;
        }

        String trimmed = label.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed.toLowerCase();
    }

    // ============ EMERGENCY SOUND MANAGEMENT ============

    /**
     * Pre-populate an emergency sound (used during app initialization).
     * Only adds if emergency=true, doesn't remove.
     * 
     * @param label     The sound label
     * @param emergency Whether this sound is an emergency
     */
    public static void seedEmergencyLabel(String label, boolean emergency) {
        if (!emergency) {
            return; // Only seed positives
        }

        String key = normalizeLabel(label);
        if (key != null) {
            emergencyLabels.add(key);
        }
    }

    /**
     * Set whether a sound should be treated as an emergency.
     * Called when user adds/removes sounds from the emergency list.
     * 
     * @param label     The sound label
     * @param emergency True to mark as emergency, false to unmark
     */
    public static void setEmergencyLabel(String label, boolean emergency) {
        String key = normalizeLabel(label);
        if (key == null) {
            return;
        }

        if (emergency) {
            emergencyLabels.add(key);
        } else {
            emergencyLabels.remove(key);
        }
    }

    /**
     * Check if a sound should trigger emergency alerts.
     * First checks user-selected emergency sounds, then falls back to heuristics.
     * 
     * @param label The sound label
     * @return True if this is an emergency sound
     */
    public static boolean isEmergencyLabel(String label) {
        String key = normalizeLabel(label);
        if (key == null) {
            return false;
        }

        // Check if user manually marked this as emergency
        if (emergencyLabels.contains(key)) {
            return true;
        }

        // Check automatic detection heuristics
        if (isEmergencyHeuristic(key)) {
            // Auto-add so future checks are faster
            emergencyLabels.add(key);
            return true;
        }

        return false;
    }

    /**
     * Get all sounds currently marked as emergency.
     * 
     * @return Unmodifiable set of emergency sound labels (lowercase)
     */
    public static Set<String> getEmergencyLabels() {
        return java.util.Collections.unmodifiableSet(new HashSet<>(emergencyLabels));
    }

    /**
     * Check if a label matches emergency heuristics (before normalization).
     * 
     * @param label The sound label (any case)
     * @return True if this looks like an emergency sound
     */
    public static boolean isEmergencyHeuristic(String label) {
        String key = normalizeLabel(label);
        if (key == null) {
            return false;
        }
        return isEmergencyHeuristicNormalized(key);
    }

    /**
     * Check if a normalized label matches emergency keywords.
     * Emergency keywords include: fire, smoke, siren, alarm, glass, gunshot,
     * explosion, emergency, screaming, crying, baby.
     * 
     * @param normalizedLabel The sound label (already lowercase)
     * @return True if contains emergency keywords
     */
    private static boolean isEmergencyHeuristicNormalized(String normalizedLabel) {
        String s = normalizedLabel.toLowerCase();

        // Check for emergency-related keywords
        if (s.contains("fire"))
            return true;
        if (s.contains("smoke"))
            return true;
        if (s.contains("siren"))
            return true;
        if (s.contains("alarm"))
            return true;
        if (s.contains("glass"))
            return true;
        if (s.contains("gunshot"))
            return true;
        if (s.contains("explosion"))
            return true;
        if (s.contains("emergency"))
            return true;
        if (s.contains("screaming"))
            return true;
        if (s.contains("crying"))
            return true;
        if (s.contains("baby"))
            return true;

        return false;
    }

    // ============ BROADCAST SEND MANAGEMENT ============

    /**
     * Set whether a sound should be broadcast to other devices.
     * 
     * @param label   The sound label
     * @param enabled True to broadcast, false to not broadcast
     */
    public static void setBroadcastSendEnabled(String label, boolean enabled) {
        if (label == null) {
            return;
        }

        if (enabled) {
            addToListIfMissing(broadcastSendLabels, label);
        } else {
            broadcastSendLabels.remove(label);
        }
    }

    /**
     * Check if a sound should be broadcast to other devices.
     * 
     * @param label The sound label
     * @return True if broadcasting is enabled for this sound
     */
    public static boolean isBroadcastSendEnabled(String label) {
        if (label == null) {
            return false;
        }
        return broadcastSendLabels.contains(label);
    }

    // ============ BROADCAST LISTEN MANAGEMENT ============

    /**
     * Set whether we should listen for this sound from other devices.
     * 
     * @param label   The sound label
     * @param enabled True to listen, false to ignore
     */
    public static void setBroadcastListenEnabled(String label, boolean enabled) {
        if (label == null) {
            return;
        }

        if (enabled) {
            addToListIfMissing(broadcastListenLabels, label);
        } else {
            broadcastListenLabels.remove(label);
        }
    }

    /**
     * Check if we should listen for this sound from other devices.
     * 
     * @param label The sound label
     * @return True if listening is enabled for this sound
     */
    public static boolean isBroadcastListenEnabled(String label) {
        if (label == null) {
            return false;
        }
        return broadcastListenLabels.contains(label);
    }

    // ============ NOTIFICATION COLOR MANAGEMENT ============

    /**
     * Set the notification color for a specific sound.
     * Used by in-app notification popups (macOS notifications can't be colored).
     * 
     * @param label    The sound label
     * @param cssColor CSS color string like "#FF5252"
     */
    public static void setNotificationColor(String label, String cssColor) {
        if (label == null || cssColor == null || cssColor.isEmpty()) {
            return;
        }

        String key = normalizeLabel(label);
        if (key != null) {
            notificationColors.put(key, cssColor);
        }
    }

    /**
     * Get the notification color for a specific sound.
     * Returns the user-set color, or a default color if not set.
     * Default colors: red for emergencies, blue for normal sounds.
     * 
     * @param label The sound label
     * @return CSS color string like "#8AB4FF"
     */
    public static String getNotificationColor(String label) {
        String key = normalizeLabel(label);
        if (key == null) {
            return "#8AB4FF"; // Default blue
        }

        // Check if user set a custom color
        String customColor = notificationColors.get(key);
        if (customColor != null) {
            return customColor;
        }

        // Use default colors based on emergency status
        boolean isEmergency = isEmergencyLabel(label);
        if (isEmergency) {
            return "#FF5252"; // Red for emergencies
        } else {
            return "#8AB4FF"; // Blue for normal sounds
        }
    }

    // ============ UTILITY METHODS ============

    /**
     * Add a value to a list only if it's not already present.
     * Prevents duplicates.
     * 
     * @param list  The list to add to
     * @param value The value to add
     */
    private static void addToListIfMissing(List<String> list, String value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }
}
