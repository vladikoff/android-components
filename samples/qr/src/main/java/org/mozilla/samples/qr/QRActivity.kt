/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.samples.qr

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import mozilla.components.lib.qr.CameraActivity

class QRActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = CameraActivity.newIntent(this)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()

    }

    override fun onPause() {

        super.onPause()
    }
}
