import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class TapticFxApp extends Application {

    private static TapticFxApp instance;

    private static final int PORT = 50000;

    private Stage primaryStage;
    private Parent mainRoot;
    private Parent settingsRoot;
    private MainViewController mainController;
    private SettingsController settingsController;

    private YamnetMic yamnetMic;
    private BroadcastListener broadcastListener;
    private BroadcastSender broadcastSender;

    private String[] labels;

    public TapticFxApp() {
        instance = this;
    }

    public static TapticFxApp getInstance() {
        return instance;
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;

        // Try several possible FXML locations, grab the first one that exists.
        List<String> possibleMainFxml = Arrays.asList(
                "/MainView.fxml",
                "/mainView.fxml",
                "/mainview.fxml",
                "/main_view.fxml",
                "/fxml/MainView.fxml",
                "/fxml/mainView.fxml",
                "/fxml/mainview.fxml",
                "/fxml/main_view.fxml"
        );

        URL mainFxml = null;
        for (String path : possibleMainFxml) {
            URL u = TapticFxApp.class.getResource(path);
            if (u != null) {
                mainFxml = u;
                System.out.println("Loaded MainView FXML from: " + path);
                break;
            }
        }

        if (mainFxml == null) {
            throw new IllegalStateException(
                    "Could not find MainView.fxml. Tried: " + possibleMainFxml +
                            "\nMake sure one of those files exists under src/main/resources."
            );
        }

        FXMLLoader mainLoader = new FXMLLoader(mainFxml);
        mainRoot = mainLoader.load();
        mainController = mainLoader.getController();

        Scene scene = new Scene(mainRoot, 640, 520);
        addCss(scene);

        stage.setTitle("Taptic Desktop");
        stage.setScene(scene);
        stage.setMinWidth(500);
        stage.setMinHeight(600);
        stage.show();

        // Backend
        broadcastSender = new BroadcastSender(PORT);
        broadcastListener = new BroadcastListener(PORT);
        Thread listenerThread = new Thread(broadcastListener, "BroadcastListener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        yamnetMic = new YamnetMic();
        labels = YamnetMic.getLabels();

        Interpreter.init(broadcastSender, mainController, labels);

        Thread micThread = new Thread(yamnetMic, "YamnetMic");
        micThread.setDaemon(true);
        micThread.start();
    }

    private void addCss(Scene scene) {
        URL css = TapticFxApp.class.getResource("/main.css");
        if (css == null) {
            css = TapticFxApp.class.getResource("/css/main.css");
        }
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
    }

    public void showSettingsView() {
        try {
            if (settingsRoot == null) {
                URL settingsFxml = TapticFxApp.class.getResource("/SettingsView.fxml");
                if (settingsFxml == null) {
                    // Try alternative locations too
                    settingsFxml = TapticFxApp.class.getResource("/fxml/SettingsView.fxml");
                }
                if (settingsFxml == null) {
                    throw new IllegalStateException(
                            "Could not find SettingsView.fxml. " +
                                    "Place it as /SettingsView.fxml or /fxml/SettingsView.fxml under src/main/resources."
                    );
                }
                FXMLLoader loader = new FXMLLoader(settingsFxml);
                settingsRoot = loader.load();
                settingsController = loader.getController();
                if (labels != null) {
                    settingsController.initWithLabels(labels);
                }
            }
            primaryStage.getScene().setRoot(settingsRoot);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showMainView() {
        if (primaryStage != null && mainRoot != null) {
            primaryStage.getScene().setRoot(mainRoot);
        }
    }

    @Override
    public void stop() throws Exception {
        if (yamnetMic != null) {
            yamnetMic.stopListening();
            yamnetMic.close();
        }
        if (broadcastListener != null) {
            broadcastListener.stopListening();
            broadcastListener.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
