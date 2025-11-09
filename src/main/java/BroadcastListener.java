import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class BroadcastListener implements Closeable {
    private final int port;
    private Thread thread;
    private volatile boolean running;
    private DatagramSocket socket;

    public interface Handler {
        void onMessage(String jsonText);
    }

    public BroadcastListener(int port) {
        this.port = port;
    }

    /** Starts a background thread; no while-loop in main. */
    public void start(Handler handler) throws SocketException {
        if (running) return;
        running = true;
        socket = new DatagramSocket(port);
        socket.setReuseAddress(true);
        thread = new Thread(() -> {
            byte[] buf = new byte[2048];
            while (running) {
                try {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    socket.receive(pkt);
                    String text = new String(pkt.getData(), pkt.getOffset(), pkt.getLength(), StandardCharsets.UTF_8);
                    handler.onMessage(text);
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[Listener] " + e.getMessage());
                    }
                }
            }
        }, "BroadcastListener");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void close() {
        running = false;
        if (socket != null) socket.close();
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) {}
        }
    }
}
