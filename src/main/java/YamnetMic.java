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

    public volatile boolean running = true;
    private static final int SR = 16000;
    private static final int WIN_SAMPLES = 15600;
    private static final int HOP_SAMPLES = 7800;
    private static final int NUM_CLASSES = 521;

    private final TfLiteModel model;
    private final TfLiteInterpreterOptions options;
    private final TfLiteInterpreter interpreter;
    private static final String[] labels = loadLabels("/models/yamnet_class_map.csv");
    private static int b1 = 0, b2 = 0, b3 = 0;
    private static double[] doubleScores = new double[3];

    public void stopListening(){
        running = false;
    }

    public YamnetMic() throws Exception {
        File modelFile = extractResource("/models/lite-model_yamnet_classification_tflite_1.tflite", "yamnet.tflite");

        model = tensorflowlite.TfLiteModelCreateFromFile(modelFile.getAbsolutePath());
        options = tensorflowlite.TfLiteInterpreterOptionsCreate();
        tensorflowlite.TfLiteInterpreterOptionsSetNumThreads(options, 8);
        interpreter = tensorflowlite.TfLiteInterpreterCreate(model, options);

        int[] dims = {WIN_SAMPLES};
        tensorflowlite.TfLiteInterpreterResizeInputTensor(interpreter, 0, dims, dims.length);
        tensorflowlite.TfLiteInterpreterAllocateTensors(interpreter);
    }

    public void listenLoop() throws Exception {
        AudioFormat fmt = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SR, 16, 1, 2, SR, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);

        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(fmt, SR * 2);
        line.start();

        byte[] hopBytes = new byte[HOP_SAMPLES * 2];
        float[] ring = new float[WIN_SAMPLES];
        int fill = 0;

        while (running) {
            readFully(line, hopBytes);
            ByteBuffer bb = ByteBuffer.wrap(hopBytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < HOP_SAMPLES; i++) {
                short s = bb.getShort();
                float v = s / 32768f;
                if (fill < WIN_SAMPLES) {
                    ring[fill++] = v;
                } else {
                    System.arraycopy(ring, HOP_SAMPLES, ring, 0, WIN_SAMPLES - HOP_SAMPLES);
                    ring[WIN_SAMPLES - HOP_SAMPLES + i] = v;
                }
            }
            if (fill < WIN_SAMPLES){
                continue;
            }

            float[] scores = infer(ring);
            printTop3(scores);

            Interpreter.sendData(doubleScores, labels[b1], labels[b2], labels[b3]);
        }
    }

    public float[] infer(float[] mono15600) {
        TfLiteTensor in = tensorflowlite.TfLiteInterpreterGetInputTensor(interpreter, 0);

        FloatPointer inFP = new FloatPointer(WIN_SAMPLES);
        for (int i = 0; i < WIN_SAMPLES; i++) inFP.put(i, mono15600[i]);
        tensorflowlite.TfLiteTensorCopyFromBuffer(in, inFP, (long)WIN_SAMPLES * Float.BYTES);
        tensorflowlite.TfLiteInterpreterInvoke(interpreter);
        TfLiteTensor out = tensorflowlite.TfLiteInterpreterGetOutputTensor(interpreter, 0);
        FloatPointer outFP = new FloatPointer(NUM_CLASSES);
        tensorflowlite.TfLiteTensorCopyToBuffer(out, outFP, (long)NUM_CLASSES * Float.BYTES);

        float[] scores = new float[NUM_CLASSES];
        outFP.get(scores);
        return scores;
    }

    private static void readFully(TargetDataLine line, byte[] buf) throws IOException {
        int off = 0, need = buf.length;
        while (off < need) {
            int n = line.read(buf, off, need - off);
            off += n;
        }
    }

    private static void printTop3(float[] scores) {
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > scores[b1]) {
                doubleScores[0] = ((double)scores[i]);
                b3 = b2;
                b2 = b1;
                b1 = i;
            } else if (scores[i] > scores[b2]) {
                doubleScores[1] = ((double)scores[i]);
                b3 = b2;
                b2 = i;
            } else if (scores[i] > scores[b3]) {
                doubleScores[2] = ((double)scores[i]);
                b3 = i;
            }
        }
        System.out.printf("Top: %s=%.3f  %s=%.3f  %s=%.3f%n",
                labels[b1], scores[b1],
                labels[b2], scores[b2],
                labels[b3], scores[b3]);
    }
    private static String[] loadLabels(String res) {
        try (InputStream reader = YamnetMic.class.getResourceAsStream(res)) {
            if (reader == null){
                return null;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(reader, StandardCharsets.UTF_8));
            List<String> outList = new ArrayList<>(NUM_CLASSES); // Use List temporarily

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

            // Convert the List back to a String array for the final return
            return outList.toArray(new String[0]);

        } catch (IOException e) {
            return null;
        }
    }

    private static File extractResource(String res, String name) throws IOException {
        try (InputStream in = YamnetMic.class.getResourceAsStream(res)) {
            File tmp = File.createTempFile(name, ".tflite");
            try (OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int r; long total = 0;
                while ((r = in.read(buf)) != -1) {
                    out.write(buf, 0, r); total += r;
                }
            }
            tmp.deleteOnExit();
            return tmp;
        }
    }

    @Override
    public void close() {
        tensorflowlite.TfLiteInterpreterDelete(interpreter);
        tensorflowlite.TfLiteInterpreterOptionsDelete(options);
        tensorflowlite.TfLiteModelDelete(model);
    }

    @Override
    public void run(){
        try {
            this.listenLoop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
