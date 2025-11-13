import java.io.IOException;

public class Interpreter {
    private static BroadcastSender sender = Main.sender;
    public static void sendData(double[] sounds, String labelOne, String labelTwo, String labelThree) throws IOException {
        if(labelOne.equals("Fire alarm") || labelTwo.equals("Fire alarm") || labelThree.equals("Fire alarm")){
            sender.sendEvent("Fire Alarm");
        }
    }
}
