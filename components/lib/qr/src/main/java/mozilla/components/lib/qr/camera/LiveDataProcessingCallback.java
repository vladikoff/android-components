package mozilla.components.lib.qr.camera;

/**
 * Created by dlivotov on 02/09/2015.
 */
public interface LiveDataProcessingCallback
{
    Object onProcessCameraFrame(byte[] data, int width, int height);

    void onReceiveProcessedCameraFrame(Object data);
}
