/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.camera.integration.viewfinder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.ExifInterface
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.impl.utils.CompareSizesByArea
import androidx.camera.core.impl.utils.futures.FutureCallback
import androidx.camera.core.impl.utils.futures.Futures
import androidx.camera.viewfinder.CameraViewfinder
import androidx.camera.viewfinder.ViewfinderSurfaceRequest
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.google.common.base.Objects
import com.google.common.util.concurrent.ListenableFuture
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Fold aware fragment for {@link CameraViewfinder}.
 */
class CameraViewfinderFoldableFragment : Fragment(), View.OnClickListener,
    ActivityCompat.OnRequestPermissionsResultCallback {

    private lateinit var cameraThread: HandlerThread

    private lateinit var cameraHandler: Handler

    private lateinit var imageReaderThread: HandlerThread

    private lateinit var imageReaderHandler: Handler

    private val cameraOpenCloseLock = Semaphore(1)

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener {
        cameraHandler.post(
            ImageSaver(
                it.acquireNextImage(),
                file
            )
        )
    }

    private lateinit var camera: CameraDevice

    private lateinit var characteristics: CameraCharacteristics

    private lateinit var cameraId: String

    private lateinit var cameraManager: CameraManager

    private lateinit var cameraViewfinder: CameraViewfinder

    private lateinit var file: File

    private lateinit var imageReader: ImageReader

    private lateinit var relativeOrientation: OrientationLiveData

    private lateinit var session: CameraCaptureSession

    private lateinit var surfaceListenableFuture: ListenableFuture<Surface>

    private lateinit var windowInfoTracker: WindowInfoTracker

    private var activeWindowLayoutInfo: WindowLayoutInfo? = null

    private var isViewfinderInLeftTop = true

    private var viewfinderSurfaceRequest: ViewfinderSurfaceRequest? = null

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.fragment_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onPrepareOptionsMenu(menu: Menu) {
        val title = "Current impl: ${cameraViewfinder.implementationMode}"
        menu.findItem(R.id.implementationMode)?.title = title
        super.onPrepareOptionsMenu(menu)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.implementationMode -> {
                cameraViewfinder.implementationMode =
                    when (cameraViewfinder.implementationMode) {
                        CameraViewfinder.ImplementationMode.PERFORMANCE ->
                            CameraViewfinder.ImplementationMode.COMPATIBLE
                        else -> CameraViewfinder.ImplementationMode.PERFORMANCE
                    }
                closeCamera()
                sendSurfaceRequest(false)
            }
            R.id.fitCenter -> cameraViewfinder.scaleType = CameraViewfinder.ScaleType.FIT_CENTER
            R.id.fillCenter -> cameraViewfinder.scaleType = CameraViewfinder.ScaleType.FILL_CENTER
            R.id.fitStart -> cameraViewfinder.scaleType = CameraViewfinder.ScaleType.FIT_START
            R.id.fitEnd -> cameraViewfinder.scaleType = CameraViewfinder.ScaleType.FIT_END
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera_view_finder_foldable, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.picture).setOnClickListener(this)
        view.findViewById<View>(R.id.toggle).setOnClickListener(this)
        view.findViewById<View>(R.id.bitmap).setOnClickListener(this)
        view.findViewById<View>(R.id.switch_area).setOnClickListener(this)

        cameraViewfinder = view.findViewById(R.id.view_finder)
        windowInfoTracker = WindowInfoTracker.getOrCreate(requireContext())
        cameraManager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    override fun onResume() {
        super.onResume()
        cameraThread = HandlerThread("CameraThread").apply { start() }
        cameraHandler = Handler(cameraThread.looper)
        imageReaderThread = HandlerThread("ImageThread").apply { start() }
        imageReaderHandler = Handler(imageReaderThread.looper)

        // Request Permission
        val cameraPermission = activity?.let {
            ContextCompat.checkSelfPermission(it, Manifest.permission.CAMERA)
        }
        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
            return
        }

        val storagePermission = activity?.let {
            ContextCompat.checkSelfPermission(it, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (storagePermission != PackageManager.PERMISSION_GRANTED) {
            requestStoragePermission()
            return
        }

        sendSurfaceRequest(false)

        lifecycleScope.launch {
            windowInfoTracker.windowLayoutInfo(requireActivity())
                .collect { newLayoutInfo ->
                    Log.d(TAG, "newLayoutInfo: $newLayoutInfo")
                    activeWindowLayoutInfo = newLayoutInfo
                    adjustPreviewByFoldingState()
                }
        }
    }

    override fun onPause() {
        closeCamera()
        cameraThread.quitSafely()
        imageReaderThread.quitSafely()
        viewfinderSurfaceRequest?.markSurfaceSafeToRelease()
        super.onPause()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.picture -> takePicture()
            R.id.toggle -> toggleCamera()
            R.id.bitmap -> saveBitmap()
            R.id.switch_area -> {
                isViewfinderInLeftTop = !isViewfinderInLeftTop
                adjustPreviewByFoldingState()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            ConfirmationDialog().show(childFragmentManager, FRAGMENT_DIALOG)
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }
    }

    @Suppress("DEPRECATION")
    private fun requestStoragePermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ConfirmationDialog().show(childFragmentManager, FRAGMENT_DIALOG)
        } else {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CAMERA_PERMISSION)
        }
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                    .show(childFragmentManager, FRAGMENT_DIALOG)
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    // ------------- Create Capture Session --------------
    private fun sendSurfaceRequest(toggleCamera: Boolean) {
        cameraViewfinder.post {
            if (isAdded && context != null) {
                setUpCameraOutputs(toggleCamera)

                val context = requireContext()
                surfaceListenableFuture =
                    cameraViewfinder.requestSurfaceAsync(viewfinderSurfaceRequest!!)

                Futures.addCallback(surfaceListenableFuture, object : FutureCallback<Surface?> {
                    override fun onSuccess(surface: Surface?) {
                        Log.d(TAG, "request onSurfaceAvailable surface = $surface")
                        if (surface != null) {
                            initializeCamera(surface)
                        }
                    }

                    override fun onFailure(t: Throwable) {
                        Log.e(TAG, "request onSurfaceClosed")
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        }
    }

    private fun setUpCameraOutputs(toggleCamera: Boolean) {
        try {
            for (cameraId in cameraManager.cameraIdList) {
                characteristics = cameraManager.getCameraCharacteristics(cameraId)
                relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
                    observe(viewLifecycleOwner, Observer { orientation ->
                        Log.d(TAG, "Orientation changed: $orientation")
                    })
                }

                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                // Toggle the front and back camera
                if (toggleCamera) {
                    val currentFacing: Int? = cameraManager.getCameraCharacteristics(this.cameraId)
                        .get<Int>(CameraCharacteristics.LENS_FACING)
                    if (Objects.equal(currentFacing, facing)) {
                        continue
                    }
                }

                val map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                ) ?: continue

                // For still image captures, we use the largest available size.
                val largest = Collections.max(
                    /* coll = */ Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)),
                    /* comp = */ CompareSizesByArea()
                )
                imageReader = ImageReader.newInstance(
                    largest.width, largest.height,
                    ImageFormat.JPEG, /*maxImages*/ 2
                ).apply {
                    setOnImageAvailableListener(onImageAvailableListener, imageReaderHandler)
                }

                this.cameraId = cameraId
                this.characteristics = cameraManager.getCameraCharacteristics(cameraId)
                viewfinderSurfaceRequest = ViewfinderSurfaceRequest(largest, characteristics)

                Log.d(TAG, "viewfinderSurfaceRequest created = $viewfinderSurfaceRequest")
                return
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    private fun initializeCamera(surface: Surface) = lifecycleScope.launch(Dispatchers.IO) {
        cameraOpenCloseLock.acquire()

        // Open the selected camera
        camera = openCamera(cameraManager, cameraId, cameraHandler)

        // Creates list of Surfaces where the camera will output frames
        val targets = listOf(surface, imageReader.surface)

        try {
            // Start a capture session using our open camera and list of Surfaces where frames will go
            session = createCaptureSession(camera, targets, cameraHandler)

            val captureRequest = camera.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }

            // This will keep sending the capture request as frequently as possible until the
            // session is torn down or session.stopRepeating() is called
            session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "createCaptureSession CameraAccessException")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "createCaptureSession IllegalArgumentException")
        } catch (e: SecurityException) {
            Log.e(TAG, "createCaptureSession SecurityException")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        try {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraOpenCloseLock.release()
                    cont.resume(device)
                }

                override fun onDisconnected(device: CameraDevice) {
                    Log.w(TAG, "Camera $cameraId has been disconnected")
                    cameraOpenCloseLock.release()
                }

                override fun onError(device: CameraDevice, error: Int) {
                    val msg = when (error) {
                        ERROR_CAMERA_DEVICE -> "Fatal (device)"
                        ERROR_CAMERA_DISABLED -> "Device policy"
                        ERROR_CAMERA_IN_USE -> "Camera in use"
                        ERROR_CAMERA_SERVICE -> "Fatal (service)"
                        ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                        else -> "Unknown"
                    }
                    Log.e(TAG, "Camera $cameraId error: ($error) $msg")
                }
            }, handler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "openCamera CameraAccessException")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "openCamera IllegalArgumentException")
        } catch (e: SecurityException) {
            Log.e(TAG, "openCamera SecurityException")
        }
    }

    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            if (::session.isInitialized) {
                session.close()
            }

            if (::camera.isInitialized) {
                camera.close()
            }

            if (::imageReader.isInitialized) {
                imageReader.close()
            }
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->

        // Create a capture session using the predefined targets; this also involves defining the
        // session state callback to be notified of when the session is ready
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    // ------------- Toggle Camera -----------
    private fun toggleCamera() {
        closeCamera()
        sendSurfaceRequest(true)
    }

    // ------------- Save Bitmap ------------
    private fun saveBitmap() {
        val bitmap: Bitmap? = cameraViewfinder.bitmap
        bitmap?.let { saveBitmapAsFile(it) }
    }

    private fun saveBitmapAsFile(bitmap: Bitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            val displayName = dateFormat.format(Date()) + "_ViewfinderBitmap.png"
            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            val resolver = requireContext().contentResolver
            val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = resolver.insert(contentUri, values)
            try {
                val fos = resolver.openOutputStream(uri!!)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos!!.close()
                showToast("Saved: $displayName")
            } catch (e: IOException) {
                Log.e(
                    TAG, "saveBitmapAsFile IOException message = " + e.message
                )
            }
        } else {
            try {
                val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                val file = File(
                    getBatchDirectoryName(),
                    dateFormat.format(Date()) + "_ViewfinderBitmap.png"
                )
                val fos = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.close()
                showToast("Saved: $file")
            } catch (e: IOException) {
                Log.e(
                    TAG, "saveBitmapAsFile IOException message = " + e.message
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getBatchDirectoryName(): String {
        val appFolderPath =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
        val dir = File(appFolderPath)
        return if (!dir.exists() && !dir.mkdirs()) {
            ""
        } else appFolderPath
    }

    private fun showToast(text: String) {
        val activity: Activity? = activity
        activity?.runOnUiThread {
            Toast.makeText(
                activity,
                text,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private class ImageSaver(
        private val image: Image,
        private val file: File
    ) :
        Runnable {
        override fun run() {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer[bytes]
            try {
                image.use {
                    FileOutputStream(file).use { output ->
                        output.write(
                            bytes
                        )
                    }
                }
            } catch (e: IOException) {
                Log.e(
                    TAG, "ImageSaver CameraAccessException message = " + e.message
                )
            }
        }
    }

    // ------------- Fold-aware -------------
    private fun adjustPreviewByFoldingState() {
        val btnSwitchArea = requireView().findViewById<View>(R.id.switch_area)
        activeWindowLayoutInfo?.displayFeatures?.firstOrNull { it is FoldingFeature }
            ?.let {
                val rect = getFeaturePositionInViewRect(
                    it,
                    cameraViewfinder.parent as View
                ) ?: return@let
                val foldingFeature = it as FoldingFeature
                if (foldingFeature.state == FoldingFeature.State.HALF_OPENED) {
                    btnSwitchArea.visibility = View.VISIBLE
                    when (foldingFeature.orientation) {
                        FoldingFeature.Orientation.VERTICAL -> {
                            if (isViewfinderInLeftTop) {
                                cameraViewfinder.moveToLeftOf(rect)
                                val blankAreaWidth =
                                    (btnSwitchArea.parent as View).width - rect.right
                                btnSwitchArea.x = rect.right +
                                    (blankAreaWidth - btnSwitchArea.width) / 2f
                                btnSwitchArea.y =
                                    (cameraViewfinder.height - btnSwitchArea.height) / 2f
                            } else {
                                cameraViewfinder.moveToRightOf(rect)
                                btnSwitchArea.x =
                                    (rect.left - btnSwitchArea.width) / 2f
                                btnSwitchArea.y =
                                    (cameraViewfinder.height - btnSwitchArea.height) / 2f
                            }
                        }
                        FoldingFeature.Orientation.HORIZONTAL -> {
                            if (isViewfinderInLeftTop) {
                                cameraViewfinder.moveToTopOf(rect)
                                val blankAreaHeight =
                                    (btnSwitchArea.parent as View).height - rect.bottom
                                btnSwitchArea.x =
                                    (cameraViewfinder.width - btnSwitchArea.width) / 2f
                                btnSwitchArea.y = rect.bottom +
                                    (blankAreaHeight - btnSwitchArea.height) / 2f
                            } else {
                                cameraViewfinder.moveToBottomOf(rect)
                                btnSwitchArea.x =
                                    (cameraViewfinder.width - btnSwitchArea.width) / 2f
                                btnSwitchArea.y =
                                    (rect.top - btnSwitchArea.height) / 2f
                            }
                        }
                    }
                } else {
                    cameraViewfinder.restore()
                    btnSwitchArea.x = 0f
                    btnSwitchArea.y = 0f
                    btnSwitchArea.visibility = View.INVISIBLE
                }
            }
    }

    private fun View.moveToLeftOf(foldingFeatureRect: Rect) {
        x = 0f
        layoutParams = layoutParams.apply {
            width = foldingFeatureRect.left
        }
    }

    private fun View.moveToRightOf(foldingFeatureRect: Rect) {
        x = foldingFeatureRect.left.toFloat()
        layoutParams = layoutParams.apply {
            width = (parent as View).width - foldingFeatureRect.left
        }
    }

    private fun View.moveToTopOf(foldingFeatureRect: Rect) {
        y = 0f
        layoutParams = layoutParams.apply {
            height = foldingFeatureRect.top
        }
    }

    private fun View.moveToBottomOf(foldingFeatureRect: Rect) {
        y = foldingFeatureRect.top.toFloat()
        layoutParams = layoutParams.apply {
            height = (parent as View).height - foldingFeatureRect.top
        }
    }

    private fun View.restore() {
        // Restore to full view
        layoutParams = layoutParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        y = 0f
        x = 0f
    }

    private fun getFeaturePositionInViewRect(
        displayFeature: DisplayFeature,
        view: View,
        includePadding: Boolean = true
    ): Rect? {
        // The location of the view in window to be in the same coordinate space as the feature.
        val viewLocationInWindow = IntArray(2)
        view.getLocationInWindow(viewLocationInWindow)

        // Intersect the feature rectangle in window with view rectangle to clip the bounds.
        val viewRect = Rect(
            viewLocationInWindow[0], viewLocationInWindow[1],
            viewLocationInWindow[0] + view.width, viewLocationInWindow[1] + view.height
        )

        // Include padding if needed
        if (includePadding) {
            viewRect.left += view.paddingLeft
            viewRect.top += view.paddingTop
            viewRect.right -= view.paddingRight
            viewRect.bottom -= view.paddingBottom
        }

        val featureRectInView = Rect(displayFeature.bounds)
        val intersects = featureRectInView.intersect(viewRect)
        if ((featureRectInView.width() == 0 && featureRectInView.height() == 0) ||
            !intersects
        ) {
            return null
        }

        // Offset the feature coordinates to view coordinate space start point
        featureRectInView.offset(-viewLocationInWindow[0], -viewLocationInWindow[1])

        return featureRectInView
    }

    private fun takePicture() {
        // Perform I/O heavy operations in a different scope
        lifecycleScope.launch(Dispatchers.IO) {
            takePictureInternal().use { result ->
                Log.d(TAG, "Result received: $result")

                // Save the result to disk
                val output = saveResult(result)
                withContext(Dispatchers.Main) {
                    showToast("Image saved: ${output.absolutePath}")
                }

                // If the result is a JPEG file, update EXIF metadata with orientation info
                if (output.extension == "jpg") {
                    val exif = ExifInterface(output.absolutePath)
                    exif.setAttribute(
                        ExifInterface.TAG_ORIENTATION, result.orientation.toString()
                    )
                    exif.saveAttributes()
                    Log.d(TAG, "EXIF metadata saved: ${output.absolutePath}")
                }
            }
        }
    }

    private suspend fun takePictureInternal():
        CombinedCaptureResult = suspendCoroutine { cont ->

        // Flush any images left in the image reader
        @Suppress("ControlFlowWithEmptyBody")
        while (imageReader.acquireNextImage() != null) {
        }

        // Start a new image queue
        val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            Log.d(TAG, "Image available in queue: ${image.timestamp}")
            imageQueue.add(image)
        }, imageReaderHandler)

        val captureRequest = session.device.createCaptureRequest(
            CameraDevice.TEMPLATE_STILL_CAPTURE).apply { addTarget(imageReader.surface) }
        session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {

            override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long
            ) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                Log.d(TAG, "Capture result received: $resultTimestamp")

                // Set a timeout in case image captured is dropped from the pipeline
                val exc = TimeoutException("Image dequeuing took too long")
                val timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                imageReaderHandler.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                // Loop in the coroutine's context until an image with matching timestamp comes
                // We need to launch the coroutine context again because the callback is done in
                //  the handler provided to the `capture` method, not in our coroutine context
                @Suppress("BlockingMethodInNonBlockingContext")
                lifecycleScope.launch(cont.context) {
                    while (true) {

                        // Dequeue images while timestamps don't match
                        val image = imageQueue.take()
                        // if (image.timestamp != resultTimestamp) continue
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            image.format != ImageFormat.DEPTH_JPEG &&
                            image.timestamp != resultTimestamp) continue
                        Log.d(TAG, "Matching image dequeued: ${image.timestamp}")

                        // Unset the image reader listener
                        imageReaderHandler.removeCallbacks(timeoutRunnable)
                        imageReader.setOnImageAvailableListener(null, null)

                        // Clear the queue of images, if there are left
                        while (imageQueue.size > 0) {
                            imageQueue.take().close()
                        }

                        // Compute EXIF orientation metadata
                        val rotation = relativeOrientation.value ?: 0
                        val mirrored = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                            CameraCharacteristics.LENS_FACING_FRONT
                        val exifOrientation = computeExifOrientation(rotation, mirrored)

                        // Build the result and resume progress
                        cont.resume(CombinedCaptureResult(
                            image, result, exifOrientation, imageReader.imageFormat))

                        // There is no need to break out of the loop, this coroutine will suspend
                    }
                }
            }
        }, cameraHandler)
    }

    /** Helper function used to save a [CombinedCaptureResult] into a [File] */
    private fun saveResult(result: CombinedCaptureResult): File {
        when (result.format) {

            // When the format is JPEG or DEPTH JPEG we can simply save the bytes as-is
            ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                val buffer = result.image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                try {
                    val output = createFile("jpg")
                    FileOutputStream(output).use { it.write(bytes) }
                    return output
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write JPEG image to file", exc)
                    throw exc
                }
            }

            // When the format is RAW we use the DngCreator utility library
            ImageFormat.RAW_SENSOR -> {
                val dngCreator = DngCreator(characteristics, result.metadata)
                try {
                    val output = createFile("dng")
                    FileOutputStream(output).use { dngCreator.writeImage(it, result.image) }
                    return output
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write DNG image to file", exc)
                    throw exc
                }
            }

            // No other formats are supported by this sample
            else -> {
                val exc = RuntimeException("Unknown image format: ${result.image.format}")
                Log.e(TAG, exc.message, exc)
                throw exc
            }
        }
    }

    private fun createFile(extension: String): File {
        val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
        return File(getBatchDirectoryName(), "IMG_${sdf.format(Date())}.$extension")
    }

    private fun computeExifOrientation(rotationDegrees: Int, mirrored: Boolean) = when {
        rotationDegrees == 0 && !mirrored -> ExifInterface.ORIENTATION_NORMAL
        rotationDegrees == 0 && mirrored -> ExifInterface.ORIENTATION_FLIP_HORIZONTAL
        rotationDegrees == 180 && !mirrored -> ExifInterface.ORIENTATION_ROTATE_180
        rotationDegrees == 180 && mirrored -> ExifInterface.ORIENTATION_FLIP_VERTICAL
        rotationDegrees == 270 && mirrored -> ExifInterface.ORIENTATION_TRANSVERSE
        rotationDegrees == 90 && !mirrored -> ExifInterface.ORIENTATION_ROTATE_90
        rotationDegrees == 90 && mirrored -> ExifInterface.ORIENTATION_TRANSPOSE
        rotationDegrees == 270 && mirrored -> ExifInterface.ORIENTATION_ROTATE_270
        rotationDegrees == 270 && !mirrored -> ExifInterface.ORIENTATION_TRANSVERSE
        else -> ExifInterface.ORIENTATION_UNDEFINED
    }

    class ErrorDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(requireActivity())
                .setMessage(requireArguments().getString(ARG_MESSAGE))
                .setPositiveButton(android.R.string.ok) { _, _ -> requireActivity().finish() }
                .create()

        companion object {
            @JvmStatic private val ARG_MESSAGE = "message"
            @JvmStatic fun newInstance(message: String): ErrorDialog = ErrorDialog().apply {
                arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
            }
        }
    }

    class ConfirmationDialog : DialogFragment() {
        @Suppress("DEPRECATION")
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(requireActivity())
                .setMessage(R.string.request_permission)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    requireParentFragment().requestPermissions(arrayOf(Manifest.permission.CAMERA),
                        REQUEST_CAMERA_PERMISSION)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    requireParentFragment().activity?.finish()
                }
                .create()
    }

    companion object {
        private const val TAG = "CameraViewfinder"

        private const val REQUEST_CAMERA_PERMISSION: Int = 1

        private const val FRAGMENT_DIALOG = "dialog"

        private const val IMAGE_BUFFER_SIZE: Int = 3

        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000

        data class CombinedCaptureResult(
            val image: Image,
            val metadata: CaptureResult,
            val orientation: Int,
            val format: Int
        ) : Closeable {
            override fun close() = image.close()
        }
    }
}