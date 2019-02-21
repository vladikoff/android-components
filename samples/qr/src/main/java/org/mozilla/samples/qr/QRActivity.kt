/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.samples.qr

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast
//import mozilla.components.lib.qr.ScannerLiveView
//import mozilla.components.lib.qr.camera.CameraController
//import mozilla.components.lib.qr.scanner.decoder.zxing.ZXDecoder
import mozilla.components.lib.qr.CameraActivity

//import mozilla.components.lib.qr.QR

class QRActivity : AppCompatActivity() {

    //lateinit var camera: ScannerLiveView
    //lateinit var controller: CameraController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        val intent = CameraActivity.newIntent(this)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
//        val decoder = ZXDecoder()
//        decoder.scanAreaPercent = 0.5
//        camera.decoder = decoder
//        camera.startScanner()
    }

    override fun onPause() {
        //camera.stopScanner()
        super.onPause()
    }
}
