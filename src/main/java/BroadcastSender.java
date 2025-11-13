import org.json.JSONObject;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

public final class BroadcastSender {

    private static int port;

    public BroadcastSender(int port) {
        this.port = port;
    }
    public static void sendEvent(String event) throws IOException {
        JSONObject json = new JSONObject().put("type", event).put("ts", System.currentTimeMillis()).put("host", getHostName());

        byte[] payload = json.toString().getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket sock = new DatagramSocket()) {
            sock.setBroadcast(true);
            DatagramPacket p = new DatagramPacket(payload, payload.length, InetAddress.getByName("255.255.255.255"), port);
            sock.send(p);
        }
    }

    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
