package mozilla.components.lib.qr;

import android.os.Handler;
import android.os.Message;

/**
 * Created by mkkim on 2016-11-23.
 */

public class PreviewCallbackHandler extends Handler implements CallbackHandler {

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);

        switch (msg.what){

        }
    }

    @Override
    public void pictureCallback() {

    }

    @Override
    public void scanCallback() {

    }

    @Override
    public void cropPictureCallback() {

    }

    @Override
    public void cropScanCallback() {

    }
}
