/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mozilla.components.lib.qr;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

public class CameraActivity extends Activity {
    private static final String TAG = CameraActivity.class.getSimpleName();
    Activity activity = this;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        /*Button qrCodeBtn = (Button) findViewById(R.id.qrcodeBtn);
        Button zxingBtn = (Button) findViewById(R.id.zxingBtn);
        qrCodeBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                getFragmentManager().beginTransaction()
                        .replace(R.id.container, Camera2BasicFragment.newInstance())
                        .addToBackStack(null)
                        .commit();
            }
        });

        zxingBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                Intent intent = new Intent("com.google.zxing.client.android.SCAN");
                intent.putExtra("SCAN_MODE", "ALL");
                startActivityForResult(intent, 0);
            }
        });*/

        startByOSVersion();
    }

    public static Intent newIntent(final Context context) {
        final Intent intent = new Intent(context, CameraActivity.class);
        //intent.putExtra(INTENT_USER_ID, user.getId());
        return intent;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == 0) {

            if(resultCode == Activity.RESULT_OK)
            {
                String contents = data.getStringExtra("SCAN_RESULT");

                //위의 contents 값에 scan result가 들어온다.
                Log.d(TAG, "result String : "+contents);
            }

        }
    }

    private void startByOSVersion(){
        startCameraProcess(2);
    }

    private void startCameraProcess(int apiVersion){
        switch (apiVersion){
            case 2:
                getFragmentManager().beginTransaction()
                        .replace(R.id.container, Camera2BasicFragment.newInstance())
                        .addToBackStack(null)
                        .commit();
                break;
        }
    }
}
