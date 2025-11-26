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
            AudioFormat.Encoding.PCM_SIGNED, // raw PCM audio
            SAMPLE_RATE,                     // samples per second
            16,                              // bits per sample
            1,                               // mono
            2,                               // bytes per frame (16-bit mono)
            SAMPLE_RATE,                     // frames per second
            false);                          // little-endian
    private static final int BUFFER_SIZE = 4096;

    // Where recognized phrases are delivered (e.g., to update the UI)
    private final Consumer<String> onText;

    // Optional status callback for surfacing issues or download progress
    private final Consumer<String> onStatus;

    // Microphone capture line and worker thread
    private TargetDataLine micLine;
    private Thread worker;

    // Ensures we only spin up one recognizer loop at a time
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SttService(Consumer<String> onText, Consumer<String> onStatus) {
        this.onText = onText;
        this.onStatus = onStatus;
    }

    /**
     * Starts streaming audio from the default microphone and emitting transcripts.
     * Kept tiny and linear so the happy path is obvious.
     *
     * @return true when capture started successfully, false if the mic was unavailable.
     */
    public boolean start() {
        if (running.get()) return true;

        try {
            micLine = openMic();
        } catch (Exception e) {
            postStatus("Mic unavailable for captions: " + e.getMessage());
            return false;
        }

        running.set(true);
        // Background thread keeps the UI thread free
        worker = new Thread(this::runLoop, "STT-Vosk");
        worker.setDaemon(true);
        worker.start();
        postStatus("Speech recognition started (offline).");
        return true;
    }

    /** Opens and primes the system microphone for 16kHz mono PCM capture. */
    private TargetDataLine openMic() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(FORMAT, SAMPLE_RATE * 2);
        line.start();
        return line;
    }

    /** Background worker that feeds microphone data into the Vosk recognizer. */
    private void runLoop() {
        try (Model model = VoskModelManager.loadModel(this::postStatus);
             Recognizer recognizer = new Recognizer(model, SAMPLE_RATE)) {

            byte[] buf = new byte[BUFFER_SIZE];
            while (running.get()) {
                int n = micLine.read(buf, 0, buf.length);
                if (n <= 0) {
                    continue;
                }

                // acceptWaveForm returns true when Vosk thinks a phrase is complete
                boolean hasFinal = recognizer.acceptWaveForm(buf, n);
                if (hasFinal) {
                    pushRecognizedText(recognizer.getResult());
                } else {
                    // Emit partial hypotheses so captions feel responsive
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
            String text = extractTranscript(jsonResult);
            if (!text.isEmpty() && onText != null) {
                onText.accept(text);
            }
        } catch (Exception ignored) {
            // If parsing fails we just skip that chunk and keep listening.
        }
    }

    /**
     * Extracts the most useful transcript from a Vosk JSON payload, preferring final text
     * but falling back to partial hypotheses so the UI can show live updates.
     */
    private String extractTranscript(String jsonResult) {
        JSONObject obj = new JSONObject(jsonResult);
        String text = obj.optString("text", "").trim();
        if (text.isEmpty()) {
            text = obj.optString("partial", "").trim();
        }
        return text;
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
