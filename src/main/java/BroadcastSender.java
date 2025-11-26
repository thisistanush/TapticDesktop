import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Tiny helper that sends a UDP broadcast with the detected label.
 * Keeping it as a standalone class keeps Interpreter short.
 */
public final class BroadcastSender {

    private final int port;
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    public BroadcastSender(int port) {
        this.port = port;
    }

    public void sendEvent(String eventLabel) throws IOException {
        JSONObject json = new JSONObject()
                .put("type", eventLabel)
                .put("time", LocalDateTime.now().format(TIME_FMT))
                .put("host", getHostName());

        byte[] payload = json.toString().getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket sock = new DatagramSocket()) {
            sock.setBroadcast(true);
            DatagramPacket p = new DatagramPacket(
                    payload, payload.length,
                    InetAddress.getByName("255.255.255.255"), port);
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
