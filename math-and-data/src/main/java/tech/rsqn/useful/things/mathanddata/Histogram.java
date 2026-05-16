package tech.rsqn.useful.things.mathanddata;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class Histogram {
    private static final Logger LOG = LoggerFactory.getLogger(Histogram.class);

    private double[] bins;
    private int numBins = -1;
    private double max;            // max frequency of any value
    private double minValue = -1;
    private double maxValue = -1;
    private double range = -1;
    private double[] boundaries;  // null = uniform mode; non-null = custom-boundary mode


    public Histogram() {
    }

    public Histogram(int nBins) {
        bins = new double[nBins];
        numBins = nBins;
    }

    public Histogram(double minValue, double maxValue, int nBins) {
        this.bins = new double[nBins];
        this.range = maxValue - minValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.numBins = nBins;
    }

    /**
     * Custom-boundary constructor. Creates {@code boundaries.length - 1} bins.
     * <p>
     * Bin i covers the range {@code (boundaries[i], boundaries[i+1]]} (right-inclusive).
     * Bin 0 also absorbs values at or below {@code boundaries[0]} (underflow).
     * Bin {@code numBins-1} also absorbs values above {@code boundaries[numBins]} (overflow).
     *
     * @param boundaries sorted ascending, all finite, length &ge; 2
     * @throws IllegalArgumentException if boundaries is null, length &lt; 2,
     *                                  not strictly ascending, or contains non-finite values
     */
    public Histogram(double[] boundaries) {
        if (boundaries == null || boundaries.length < 2) {
            throw new IllegalArgumentException("boundaries must have length >= 2");
        }
        for (int i = 0; i < boundaries.length; i++) {
            if (!Double.isFinite(boundaries[i])) {
                throw new IllegalArgumentException(
                        "boundaries must all be finite; found " + boundaries[i] + " at index " + i);
            }
        }
        for (int i = 0; i < boundaries.length - 1; i++) {
            if (boundaries[i] >= boundaries[i + 1]) {
                throw new IllegalArgumentException(
                        "boundaries must be strictly ascending; violation at index " + i);
            }
        }
        this.boundaries = Arrays.copyOf(boundaries, boundaries.length);
        this.numBins = boundaries.length - 1;
        this.bins = new double[numBins];
    }

    public void addToBin(int i) {
        bins[i]++;
        if (bins[i] > max) max = bins[i];
    }

    public void submit(double d) {
        int bin = findBin(d);
        if (bin < 0) {
            return;
        }
        addToBin(bin);
    }

    /**
     * Submits a value with a weight. The weight is added to the resolved bin instead of 1.
     * Use for volume-weighted histograms where each submission carries a magnitude.
     *
     * @param value  the value to bin
     * @param weight the amount to add to the bin (e.g., trade volume)
     */
    public void submitWeighted(double value, double weight) {
        int bin = findBin(value);
        if (bin < 0) {
            return;
        }
        bins[bin] += weight;
        if (bins[bin] > max) max = bins[bin];
    }


    /**
     * Returns the average per-bin absolute difference between this histogram and {@code o}.
     *
     * @throws IllegalArgumentException if one histogram is in uniform mode and the other is in
     *                                  custom-boundary mode, or if both are custom-boundary but with
     *                                  different boundary arrays
     */
    public double avgDifference(Histogram o) {
        boolean thisCustom = this.boundaries != null;
        boolean otherCustom = o.boundaries != null;
        if (thisCustom != otherCustom) {
            throw new IllegalArgumentException(
                    "Cannot compare histograms with different modes (uniform vs custom-boundary)");
        }
        if (thisCustom && !Arrays.equals(this.boundaries, o.boundaries)) {
            throw new IllegalArgumentException(
                    "Cannot compare custom-boundary histograms with different boundary sets");
        }

        double score = 0;

        if (numBins != o.numBins) {
            return 0;
        }

        for (int i = 0; i < numBins; i++) {
            double mv = bins[i];
            double ov = o.bins[i];
            score += Math.abs(mv-ov);
        }

        if ( score != 0) {
            score = score / numBins;
        }

        return score;
    }

    private int findBin(double v) {
        return boundaries == null ? findBinUniform(v) : findBinCustom(v);
    }

    private int findBinUniform(double v) {
        if (range == -1) {
            throw new RuntimeException("Histogram not initialised with ranges");
        }
        if (v < minValue || v > maxValue) {
            LOG.warn("Value " + v + " is not within initialised range " + minValue + " -> " + maxValue);
            return -1;
        } else {
            // search for histogram bin into which x falls
            double binWidth = range / numBins;
            for (int i = 0; i < numBins; i++) {
                double highEdge = minValue + (i + 1) * binWidth;
                if (v <= highEdge) {
                    return i;
                }
            }
        }
        throw new RuntimeException("Unable to resolve bind for " + v + " is not within initialised range " + minValue + " -> " + maxValue);
    }

    // Right-inclusive: bin i covers (boundaries[i], boundaries[i+1]].
    // Values <= boundaries[0] clamp to bin 0 (underflow); values > boundaries[numBins] clamp to bin numBins-1 (overflow).
    private int findBinCustom(double v) {
        if (v <= boundaries[0]) return 0;
        for (int i = 0; i < numBins - 1; i++) {
            if (v <= boundaries[i + 1]) return i;
        }
        return numBins - 1;
    }


    /**
     * Resets all bin counts to zero and resets max. Boundaries/range are preserved.
     */
    public void reset() {
        Arrays.fill(bins, 0, numBins, 0.0);
        max = 0;
    }

    public void normalize() {
        double v;
        double r = 1.0d / max;

        for (int i = 0; i < numBins; i++) {
            v = bins[i];
            bins[i] = v * r;
        }

        max = 1.0d;
    }

    /**
     * Divides each bin by {@code binWeights[i]}, then recomputes max.
     * <p>
     * Use for density-correct normalisation on non-uniform bins, e.g.:
     * <pre>
     *   double[] b = getBoundaries();
     *   double[] widths = new double[getNumBins()];
     *   for (int i = 0; i &lt; widths.length; i++) widths[i] = b[i+1] - b[i];
     *   normalize(widths);
     * </pre>
     * Modifies bins in-place; not reversible.
     *
     * @param binWeights per-bin divisors; length must equal {@code numBins}
     * @throws IllegalArgumentException if {@code binWeights.length != numBins}
     */
    public void normalize(double[] binWeights) {
        if (binWeights.length != numBins) {
            throw new IllegalArgumentException(
                    "binWeights.length must equal numBins (" + numBins + ")");
        }
        max = 0;
        for (int i = 0; i < numBins; i++) {
            bins[i] = bins[i] / binWeights[i];
            if (bins[i] > max) max = bins[i];
        }
    }

    /**
     * Reconstructs a custom-boundary histogram from a serialised count array.
     * <p>
     * Inverse of {@code toDoubleArray()}: {@code fromDoubleArray(h.toDoubleArray(), h.getBoundaries())}
     * produces a histogram equivalent to {@code h}.
     *
     * @param counts     {@code double[boundaries.length - 1]} — the count for each bin
     * @param boundaries the same boundary array used when the histogram was created
     * @throws IllegalArgumentException if boundaries are invalid or counts.length != boundaries.length - 1
     */
    public static Histogram fromDoubleArray(double[] counts, double[] boundaries) {
        Histogram h = new Histogram(boundaries);
        if (counts.length != h.numBins) {
            throw new IllegalArgumentException(
                    "counts.length (" + counts.length + ") must equal boundaries.length-1 (" + h.numBins + ")");
        }
        for (int i = 0; i < h.numBins; i++) {
            h.bins[i] = counts[i];
            if (counts[i] > h.max) h.max = counts[i];
        }
        return h;
    }

    /**
     * Returns a defensive copy of the bin counts.
     * <p>
     * Serialisation contract: {@code double[numBins]}, index i = count for bin i.
     * Boundaries are not included; store them separately for reconstruction via
     * {@code fromDoubleArray(double[] counts, double[] boundaries)}.
     */
    public double[] toDoubleArray() {
        return Arrays.copyOf(bins, numBins);
    }

    /**
     * Returns a copy of the bin counts at the time of the call.
     * <p>
     * NOT guaranteed to be a consistent point-in-time view without external synchronisation.
     * Callers must hold their own lock across {@code submit()}/{@code snapshot()} pairs
     * if consistency is required.
     */
    public double[] snapshot() {
        return Arrays.copyOf(bins, numBins);
    }

    /**
     * Returns the raw internal bins array. NOT a copy — mutation affects the histogram.
     * Do not hold a reference across {@code submit()} calls without external synchronisation.
     */
    public double[] getBins() {
        return bins;
    }

    /**
     * Returns a defensive copy of the boundaries array, or {@code null} if in uniform mode.
     */
    public double[] getBoundaries() {
        return boundaries == null ? null : Arrays.copyOf(boundaries, boundaries.length);
    }

    /** Returns the number of bins. */
    public int getNumBins() {
        return numBins;
    }

    private String renderBar(double max, double v) {
        if (v == 0) {
            return "";
        }

        double p = v / max;

        int n = new Double(p * 10d).intValue();

        String s = "";
        for (int i = 0; i < n; i++) {
            s += "=";
        }
        return s;
    }

    public String render() {
        String ret = "";

        for (int i = 0; i < bins.length; i++) {
            String label;
            if (boundaries != null) {
                if (i == 0) {
                    label = "bin[" + i + "][underflow+" + boundaries[0] + "-" + boundaries[1] + "]";
                } else if (i == numBins - 1) {
                    label = "bin[" + i + "][" + boundaries[i] + "-" + boundaries[i + 1] + "+overflow]";
                } else {
                    label = "bin[" + i + "][" + boundaries[i] + "-" + boundaries[i + 1] + "]";
                }
            } else {
                label = "bin[" + i + "]";
            }
            String s = label + "(" + bins[i] + ") : " + renderBar(max, bins[i]) + "\n";
            ret += s;
        }

        return ret;
    }


    @Override
    public String toString() {
        return "Histogram{" +
                "bins=" + Arrays.toString(bins) +
                ", numBins=" + numBins +
                ", max=" + max +
                ", minValue=" + minValue +
                ", maxValue=" + maxValue +
                ", range=" + range +
                ", boundaries=" + Arrays.toString(boundaries) +
                '}';
    }
}
