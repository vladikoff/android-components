package mozilla.components.lib.qr.camera.v2;

import android.view.SurfaceView;

import mozilla.components.lib.qr.camera.CameraController;
import mozilla.components.lib.qr.camera.CameraDelayedOperationResult;
import mozilla.components.lib.qr.camera.CameraInfo;
import mozilla.components.lib.qr.camera.LiveDataProcessingCallback;
import mozilla.components.lib.qr.camera.PictureProcessingCallback;

/**
 * Created by dlivotov on 02/09/2015.
 */
public class DefaultCameraV2Controller implements CameraController
{
    public DefaultCameraV2Controller(CameraInfo camera, CameraDelayedOperationResult callback)
    {

    }

    @Override
    public boolean isReady()
    {
        return false;
    }

    @Override
    public void close()
    {

    }

    @Override
    public void close(CameraDelayedOperationResult callback)
    {

    }

    @Override
    public void startPreview(SurfaceView holder)
    {

    }

    @Override
    public void stopPreview()
    {

    }

    @Override
    public void requestLiveData(LiveDataProcessingCallback callback)
    {

    }

    @Override
    public void takePicture(PictureProcessingCallback callback)
    {

    }

    @Override
    public void switchFlashlight(boolean turnOn)
    {

    }

    @Override
    public void switchAutofocus(boolean useAutofocus)
    {

    }

    @Override
    public void requestFocus()
    {

    }
}
