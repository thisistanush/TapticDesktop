import java.awt.Toolkit;

/**
 * Small helper for playing simple notification sounds.
 * Keeps MainViewController clean.
 */
public final class NotificationSoundPlayer {

    private NotificationSoundPlayer() {}

    public static void play(String name) {
        if (name == null) return;
        String trimmed = name.trim();
        if (trimmed.equalsIgnoreCase("None")) {
            return;
        }
        try {
            Toolkit.getDefaultToolkit().beep();
            if (trimmed.equalsIgnoreCase("Double beep")) {
                Thread.sleep(130);
                Toolkit.getDefaultToolkit().beep();
            }
        } catch (Throwable ignored) {
        }
    }
}
