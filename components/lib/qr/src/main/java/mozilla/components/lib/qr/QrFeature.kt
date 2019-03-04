/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.lib.qr

import android.Manifest.permission.CAMERA
import android.content.Context
import android.support.v4.app.FragmentManager

typealias OnScanCompleted = () -> String
typealias OnNeedToRequestPermissions = (permissions: Array<String>) -> Unit

class QrFeature(
    private val applicationContext: Context,
    var onNeedToRequestPermissions: OnNeedToRequestPermissions = { },
    var onScanCompleted: OnScanCompleted = { -> String },
    private val fragmentManager: FragmentManager? = null
)