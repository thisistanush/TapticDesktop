import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Ensures a Vosk speech model is available locally.
 * Downloads a compact English model on first use so speech recognition works offline.
 */
public final class VoskModelManager {

    private static final String MODEL_NAME = "vosk-model-small-en-us-0.15";
    private static final String MODEL_ZIP = MODEL_NAME + ".zip";
    private static final String DOWNLOAD_URL =
            "https://alphacephei.com/vosk/models/" + MODEL_ZIP;

    private static final String MODEL_PROP = "taptic.vosk.model";
    private static final String MODEL_ENV = "TAPTIC_VOSK_MODEL";

    private static Path cachedModelDir;

    private VoskModelManager() {
    }

    /**
     * Returns a ready-to-use model, downloading it if needed.
     */
    public static synchronized Model loadModel(Consumer<String> statusReporter) throws IOException {
        if (cachedModelDir != null && Files.isDirectory(cachedModelDir)) {
            return new Model(cachedModelDir.toString());
        }

        LibVosk.setLogLevel(LogLevel.WARNINGS);

        Path manualModel = resolveManualModelPath(statusReporter);
        if (manualModel != null) {
            cachedModelDir = manualModel;
            return new Model(manualModel.toString());
        }

        Path manualModel = resolveManualModelPath(statusReporter);
        if (manualModel != null) {
            cachedModelDir = manualModel;
            return new Model(manualModel.toString());
        }

        Path baseDir = Paths.get(System.getProperty("user.home"), ".taptic", "stt");
        Files.createDirectories(baseDir);
        Path modelDir = baseDir.resolve(MODEL_NAME);

        if (!Files.isDirectory(modelDir)) {
            Path zipPath = baseDir.resolve(MODEL_ZIP);
            downloadModel(zipPath, statusReporter);
            unzip(zipPath, baseDir, statusReporter);
        }

        cachedModelDir = modelDir;
        return new Model(modelDir.toString());
    }

    private static Path resolveManualModelPath(Consumer<String> statusReporter) {
        String manualPath = System.getProperty(MODEL_PROP);
        if (manualPath == null || manualPath.isBlank()) {
            manualPath = System.getenv(MODEL_ENV);
        }

        if (manualPath == null || manualPath.isBlank()) {
            return null;
        }

        Path candidate = Paths.get(manualPath).toAbsolutePath();
        if (Files.isDirectory(candidate)) {
            if (statusReporter != null) {
                statusReporter.accept("Using local speech model at " + candidate + ".");
            }
            return candidate;
        }

        if (statusReporter != null) {
            statusReporter.accept("Speech model path not found: " + candidate);
        }
        return null;
    }

    private static void downloadModel(Path destination, Consumer<String> statusReporter) throws IOException {
        if (Files.exists(destination)) {
            Files.delete(destination);
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(DOWNLOAD_URL).openConnection();
        conn.setRequestProperty("User-Agent", "TapticDesktop/1.0");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        int length = conn.getContentLength();
        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(destination)) {

            byte[] buf = new byte[8192];
            long total = 0;
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
                total += r;
                if (length > 0 && statusReporter != null) {
                    int pct = (int) Math.min(100, (total * 100) / length);
                    statusReporter.accept("Downloading speech model… " + pct + "%");
                }
            }
        }
    }

    private static void unzip(Path zipFile, Path targetDir, Consumer<String> statusReporter) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buf = new byte[16384];
            while ((entry = zis.getNextEntry()) != null) {
                Path resolved = targetDir.resolve(entry.getName()).normalize();
                if (!Objects.requireNonNull(resolved).startsWith(targetDir)) {
                    throw new IOException("Unsafe zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    try (OutputStream out = Files.newOutputStream(resolved)) {
                        int n;
                        while ((n = zis.read(buf)) > 0) {
                            out.write(buf, 0, n);
                        }
                    }
                }
            }
        }
        if (statusReporter != null) {
            statusReporter.accept("Speech model unpacked.");
        }
    }
}
