package tech.rsqn.useful.things.mathanddata;

public class Dimensions2D {
    private int w;
    private int h;
    private double scale;

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public int getW() {
        return w;
    }

    public void setW(int w) {
        this.w = w;
    }

    public int getH() {
        return h;
    }

    public void setH(int h) {
        this.h = h;
    }

    public double getWidthAsDouble() {
        return new Double(w);
    }

    public double getHeightAsDouble() {
        return new Double(h);
    }
}
