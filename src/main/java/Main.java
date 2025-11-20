import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Legacy entry point. Prefer TapticFxApp (richer navigation),
 * but this stays for compatibility and quick runs.
 */
public class Main extends Application {

    private YamnetMic yamnetMic;
    private BroadcastListener broadcastListener;
    private static final int PORT = 50000;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/fxml/main_view.fxml"));
        Parent root = loader.load();
        MainViewController controller = loader.getController();

        Scene scene = new Scene(root, 640, 520);
        scene.getStylesheets().add(Main.class.getResource("/fxml/main.css").toExternalForm());
        stage.setTitle("Taptic Desktop");
        stage.setScene(scene);
        stage.show();

        BroadcastSender sender = new BroadcastSender(PORT);
        broadcastListener = new BroadcastListener(PORT);
        Thread listenerThread = new Thread(broadcastListener, "BroadcastListener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        yamnetMic = new YamnetMic();
        Interpreter.init(sender, controller, YamnetMic.getLabels());

        Thread micThread = new Thread(yamnetMic, "YamnetMic");
        micThread.setDaemon(true);
        micThread.start();

        controller.setStatusText("Listening… say something loud near the mic.");
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
