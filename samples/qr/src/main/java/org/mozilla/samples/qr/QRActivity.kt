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
import kotlinx.android.synthetic.main.activity_qr.*
//import mozilla.components.lib.qr.QR

class QRActivity : AppCompatActivity(), View.OnClickListener {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_qr)

        fatalQRButton.setOnClickListener(this)
        qrButton.setOnClickListener(this)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()

        unregisterReceiver(receiver)
    }

    @Suppress("TooGenericExceptionThrown")
    override fun onClick(view: View) {
        when (view) {
            fatalQRButton -> throw RuntimeException("Boom!")

            qrButton -> {
                // Pretend GeckoView has qred by re-building a qr Intent and launching the QRHandlerService.
                val intent = Intent("org.mozilla.gecko.ACTION_CRASHED")
                intent.component = ComponentName(
                    packageName,
                    "mozilla.components.lib.qr.handler.QRHandlerService"
                )
                intent.putExtra(
                    "minidumpPath",
                    "${filesDir.path}/mozilla/QR Reports/pending/3ba5f665-8422-dc8e-a88e-fc65c081d304.dmp"
                )
                intent.putExtra("fatal", false)
                intent.putExtra(
                    "extrasPath",
                    "${filesDir.path}/mozilla/QR Reports/pending/3ba5f665-8422-dc8e-a88e-fc65c081d304.extra"
                )
                intent.putExtra("minidumpSuccess", true)

                ContextCompat.startForegroundService(this, intent)
            }
        }
    }
}
