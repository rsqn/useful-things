package tech.rsqn.useful.things.mathanddata;

public class IntMatrix extends AbstractMatrix<Integer> {

    @Override
    protected Integer[] arr(int len) {
        return new Integer[len];
    }

    public Integer sum() {
        int ret = 0;

        for (Integer i : data) {
            ret += i;
        }
        return ret;
    }

    public Integer sum(int[] columnBypass) {
        int ret = 0;

        for (int col = 0; col < this.getCols(); col++) {
            if (shouldBypassColumn(col, columnBypass)) {
                continue;
            }

            for (int row = 0; row < this.getRows(); row++) {
                int v = this.get(0, col, row);
                ret += v;
            }
        }

        return ret;
    }


    public Integer sumPositive() {
        int ret = 0;

        for (Integer i : data) {
            if (i > 0) {
                ret += i;
            }
        }
        return ret;
    }


    public Integer sumPositive(int[] columnBypass) {
        int ret = 0;

        for (int col = 0; col < this.getCols(); col++) {
            if (shouldBypassColumn(col, columnBypass)) {
                continue;
            }

            for (int row = 0; row < this.getRows(); row++) {
                int v = this.get(0, col, row);
                if ( v > 0 ) {
                    ret += v;
                }
            }
        }

        return ret;
    }


    public IntMatrix applyMask(IntMatrix mask) {
        return applyMask(mask, new int[]{});
    }

    private boolean shouldBypassColumn(int col, int[] columnBypass) {
        for (int i = 0; i < columnBypass.length; i++) {
            if (col == columnBypass[i]) {
                return true;
            }
        }
        return false;
    }

    public IntMatrix applyMask(IntMatrix mask, int[] columnBypass) {
        IntMatrix ret = new IntMatrix().with(this.depth, this.channels, this.cols, this.rows, 0);
        ret.zero(0);

        if (this.lengthAsVector() != mask.lengthAsVector()) {
            throw new RuntimeException("data.length != mask.length " + data.length + " : " + mask.lengthAsVector());
        }

        for (int col = 0; col < this.getCols(); col++) {
            if (shouldBypassColumn(col, columnBypass)) {
                continue;
            }

            for (int row = 0; row < this.getRows(); row++) {
                int v = this.get(0, col, row);
                int mv = mask.get(0, col, row);
                v = v * mv;
                if (v == 0 && mv > 0) {
                    v = -1;
                }
                ret.set(0, col, row, v);

            }
        }
        return ret;
    }
}
