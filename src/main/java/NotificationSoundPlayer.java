import java.awt.Toolkit;

/**
 * Small helper for playing simple notification sounds.
 * Keeps MainViewController clean.
 */
public final class NotificationSoundPlayer {

    private NotificationSoundPlayer() {}

    public static void play(String name) {
        playInternal(name, false);
    }

    public static void playEmergency(String name) {
        playInternal(name, true);
    }

    private static void playInternal(String name, boolean emergency) {
        if (name == null) return;
        final String trimmed = name.trim();
        if (trimmed.equalsIgnoreCase("None")) {
            return;
        }

        Thread t = new Thread(() -> {
            try {
                if (emergency) {
                    playEmergencyPattern(trimmed);
                } else {
                    playStandardPattern(trimmed);
                }
            } catch (Throwable ignored) {
            }
        }, emergency ? "EmergencySound" : "NotificationSound");
        t.setDaemon(true);
        t.start();
    }

    private static void playStandardPattern(String name) throws InterruptedException {
        Toolkit.getDefaultToolkit().beep();
        if (name.equalsIgnoreCase("Double beep")) {
            Thread.sleep(130);
            Toolkit.getDefaultToolkit().beep();
        }
    }

    private static void playEmergencyPattern(String name) throws InterruptedException {
        if (name.equalsIgnoreCase("Alarm pulse")) {
            for (int i = 0; i < 6; i++) {
                Toolkit.getDefaultToolkit().beep();
                Thread.sleep(180);
                Toolkit.getDefaultToolkit().beep();
                Thread.sleep(220);
            }
        } else if (name.equalsIgnoreCase("Rapid beeps")) {
            for (int i = 0; i < 12; i++) {
                Toolkit.getDefaultToolkit().beep();
                Thread.sleep(120);
            }
        } else {
            playStandardPattern(name);
        }
    }
}
