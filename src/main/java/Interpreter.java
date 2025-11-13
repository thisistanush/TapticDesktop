import java.io.IOException;

public class Interpreter {
    public static void sendData(double[] sounds, String labelOne, String labelTwo, String labelThree) throws IOException {
        if(labelOne.equals("Fire alarm") || labelTwo.equals("Fire alarm") || labelThree.equals("Fire alarm")){
            BroadcastSender.sendEvent("Fire Alarm");
        }
    }
}
