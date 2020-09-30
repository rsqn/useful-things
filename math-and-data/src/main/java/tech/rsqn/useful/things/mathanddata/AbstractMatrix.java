package tech.rsqn.useful.things.mathanddata;

import java.util.Arrays;

public abstract class AbstractMatrix<T> {
    protected int channels;
    protected int cols;
    protected int rows;
    protected T[] data;

    /**
     * These are not fully supported right now, but how this works is sufficent
     * This should actualy change the underlying array
     */
    public static final int D64F = 1;
    public static final int D32F = 2;
    public static final int D8U = 3;

    public int depth = D64F;

    protected abstract T[] arr(int len);

    public <MT> MT with(int depth, int channels, int cols, int rows, T dVal) {
        if (!(depth == D64F || depth == D32F || depth == D8U)) {
            throw new RuntimeException("Unsupported depth " + depth);
        }
        this.depth = depth;
        this.channels = channels;
        this.cols = cols;
        this.rows = rows;
        zero(dVal);
        return (MT) this;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }


    public int lengthAsVector() {
        return cols * rows;
    }

    public int vectorPtr(int i) {
        int y = i / rows;
        int x = i % rows;

        return ptr(0, x, y);
    }

    private int ptr(int channel, int x, int y) {
//        if ( channel == 1) {
//            throw new RuntimeException("rarr");
//        }
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

    public void set(int channel, int x, int y, T v) {
        data[ptr(channel, x, y)] = v;
    }

    public T get(int channel, int x, int y) {
        return data[ptr(channel, x, y)];
    }

    public T[] get(int x, int y) {
        T[] ret = arr(channels);
        int base = ptr(0, x, y);

        for (int i = 0; i < channels; i++) {
            ret[i] = data[base + i];
        }
        return ret;
    }

    public T[] get(int e) {
        T[] ret = arr(channels);
        int base = (e * channels);

        for (int i = 0; i < channels; i++) {
            ret[i] = data[base + i];
        }
        return ret;
    }

    public void set(int e, T[] v) {
        int base = (e * channels);

        for (int i = 0; i < channels; i++) {
            data[base + i] = v[i];
        }
    }

    protected void zero() {
        data = arr(channels * (cols * rows));
    }

    public void zero(T v) {
        data = arr(channels * (cols * rows));
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

    public abstract T sum();


    public String print(String n) {
        String ret = "";

        ret += "\n###### MATRIX " + n + " #####################################\n";

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                ret += "" + get(col, row)[0] + ", ";
            }
            ret += "\n";
        }
        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractMatrix matrix = (AbstractMatrix) o;

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

