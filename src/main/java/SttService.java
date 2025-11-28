import edu.cmu.sphinx.api.Configuration;
import edu.cmu.sphinx.api.LiveSpeechRecognizer;
import edu.cmu.sphinx.api.SpeechResult;

import java.io.IOException;

/**
 * Simple speech-to-text service using CMU Sphinx.
 *
 * - Runs on its own background thread.
 * - Sends recognized text back to MainViewController.
 * - Uses only basic Java (no lambdas, no Consumer, no AtomicBoolean).
 */
public class SttService implements AutoCloseable, Runnable {

    // Reference to the UI controller so we can push text / status messages.
    private final MainViewController controller;

    // Background worker thread for Sphinx.
    private Thread worker;

    // Sphinx recognizer instance.
    private LiveSpeechRecognizer recognizer;

    // Flag to control the loop.
    private volatile boolean running = false;

    public SttService(MainViewController controller) {
        this.controller = controller;
    }

    /**
     * Try to start the speech recognition thread.
     *
     * @return true if Sphinx was started, false if the library is missing or
     *         something failed before starting.
     */
    public boolean start() {
        // Already running
        if (running) {
            return true;
        }

        // Check if the Sphinx classes are on the classpath.
        try {
            Class.forName("edu.cmu.sphinx.api.Configuration");
        } catch (ClassNotFoundException e) {
            if (controller != null) {
                controller.postCaptionSystemMessage(
                        "System: Speech recognition library (Sphinx) not found. " +
                                "Caption mic will be disabled.\n" +
                                e.toString()
                );
            }
            return false;
        }

        running = true;

        // Start background thread
        worker = new Thread(this, "STT-Sphinx");
        worker.setDaemon(true);
        worker.start();

        postStatus("System: Speech recognition started (Sphinx).");
        return true;
    }

    /**
     * Main loop for the background thread.
     * Sets up Sphinx, listens on the mic, and sends recognized text to the UI.
     */
    @Override
    public void run() {
        while (running) {
            try {
                recognizer = buildRecognizer();
                recognizer.startRecognition(true);
                postStatus("System: Listening… speak into your microphone.");

                while (running) {
                    SpeechResult result = recognizer.getResult();
                    if (!running) {
                        break;
                    }
                    if (result == null) {
                        try {
                            Thread.sleep(30);
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }
                    String text = result.getHypothesis();
                    if (text != null) {
                        text = text.trim();
                    }
                    if (text != null && !text.isEmpty() && controller != null) {
                        controller.pushCaptionText(text);
                    }
                }
            } catch (IOException e) {
                postStatus("System: Speech recognition error (IO): " + e.getMessage());
                break;
            } catch (Throwable t) {
                // Catch anything else so the thread does not silently die; retry if still running.
                postStatus("System: Speech recognition crashed: " + t.toString());
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
            } finally {
                // Clean up Sphinx
                if (recognizer != null) {
                    try {
                        recognizer.stopRecognition();
                    } catch (Exception ignored) {
                    }
                }
                recognizer = null;
            }
        }
        running = false;
        postStatus("System: Speech recognition stopped.");
    }

    /**
     * Build the Sphinx recognizer with the default English model.
     */
    private LiveSpeechRecognizer buildRecognizer() throws IOException {
        Configuration configuration = new Configuration();

        // Use the built-in English acoustic model + dictionary + language model.
        configuration.setAcousticModelPath("resource:/edu/cmu/sphinx/models/en-us/en-us");
        configuration.setDictionaryPath("resource:/edu/cmu/sphinx/models/en-us/cmudict-en-us.dict");
        configuration.setLanguageModelPath("resource:/edu/cmu/sphinx/models/en-us/en-us.lm.bin");

        return new LiveSpeechRecognizer(configuration);
    }

    /**
     * Request the recognizer thread to stop.
     * Safe to call multiple times.
     */
    public void stop() {
        running = false;
        if (recognizer != null) {
            try {
                recognizer.stopRecognition();
            } catch (Exception ignored) {
            }
        }
    }

    private void postStatus(String msg) {
        if (controller != null && msg != null && !msg.isEmpty()) {
            controller.postCaptionSystemMessage(msg);
        }
    }

    @Override
    public void close() {
        stop();
    }
}
