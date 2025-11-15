import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class BroadcastListener implements Closeable, Runnable {

    private final int port;
    private volatile boolean running = true;
    private DatagramSocket socket;

    public BroadcastListener(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try {
            start();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    public void start() throws SocketException {
        socket = new DatagramSocket(port);
        socket.setReuseAddress(true);
        byte[] buf = new byte[2048];

        while (running) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);
                String text = new String(
                        pkt.getData(),
                        pkt.getOffset(),
                        pkt.getLength(),
                        StandardCharsets.UTF_8
                );

                // ignore messages this host emitted
                if (text.indexOf(getHostName()) >= 0) {
                    continue;
                }

                // hand off to Interpreter so it can update UI / notifications
                Interpreter.handleBroadcastJson(text);

            } catch (IOException e) {
                if (running) {
                    System.err.println("[Listener] " + e.getMessage());
                }
            }
        }
    }

    public void stopListening() {
        running = false;
    }

    @Override
    public void close() {
        running = false;
        if (socket != null) {
            socket.close();
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
