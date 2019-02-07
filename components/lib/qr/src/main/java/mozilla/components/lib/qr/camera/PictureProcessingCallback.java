package mozilla.components.lib.qr.camera;

/**
 * Created by dlivotov on 02/09/2015.
 */
public interface PictureProcessingCallback
{
    void onShutterTriggered();

    void onRawPictureTaken(byte[] rawData);

    void onPictureTaken(byte[] jpegData);
}
