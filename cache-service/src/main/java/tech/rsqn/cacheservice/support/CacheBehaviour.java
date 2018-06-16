package tech.rsqn.cacheservice.support;


/**
 * Created by IntelliJ IDEA.
 * User: mandrewes
 * Date: 15/03/12
 *
 */
public class CacheBehaviour {
    private boolean _clearCacheAfterRead;
    private boolean _returnIfItemIsNotCached;

    public void returnIfItemIsNotCached() {
        this._returnIfItemIsNotCached = true;
    }

    public void clearCacheAfterRead() {
        this._clearCacheAfterRead = true;
    }

    public void enableDefaultBehaviour() {
        _clearCacheAfterRead = false;
        _returnIfItemIsNotCached = false;
    }

    public boolean isClearCacheAfterRead() {
        return _clearCacheAfterRead;
    }

    public boolean isReturnIfItemIsNotCached() {
        return _returnIfItemIsNotCached;
    }

    @Override
    public String toString() {
        return "CacheBehaviour{" + "_clearCacheAfterRead=" +
        _clearCacheAfterRead + ", _returnIfItemIsNotCached=" +
        _returnIfItemIsNotCached + '}';
    }
}
