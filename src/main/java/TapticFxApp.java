import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;

public class TapticFxApp extends Application {

    private static TapticFxApp INSTANCE;

    private Stage primaryStage;
    private Scene mainScene;
    private Scene settingsScene;

    private MainViewController mainController;
    private SettingsController settingsController;
    private Stage bubbleStage;
    private Label bubbleLabel;

    private YamnetMic yamnetMic;
    private BroadcastListener broadcastListener;
    private BroadcastSender broadcastSender;

    private static final int PORT = 50000;

    public TapticFxApp() {
        INSTANCE = this;
    }

    public static TapticFxApp getInstance() {
        return INSTANCE;
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;

        // ----- MAIN VIEW -----
        URL mainFxml = findResource(
                "/fxml/MainView.fxml",
                "/fxml/main_view.fxml",
                "/MainView.fxml",
                "/main_view.fxml"
        );
        if (mainFxml == null) {
            throw new IllegalStateException(
                    "Could not find MainView.fxml or main_view.fxml.\n" +
                            "Put it in one of:\n" +
                            "  src/main/resources/fxml/MainView.fxml\n" +
                            "  src/main/resources/fxml/main_view.fxml\n" +
                            "  src/main/resources/MainView.fxml\n" +
                            "  src/main/resources/main_view.fxml"
            );
        }

        FXMLLoader mainLoader = new FXMLLoader(mainFxml);
        Parent mainRoot = mainLoader.load();
        mainController = mainLoader.getController();

        mainScene = new Scene(mainRoot, 900, 600);
        applyCss(mainScene);

        stage.setTitle("Taptic Desktop");
        stage.setScene(mainScene);
        stage.setMinWidth(800);
        stage.setMinHeight(500);
        stage.show();

        // Floating bubble when minimized
        setupBubbleStage();
        stage.iconifiedProperty().addListener((obs, old, iconified) -> {
            if (iconified) {
                showBubble();
            } else {
                hideBubble();
            }
        });

        // ----- AUDIO + NETWORK -----
        startAudioAndNetwork();
    }

    // Helper that tries multiple resource paths and returns the first that exists
    private URL findResource(String... paths) {
        for (String p : paths) {
            URL url = getClass().getResource(p);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    // Helper to apply CSS from likely locations
    private void applyCss(Scene scene) {
        if (scene == null) return;

        String[] cssPaths = {
                "/fxml/main.css",
                "/main.css",
                "/fxml/styles.css",
                "/styles.css"
        };

        for (String p : cssPaths) {
            URL css = getClass().getResource(p);
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
                break;
            }
        }
    }

    private void startAudioAndNetwork() {
        // Broadcast sender / listener
        try {
            broadcastSender = new BroadcastSender(PORT);
            broadcastListener = new BroadcastListener(PORT);
            Thread listenerThread = new Thread(broadcastListener, "BroadcastListener");
            listenerThread.setDaemon(true);
            listenerThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Interpreter hooks: controller + broadcast sender + labels
        try {
            yamnetMic = new YamnetMic();
            Interpreter.init(broadcastSender, mainController, YamnetMic.getLabels());

            Thread micThread = new Thread(yamnetMic, "YamnetMic");
            micThread.setDaemon(true);
            micThread.start();

            if (mainController != null) {
                mainController.setStatusText("Listening… say something loud near the mic.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (mainController != null) {
                mainController.showMicError(e.getMessage());
            }
        }
    }

    // ----- SETTINGS NAV -----

    public void showSettingsView() {
        if (settingsScene == null) {
            try {
                URL settingsFxml = findResource(
                        "/fxml/SettingsView.fxml",
                        "/fxml/settings_view.fxml",
                        "/SettingsView.fxml",
                        "/settings_view.fxml"
                );
                if (settingsFxml == null) {
                    throw new IllegalStateException(
                            "Could not find SettingsView.fxml or settings_view.fxml.\n" +
                                    "Put it in one of:\n" +
                                    "  src/main/resources/fxml/SettingsView.fxml\n" +
                                    "  src/main/resources/fxml/settings_view.fxml\n" +
                                    "  src/main/resources/SettingsView.fxml\n" +
                                    "  src/main/resources/settings_view.fxml"
                    );
                }

                FXMLLoader settingsLoader = new FXMLLoader(settingsFxml);
                Parent settingsRoot = settingsLoader.load();
                settingsController = settingsLoader.getController();

                settingsScene = new Scene(settingsRoot, 900, 600);
                applyCss(settingsScene);

                if (settingsController != null) {
                    settingsController.initWithLabels(YamnetMic.getLabels());
                }

            } catch (IOException e) {
                throw new RuntimeException("Failed to load SettingsView FXML", e);
            }
        }

        primaryStage.setScene(settingsScene);
    }

    public void showMainView() {
        if (mainScene != null) {
            primaryStage.setScene(mainScene);
        }
    }

    // Bubble text hook (for floating popup UI)
    public void updateBubbleText(String text) {
        if (bubbleLabel != null) {
            bubbleLabel.setText(text == null ? "-" : text);
        }
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public MainViewController getMainController() {
        return mainController;
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        // Clean up audio + network
        if (yamnetMic != null) {
            yamnetMic.stopListening();
            yamnetMic.close();
        }
        if (broadcastListener != null) {
            broadcastListener.stopListening();
            broadcastListener.close();
        }
        hideBubble();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void setupBubbleStage() {
        bubbleLabel = new Label("-");
        bubbleLabel.setStyle("-fx-background-color: rgba(37,31,76,0.92);"
                + "-fx-text-fill: white;"
                + "-fx-padding: 8 12 8 12;"
                + "-fx-background-radius: 12;"
                + "-fx-font-size: 15;"
                + "-fx-font-weight: bold;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 14, 0.3, 0, 3);");

        StackPane root = new StackPane(bubbleLabel);
        root.setStyle("-fx-background-color: transparent;");

        bubbleStage = new Stage(StageStyle.TRANSPARENT);
        bubbleStage.setAlwaysOnTop(true);
        Scene scene = new Scene(root);
        scene.setFill(null);
        bubbleStage.setScene(scene);
        bubbleStage.setWidth(240);
        bubbleStage.setHeight(80);
        hideBubble();
    }

    private void showBubble() {
        if (bubbleStage != null && !bubbleStage.isShowing()) {
            if (primaryStage != null) {
                bubbleStage.setX(primaryStage.getX() + Math.max(0, primaryStage.getWidth() - 260));
                bubbleStage.setY(primaryStage.getY() + 40);
            }
            bubbleStage.show();
        }
    }

    private void hideBubble() {
        if (bubbleStage != null && bubbleStage.isShowing()) {
            bubbleStage.hide();
        }
    }
}
