import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {
    private static final int PORT = 50000;
    public static BroadcastSender sender = new BroadcastSender(PORT);

    public static void main(String[] args) throws Exception {
        Thread mic = new Thread(new YamnetMic());
        mic.start();

        Thread listener = new Thread(new BroadcastListener(PORT));
        listener.start();






    }
}
