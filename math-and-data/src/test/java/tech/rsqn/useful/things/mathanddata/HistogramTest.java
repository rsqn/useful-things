package tech.rsqn.useful.things.mathanddata;

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
}
