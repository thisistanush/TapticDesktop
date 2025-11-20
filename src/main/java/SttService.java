import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding;
import com.google.cloud.speech.v1.RecognitionMetadata;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognitionResult;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.cloud.speech.v1.SpeechContext;

import javax.sound.sampled.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Very small wrapper around Google Cloud Speech streaming recognition.
 * Requires GOOGLE_APPLICATION_CREDENTIALS to be set to a service account JSON.
 */
public class SttService implements AutoCloseable {

    private static final int SAMPLE_RATE = 16000;
    private static final AudioFormat FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false);

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

        if (System.getenv("GOOGLE_APPLICATION_CREDENTIALS") == null) {
            postStatus("Set GOOGLE_APPLICATION_CREDENTIALS to your service account JSON file for speech recognition.");
            return false;
        }

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        try {
            micLine = (TargetDataLine) AudioSystem.getLine(info);
            micLine.open(FORMAT, SAMPLE_RATE * 2);
            micLine.start();
        } catch (Exception e) {
            postStatus("Mic unavailable for captions: " + e.getMessage());
            return false;
        }

        running.set(true);
        worker = new Thread(this::runLoop, "STT-Google");
        worker.setDaemon(true);
        worker.start();
        postStatus("Speech recognition started.");
        return true;
    }

    private void runLoop() {
        try {
            SpeechSettings settings = SpeechSettings.newBuilder().build();
            try (SpeechClient client = SpeechClient.create(settings)) {

                ResponseObserver<StreamingRecognizeResponse> observer = new ResponseObserver<>() {
                    @Override public void onStart(StreamController controller) {}
                    @Override public void onResponse(StreamingRecognizeResponse resp) {
                        for (StreamingRecognitionResult r : resp.getResultsList()) {
                            if (r.getAlternativesCount() > 0) {
                                String txt = r.getAlternatives(0).getTranscript();
                                if (txt != null && !txt.isBlank() && onText != null) {
                                    onText.accept(txt.trim());
                                }
                            }
                        }
                    }
                    @Override public void onComplete() {}
                    @Override public void onError(Throwable t) {
                        postStatus("Speech recognition error: " + t.getMessage());
                    }
                };

                ClientStream<StreamingRecognizeRequest> stream =
                        client.streamingRecognizeCallable().splitCall(observer);

                StreamingRecognitionConfig config = StreamingRecognitionConfig.newBuilder()
                        .setConfig(RecognitionConfig.newBuilder()
                                .setEncoding(AudioEncoding.LINEAR16)
                                .setSampleRateHertz(SAMPLE_RATE)
                                .setLanguageCode("en-US")
                                .setMetadata(RecognitionMetadata.newBuilder()
                                        .setInteractionType(RecognitionMetadata.InteractionType.DISCUSSION)
                                        .build())
                                .addAllSpeechContexts(List.of(SpeechContext.newBuilder()
                                        .addPhrases("Taptic").addPhrases("notification").build()))
                                .build())
                        .setInterimResults(false)
                        .setSingleUtterance(false)
                        .build();

                stream.send(StreamingRecognizeRequest.newBuilder()
                        .setStreamingConfig(config)
                        .build());

                byte[] buf = new byte[3200];
                while (running.get()) {
                    int n = micLine.read(buf, 0, buf.length);
                    if (n <= 0) continue;
                    stream.send(StreamingRecognizeRequest.newBuilder()
                            .setAudioContent(com.google.protobuf.ByteString.copyFrom(buf, 0, n))
                            .build());
                }
                stream.closeSend();
            }

        } catch (IOException e) {
            postStatus("Could not start speech client: " + e.getMessage());
        } finally {
            closeMic();
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
