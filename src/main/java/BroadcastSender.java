import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Sends sound detection events to other Taptic Desktop devices on the network.
 * Uses UDP broadcast so all devices on the local network can receive the
 * message.
 * 
 * When this device detects a sound that's marked for broadcasting,
 * it sends a JSON message containing:
 * - type: The sound label (e.g., "Knock", "Door")
 * - time: When it was detected (HH:mm:ss format)
 * - host: This computer's hostname
 */
public final class BroadcastSender {

    private final int port;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Create a broadcaster that sends on the specified port.
     * 
     * @param port UDP port number (typically 9876)
     */
    public BroadcastSender(int port) {
        this.port = port;
    }

    /**
     * Broadcast a sound detection event to the network.
     * 
     * @param eventLabel The sound that was detected
     * @throws IOException If network sending fails
     */
    public void sendEvent(String eventLabel) throws IOException {
        // Create JSON message with event details
        JSONObject json = new JSONObject();
        json.put("type", eventLabel);
        json.put("time", LocalDateTime.now().format(TIME_FORMAT));
        json.put("host", getHostName());

        // Convert to bytes
        byte[] payload = json.toString().getBytes(StandardCharsets.UTF_8);

        // Send UDP broadcast
        DatagramSocket sock = new DatagramSocket();
        try {
            sock.setBroadcast(true);
            InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(payload, payload.length, broadcastAddress, port);
            sock.send(packet);
        } finally {
            sock.close();
        }
    }

    /**
     * Get this computer's hostname.
     * 
     * @return Hostname, or "unknown" if it can't be determined
     */
    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
