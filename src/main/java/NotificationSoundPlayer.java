import java.awt.*;

public final class NotificationSoundPlayer {

    private NotificationSoundPlayer() {}

    public static void play(String soundName) {
        if (soundName == null || "None".equalsIgnoreCase(soundName)) {
            return;
        }

        // Run on a background thread so we never block JavaFX
        new Thread(() -> {
            try {
                if ("System beep".equalsIgnoreCase(soundName)) {
                    Toolkit.getDefaultToolkit().beep();
                } else if ("Double beep".equalsIgnoreCase(soundName)) {
                    Toolkit.getDefaultToolkit().beep();
                    Thread.sleep(150);
                    Toolkit.getDefaultToolkit().beep();
                } else {
                    // fallback
                    Toolkit.getDefaultToolkit().beep();
                }
            } catch (InterruptedException ignored) {
            } catch (Throwable ignored) {
            }
        }, "NotifySound").start();
    }
}
