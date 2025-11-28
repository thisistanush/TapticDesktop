import java.awt.Toolkit;

/**
 * Plays notification sounds using the system beep.
 * Supports different sound patterns for normal and emergency notifications.
 * 
 * Sounds play in a background thread so they don't block the UI.
 */
public final class NotificationSoundPlayer {

    // Private constructor - only static methods
    private NotificationSoundPlayer() {
    }

    /**
     * Play a normal notification sound.
     * 
     * @param name Sound name ("System beep", "Double beep", etc.)
     */
    public static void play(String name) {
        playInternal(name, false);
    }

    /**
     * Play an emergency notification sound (louder/more urgent).
     * 
     * @param name Sound name ("Alarm pulse", "Rapid beeps", etc.)
     */
    public static void playEmergency(String name) {
        playInternal(name, true);
    }

    /**
     * Internal method to play a sound in a background thread.
     * 
     * @param name      Sound name
     * @param emergency True for emergency sounds, false for normal
     */
    private static void playInternal(String name, boolean emergency) {
        if (name == null) {
            return;
        }

        String trimmed = name.trim();
        if (trimmed.equalsIgnoreCase("None")) {
            return; // User disabled sound
        }

        // Play in background thread so it doesn't block the UI
        String threadName;
        if (emergency) {
            threadName = "EmergencySound";
        } else {
            threadName = "NotificationSound";
        }

        Thread soundThread = new Thread(() -> {
            try {
                if (emergency) {
                    playEmergencyPattern(trimmed);
                } else {
                    playStandardPattern(trimmed);
                }
            } catch (Throwable ignored) {
                // Ignore sound playback errors
            }
        }, threadName);

        soundThread.setDaemon(true);
        soundThread.start();
    }

    /**
     * Play a standard notification sound pattern.
     * 
     * @param name Sound name
     * @throws InterruptedException If interrupted while sleeping
     */
    private static void playStandardPattern(String name) throws InterruptedException {
        Toolkit.getDefaultToolkit().beep();

        if (name.equalsIgnoreCase("Double beep")) {
            Thread.sleep(130);
            Toolkit.getDefaultToolkit().beep();
        }
    }

    /**
     * Play an emergency sound pattern (more urgent).
     * 
     * @param name Sound name
     * @throws InterruptedException If interrupted while sleeping
     */
    private static void playEmergencyPattern(String name) throws InterruptedException {
        if (name.equalsIgnoreCase("Alarm pulse")) {
            // Rapid double-beep pattern (6 times)
            for (int i = 0; i < 6; i++) {
                Toolkit.getDefaultToolkit().beep();
                Thread.sleep(180);
                Toolkit.getDefaultToolkit().beep();
                Thread.sleep(220);
            }
        } else if (name.equalsIgnoreCase("Rapid beeps")) {
            // Fast repeated beeps (12 times)
            for (int i = 0; i < 12; i++) {
                Toolkit.getDefaultToolkit().beep();
                Thread.sleep(120);
            }
        } else {
            // Unknown emergency sound, just play standard
            playStandardPattern(name);
        }
    }
}
