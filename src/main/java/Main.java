import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {
    private static final int PORT = 50000;

    public static void main(String[] args) throws Exception {
        System.out.println("Starting listener on UDP port " + PORT + " …");
        BroadcastListener listener = new BroadcastListener(PORT);
        listener.start(jsonText -> {
            // Minimal filter: only log if it's a 'fire' message
            try {
                JSONObject obj = new JSONObject(jsonText);
                if ("fire".equalsIgnoreCase(obj.optString("type", ""))) {
                    System.out.println("[RECV] FIRE alert from " + obj.optString("host", "?")
                            + " @ " + obj.optLong("ts", 0));
                } else {
                    System.out.println("[RECV] " + jsonText);
                }
            } catch (Exception e) {
                System.out.println("[RECV] (non-JSON) " + jsonText);
            }
        });

        System.out.println("Press 'y' + Enter to broadcast FIRE. Press 'q' + Enter to quit.");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim().toLowerCase();
                if ("q".equals(line)) break;
                if ("y".equals(line)) {
                    try {
                        BroadcastSender.sendFire(PORT);
                        System.out.println("[SEND] FIRE broadcast sent.");
                    } catch (Exception e) {
                        System.err.println("[SEND] Failed: " + e.getMessage());
                    }
                } else if (!line.isEmpty()) {
                    System.out.println("Unknown command: " + line + " (use 'y' or 'q')");
                }
            }
        } finally {
            listener.close();
            System.out.println("Goodbye.");
        }
    }
}
