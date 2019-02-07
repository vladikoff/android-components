package mozilla.components.lib.qr;

/**
 * Created by mkkim on 2016-11-15.
 */

public interface CallbackHandler {
    public void pictureCallback();
    public void scanCallback();
    public void cropPictureCallback();
    public void cropScanCallback();
}
