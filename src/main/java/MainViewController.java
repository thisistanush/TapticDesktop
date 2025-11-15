import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.*;

public class MainViewController {

    @FXML private BorderPane rootPane;

    @FXML private Label statusLabel;
    @FXML private Label nowLabel;
    @FXML private Label top1Label;
    @FXML private Label top2Label;
    @FXML private Label top3Label;

    @FXML private ProgressBar top1Bar;
    @FXML private ProgressBar top2Bar;
    @FXML private ProgressBar top3Bar;

    @FXML private VBox monitoredBox;
    @FXML private VBox notifyBox;

    @FXML private TabPane optionsTabPane;

    private final Map<String, CheckBox> monitoredMap = new HashMap<>();
    private final Map<String, CheckBox> notifyMap = new HashMap<>();

    private Timeline flashTimeline;

    @FXML
    private void initialize() {
        if (statusLabel != null) {
            statusLabel.setText("Listening…");
        }
    }

    /**
     * Called once from Interpreter.init() after labels are known.
     * Builds the dynamic lists for:
     *  - Monitored
     *  - Notify
     */
    public void initMonitoredLists(String[] allLabels) {
        if (allLabels == null) return;

        List<String> interesting = new ArrayList<>();
        for (String label : allLabels) {
            if (label != null && isInterestingLabel(label)) {
                interesting.add(label);
            }
        }
        interesting.sort(String.CASE_INSENSITIVE_ORDER);

        Platform.runLater(() -> {
            monitoredBox.getChildren().clear();
            notifyBox.getChildren().clear();

            monitoredMap.clear();
            notifyMap.clear();

            for (String label : interesting) {
                // Monitored
                CheckBox m = new CheckBox(label);
                m.setSelected(true);
                m.setTooltip(new Tooltip("If checked, Taptic will pay attention to this sound."));
                monitoredMap.put(label, m);
                monitoredBox.getChildren().add(m);

                // Notify
                CheckBox n = new CheckBox(label);
                n.setSelected(true);
                n.setTooltip(new Tooltip("If checked, you will get a notification for this sound."));
                notifyMap.put(label, n);
                notifyBox.getChildren().add(n);
            }
        });
    }

    /**
     * Rough heuristic: labels that a deaf user might care about.
     */
    private boolean isInterestingLabel(String label) {
        String lower = label.toLowerCase(Locale.ROOT);

        // Obvious "background" / useless things
        String[] bad = {
                "silence", "quiet", "room tone", "noise", "static", "hum", "hiss",
                "wind noise", "white noise", "pink noise",
                "drip", "dripping", "raindrop"
        };
        for (String b : bad) {
            if (lower.contains(b)) return false;
        }

        // Keep alarms, sirens, doorbells, knocks, phones, babies, dogs, glass, etc.
        String[] good = {
                "alarm", "fire", "smoke", "siren",
                "door", "doorbell", "door bell", "door knock", "knocking",
                "door open", "door close",
                "window", "glass", "glass breaking",
                "phone", "telephone", "ring", "ringtone",
                "baby", "infant", "cry", "crying",
                "child", "kid",
                "dog", "bark", "cat", "meow",
                "microwave", "oven", "timer", "beep",
                "washing machine", "laundry", "dryer",
                "dishwasher",
                "tap", "faucet", "running water",
                "car horn", "car alarm", "horn", "engine", "motorcycle",
                "gunshot", "explosion",
                "footstep", "walking", "knock",
                "shout", "scream", "yell",
                "applause",
                "cough", "sneeze",
                "thunder"
        };
        for (String g : good) {
            if (lower.contains(g)) return true;
        }

        return false;
    }

    public void setStatusText(String text) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(text);
            }
        });
    }

    /**
     * Update the live "top 3" view. Called from Interpreter (NOT on FX thread).
     */
    public void updateTop3(String l1, double s1,
                           String l2, double s2,
                           String l3, double s3) {
        Platform.runLater(() -> {
            if (nowLabel != null) {
                nowLabel.setText(l1 != null ? l1 : "-");
            }
            updateRow(top1Label, top1Bar, l1, s1);
            updateRow(top2Label, top2Bar, l2, s2);
            updateRow(top3Label, top3Bar, l3, s3);
        });
    }

    private void updateRow(Label label, ProgressBar bar, String cls, double score) {
        if (label == null || bar == null) return;
        String name = (cls == null) ? "-" : cls;
        double clamped = Math.max(0.0, Math.min(1.0, score));
        int pct = (int) Math.round(clamped * 100.0);
        label.setText(String.format("%s (%d%%)", name, pct));
        bar.setProgress(clamped);
    }

    public boolean isMonitored(String label) {
        CheckBox cb = monitoredMap.get(label);
        // If we don't know the label, default to "not monitored"
        return cb != null && cb.isSelected();
    }

    public boolean isNotifyEnabled(String label) {
        CheckBox cb = notifyMap.get(label);
        return cb != null && cb.isSelected();
    }

    /**
     * Called from Interpreter when a local sound crossed the threshold.
     */
    public void handleNotification(String label, double score, boolean emergency) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                int pct = (int) Math.round(score * 100.0);
                statusLabel.setText(String.format("Detected %s (%d%%)", label, pct));
            }

            if (AppConfig.playSound) {
                NotificationSoundPlayer.play(AppConfig.notificationSound);
            }

            showMacNotification(label, score);

            if (emergency && AppConfig.flashEmergency) {
                flashEmergency();
            }
        });
    }

    /**
     * Called from Interpreter when a remote machine broadcasted an event.
     */
    public void handleRemoteNotification(String label, String host, boolean emergency) {
        // Respect broadcast listen + monitored + notify
        if (!AppConfig.isBroadcastListenEnabled(label)) return;
        if (!isMonitored(label)) return;
        if (!isNotifyEnabled(label)) return;

        final double score = 1.0; // treat remote event as 100%

        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText("Remote: " + label + " (" + host + ")");
            }
            if (nowLabel != null) {
                nowLabel.setText(label);
            }

            // Show it as the top detection
            updateRow(top1Label, top1Bar, label, score);
            updateRow(top2Label, top2Bar, null, 0.0);
            updateRow(top3Label, top3Bar, null, 0.0);

            if (AppConfig.playSound) {
                NotificationSoundPlayer.play(AppConfig.notificationSound);
            }

            if (emergency && AppConfig.flashEmergency) {
                flashEmergency();
            }
        });
    }

    private void showMacNotification(String label, double score) {
        String title = "Taptic Desktop";
        int pct = (int) Math.round(score * 100.0);
        String message = label + " (" + pct + "%)";

        try {
            // macOS notification via AppleScript
            String script = "display notification \"" + escapeForAppleScript(message) +
                    "\" with title \"" + escapeForAppleScript(title) + "\"";
            new ProcessBuilder("osascript", "-e", script).start();
        } catch (Exception ignored) {
            // non-macOS or failure → just skip OS notification
        }
    }

    private String escapeForAppleScript(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void flashEmergency() {
        if (flashTimeline != null) {
            flashTimeline.stop();
        }
        flashTimeline = new Timeline();
        // ~5 seconds, toggle every 0.3s
        int steps = 16;
        for (int i = 0; i <= steps; i++) {
            boolean on = (i % 2 == 0);
            flashTimeline.getKeyFrames().add(new KeyFrame(
                    Duration.millis(i * 300),
                    e -> rootPane.setStyle(on
                            ? "-fx-background-color: #FFCDD2;"
                            : "-fx-background-color: white;")));
        }
        flashTimeline.play();
    }

    @FXML
    private void onSettingsClicked() {
        TapticFxApp app = TapticFxApp.getInstance();
        if (app != null) {
            app.showSettingsView();
        }
    }
}
