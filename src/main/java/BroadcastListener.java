import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * Listens for sound detection broadcasts from other Taptic Desktop devices.
 * Runs in a background thread continuously listening for UDP messages.
 * 
 * When a message is received, it:
 * 1. Checks if it's from this device (and ignores it if so)
 * 2. Passes the JSON to Interpreter for processing
 */
public class BroadcastListener implements Closeable, Runnable {

    private final int port;
    private volatile boolean running = true;
    private DatagramSocket socket;

    /**
     * Create a listener on the specified port.
     * 
     * @param port UDP port number (typically 9876)
     */
    public BroadcastListener(int port) {
        this.port = port;
    }

    /**
     * Run method for Thread. Starts listening for broadcasts.
     */
    @Override
    public void run() {
        try {
            start();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Start listening for broadcast messages.
     * This method blocks until stopListening() is called.
     * 
     * @throws SocketException If the socket can't be created
     */
    public void start() throws SocketException {
        socket = new DatagramSocket(port);
        socket.setReuseAddress(true);
        byte[] buffer = new byte[2048];

        // Keep receiving packets until stopped
        while (running) {
            try {
                // Wait for a packet
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Convert packet data to string
                String jsonText = new String(
                        packet.getData(),
                        packet.getOffset(),
                        packet.getLength(),
                        StandardCharsets.UTF_8);

                // Ignore messages from this computer (don't notify ourselves)
                if (jsonText.contains(getHostName())) {
                    continue;
                }

                // Pass to Interpreter for processing
                Interpreter.handleBroadcastJson(jsonText);

            } catch (IOException e) {
                if (running) {
                    System.err.println("[Listener] " + e.getMessage());
                }
            }
        }
    }

    /**
     * Stop listening for broadcasts.
     */
    public void stopListening() {
        running = false;
    }

    /**
     * Close the listener and release resources.
     */
    @Override
    public void close() {
        running = false;
        if (socket != null) {
            socket.close();
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
