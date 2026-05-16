package tech.rsqn.useful.things.mathanddata;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.security.SecureRandom;

public class HistogramTest {

    SecureRandom r = new SecureRandom();

    public double getRange(double lo, double hi) {
        if (lo > hi) {
            throw new IllegalArgumentException("lo > hi");
        }
        double range = hi -  lo + 1;
        double frac = (range * r.nextDouble());
        return (frac + lo);
    }


    @Test
    public void shouldPrintSimpleHistogram() throws Exception {


        double min = 100d;
        double max = 1000d;

        Histogram hist = new Histogram(min,max,11);

        for (int i = 0; i < 100; i++) {
            double v = getRange(min,max);
            hist.submit(v);
        }

        System.out.println(hist.render());

    }


    @Test
    public void shouldNormaliseSimpleHistogram() throws Exception {


        double min = 100d;
        double max = 1000d;

        Histogram hist = new Histogram(min,max,11);

        for (int i = 0; i < 100; i++) {
            double v =  getRange(min,max);
            hist.submit(v);
        }

        hist.normalize();

        System.out.println(hist.render());

    }

    // --- Custom-boundary tests ---

    @Test
    public void customBoundaries_validInput_createsBins() {
        double[] boundaries = {0.0, 1.0, 2.0, 3.0};
        Histogram hist = new Histogram(boundaries);
        Assert.assertEquals(hist.getNumBins(), 3);
    }

    @Test
    public void customBoundaries_submit_routesValuesToCorrectBin() {
        double[] boundaries = {0.0, 1.0, 2.0, 3.0};
        Histogram hist = new Histogram(boundaries);
        hist.submit(0.5);  // bin 0: (0,1]
        hist.submit(1.5);  // bin 1: (1,2]
        hist.submit(2.5);  // bin 2: (2,3]
        double[] counts = hist.toDoubleArray();
        Assert.assertEquals(counts[0], 1.0);
        Assert.assertEquals(counts[1], 1.0);
        Assert.assertEquals(counts[2], 1.0);
    }

    @Test
    public void customBoundaries_underflow_goesToBinZero() {
        double[] boundaries = {0.0, 1.0, 2.0, 3.0};
        Histogram hist = new Histogram(boundaries);
        hist.submit(-10.0);
        hist.submit(-1000.0);
        double[] counts = hist.toDoubleArray();
        Assert.assertEquals(counts[0], 2.0);
        Assert.assertEquals(counts[1], 0.0);
        Assert.assertEquals(counts[2], 0.0);
    }

    @Test
    public void customBoundaries_overflow_goesToLastBin() {
        double[] boundaries = {0.0, 1.0, 2.0, 3.0};
        Histogram hist = new Histogram(boundaries);
        hist.submit(100.0);
        hist.submit(1e9);
        double[] counts = hist.toDoubleArray();
        Assert.assertEquals(counts[0], 0.0);
        Assert.assertEquals(counts[1], 0.0);
        Assert.assertEquals(counts[2], 2.0);
    }

    @Test
    public void customBoundaries_invalidInput_throwsIllegalArgument() {
        // length < 2
        try {
            new Histogram(new double[]{1.0});
            Assert.fail("Expected IllegalArgumentException for length-1 boundaries");
        } catch (IllegalArgumentException e) { /* expected */ }

        // non-ascending
        try {
            new Histogram(new double[]{3.0, 1.0, 2.0});
            Assert.fail("Expected IllegalArgumentException for non-ascending boundaries");
        } catch (IllegalArgumentException e) { /* expected */ }

        // equal adjacent values (not strictly ascending)
        try {
            new Histogram(new double[]{1.0, 1.0, 2.0});
            Assert.fail("Expected IllegalArgumentException for equal adjacent boundaries");
        } catch (IllegalArgumentException e) { /* expected */ }

        // NaN
        try {
            new Histogram(new double[]{0.0, Double.NaN, 2.0});
            Assert.fail("Expected IllegalArgumentException for NaN in boundaries");
        } catch (IllegalArgumentException e) { /* expected */ }

        // Infinity
        try {
            new Histogram(new double[]{0.0, Double.POSITIVE_INFINITY});
            Assert.fail("Expected IllegalArgumentException for Infinity in boundaries");
        } catch (IllegalArgumentException e) { /* expected */ }
    }

    @Test
    public void toDoubleArray_returnsCopyOfCounts() {
        double[] boundaries = {0.0, 1.0, 2.0};
        Histogram hist = new Histogram(boundaries);
        hist.submit(0.5);
        double[] copy = hist.toDoubleArray();
        Assert.assertEquals(copy[0], 1.0);
        copy[0] = 999.0;  // mutate copy
        double[] copy2 = hist.toDoubleArray();
        Assert.assertEquals(copy2[0], 1.0, "Mutation of toDoubleArray result must not affect histogram");
    }

    @Test
    public void snapshot_returnsDefensiveCopy() {
        double[] boundaries = {0.0, 1.0, 2.0};
        Histogram hist = new Histogram(boundaries);
        hist.submit(0.5);
        double[] snap = hist.snapshot();
        Assert.assertEquals(snap[0], 1.0);
        hist.submit(0.5);
        hist.submit(0.5);
        Assert.assertEquals(snap[0], 1.0, "Further submit() calls must not mutate the snapshot");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void avgDifference_incompatibleModes_throwsException() {
        Histogram uniform = new Histogram(0.0, 10.0, 3);
        Histogram custom = new Histogram(new double[]{0.0, 1.0, 2.0, 3.0});
        uniform.avgDifference(custom);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void avgDifference_differentBoundaries_throwsException() {
        Histogram h1 = new Histogram(new double[]{0.0, 1.0, 2.0});
        Histogram h2 = new Histogram(new double[]{0.0, 1.5, 3.0});
        h1.avgDifference(h2);
    }

    @Test
    public void avgDifference_compatibleCustom_returnsScore() {
        double[] boundaries = {0.0, 1.0, 2.0, 3.0};
        Histogram h1 = new Histogram(boundaries);
        Histogram h2 = new Histogram(boundaries);
        h1.submit(0.5);  // h1 bin 0 = 1
        h2.submit(1.5);  // h2 bin 1 = 1
        // |1-0| + |0-1| + |0-0| = 2, avg = 2/3
        double score = h1.avgDifference(h2);
        Assert.assertEquals(score, 2.0 / 3.0, 1e-9);
    }

    @Test
    public void reset_clearsBinsAndMax() {
        double[] boundaries = {0.0, 1.0, 2.0, 3.0};
        Histogram hist = new Histogram(boundaries);
        hist.submit(0.5);
        hist.submit(1.5);
        hist.reset();
        double[] counts = hist.toDoubleArray();
        for (double count : counts) {
            Assert.assertEquals(count, 0.0);
        }
        // verify further submits still work correctly after reset
        hist.submit(2.5);
        Assert.assertEquals(hist.toDoubleArray()[2], 1.0);
    }

    @Test
    public void fromDoubleArray_roundtrip_preservesCounts() {
        double[] boundaries = {0.0, 1.0, 2.0, 3.0};
        Histogram orig = new Histogram(boundaries);
        orig.submit(0.5);
        orig.submit(1.5);
        orig.submit(1.8);
        double[] counts = orig.toDoubleArray();

        Histogram restored = Histogram.fromDoubleArray(counts, boundaries);
        Assert.assertEquals(restored.toDoubleArray(), counts);
        Assert.assertEquals(restored.getNumBins(), orig.getNumBins());
        Assert.assertNotSame(restored.getBoundaries(), boundaries, "boundaries must be a defensive copy");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void fromDoubleArray_wrongCountsLength_throwsException() {
        double[] boundaries = {0.0, 1.0, 2.0, 3.0};  // expects 3 bins
        Histogram.fromDoubleArray(new double[]{1.0, 2.0}, boundaries);  // only 2 counts
    }

    @Test
    public void render_customMode_labelsOverflowBins() {
        double[] boundaries = {0.0, 1.0, 2.0, 3.0};
        Histogram hist = new Histogram(boundaries);
        hist.submit(-1.0);   // underflow → bin 0
        hist.submit(1.5);    // bin 1
        hist.submit(100.0);  // overflow → bin 2
        String output = hist.render();
        System.out.println(output);
        Assert.assertTrue(output.contains("underflow"), "First bin label must contain 'underflow'");
        Assert.assertTrue(output.contains("overflow"), "Last bin label must contain 'overflow'");
    }
}
