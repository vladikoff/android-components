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

package mozilla.components.lib.qr

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.app.Fragment
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.content.ContextCompat
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer

import java.io.File
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Comparator
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class Camera2BasicFragment : Fragment(), View.OnClickListener {

    private var mTextureView: AutoFitTextureView? = null
    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */

    private val mSurfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "onSurfaceTextureAvailable")
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "onSurfaceTextureSizeChanged")
            configureTransform(width, height)
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
            Log.d(TAG, "onSurfaceTextureUpdated")
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            Log.d(TAG, "onSurfaceTextureDestroyed")
            return true
        }
    }

    private var mCameraId: String? = null

    private var mCaptureSession: CameraCaptureSession? = null
    private var mCameraDevice: CameraDevice? = null
    /**
     * The [android.util.Size] of camera preview.
     */
    private var mPreviewSize: Size? = null

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.
     */
    private val mStateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release()
            mCameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            Log.d(TAG, "onDisconnected()")
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            Log.d(TAG, "onError()")
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
            val activity = activity
            activity?.finish()
        }

        override fun onClosed(camera: CameraDevice) {
            super.onClosed(camera)
            Log.d(TAG, "onClose()")
        }
    }

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     * A [Handler] for running tasks in the background.
     */
    private var mBackgroundThread: HandlerThread? = null
    private var mBackgroundHandler: Handler? = null

    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null

    /**
     * [CaptureRequest] generated by [.mPreviewRequestBuilder]
     */
    private var mPreviewRequest: CaptureRequest? = null

    /**
     * The current state of camera state for taking pictures.
     *
     * @see .mCaptureCallback
     */
    private var mState = STATE_PREVIEW

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val mCameraOpenCloseLock = Semaphore(1)

    /**
     * Whether the current camera device supports Flash or not.
     */
    private val mFlashSupported: Boolean = false

    /**
     * Orientation of the camera sensor
     */
    private var mSensorOrientation: Int = 0

    /**
     * A [CameraCaptureSession.CaptureCallback] that handles events related to JPEG capture.
     */
    private val mCaptureCallback = object : CameraCaptureSession.CaptureCallback() {

        private fun process(result: CaptureResult) {

            when (mState) {
                STATE_PREVIEW -> {
                    Log.d(TAG, "STATE_PREVIEW")
                }//callbackHandler.scanCallback();
                STATE_WAITING_LOCK -> {
                    Log.d(TAG, "STATE_WAITING_LOCK")
                    val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                    Log.d(TAG, "afState:" + afState + " | " + CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED + " | " + CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED)
                    if (afState == null) {
                        callbackHandler.pictureCallback()
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                        Log.d(TAG, "aeState:" + aeState + " | " + CaptureResult.CONTROL_AE_STATE_CONVERGED)
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN
                            Log.d(TAG, "mState = STATE_PICTURE_TAKEN = $STATE_PICTURE_TAKEN")
                            callbackHandler.pictureCallback()
                        } else {
                            Log.d(TAG, "runPrecaptureSequence()")
                            runPrecaptureSequence()
                        }
                    }
                }
                STATE_WAITING_PRECAPTURE -> {
                    Log.d(TAG, "STATE_WAITING_PRECAPTURE")
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    Log.d(TAG, "aeState:" + aeState!!)
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE
                    }
                }
            }
        }

        override fun onCaptureProgressed(session: CameraCaptureSession,
                                         request: CaptureRequest,
                                         partialResult: CaptureResult) {
            //Log.d(TAG, "onCaptureProgressed");
            process(partialResult)
        }

        override fun onCaptureCompleted(session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        result: TotalCaptureResult) {
            Log.d(TAG, "onCaptureCompleted($mState)")
            process(result)
        }

    }

    /**
     * An [ImageReader] that handles still image capture. &
     * This is the output file for our picture.
     */
    private var mImageReader: ImageReader? = null
    private var mFile: File? = null
    private var count: Int = 0
    private var data: ByteArray? = null
    private val mOnImageAvailableListener = object : ImageReader.OnImageAvailableListener {

        private var image: Image? = null

        override fun onImageAvailable(reader: ImageReader) {
            Log.d(TAG, "onImageAvailable(" + count++ + ")")
            Log.d(TAG, "onImageAvailable(mState=$mState, mQrState=$mQrState)")
            if (mCaptureSession == null) {
                Log.d("CAPTURE_SESSION", "NULL")
            } else {
                Log.d("CAPTURE_SESSION", "EXIST")
            }
            try {
                image = reader.acquireNextImage()
                val buffer = image!!.planes[0].buffer
                data = ByteArray(buffer.remaining())
                buffer.get(data)
                val width = image!!.width
                val height = image!!.height
                Log.d(TAG, "availableImageSize(" + width + "x" + height + ")")

                val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
                val bitmap = BinaryBitmap(HybridBinarizer(source))
                Log.d(TAG, "convertedBitmapSize(" + bitmap.width + "x" + bitmap.height + ")")

                if (mQrState == STATE_FIND_QRCODE) {
                    mQrState = STATE_DECODE_PROGRESS
                    val imageAsyncTask = ImageAsyncTask()
                    imageAsyncTask.execute(bitmap)
                }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            } finally {
                if (image != null) {
                    image!!.close()
                }
            }
        }
    }

    private val callbackHandler = object : CallbackHandler {
        override fun pictureCallback() {
            Log.d(TAG, "pictureCallback($mState)")
            try {
                val activity = activity
                if (null == activity || null == mCameraDevice) {
                    return
                }
                // This is the CaptureRequest.Builder that we use to take a picture.
                val captureBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                captureBuilder.addTarget(mImageReader!!.surface)

                // Use the same AE and AF modes as the preview.
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

                // Orientation
                val rotation = activity.windowManager.defaultDisplay.rotation
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation))

                val CaptureCallback = object : CameraCaptureSession.CaptureCallback() {

                    override fun onCaptureCompleted(session: CameraCaptureSession,
                                                    request: CaptureRequest,
                                                    result: TotalCaptureResult) {
                        Log.d(TAG, "result.getFrameNumber() : " + result.frameNumber)
                        //showToast("Saved: " + mFile);
                        unlockFocus()
                    }
                }

                mCaptureSession!!.stopRepeating()
                mCaptureSession!!.capture(captureBuilder.build(), CaptureCallback, null)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }

        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        Log.d(TAG, "onCreateView")
        return inflater.inflate(R.layout.fragment_camera2_basic, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated")
        view.findViewById<View>(R.id.picture).setOnClickListener(this)
        mTextureView = view.findViewById<View>(R.id.texture) as AutoFitTextureView
        mQrState = STATE_FIND_QRCODE
        decodeState = STATE_FIND_QRCODE
    }

    override fun onClick(view: View) {
        if (view.id == R.id.picture) {
            takePicture()
        }
    }

    override fun onResume() {
        Log.d(TAG, "onResume()")
        super.onResume()
        startBackgroundThread()
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView!!.isAvailable) {
            openCamera(mTextureView!!.width, mTextureView!!.height)
        } else {
            mTextureView!!.surfaceTextureListener = mSurfaceTextureListener
        }
    }

    override fun onPause() {
        Log.d(TAG, "onPause()")
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    private fun startBackgroundThread() {
        Log.d(TAG, "startBackgroundThread....")
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        Log.d(TAG, "stopBackgroundThread....")
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

    }

    //    private void requestCameraPermission() {
    //        if (FragmentCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
    //            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
    //        } else {
    //            FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
    //                    REQUEST_CAMERA_PERMISSION);
    //        }
    //    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(childFragmentManager, FRAGMENT_DIALOG)
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    /**
     * Initiate a still image capture.
     */
    private fun takePicture() {
        lockFocus()
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private fun lockFocus() {
        Log.d(TAG, "lockFocus()")
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START)
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK
            Log.d(TAG, "mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);")
            mCaptureSession!!.capture(mPreviewRequestBuilder!!.build(), mCaptureCallback,
                    mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private fun setUpCameraOutputs(width: Int, height: Int) {
        Log.d(TAG, "setUpCameraOutputs. width:$width, height:$height")
        val activity = activity
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }

                val map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue

                // For still image captures, we use the largest available size.
                /*Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, 2);*/
                val largest = Collections.max(
                        Arrays.asList(*map.getOutputSizes(ImageFormat.YUV_420_888)),
                        CompareSizesByArea())
                // 켭처 영역 생성(사이즈, 포맷, 최대 이미지 수)
                //mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                mImageReader = ImageReader.newInstance(1920, 1080,
                        ImageFormat.YUV_420_888, 2)
                mImageReader!!.setOnImageAvailableListener(
                        mOnImageAvailableListener, mBackgroundHandler)

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                val displayRotation = activity.windowManager.defaultDisplay.rotation

                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                var swappedDimensions = false
                when (displayRotation) {
                    Surface.ROTATION_0, Surface.ROTATION_180 -> if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                        swappedDimensions = true
                    }
                    Surface.ROTATION_90, Surface.ROTATION_270 -> if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                        swappedDimensions = true
                    }
                    else -> Log.e(TAG, "Display rotation is invalid: $displayRotation")
                }

                val displaySize = Point()
                activity.windowManager.defaultDisplay.getSize(displaySize)
                var rotatedPreviewWidth = width
                var rotatedPreviewHeight = height
                var maxPreviewWidth = displaySize.x
                var maxPreviewHeight = displaySize.y

                if (swappedDimensions) {
                    rotatedPreviewWidth = height
                    rotatedPreviewHeight = width
                    maxPreviewWidth = displaySize.y
                    maxPreviewHeight = displaySize.x
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest)

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                val orientation = resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView!!.setAspectRatio(
                            mPreviewSize!!.width, mPreviewSize!!.height)
                } else {
                    mTextureView!!.setAspectRatio(
                            mPreviewSize!!.height, mPreviewSize!!.width)
                }
                Log.d(TAG, "width:" + mPreviewSize!!.width + ",height:" + mPreviewSize!!.height)
                // Check if the flash is supported.
                //Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                //mFlashSupported = available == null ? false : available;

                mCameraId = cameraId
                return
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(childFragmentManager, FRAGMENT_DIALOG)
        }

    }

    /**
     * Opens the camera specified by [Camera2BasicFragment.mCameraId].
     */
    private fun openCamera(width: Int, height: Int) {
        Log.d(TAG, "openCamera($width,$height)")
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            //requestCameraPermission();
            return
        }
        setUpCameraOutputs(width, height)
        configureTransform(width, height)
        val activity = activity
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            Log.d(TAG, "openCamera.manager.openCamera")
            manager.openCamera(mCameraId!!, mStateCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }

    }

    /**
     * Closes the current [CameraDevice].
     */
    private fun closeCamera() {
        Log.d(TAG, "closeCamera()")
        try {
            mCameraOpenCloseLock.acquire()
            if (null != mCaptureSession) {
                mCaptureSession!!.close()
                mCaptureSession = null
            }
            if (null != mCameraDevice) {
                mCameraDevice!!.close()
                mCameraDevice = null
            }
            if (null != mImageReader) {
                mImageReader!!.close()
                mImageReader = null
            }
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */
    private fun createCameraPreviewSession() {
        try {
            val texture = mTextureView!!.surfaceTexture!!

            // We configure the size of default buffer to be the size of camera preview we want.
            Log.d(TAG, "defaultBufferSize(width:" + mPreviewSize!!.width + ", height:" + mPreviewSize!!.height + ")")
            texture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)

            val surface = Surface(texture)
            val mImageSurface = mImageReader!!.surface
            mPreviewRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder!!.addTarget(mImageSurface)
            mPreviewRequestBuilder!!.addTarget(surface)

            //mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
            mCameraDevice!!.createCaptureSession(Arrays.asList(mImageSurface, surface),
                    object : CameraCaptureSession.StateCallback() {

                        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                            if (null == mCameraDevice) return

                            mCaptureSession = cameraCaptureSession
                            try {
                                mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

                                mPreviewRequest = mPreviewRequestBuilder!!.build()
                                Log.d("REPEAT_CAPTURE_SESSION", "REPEAT REQUEST")
                                mCaptureSession!!.setRepeatingRequest(mPreviewRequest!!,
                                        mCaptureCallback, mBackgroundHandler)
                                /*mCaptureSession.capture(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);*/
                            } catch (e: CameraAccessException) {
                                e.printStackTrace()
                            }

                        }

                        override fun onConfigureFailed(
                                cameraCaptureSession: CameraCaptureSession) {
                            showToast("Failed")
                        }
                    }, null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        Log.d(TAG, "configureTransform. viewWidth:$viewWidth, viewHeight$viewHeight")
        val activity = activity
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return
        }
        val rotation = activity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, mPreviewSize!!.height.toFloat(), mPreviewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                    viewHeight.toFloat() / mPreviewSize!!.height,
                    viewWidth.toFloat() / mPreviewSize!!.width)
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        mTextureView!!.setTransform(matrix)
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in [.mCaptureCallback] from [.lockFocus].
     */
    private fun runPrecaptureSequence() {
        Log.d(TAG, "runPrecaptureSequence()")
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE
            mCaptureSession!!.capture(mPreviewRequestBuilder!!.build(), mCaptureCallback,
                    mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private fun getOrientation(rotation: Int): Int {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private fun unlockFocus() {
        Log.d(TAG, "unlockFocus")
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            mCaptureSession!!.capture(mPreviewRequestBuilder!!.build(), mCaptureCallback,
                    mBackgroundHandler)

            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW
            mCaptureSession!!.setRepeatingRequest(mPreviewRequest!!, mCaptureCallback,
                    mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    inner class ImageAsyncTask : AsyncTask<BinaryBitmap, Void, Void>() {

        private val multiFormatReader = MultiFormatReader()
        private var image: BinaryBitmap? = null
        private var rawResult: Result? = null

        override fun onPreExecute() {
            super.onPreExecute()
        }

        override fun doInBackground(vararg bitmaps: BinaryBitmap): Void? {
            this.image = bitmaps[0]
            rawResult = null

            if (image == null) {
                Log.d(TAG, "ImageAsyncTask.doInBackground >>> image is null")
                return null
            }
            if (mQrState != STATE_DECODE_PROGRESS) {
                Log.d(TAG, "ImageAsyncTask.doInBackground >>> STATE is NOT DECODE_PROGRESS!!")
                return null
            }

            try {
                Log.d(TAG, (image!!.width / 4).toString() + "," + image!!.height / 4 + "," + image!!.width / 2 + "," + image!!.height / 2)
                image = image!!.crop(image!!.width / 4, image!!.height / 4, image!!.width / 2, image!!.height / 2)
                //image = image.crop(478, 286, 963, 506);
                Log.d("ImageAsyncTask", "doInBackground.decode BinaryBitmap(" + image!!.width + "x" + image!!.height + ")")
                rawResult = multiFormatReader.decodeWithState(image)
                Log.d("ImageAsyncTask", "doInBackground.decode complete")
                if (rawResult != null) {
                    mQrState = STATE_QRCODE_EXIST
                    Log.d(TAG, "ImageAsyncTask.doInBackground.rawResult: " + rawResult!!.toString())
                }
            } catch (e: NotFoundException) {
                mQrState = STATE_FIND_QRCODE
                //continue
            } finally {
                multiFormatReader.reset()
            }
            return null
        }

        override fun onPostExecute(aVoid: Void?) {
            if (mQrState == STATE_QRCODE_EXIST) {
                val activity = activity
                activity?.onBackPressed()
            }
            super.onPostExecute(aVoid)
        }
    }

    fun imageAlert(bitmap: Bitmap) {
        ImageDialog(bitmap).show(childFragmentManager, FRAGMENT_DIALOG)
    }

    @SuppressLint("ValidFragment")
    class ImageDialog(private val mBitmap: Bitmap?) : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
            val layoutInflater = activity
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val layoutDialog = layoutInflater.inflate(R.layout.fragment_camera_preview, null)
            val mImage2 = layoutDialog.findViewById<View>(R.id.imageView) as ImageView
            if (mBitmap != null) {
                Log.d(TAG, "setImageBitmap()")
                mImage2.setImageBitmap(mBitmap)
            } else {
                Log.d(TAG, "bImage is null2")
            }
            return AlertDialog.Builder(activity)
                    .setView(layoutDialog)
                    .setPositiveButton(android.R.string.ok) { dialog, which -> }
                    .create()
        }
    }

    /**
     * Compares two `Size`s based on their areas.
     */
    internal class CompareSizesByArea : Comparator<Size> {

        override fun compare(lhs: Size, rhs: Size): Int {
            // We cast here to ensure the multiplications won't overflow
            return java.lang.Long.signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
        }

    }

    /**
     * Shows an error message dialog.
     */
    class ErrorDialog : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
            val activity = activity
            return AlertDialog.Builder(activity)
                    .setMessage(arguments.getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok) { dialogInterface, i -> activity.finish() }
                    .create()
        }

        companion object {

            private val ARG_MESSAGE = "message"

            fun newInstance(message: String): ErrorDialog {
                val dialog = ErrorDialog()
                val args = Bundle()
                args.putString(ARG_MESSAGE, message)
                dialog.arguments = args
                return dialog
            }
        }

    }

    /**
     * Shows a [Toast] on the UI thread.
     *
     * @param text The message to show
     */
    private fun showToast(text: String) {
        val activity = activity
        activity?.runOnUiThread { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show() }
    }

    companion object {

        private val TAG = Camera2BasicFragment::class.java.simpleName

        fun newInstance(): Camera2BasicFragment {
            return Camera2BasicFragment()
        }

        /**
         * Conversion from screen rotation to JPEG orientation.
         */
        private val ORIENTATIONS = SparseIntArray()
        private val REQUEST_CAMERA_PERMISSION = 1
        private val FRAGMENT_DIALOG = "dialog"

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        private val STATE_PREVIEW = 0
        private val STATE_WAITING_LOCK = 1
        private val STATE_WAITING_PRECAPTURE = 2
        private val STATE_WAITING_NON_PRECAPTURE = 3
        private val STATE_PICTURE_TAKEN = 4

        private val STATE_FIND_QRCODE = 0
        private val STATE_DECODE_PROGRESS = 1
        private val STATE_QRCODE_EXIST = 2

        /**
         * Max preview width, height that is guaranteed by Camera2 API
         */
        private val MAX_PREVIEW_WIDTH = 1920
        private val MAX_PREVIEW_HEIGHT = 1080
        /**
         * Given `choices` of `Size`s supported by a camera, choose the smallest one that
         * is at least as large as the respective texture view size, and that is at most as large as the
         * respective max size, and whose aspect ratio matches with the specified value. If such size
         * doesn't exist, choose the largest one that is at most as large as the respective max size,
         * and whose aspect ratio matches with the specified value.
         *
         * @param choices           The list of sizes that the camera supports for the intended output
         * class
         * @param textureViewWidth  The width of the texture view relative to sensor coordinate
         * @param textureViewHeight The height of the texture view relative to sensor coordinate
         * @param maxWidth          The maximum width that can be chosen
         * @param maxHeight         The maximum height that can be chosen
         * @param aspectRatio       The aspect ratio
         * @return The optimal `Size`, or an arbitrary one if none were big enough
         */
        private fun chooseOptimalSize(choices: Array<Size>, textureViewWidth: Int,
                                      textureViewHeight: Int, maxWidth: Int, maxHeight: Int, aspectRatio: Size): Size {

            // Collect the supported resolutions that are at least as big as the preview Surface
            val bigEnough = ArrayList<Size>()
            // Collect the supported resolutions that are smaller than the preview Surface
            val notBigEnough = ArrayList<Size>()
            val w = aspectRatio.width
            val h = aspectRatio.height
            for (option in choices) {
                if (option.width <= maxWidth && option.height <= maxHeight &&
                        option.height == option.width * h / w) {
                    if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                }
            }

            // Pick the smallest of those big enough. If there is no one big enough, pick the
            // largest of those not big enough.
            if (bigEnough.size > 0) {
                return Collections.min(bigEnough, CompareSizesByArea())
            } else if (notBigEnough.size > 0) {
                return Collections.max(notBigEnough, CompareSizesByArea())
            } else {
                Log.e(TAG, "Couldn't find any suitable preview size")
                return choices[0]
            }
        }

        private var mQrState: Int = 0

        private var decodeState: Int = 0
        private val imgMax = false
    }
}
