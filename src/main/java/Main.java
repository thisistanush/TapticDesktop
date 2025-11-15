import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    private static final int PORT = 50000;

    private YamnetMic yamnetMic;
    private BroadcastListener broadcastListener;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/MainView.fxml"));
        Parent root = loader.load();
        MainViewController controller = loader.getController();

        Scene scene = new Scene(root, 640, 520);
        scene.getStylesheets().add(Main.class.getResource("/main.css").toExternalForm());
        stage.setTitle("Taptic Desktop");
        stage.setScene(scene);
        stage.show();

        // Networking
        BroadcastSender sender = new BroadcastSender(PORT);
        broadcastListener = new BroadcastListener(PORT);
        Thread listenerThread = new Thread(broadcastListener, "BroadcastListener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        // YamNet mic
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
