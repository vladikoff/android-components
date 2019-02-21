package mozilla.components.lib.qr

interface CallbackHandler {
    fun pictureCallback()
    fun scanCallback()
    fun cropPictureCallback()
    fun cropScanCallback()
}
