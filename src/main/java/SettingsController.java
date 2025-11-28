import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.util.Objects.requireNonNullElse;

public class SettingsController {

    @FXML
    private VBox broadcastSendBox;
    @FXML
    private VBox broadcastListenBox;

    @FXML
    private CheckBox soundCheckBox;
    @FXML
    private CheckBox flashCheckBox;
    @FXML
    private Slider sensitivitySlider;
    @FXML
    private ChoiceBox<String> notificationSoundChoiceBox;
    @FXML
    private ChoiceBox<String> emergencySoundChoiceBox;
    @FXML
    private ChoiceBox<String> emergencyLabelChoiceBox;
    @FXML
    private ChoiceBox<String> notificationEmojiChoiceBox;

    @FXML
    private Label soundHelpLabel;
    @FXML
    private Label flashHelpLabel;
    @FXML
    private Label sensitivityHelpLabel;
    @FXML
    private Label broadcastSendHelpLabel;
    @FXML
    private Label broadcastListenHelpLabel;

    // Per-sound notification colors
    @FXML
    private VBox notificationColorBox;
    @FXML
    private FlowPane emergencyLabelChips;

    private final Map<String, CheckBox> broadcastSendMap = new HashMap<>();
    private final Map<String, CheckBox> broadcastListenMap = new HashMap<>();

    @FXML
    private void initialize() {
        if (soundCheckBox != null) {
            soundCheckBox.setSelected(AppConfig.playSound);
            soundCheckBox.setTooltip(new Tooltip(
                    "If enabled, Taptic plays a sound whenever a notification is triggered."));
            soundCheckBox.selectedProperty().addListener(
                    (obs, old, val) -> AppConfig.playSound = val);
        }

        if (flashCheckBox != null) {
            flashCheckBox.setSelected(AppConfig.flashEmergency);
            flashCheckBox.setTooltip(new Tooltip(
                    "If enabled, the screen flashes red for a few seconds for emergency sounds."));
            flashCheckBox.selectedProperty().addListener(
                    (obs, old, val) -> AppConfig.flashEmergency = val);
        }

        if (sensitivitySlider != null) {
            sensitivitySlider.setMin(0.05);
            sensitivitySlider.setMax(0.9);
            sensitivitySlider.setValue(AppConfig.notifyThreshold);
            sensitivitySlider.setShowTickMarks(true);
            sensitivitySlider.setShowTickLabels(true);
            sensitivitySlider.setMajorTickUnit(0.2);
            sensitivitySlider.setBlockIncrement(0.05);
            sensitivitySlider.valueProperty().addListener(
                    (obs, old, val) -> AppConfig.notifyThreshold = val.doubleValue());
        }

        if (notificationSoundChoiceBox != null) {
            notificationSoundChoiceBox.getItems().addAll(
                    "System beep",
                    "Double beep",
                    "None");
            notificationSoundChoiceBox.setValue(AppConfig.notificationSound);
            notificationSoundChoiceBox.getSelectionModel().selectedItemProperty()
                    .addListener((obs, old, val) -> {
                        if (val != null) {
                            AppConfig.notificationSound = val;
                        }
                    });
        }

        if (emergencySoundChoiceBox != null) {
            emergencySoundChoiceBox.getItems().addAll(
                    "Alarm pulse",
                    "Rapid beeps",
                    "Double beep",
                    "System beep");
            emergencySoundChoiceBox.setValue(AppConfig.emergencyNotificationSound);
            emergencySoundChoiceBox.getSelectionModel().selectedItemProperty()
                    .addListener((obs, old, val) -> {
                        if (val != null) {
                            AppConfig.emergencyNotificationSound = val;
                        }
                    });
        }

        if (notificationEmojiChoiceBox != null) {
            notificationEmojiChoiceBox.getItems().addAll(
                    "🔵", "🔴", "🟢", "🔔", "✨", "⚡");
            if (!notificationEmojiChoiceBox.getItems().contains(AppConfig.notificationEmoji)) {
                notificationEmojiChoiceBox.getItems().add(0, AppConfig.notificationEmoji);
            }
            notificationEmojiChoiceBox.setValue(AppConfig.notificationEmoji);
            notificationEmojiChoiceBox.setTooltip(new Tooltip(
                    "Emoji appended to macOS notifications."));
            notificationEmojiChoiceBox.getSelectionModel().selectedItemProperty()
                    .addListener((obs, old, val) -> {
                        if (val != null) {
                            AppConfig.notificationEmoji = val;
                        }
                    });
        }

        if (soundHelpLabel != null) {
            soundHelpLabel.setText(
                    "Choose whether Taptic should play a sound when it sends you a notification.");
        }
        if (flashHelpLabel != null) {
            flashHelpLabel.setText(
                    "Emergency sounds (fire alarm, glass breaking, gunshot, etc.) flash the whole screen bright red for 10 seconds.");
        }
        if (sensitivityHelpLabel != null) {
            sensitivityHelpLabel.setText(
                    "Higher sensitivity → more notifications (even low-confidence detections). Lower sensitivity → only very confident detections.");
        }
        if (broadcastSendHelpLabel != null) {
            broadcastSendHelpLabel.setText(
                    "Select which sounds this device should broadcast to other Taptic Desktop instances on your network.");
        }
        if (broadcastListenHelpLabel != null) {
            broadcastListenHelpLabel.setText(
                    "Select which sounds from other devices this computer should react to (as if it heard them itself).");
        }

        // Fallback: if initWithLabels wasn't called explicitly, populate using Yamnet
        // labels.
        if (broadcastSendBox != null && broadcastListenBox != null && notificationColorBox != null
                && broadcastSendBox.getChildren().isEmpty()
                && broadcastListenBox.getChildren().isEmpty()
                && notificationColorBox.getChildren().isEmpty()) {
            initWithLabels(requireNonNullElse(YamnetMic.getLabels(), new String[0]));
        }
    }

    public void initWithLabels(String[] allLabels) {
        System.out.println("SettingsController.initWithLabels called with " +
                (allLabels != null ? allLabels.length : 0) + " labels");

        if (allLabels == null)
            return;

        List<String> interesting = new ArrayList<>();
        for (String label : allLabels) {
            if (label != null && isInterestingLabel(label)) {
                interesting.add(label);
            }
        }
        interesting.sort(String.CASE_INSENSITIVE_ORDER);

        System.out.println("SettingsController: Found " + interesting.size() + " interesting labels");

        Platform.runLater(() -> {
            if (broadcastSendBox == null || broadcastListenBox == null || notificationColorBox == null) {
                // If this hits, FXML isn't wired correctly.
                System.err.println(
                        "SettingsController: Required VBox fields are null. Check fx:id in SettingsView.fxml.");
                System.err.println("  broadcastSendBox: " + (broadcastSendBox != null));
                System.err.println("  broadcastListenBox: " + (broadcastListenBox != null));
                System.err.println("  notificationColorBox: " + (notificationColorBox != null));
                return;
            }

            // Emergency label fields are optional
            if (emergencyLabelChoiceBox == null || emergencyLabelChips == null) {
                System.out.println(
                        "SettingsController: Emergency label UI elements not found in FXML (optional feature)");
            }

            broadcastSendBox.getChildren().clear();
            broadcastListenBox.getChildren().clear();
            notificationColorBox.getChildren().clear();
            if (emergencyLabelChips != null) { // Clear only if present
                emergencyLabelChips.getChildren().clear();
            }

            broadcastSendMap.clear();
            broadcastListenMap.clear();

            if (interesting.isEmpty()) {
                String placeholderText = "No labels available";

                Label sendPlaceholder = new Label(placeholderText);
                sendPlaceholder.getStyleClass().add("placeholder-label");
                broadcastSendBox.getChildren().add(sendPlaceholder);

                Label listenPlaceholder = new Label(placeholderText);
                listenPlaceholder.getStyleClass().add("placeholder-label");
                broadcastListenBox.getChildren().add(listenPlaceholder);

                Label colorPlaceholder = new Label(placeholderText);
                colorPlaceholder.getStyleClass().add("placeholder-label");
                notificationColorBox.getChildren().add(colorPlaceholder);

                emergencyLabelChoiceBox.getItems().clear();
                return;
            }

            for (String label : interesting) {
                boolean emergencyDefault = AppConfig.isEmergencyHeuristic(label);
                AppConfig.seedEmergencyLabel(label, emergencyDefault);

                // Broadcast send
                CheckBox sendCb = new CheckBox(label);
                sendCb.setSelected(true);
                sendCb.setTooltip(new Tooltip(
                        "If checked, this device tells the network when it hears this sound."));
                broadcastSendMap.put(label, sendCb);
                broadcastSendBox.getChildren().add(sendCb);
                AppConfig.setBroadcastSendEnabled(label, true);
                sendCb.selectedProperty().addListener(
                        makeBroadcastListener(label, true));

                // Broadcast listen
                CheckBox listenCb = new CheckBox(label);
                listenCb.setSelected(true);
                listenCb.setTooltip(new Tooltip(
                        "If checked, this device reacts when another device hears this sound."));
                broadcastListenMap.put(label, listenCb);
                broadcastListenBox.getChildren().add(listenCb);
                AppConfig.setBroadcastListenEnabled(label, true);
                listenCb.selectedProperty().addListener(
                        makeBroadcastListener(label, false));

                // Per-sound color row
                HBox row = new HBox(8);
                Label nameLabel = new Label(label);

                javafx.scene.control.ColorPicker picker = new javafx.scene.control.ColorPicker(
                        Color.web(AppConfig.getNotificationColor(label)));
                picker.setPrefWidth(120);
                picker.setTooltip(new Tooltip(
                        "Color used when this sound triggers a notification."));

                // Seed the map with the default color so it sticks immediately
                AppConfig.setNotificationColor(label, AppConfig.getNotificationColor(label));

                picker.valueProperty().addListener((obs, old, val) -> {
                    if (val != null) {
                        String cssColor = toCssColor(val);
                        AppConfig.setNotificationColor(label, cssColor);
                    }
                });

                row.getChildren().addAll(nameLabel, picker);
                notificationColorBox.getChildren().add(row);
            }

            if (emergencyLabelChoiceBox != null) {
                emergencyLabelChoiceBox.getItems().setAll(interesting);
                if (!interesting.isEmpty()) {
                    emergencyLabelChoiceBox.setValue(interesting.get(0));
                }
            }

            if (emergencyLabelChips != null) {
                refreshEmergencyChips();
            }
        });
    }

    @FXML
    private void onAddEmergencyLabelClicked() {
        if (emergencyLabelChoiceBox == null || emergencyLabelChips == null)
            return;
        String selected = emergencyLabelChoiceBox.getSelectionModel().getSelectedItem();
        if (selected == null || selected.isBlank())
            return;
        AppConfig.setEmergencyLabel(selected, true);
        refreshEmergencyChips();
    }

    private void refreshEmergencyChips() {
        if (emergencyLabelChips == null)
            return; // Optional feature
        emergencyLabelChips.getChildren().clear();
        AppConfig.getEmergencyLabels().stream()
                .sorted(String::compareToIgnoreCase)
                .forEach(label -> emergencyLabelChips.getChildren().add(makeChip(label)));
    }

    private HBox makeChip(String label) {
        HBox chip = new HBox(6);
        chip.getStyleClass().add("emergency-chip");
        Label name = new Label(label);
        Button remove = new Button("✕");
        remove.getStyleClass().add("chip-remove-button");
        remove.setOnAction(e -> {
            AppConfig.setEmergencyLabel(label, false);
            refreshEmergencyChips();
        });
        chip.getChildren().addAll(name, remove);
        return chip;
    }

    private String toCssColor(Color c) {
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return String.format("#%02X%02X%02X", r, g, b);
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

    private boolean isInterestingLabel(String label) {
        String lower = label.toLowerCase(Locale.ROOT);

        String[] bad = {
                "silence", "quiet", "room tone", "noise", "static", "hum", "hiss",
                "wind noise", "white noise", "pink noise",
                "drip", "dripping", "raindrop"
        };
        for (String b : bad) {
            if (lower.contains(b))
                return false;
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
            if (lower.contains(g))
                return true;
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
