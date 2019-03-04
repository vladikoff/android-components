/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.qr

import android.Manifest.permission.CAMERA
import android.content.Context
import android.support.v4.app.FragmentManager
import mozilla.components.support.ktx.android.content.isPermissionGranted

typealias OnScanResult = () -> String
typealias OnNeedToRequestPermissions = (permissions: Array<String>) -> Unit

class QrFeature(
    private val applicationContext: Context,
    var onNeedToRequestPermissions: OnNeedToRequestPermissions = { },
    var onScanResult: OnScanResult = { -> "" }, // TODO: what's up with this?
    private val fragmentManager: FragmentManager,
    private val fragment: QrFragment = QrFragment.newInstance()
) {
    fun scan(container: Int): Boolean {
        return if (applicationContext.isPermissionGranted(CAMERA)) {
            fragmentManager.beginTransaction()
                    .replace(container, fragment)
                    .addToBackStack(null)
                    .commit()
            true
        } else {
            onNeedToRequestPermissions(arrayOf(CAMERA))
            false
        }
    }

}
