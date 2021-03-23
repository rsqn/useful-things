package tech.rsqn.useful.things.mathanddata;

import java.util.Arrays;

public class Matrix {
    private int channels;
    private int cols;

    private int rows;
    public double[] data;

    public static final int D64F = 1;
    public static final int D32F = 2;
    public static final int D8U = 3;

    public int depth = D64F;

    public Matrix() {
    }

    public Matrix with(int channels, int cols, int rows) {
        this.channels = channels;
        this.cols = cols;
        this.rows = rows;
        zero();
        return this;
    }

    public Matrix with(int depth, int channels, int cols, int rows) {

        if (!(depth == D64F || depth == D32F || depth == D8U)) {
            throw new RuntimeException("Unsupported depth " + depth);
        }
        this.depth = depth;
        this.channels = channels;
        this.cols = cols;
        this.rows = rows;
        zero();
        return this;
    }


    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    @Override
    public String toString() {
        return "Matrix{" +
                "channels=" + channels +
                ", cols=" + cols +
                ", rows=" + rows +
                '}';
    }


    public int lengthAsVector() {
        return cols * rows;
    }

    public int vectorPtr(int i) {
        int y = i / rows;
        int x = i % rows;

        return ptr(0, x, y);
    }
//    private int ptr(int channel, int x, int y) {
//        int ret = ptrX(channel, x, y);
//        System.out.println("ptr x/y/c " + x + "/" + y + "/" + channel + " = " + ret);
//        return ret;
//    }

    private int ptr(int channel, int x, int y) {
        if (x == 0) {
            return (cols * y * channels) + channel;
        }

        if (y == 0) {
            return (x * channels) + channel;
        }

        if (x > 0 && y > 0) {
            return (cols * y * channels) + (x * channels) + channel;
        }

        if (x == 0 && y == 0) {
            return channel;
        }

        throw new RuntimeException("Unable to calculate data pointer");
    }

    public void set(int channel, int x, int y, double v) {
        data[ptr(channel, x, y)] = v;
    }

    public double get(int channel, int x, int y) {
        return data[ptr(channel, x, y)];
    }

    public double[] get(int x, int y) {
        double[] ret = new double[channels];
        int base = ptr(0, x, y);

        for (int i = 0; i < channels; i++) {
            ret[i] = data[base + i];
        }
        return ret;
    }

    public double[] get(int e) {
        double[] ret = new double[channels];
        int base = (e * channels);

        for (int i = 0; i < channels; i++) {
            ret[i] = data[base + i];
        }
        return ret;
    }

    private void zero() {
        data = new double[channels * (cols * rows)];
    }

    private void zero(double v) {
        data = new double[channels * (cols * rows)];
        for (int i = 0; i < data.length; i++) {
            data[i] = v;
        }
    }

    public int getChannels() {
        return channels;
    }

    public void setChannels(int channels) {
        this.channels = channels;
    }

    public int getCols() {
        return cols;
    }

    public void setCols(int cols) {
        this.cols = cols;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

//    public String toBigString() {
//
//    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Matrix matrix = (Matrix) o;

        if (channels != matrix.channels) return false;
        if (cols != matrix.cols) return false;
        if (rows != matrix.rows) return false;
        return Arrays.equals(data, matrix.data);

    }

    @Override
    public int hashCode() {
        int result = channels;
        result = 31 * result + cols;
        result = 31 * result + rows;
        result = 31 * result + (data != null ? Arrays.hashCode(data) : 0);
        return result;
    }
}
