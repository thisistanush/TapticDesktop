import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import javax.sound.sampled.*;
import java.util.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

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

    // Sound intensity meter
    @FXML private ProgressBar levelBar;

    @FXML private VBox monitoredBox;
    @FXML private VBox notifyBox;

    @FXML private TabPane optionsTabPane;

    // History sidebar
    @FXML private ListView<String> historyList;
    @FXML private ToggleButton historyToggle;
    @FXML private VBox historyDrawer;
    private TranslateTransition historyTransition;

    // Caption tab (chat-like)
    @FXML private ListView<CaptionMessage> captionList;
    @FXML private TextField captionInputField;
    @FXML private ToggleButton captionMicToggle;

    @FXML private Label micWarningLabel;

    private final Map<String, CheckBox> monitoredMap = new HashMap<>();
    private final Map<String, CheckBox> notifyMap = new HashMap<>();
    private final Map<ProgressBar, Timeline> progressAnimations = new HashMap<>();

    private Timeline flashTimeline;

    // STT service (Google Cloud Speech)
    private SttService sttService;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML
    private void initialize() {
        if (statusLabel != null) {
            statusLabel.setText("Listening…");
        }

        if (micWarningLabel != null) {
            micWarningLabel.setVisible(false);
            micWarningLabel.setManaged(false);
        }

        // Enter in caption field sends message
        if (captionInputField != null) {
            captionInputField.setOnAction(e -> sendCaptionMessage());
        }

        if (captionMicToggle != null) {
            captionMicToggle.setText("Start mic");
        }

        // Caption list with replay button for "You" messages
        if (captionList != null) {
            captionList.setCellFactory(list -> new ListCell<>() {
                private final Label textLabel = new Label();
                private final Button replayButton = new Button("Replay");
                private final HBox container = new HBox(8, textLabel, replayButton);

                {
                    replayButton.setStyle("-fx-text-fill: black;");
                    replayButton.setOnAction(e -> {
                        CaptionMessage msg = getItem();
                        if (msg != null) {
                            speakText(msg.getText());
                        }
                    });
                }

                @Override
                protected void updateItem(CaptionMessage item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                    } else {
                        textLabel.setText(item.getSpeaker() + ": " + item.getText());
                        // Replay only for your own messages
                        replayButton.setVisible("You".equals(item.getSpeaker()));
                        setGraphic(container);
                    }
                }
            });
        }

        if (historyList != null) {
            historyList.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);
                        boolean important = item.startsWith("★");
                        boolean remote = item.contains("[REMOTE]");
                        if (important) {
                            setStyle("-fx-text-fill: #FFC46B; -fx-font-weight: bold;");
                        } else if (remote) {
                            setStyle("-fx-text-fill: #8AB4FF;");
                        } else {
                            setStyle("-fx-text-fill: #E5E9F0;");
                        }
                    }
                }
            });
        }

        // History drawer slides in/out
        if (historyDrawer != null) {
            historyDrawer.setVisible(false);
            historyDrawer.setManaged(false);
            historyDrawer.setTranslateX(360);
            historyTransition = new TranslateTransition(Duration.millis(240), historyDrawer);
        }

        sttService = new SttService(this::pushCaptionText, this::postCaptionSystemMessage);
    }

    // ---------------------------------------------------------------------
    // Label lists (Monitored / Notify)
    // ---------------------------------------------------------------------

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

    private boolean isInterestingLabel(String label) {
        String lower = label.toLowerCase(Locale.ROOT);

        String[] bad = {
                "silence", "quiet", "room tone", "noise", "static", "hum", "hiss",
                "wind noise", "white noise", "pink noise",
                "drip", "dripping", "raindrop"
        };
        for (String b : bad) {
            if (lower.contains(b)) return false;
        }

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

    // ---------------------------------------------------------------------
    // Status / sound level / top-3
    // ---------------------------------------------------------------------

    public void setStatusText(String text) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(text);
            }
        });
    }

    /** Sound intensity meter (0..1). Called from Interpreter. */
    public void updateSoundLevel(double level) {
        if (levelBar == null) return;
        double clamped = Math.max(0.0, Math.min(1.0, level));
        Platform.runLater(() -> animateProgress(levelBar, clamped));
    }

    /** Update the live "top 3" view. Called from Interpreter (NOT on FX thread). */
    public void updateTop3(String l1, double s1,
                           String l2, double s2,
                           String l3, double s3) {
        Platform.runLater(() -> {
            if (nowLabel != null) {
                animateNowLabel(l1 != null ? l1 : "-");
            }
            updateRow(top1Label, top1Bar, l1, s1);
            updateRow(top2Label, top2Bar, l2, s2);
            updateRow(top3Label, top3Bar, l3, s3);

            TapticFxApp app = TapticFxApp.getInstance();
            if (app != null) {
                app.updateBubbleText(l1 != null ? l1 : "-");
            }
        });
    }

    private void updateRow(Label label, ProgressBar bar, String cls, double score) {
        if (label == null || bar == null) return;
        String name = (cls == null) ? "-" : cls;
        double clamped = Math.max(0.0, Math.min(1.0, score));
        int pct = (int) Math.round(clamped * 100.0);
        animateLabel(label, String.format("%s (%d%%)", name, pct));
        animateProgress(bar, clamped);
    }

    private void animateNowLabel(String text) {
        if (nowLabel == null) return;
        if (Objects.equals(nowLabel.getText(), text)) return;
        nowLabel.setOpacity(0.25);
        nowLabel.setText(text);
        FadeTransition ft = new FadeTransition(Duration.millis(200), nowLabel);
        ft.setToValue(1.0);
        ft.play();
    }

    private void animateLabel(Label label, String text) {
        if (label == null) return;
        if (Objects.equals(label.getText(), text)) return;
        label.setOpacity(0.4);
        label.setText(text);
        FadeTransition ft = new FadeTransition(Duration.millis(160), label);
        ft.setToValue(1.0);
        ft.play();
    }

    private void animateProgress(ProgressBar bar, double target) {
        if (bar == null) return;
        target = Math.max(0, Math.min(1, target));
        Timeline existing = progressAnimations.get(bar);
        if (existing != null) {
            existing.stop();
        }
        double start = bar.getProgress();
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(bar.progressProperty(), start)),
                new KeyFrame(Duration.millis(260), new KeyValue(bar.progressProperty(), target))
        );
        progressAnimations.put(bar, t);
        t.play();
    }

    public boolean isMonitored(String label) {
        CheckBox cb = monitoredMap.get(label);
        return cb != null && cb.isSelected();
    }

    public boolean isNotifyEnabled(String label) {
        CheckBox cb = notifyMap.get(label);
        return cb != null && cb.isSelected();
    }

    // ---------------------------------------------------------------------
    // Notifications (local + remote)
    // ---------------------------------------------------------------------

    /** Local notification (this Mac). */
    public void handleNotification(String label, double score, boolean emergency) {
        Platform.runLater(() -> {
            int pct = (int) Math.round(score * 100.0);
            String text = String.format("THIS MAC • %s (%d%%)", label, pct);
            if (statusLabel != null) {
                statusLabel.setText(text);
                String color = AppConfig.getNotificationColor(label);
                statusLabel.setStyle("-fx-text-fill: " + color + ";");
            }

            if (AppConfig.playSound) {
                NotificationSoundPlayer.play(AppConfig.notificationSound);
            }

            showMacNotification("This Mac", label, score);

            if (emergency && AppConfig.flashEmergency) {
                flashEmergency();
            }
        });
    }

    /** Remote notification from another machine (via broadcast). */
    public void handleRemoteNotification(String label, String host, boolean emergency) {
        if (!AppConfig.isBroadcastListenEnabled(label)) return;
        if (!isMonitored(label)) return;
        if (!isNotifyEnabled(label)) return;

        final double score = 1.0;

        Platform.runLater(() -> {
            String source = (host == null || host.isBlank()) ? "Remote device" : host;
            String text = String.format("REMOTE (%s) • %s", source, label);
            if (statusLabel != null) {
                statusLabel.setText(text);
                String color = AppConfig.getNotificationColor(label);
                statusLabel.setStyle("-fx-text-fill: " + color + ";");
            }
            if (nowLabel != null) {
                nowLabel.setText(label);
            }

            updateRow(top1Label, top1Bar, label, score);
            updateRow(top2Label, top2Bar, null, 0.0);
            updateRow(top3Label, top3Bar, null, 0.0);

            if (AppConfig.playSound) {
                NotificationSoundPlayer.play(AppConfig.notificationSound);
            }

            showMacNotification("Remote: " + source, label, score);

            if (emergency && AppConfig.flashEmergency) {
                flashEmergency();
            }
        });
    }

    private void showMacNotification(String prefix, String label, double score) {
        String title = "Taptic Desktop";
        int pct = (int) Math.round(score * 100.0);
        String message = prefix + ": " + label + " (" + pct + "%)";

        try {
            String script = "display notification \"" + escapeForAppleScript(message) +
                    "\" with title \"" + escapeForAppleScript(title) + "\"";
            new ProcessBuilder("osascript", "-e", script).start();
        } catch (Exception ignored) {
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
        int steps = 16;
        for (int i = 0; i <= steps; i++) {
            boolean on = (i % 2 == 0);
            flashTimeline.getKeyFrames().add(new KeyFrame(
                    Duration.millis(i * 300),
                    e -> rootPane.setStyle(on
                            ? "-fx-background-color: #FFCDD2;"
                            : "-fx-background-color: #050814;")));
        }
        flashTimeline.play();
    }

    /** Called from Interpreter when mic is missing / broken. */
    public void showMicError(String msg) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText("Mic error: " + msg);
                statusLabel.setStyle("-fx-text-fill: #FF5252;");
            }
            if (micWarningLabel != null) {
                micWarningLabel.setText("Mic missing");
                micWarningLabel.setVisible(true);
                micWarningLabel.setManaged(true);
            }
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Microphone problem");
            alert.setHeaderText("Taptic cannot access the microphone.");
            alert.setContentText(msg);
            alert.show();
        });
    }

    // ---------------------------------------------------------------------
    // History: all sounds (local + remote) with important ones highlighted
    // ---------------------------------------------------------------------

    @FXML
    private void onHistoryToggled() {
        if (historyDrawer == null || historyTransition == null) return;
        boolean show = historyToggle != null && historyToggle.isSelected();
        historyDrawer.setVisible(true);
        historyDrawer.setManaged(true);
        historyTransition.stop();
        historyTransition.setToX(show ? 0 : 360);
        historyTransition.setOnFinished(e -> {
            if (!show) {
                historyDrawer.setVisible(false);
                historyDrawer.setManaged(false);
            }
        });
        historyTransition.playFromStart();
        if (historyToggle != null) {
            historyToggle.setText(show ? "History ▾" : "▸ History");
        }
    }

    @FXML
    private void onHistoryClearClicked() {
        if (historyList != null) {
            historyList.getItems().clear();
        }
    }

    public void addHistory(String label,
                           double score,
                           boolean emergency,
                           boolean local,
                           String host,
                            boolean important) {
        if (historyList == null || label == null) return;
        int pct = (int) Math.round(score * 100.0);
        String src;
        if (local) {
            src = "[THIS MAC]";
        } else {
            String h = (host == null || host.isBlank()) ? "remote" : host;
            src = "[REMOTE " + h + "]";
        }
        String tag = emergency ? "EMERGENCY" : "normal";
        String prefix = important ? "★ " : "";
        String time = TIME_FMT.format(LocalTime.now());
        String entry = String.format("%s%s %s – %s [%s] (%d%%)",
                prefix, time, src, label, tag, pct);

        Platform.runLater(() -> {
            historyList.getItems().add(0, entry); // newest on top
            if (historyList.getItems().size() > 400) {
                historyList.getItems().remove(historyList.getItems().size() - 1);
            }
        });
    }

    // ---------------------------------------------------------------------
    // Caption tab: text input + TTS for your replies
    // ---------------------------------------------------------------------

    private void sendCaptionMessage() {
        if (captionInputField == null || captionList == null) return;
        String text = captionInputField.getText();
        if (text == null || text.isBlank()) return;
        captionInputField.clear();

        CaptionMessage msg = new CaptionMessage("You", text);
        captionList.getItems().add(msg);

        // speak immediately
        speakText(text);
    }

    private void speakText(String text) {
        new Thread(() -> {
            try {
                new ProcessBuilder("say", "-r", "240", text).start();
            } catch (Exception ignored) {
            }
        }, "TTS").start();
    }

    @FXML
    private void onCaptionSendClicked() {
        sendCaptionMessage();
    }

    @FXML
    private void onCaptionMicToggled() {
        if (captionMicToggle == null) return;
        boolean on = captionMicToggle.isSelected();
        captionMicToggle.setText(on ? "Listening…" : "Start mic");

        if (on) {
            boolean ok = sttService != null && sttService.start();
            if (!ok) {
                captionMicToggle.setSelected(false);
                captionMicToggle.setText("Start mic");
            }
        } else {
            if (sttService != null) {
                sttService.stop();
            }
        }
    }

    private void pushCaptionText(String text) {
        if (text == null || text.isBlank()) return;
        Platform.runLater(() -> captionList.getItems().add(new CaptionMessage("Them", text)));
    }

    // ---------------------------------------------------------------------
    // REAL STT using Google Cloud Speech (streaming)
    // ---------------------------------------------------------------------

    private void postCaptionSystemMessage(String text) {
        if (text == null || text.isBlank()) return;
        Platform.runLater(() -> captionList.getItems().add(new CaptionMessage("System", text)));
    }

    // ---------------------------------------------------------------------
    // Settings navigation
    // ---------------------------------------------------------------------

    @FXML
    private void onSettingsClicked() {
        TapticFxApp app = TapticFxApp.getInstance();
        if (app != null) {
            app.showSettingsView();
        }
    }
}
