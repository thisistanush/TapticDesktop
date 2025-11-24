import org.json.JSONObject;
import org.vosk.Recognizer;
import org.vosk.Model;

import javax.sound.sampled.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Offline speech-to-text service powered by Vosk.
 * Downloads a compact English model on first use so captions work without any setup.
 */
public class SttService implements AutoCloseable {

    private static final int SAMPLE_RATE = 16000;
    private static final AudioFormat FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false);
    private static final int BUFFER_SIZE = 4096;

    private final Consumer<String> onText;
    private final Consumer<String> onStatus;
    private TargetDataLine micLine;
    private Thread worker;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SttService(Consumer<String> onText, Consumer<String> onStatus) {
        this.onText = onText;
        this.onStatus = onStatus;
    }

    /** Starts streaming if not already running. Returns true if started. */
    public boolean start() {
        if (running.get()) return true;

        try {
            micLine = openMic();
        } catch (Exception e) {
            postStatus("Mic unavailable for captions: " + e.getMessage());
            return false;
        }

        running.set(true);
        worker = new Thread(this::runLoop, "STT-Vosk");
        worker.setDaemon(true);
        worker.start();
        postStatus("Speech recognition started (offline).");
        return true;
    }

    private TargetDataLine openMic() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(FORMAT, SAMPLE_RATE * 2);
        line.start();
        return line;
    }

    private void runLoop() {
        try (Model model = VoskModelManager.loadModel(this::postStatus);
             Recognizer recognizer = new Recognizer(model, SAMPLE_RATE)) {

            byte[] buf = new byte[BUFFER_SIZE];
            while (running.get()) {
                int n = micLine.read(buf, 0, buf.length);
                if (n <= 0) continue;

                boolean hasFinal = recognizer.acceptWaveForm(buf, n);
                if (hasFinal) {
                    pushRecognizedText(recognizer.getResult());
                } else {
                    pushRecognizedText(recognizer.getPartialResult());
                }
            }
        } catch (Exception e) {
            postStatus("Speech recognition error: " + e.getMessage());
        } finally {
            closeMic();
            running.set(false);
        }
    }

    private void pushRecognizedText(String jsonResult) {
        if (jsonResult == null || jsonResult.isBlank()) return;
        try {
            JSONObject obj = new JSONObject(jsonResult);
            String text = obj.optString("text", "").trim();
            if (!text.isEmpty() && onText != null) {
                onText.accept(text);
            }
        } catch (Exception ignored) {
        }
    }

    public void stop() {
        running.set(false);
        closeMic();
    }

    private void closeMic() {
        if (micLine != null) {
            try { micLine.stop(); } catch (Exception ignored) {}
            try { micLine.close(); } catch (Exception ignored) {}
            micLine = null;
        }
    }

    private void postStatus(String msg) {
        if (onStatus != null && msg != null && !msg.isBlank()) {
            onStatus.accept(msg);
        }
    }

    @Override
    public void close() {
        stop();
    }
}
