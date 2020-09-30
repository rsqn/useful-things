package tech.rsqn.useful.things.mathanddata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class Histogram {
    private static final Logger log = LoggerFactory.getLogger(Histogram.class);

    private double[] bins;
    private int numBins = -1;
    private double max;            // max frequency of any value
    private double minValue = -1;
    private double maxValue = -1;
    private double range = -1;


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


    public double avgDifference(Histogram o) {
        double score = 0;

        if (numBins != o.numBins) {
            return 0;
        }

        //todo - difference hist?

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
        //todo - support underflow and overflow
        if (range == -1) {
            throw new RuntimeException("Histogram not initialised with ranges");
        }
        if (v < minValue || v > maxValue) {
            log.warn("Value " + v + " is not within initialised range " + minValue + " -> " + maxValue);
//            throw new RuntimeException("Value " + v + " is not within initialised range " + minValue + " -> " + maxValue);
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


    public void normalize() {
        double v;
        double r = 1.0d / max;

        for (int i = 0; i < numBins; i++) {
            v = bins[i];
            bins[i] = v * r;
        }

        max = 1.0d;
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
            String s = "bin[" + i + "](" + bins[i] + ") : " + renderBar(max, bins[i]) + "\n";
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
                '}';
    }
}
