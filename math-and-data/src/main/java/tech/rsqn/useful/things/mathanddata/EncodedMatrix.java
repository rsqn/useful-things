package tech.rsqn.useful.things.mathanddata;

import me.lemire.integercompression.differential.IntegratedIntCompressor;

public class EncodedMatrix {
    private int cols;
    private int rows;
    private int channels;
    private String data;
    private String encoding = "II";

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

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    public EncodedMatrix with(IntMatrix m) {
        this.cols = m.getCols();
        this.rows = m.getRows();
        this.channels = m.getChannels();
        this.data = encArray(m.data);
        return this;
    }

    public IntMatrix extractMatrix() {
        IntMatrix ret = new IntMatrix().with(1,channels,cols,rows,0);

        Integer[] extracted = decArray(data);
        ret.data = extracted;

        return ret;
    }

    private String arrayToString(int[] arr) {
        String s = "";
        for (int i = 0; i < arr.length; i++) {
            s += arr[i];
            if (i < arr.length - 1) {
                s += ",";
            }
        }
        return s;
    }

    private int[] stringToArray(String s) {
        String[] parts = s.split(",");
        int[] ret = new int[parts.length];

        for (int i = 0; i < parts.length; i++) {
            ret[i] = Integer.parseInt(parts[i]);
        }

        return ret;
    }

    private int[] copy(Integer[] arr) {
        int[] copy = new int[arr.length];
        for (int i = 0; i < arr.length; i++) {
            copy[i] = arr[i].intValue();
        }
        return copy;
    }

    private Integer[] copy(int[] arr) {
        Integer[] copy = new Integer[arr.length];
        for (int i = 0; i < arr.length; i++) {
            copy[i] = arr[i];
        }
        return copy;
    }

    private String encArray(Integer[] arr) {
        int[] _copy = copy(arr);
        IntegratedIntCompressor iic = new IntegratedIntCompressor();
        int[] compressed = iic.compress(_copy);
        return arrayToString(compressed);
    }

    private Integer[] decArray(String s) {
        int[] arr = stringToArray(s);
        IntegratedIntCompressor iic = new IntegratedIntCompressor();
        int[] uncompressed = iic.uncompress(arr);
        return copy(uncompressed);
    }

}
