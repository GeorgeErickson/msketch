package sketches;

import java.io.IOException;

public class SketchLoader {
    public static QuantileSketch load(
            String sketchName
    ) throws IOException {
        if (sketchName.startsWith("cmoment")) {
            return new CMomentSketch(1e-9);
        } else if (sketchName.startsWith("tdigest")) {
            return new TDigestSketch();
        } else if (sketchName.startsWith("yahoo")) {
            return new YahooSketch();
        } else if (sketchName.startsWith("spark_gk")) {
            return new SparkGKSketch();
        } else if (sketchName.startsWith("sampling")) {
            return new SamplingSketch();
        } else if (sketchName.startsWith("reservoir_sampling")) {
            return new ReservoirSamplingSketch();
        } else if (sketchName.startsWith("histogram")) {
            return new HistogramSketch();
        } else if (sketchName.startsWith("moment")) {
            return new MomentSketch(1e-9);
        } else if (sketchName.startsWith("hmoment")) {
            return new HybridMomentSketch(1e-9);
        } else if (sketchName.startsWith("approx_histogram")) {
            return new ApproximateHistogramSketch();
        } else if (sketchName.startsWith("random")) {
            return new RandomSketch();
        }
        throw new IOException("Invalid Sketch");
    }
}
