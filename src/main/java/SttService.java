import edu.cmu.sphinx.api.Configuration;
import edu.cmu.sphinx.api.LiveSpeechRecognizer;
import edu.cmu.sphinx.api.SpeechResult;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Real-time speech-to-text powered by CMU Sphinx.
 * Listens to the system microphone and streams recognized phrases back to the UI.
 */
public class SttService implements AutoCloseable {

    // Where recognized phrases are delivered (e.g., to update the UI)
    private final Consumer<String> onText;

    // Optional status callback for surfacing issues
    private final Consumer<String> onStatus;

    private Thread worker;
    private LiveSpeechRecognizer recognizer;

    // Ensures we only spin up one recognizer loop at a time
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SttService(Consumer<String> onText, Consumer<String> onStatus) {
        this.onText = onText;
        this.onStatus = onStatus;
    }

    /**
     * Starts streaming audio from the default microphone and emitting transcripts.
     *
     * @return true when capture started successfully, false otherwise.
     */
    public boolean start() {
        if (running.get()) return true;

        running.set(true);
        worker = new Thread(this::runLoop, "STT-Sphinx");
        worker.setDaemon(true);
        worker.start();
        postStatus("Speech recognition started (Sphinx).");
        return true;
    }

    /** Background worker that feeds microphone data into the Sphinx recognizer. */
    private void runLoop() {
        try {
            recognizer = buildRecognizer();
            recognizer.startRecognition(true);
            postStatus("Listening… Speak into your microphone!");

            SpeechResult result;
            while (running.get() && (result = recognizer.getResult()) != null) {
                String text = result.getHypothesis();
                if (text != null && !text.isBlank() && onText != null) {
                    onText.accept(text.trim());
                }
            }
        } catch (IOException e) {
            postStatus("Speech recognition error: " + e.getMessage());
        } finally {
            if (recognizer != null) {
                try { recognizer.stopRecognition(); } catch (Exception ignored) {}
            }
            recognizer = null;
            running.set(false);
        }
    }

    private LiveSpeechRecognizer buildRecognizer() throws IOException {
        Configuration configuration = new Configuration();
        return new LiveSpeechRecognizer(configuration);
    }

    public void stop() {
        running.set(false);
        if (recognizer != null) {
            try { recognizer.stopRecognition(); } catch (Exception ignored) {}
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
