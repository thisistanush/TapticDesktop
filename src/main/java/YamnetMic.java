import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.tensorflowlite.TfLiteInterpreter;
import org.bytedeco.tensorflowlite.TfLiteInterpreterOptions;
import org.bytedeco.tensorflowlite.TfLiteModel;
import org.bytedeco.tensorflowlite.TfLiteTensor;
import org.bytedeco.tensorflowlite.global.tensorflowlite;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class YamnetMic implements AutoCloseable, Runnable {

    private static final int SR = 16000;
    private static final int WIN_SAMPLES = 15600;
    private static final int HOP_SAMPLES = 7800;
    private static final int NUM_CLASSES = 521;

    private final TfLiteModel model;
    private final TfLiteInterpreterOptions options;
    private final TfLiteInterpreter interpreter;
    private volatile boolean running = true;
    private TargetDataLine micLine;

    private static final String[] LABELS =
            loadLabels("/models/yamnet_class_map.csv");

    public YamnetMic() throws Exception {
        File modelFile = extractResource(
                "/models/lite-model_yamnet_classification_tflite_1.tflite",
                "yamnet.tflite"
        );

        model = tensorflowlite.TfLiteModelCreateFromFile(
                modelFile.getAbsolutePath());
        if (model == null || model.isNull()) {
            throw new IOException("TfLiteModelCreateFromFile failed");
        }

        options = tensorflowlite.TfLiteInterpreterOptionsCreate();
        tensorflowlite.TfLiteInterpreterOptionsSetNumThreads(options, 4);

        interpreter = tensorflowlite.TfLiteInterpreterCreate(model, options);
        if (interpreter == null || interpreter.isNull()) {
            throw new IOException("TfLiteInterpreterCreate failed");
        }

        int[] dims = { WIN_SAMPLES };
        int st = tensorflowlite.TfLiteInterpreterResizeInputTensor(
                interpreter, 0, dims, dims.length);
        if (st != tensorflowlite.kTfLiteOk) {
            throw new IllegalStateException("ResizeInputTensor failed: " + st);
        }

        st = tensorflowlite.TfLiteInterpreterAllocateTensors(interpreter);
        if (st != tensorflowlite.kTfLiteOk) {
            throw new IllegalStateException("AllocateTensors failed: " + st);
        }
    }

    public static String[] getLabels() {
        return LABELS;
    }

    public void stopListening() {
        running = false;
    }

    @Override
    public void run() {
        try {
            listenLoop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void listenLoop() throws Exception {
        AudioFormat fmt = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SR, 16, 1, 2, SR, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);

        try {
            micLine = (TargetDataLine) AudioSystem.getLine(info);
            micLine.open(fmt, SR * 2);
        } catch (Exception e) {
            Interpreter.reportMicError("Microphone not available: " + e.getMessage());
            return;
        }

        micLine.start();

        byte[] hopBytes = new byte[HOP_SAMPLES * 2];
        float[] ring = new float[WIN_SAMPLES];
        int fill = 0;

        while (running) {
            readFully(micLine, hopBytes);

            ByteBuffer bb = ByteBuffer.wrap(hopBytes)
                    .order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < HOP_SAMPLES; i++) {
                short s = bb.getShort();
                float v = s / 32768f;
                if (fill < WIN_SAMPLES) {
                    ring[fill++] = v;
                } else {
                    System.arraycopy(
                            ring, HOP_SAMPLES,
                            ring, 0,
                            WIN_SAMPLES - HOP_SAMPLES);
                    ring[WIN_SAMPLES - HOP_SAMPLES + i] = v;
                }
            }
            if (fill < WIN_SAMPLES) {
                continue;
            }

            // simple RMS level for sound intensity meter
            double sumSq = 0.0;
            for (int i = 0; i < WIN_SAMPLES; i++) {
                double v = ring[i];
                sumSq += v * v;
            }
            double rms = Math.sqrt(sumSq / WIN_SAMPLES); // 0..~1
            double level = Math.min(1.0, rms * 4.0);      // amplify a bit

            float[] scores = infer(ring);
            Interpreter.onFrame(scores, LABELS, level);
        }
    }

    private static void readFully(TargetDataLine line, byte[] buf) throws IOException {
        int off = 0;
        int need = buf.length;
        while (off < need) {
            int n = line.read(buf, off, need - off);
            if (n < 0) throw new EOFException("mic closed");
            off += n;
        }
    }

    private float[] infer(float[] mono15600) {
        TfLiteTensor in = tensorflowlite.TfLiteInterpreterGetInputTensor(
                interpreter, 0);

        FloatPointer inFP = new FloatPointer(WIN_SAMPLES);
        for (int i = 0; i < WIN_SAMPLES; i++) {
            inFP.put(i, mono15600[i]);
        }

        int st = tensorflowlite.TfLiteTensorCopyFromBuffer(
                in, inFP, (long) WIN_SAMPLES * Float.BYTES);
        if (st != tensorflowlite.kTfLiteOk) {
            throw new IllegalStateException("CopyFromBuffer failed: " + st);
        }

        st = tensorflowlite.TfLiteInterpreterInvoke(interpreter);
        if (st != tensorflowlite.kTfLiteOk) {
            throw new IllegalStateException("Invoke failed: " + st);
        }

        TfLiteTensor out = tensorflowlite.TfLiteInterpreterGetOutputTensor(
                interpreter, 0);
        FloatPointer outFP = new FloatPointer(NUM_CLASSES);
        st = tensorflowlite.TfLiteTensorCopyToBuffer(
                out, outFP, (long) NUM_CLASSES * Float.BYTES);
        if (st != tensorflowlite.kTfLiteOk) {
            throw new IllegalStateException("CopyToBuffer failed: " + st);
        }

        float[] scores = new float[NUM_CLASSES];
        outFP.get(scores);
        return scores;
    }

    private static String[] loadLabels(String res) {
        try (InputStream reader = YamnetMic.class.getResourceAsStream(res)) {
            if (reader == null) {
                System.err.println("Label file not found: " + res);
                return new String[0];
            }

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(reader, StandardCharsets.UTF_8));
            List<String> outList = new ArrayList<>(NUM_CLASSES);

            String line;
            boolean header = true;
            while ((line = br.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                String[] p = line.split(",", 3);
                if (p.length == 3) outList.add(p[2].trim());
            }
            return outList.toArray(new String[0]);

        } catch (IOException e) {
            e.printStackTrace();
            return new String[0];
        }
    }

    private static File extractResource(String res, String name) throws IOException {
        try (InputStream in = YamnetMic.class.getResourceAsStream(res)) {
            if (in == null) throw new FileNotFoundException("Missing resource: " + res);
            File tmp = File.createTempFile(name, ".tflite");
            try (OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) != -1) {
                    out.write(buf, 0, r);
                }
            }
            tmp.deleteOnExit();
            return tmp;
        }
    }

    @Override
    public void close() {
        running = false;
        if (micLine != null) {
            micLine.stop();
            micLine.close();
        }
        if (interpreter != null && !interpreter.isNull()) {
            tensorflowlite.TfLiteInterpreterDelete(interpreter);
        }
        if (options != null && !options.isNull()) {
            tensorflowlite.TfLiteInterpreterOptionsDelete(options);
        }
        if (model != null && !model.isNull()) {
            tensorflowlite.TfLiteModelDelete(model);
        }
    }
}
