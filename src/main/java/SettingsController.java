import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.*;

public class SettingsController {

    @FXML private VBox broadcastSendBox;
    @FXML private VBox broadcastListenBox;

    @FXML private CheckBox soundCheckBox;
    @FXML private CheckBox flashCheckBox;
    @FXML private Slider sensitivitySlider;
    @FXML private ChoiceBox<String> notificationSoundChoiceBox;

    @FXML private Label soundHelpLabel;
    @FXML private Label flashHelpLabel;
    @FXML private Label sensitivityHelpLabel;
    @FXML private Label broadcastSendHelpLabel;
    @FXML private Label broadcastListenHelpLabel;

    private final Map<String, CheckBox> broadcastSendMap = new HashMap<>();
    private final Map<String, CheckBox> broadcastListenMap = new HashMap<>();

    @FXML
    private void initialize() {
        // Initial values from AppConfig
        if (soundCheckBox != null) {
            soundCheckBox.setSelected(AppConfig.playSound);
            soundCheckBox.setTooltip(new Tooltip("If enabled, Taptic plays a sound whenever a notification is triggered."));
            soundCheckBox.selectedProperty().addListener((obs, old, val) -> AppConfig.playSound = val);
        }

        if (flashCheckBox != null) {
            flashCheckBox.setSelected(AppConfig.flashEmergency);
            flashCheckBox.setTooltip(new Tooltip("If enabled, the screen flashes red for a few seconds for emergency sounds."));
            flashCheckBox.selectedProperty().addListener((obs, old, val) -> AppConfig.flashEmergency = val);
        }

        if (sensitivitySlider != null) {
            sensitivitySlider.setMin(0.1);
            sensitivitySlider.setMax(0.9);
            sensitivitySlider.setValue(AppConfig.notifyThreshold);
            sensitivitySlider.setShowTickMarks(true);
            sensitivitySlider.setShowTickLabels(true);
            sensitivitySlider.setMajorTickUnit(0.2);
            sensitivitySlider.setBlockIncrement(0.05);
            sensitivitySlider.valueProperty().addListener(
                    (obs, old, val) -> AppConfig.notifyThreshold = val.doubleValue()
            );
        }

        if (notificationSoundChoiceBox != null) {
            notificationSoundChoiceBox.getItems().addAll("System beep", "Double beep", "None");
            notificationSoundChoiceBox.setValue(AppConfig.notificationSound);
            notificationSoundChoiceBox.getSelectionModel().selectedItemProperty().addListener(
                    (obs, old, val) -> {
                        if (val != null) AppConfig.notificationSound = val;
                    }
            );
        }

        if (soundHelpLabel != null) {
            soundHelpLabel.setText("Choose whether Taptic should play a sound when it sends you a notification.");
        }
        if (flashHelpLabel != null) {
            flashHelpLabel.setText("Emergency sounds (fire alarm, glass breaking, gunshot, etc.) can flash the screen red so you notice them visually.");
        }
        if (sensitivityHelpLabel != null) {
            sensitivityHelpLabel.setText("Higher sensitivity → more notifications (even low-confidence detections). Lower sensitivity → only very confident detections.");
        }
        if (broadcastSendHelpLabel != null) {
            broadcastSendHelpLabel.setText("Select which sounds this device should broadcast to other Taptic Desktop instances on your network.");
        }
        if (broadcastListenHelpLabel != null) {
            broadcastListenHelpLabel.setText("Select which sounds from other devices this computer should react to (as if it heard them itself).");
        }
    }

    /**
     * Called from TapticFxApp when settings view is first created.
     * Populates broadcast send/listen lists.
     */
    public void initWithLabels(String[] allLabels) {
        if (allLabels == null) return;

        List<String> interesting = new ArrayList<>();
        for (String label : allLabels) {
            if (label != null && isInterestingLabel(label)) {
                interesting.add(label);
            }
        }
        interesting.sort(String.CASE_INSENSITIVE_ORDER);

        Platform.runLater(() -> {
            broadcastSendBox.getChildren().clear();
            broadcastListenBox.getChildren().clear();
            broadcastSendMap.clear();
            broadcastListenMap.clear();

            for (String label : interesting) {
                // Send
                CheckBox sendCb = new CheckBox(label);
                sendCb.setSelected(true); // default: send everything useful
                sendCb.setTooltip(new Tooltip("If checked, this device tells the network when it hears this sound."));
                broadcastSendMap.put(label, sendCb);
                broadcastSendBox.getChildren().add(sendCb);
                // Initialize AppConfig
                AppConfig.setBroadcastSendEnabled(label, true);
                sendCb.selectedProperty().addListener(makeBroadcastListener(label, true));

                // Listen
                CheckBox listenCb = new CheckBox(label);
                listenCb.setSelected(true); // default: listen to everything useful
                listenCb.setTooltip(new Tooltip("If checked, this device reacts when another device hears this sound."));
                broadcastListenMap.put(label, listenCb);
                broadcastListenBox.getChildren().add(listenCb);
                AppConfig.setBroadcastListenEnabled(label, true);
                listenCb.selectedProperty().addListener(makeBroadcastListener(label, false));
            }
        });
    }

    private ChangeListener<Boolean> makeBroadcastListener(String label, boolean send) {
        return (obs, old, val) -> {
            if (send) {
                AppConfig.setBroadcastSendEnabled(label, val);
            } else {
                AppConfig.setBroadcastListenEnabled(label, val);
            }
        };
    }

    // Reuse the same "interesting" heuristics
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

    @FXML
    private void onBackClicked() {
        TapticFxApp app = TapticFxApp.getInstance();
        if (app != null) {
            app.showMainView();
        }
    }
}
