import org.json.JSONObject;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

public final class BroadcastSender {

    private BroadcastSender() {}

    public static void sendFire(int port) throws IOException {
        JSONObject json = new JSONObject()
                .put("type", "fire")
                .put("ts", System.currentTimeMillis())
                .put("host", getHostName());

        byte[] payload = json.toString().getBytes(StandardCharsets.UTF_8);

        // 1) Send to limited broadcast 255.255.255.255
        try (DatagramSocket sock = new DatagramSocket()) {
            sock.setBroadcast(true);
            DatagramPacket p = new DatagramPacket(payload, payload.length,
                    InetAddress.getByName("255.255.255.255"), port);
            sock.send(p);
        }

        // 2) Also try subnet-directed broadcasts for each active interface (more reliable on some LANs)
        try (DatagramSocket sock = new DatagramSocket()) {
            sock.setBroadcast(true);
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;

                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress ba = ia.getBroadcast();
                    if (ba == null) continue; // not an IPv4 iface or no broadcast
                    DatagramPacket packet = new DatagramPacket(payload, payload.length, ba, port);
                    sock.send(packet);
                }
            }
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
