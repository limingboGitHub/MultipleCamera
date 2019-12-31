package com.example.lenovo.multiplecamera.camera2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.*
import android.support.annotation.RequiresApi
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.Toast
import com.example.lenovo.multiplecamera.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.experimental.and

/**
 * Created by Lenovo on 2019/11/29.
 */
@RequiresApi(Build.VERSION_CODES.P)
class Camera2Utils(private val context: Context,
                   private val cameraOpenFailedCallback: CameraOpenFailedCallback){

    companion object {
        val OPEN_FAILED_NO_PERMISSION = 1
        val OPEN_FAILED_NOT_SUPPORT_MULTIPLE_CAMERA = 2
    }

    var cameraManager : CameraManager? = null
    var cameraCharacteristics : CameraCharacteristics? = null

    var cameraDevice : CameraDevice? = null
    var captureSession : CameraCaptureSession? = null

    private val previewSurfaces = ArrayList<Surface>()
    private val imageSurfaces = ArrayList<Surface>()

    var imageSize : Size? = null

    fun setPreviewSurfaces(surfaces:List<Surface>){
        previewSurfaces.addAll(surfaces)
    }

    fun selectCameraIdThenOpen() {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            cameraOpenFailedCallback.openFailed(OPEN_FAILED_NO_PERMISSION)
            return
        }

        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager?
        val cameraIds = cameraManager!!.cameraIdList
        for (cameraID in cameraIds){
            val cameraCharacteristics = cameraManager!!.getCameraCharacteristics(cameraID)
            val cameraLensFace = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
            if (cameraLensFace == CameraCharacteristics.LENS_FACING_BACK){
                val capabilities = cameraCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                if (capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT)){
                    openLogicalCamera(cameraID)
                    return
                }else if (capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)){
                    this.cameraCharacteristics = cameraCharacteristics
                    val physicalCameraIds =  cameraCharacteristics!!.physicalCameraIds

                    var index = 0
                    for (physicalCameraId in physicalCameraIds){
                        val physicalCameraCharacteristics = cameraManager!!.getCameraCharacteristics(physicalCameraId)
                        val map = physicalCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        val sizes = map.getOutputSizes(SurfaceTexture::class.java)
                        if (index == 2){
                            imageSize = sizes[0]
                        }
                        index ++
                    }

                    openPhysicalCamera(cameraID,physicalCameraIds.toTypedArray())
                    return
                }
            }
        }

        cameraOpenFailedCallback.openFailed(OPEN_FAILED_NOT_SUPPORT_MULTIPLE_CAMERA)
    }

    private fun openLogicalCamera(logicalCameraId: String){
        if (cameraManager==null)
            return
        cameraManager!!.openCamera(logicalCameraId, AsyncTask.SERIAL_EXECUTOR,object : CameraDevice.StateCallback(){
            override fun onOpened(camera: CameraDevice?) {
                if (camera!=null){
                    cameraDevice = camera

                    val mainHandler = Handler(Looper.getMainLooper())
                    val previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    val imageAvailableListener = ImageReaderListener()
                    val imageReader = ImageReader.newInstance(1920,1080,ImageFormat.DEPTH16,1)
                    imageReader.setOnImageAvailableListener(imageAvailableListener,mainHandler)
                    imageSurfaces.add(imageReader.surface)

                    previewBuilder.addTarget(previewSurfaces[0])
                    camera.createCaptureSession(listOf(previewSurfaces[0],imageReader.surface),object:CameraCaptureSession.StateCallback(){

                        override fun onConfigured(session: CameraCaptureSession?) {
                            if (session == null)
                                return
                            captureSession = session

                            session.setRepeatingRequest(previewBuilder.build(),null,null)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession?) {

                        }

                    },mainHandler)
                }
            }

            override fun onDisconnected(camera: CameraDevice?) {
                camera?.close()
            }

            override fun onError(camera: CameraDevice?, error: Int) {
                camera?.close()
            }

        })
    }

    private fun openPhysicalCamera(logicalCameraId:String, physicalCameraIds:Array<String>){
        if (cameraManager==null)
            return

        if (Build.VERSION.SDK_INT >=28){
            cameraManager!!.openCamera(logicalCameraId, AsyncTask.SERIAL_EXECUTOR,object : CameraDevice.StateCallback(){
                override fun onOpened(camera: CameraDevice?) {
                    if (camera!=null){
                        cameraDevice = camera
                        startPreview(camera,physicalCameraIds)
                    }
                }

                override fun onDisconnected(camera: CameraDevice?) {
                    camera?.close()
                }

                override fun onError(camera: CameraDevice?, error: Int) {
                    camera?.close()
                }

            })
        }else
            throw RuntimeException("only android 9.0 can open multipleCamera")
    }

    private fun startPreview(cameraDevice: CameraDevice, physicalCameraIds: Array<String>) {
        val allOutputConfigs = ArrayList<OutputConfiguration>()

        val outputConfigLogical = OutputConfiguration(previewSurfaces[0])
        allOutputConfigs.add(outputConfigLogical)

        if (physicalCameraIds.isNotEmpty()){
            val outputConfigPhysical1 = OutputConfiguration(previewSurfaces[1]).apply {
                setPhysicalCameraId(physicalCameraIds[0])
            }
            allOutputConfigs.add(outputConfigPhysical1)

            if (physicalCameraIds.size>=2 && previewSurfaces.size>=3){
                val outputConfigPhysical2 = OutputConfiguration(previewSurfaces[2]).apply {
                    setPhysicalCameraId(physicalCameraIds[1])
                }
                allOutputConfigs.add(outputConfigPhysical2)
            }
        }else
            throw RuntimeException("have no physical camera")

        //两个摄像头图片获取监听
        val imageAvailableListener = ImageReaderListener()
        val physicalOneImageReader = ImageReader.newInstance(1920,1080,ImageFormat.JPEG,1)
        physicalOneImageReader.setOnImageAvailableListener(imageAvailableListener, Handler(Looper.getMainLooper()))

        val physicalOneOutputConfig = OutputConfiguration(physicalOneImageReader.surface).apply { setPhysicalCameraId(physicalCameraIds[0]) }
        allOutputConfigs.add(physicalOneOutputConfig)
        imageSurfaces.add(physicalOneImageReader.surface)

        val physicalTwoImageReader = ImageReader.newInstance(1920,1080,ImageFormat.JPEG,1)
        physicalTwoImageReader.setOnImageAvailableListener(imageAvailableListener, Handler(Looper.getMainLooper()))
        val physicalTwoOutputConfig = OutputConfiguration(physicalTwoImageReader.surface).apply { setPhysicalCameraId(physicalCameraIds[1]) }
        allOutputConfigs.add(physicalTwoOutputConfig)
        imageSurfaces.add(physicalTwoImageReader.surface)

        val sessionConfiguration = SessionConfiguration(SessionConfiguration.SESSION_REGULAR
                ,allOutputConfigs,AsyncTask.SERIAL_EXECUTOR
                ,object :CameraCaptureSession.StateCallback(){

            override fun onConfigured(session: CameraCaptureSession?) {
                if (session!=null){
                    captureSession = session
                    val previewBuilder = session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        previewSurfaces.forEach { addTarget(it) }
                    }
                    session.setRepeatingRequest(previewBuilder.build(),null,null)
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession?) {
                Log.e("cameraTip","onConfigureFailed")
            }

        })

        cameraDevice.createCaptureSession(sessionConfiguration)
    }

    fun takePicture(){
        if (cameraDevice==null || captureSession==null)
            return

        val photoBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        photoBuilder.apply {
            imageSurfaces.forEach { addTarget(it) }
        }
        photoBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

        captureSession!!.capture(photoBuilder.build(),object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession?, request: CaptureRequest?, result: TotalCaptureResult?) {
                super.onCaptureCompleted(session, request, result)
                Toast.makeText(context,R.string.capture_success,Toast.LENGTH_SHORT).show()
            }
        },null)
    }

    fun releaseCamera(){
        if (captureSession!=null){
            captureSession!!.close()
            captureSession = null
        }

        if (cameraDevice!=null){
            cameraDevice!!.close()
            cameraDevice = null
        }
    }

    inner class ImageReaderListener : ImageReader.OnImageAvailableListener {

        @RequiresApi(api = Build.VERSION_CODES.P)
        override fun onImageAvailable(reader: ImageReader) {
            val image = reader.acquireNextImage()
            if (image == null || image.planes[0] == null) {
                return
            }
            if (image.format == ImageFormat.JPEG){
                val buffer = image.planes[0].buffer
                val imageBytes = ByteArray(buffer.remaining())
                buffer.get(imageBytes)
                buffer.clear()
                val bitmap: Bitmap? = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                val fileName = System.currentTimeMillis().toString() + ".JPG"
//                saveBitmapToFile(bitmap,fileName)
            }else if (image.format == ImageFormat.YUV_420_888){
                //Y通道
                val yBuffer = image.planes[0].buffer
                val yBytes = ByteArray(yBuffer.remaining())
                yBuffer.get(yBytes)
                //V通道
                val vBuffer = image.planes[2].buffer
                val vBytes = ByteArray(vBuffer.remaining())
                vBuffer.get(vBytes)
                //Y+V为NV21
                val NV21Byte = ByteArray(yBytes.size+vBytes.size);

                System.arraycopy(yBytes,0,NV21Byte,0,yBytes.size);
                System.arraycopy(vBytes,0,NV21Byte,yBytes.size,vBytes.size);

                //yuv转rgb
                val yuvImage = YuvImage(NV21Byte,ImageFormat.NV21,image.width,image.height,null);

                val fileName = System.currentTimeMillis().toString() + ".JPG"
//                saveYuvImageToFile(yuvImage,fileName)
            }else if (image.format == ImageFormat.DEPTH16){
                val shortDepthBuffer = image.planes[0].buffer.asShortBuffer();
                val pixels = IntArray(image.width * image.height)
                for (index in 0 until shortDepthBuffer.remaining()){
                    val depthSample = shortDepthBuffer.get().toInt()
                    val depthRange = (depthSample and 0x1FFF).toShort();
                    val depthConfidence = (depthSample shr 13 and 0x7)
                    val depthPercentage = if (depthConfidence == 0) 1f else (depthConfidence - 1) / 7f

                    pixels[index] = distanceReflectColor(depthRange,depthPercentage)
                }
                val bitmap = Bitmap.createBitmap(image.width,image.height,Bitmap.Config.ARGB_8888)
                bitmap.setPixels(pixels,0,image.width,0,0,image.width,image.height)

                val fileName = System.currentTimeMillis().toString()+".JPG"
                saveBitmapToFile(bitmap,fileName)
            }
            image.close()
        }
    }

    private fun distanceReflectColor(distance:Short,depthPercentage:Float):Int{
        var color = 0
        if (distance<=10000){
            color = (255 * (1- distance/10000f)).toInt()
        }else
            color = 0

        if (depthPercentage<0.5)
            color = 0
        return Color.rgb(color,color,color)
    }

    private fun saveBitmapToFile(bitmap:Bitmap?,fileName:String){
        if (bitmap==null)
            return
        val file = File(Environment.getExternalStorageDirectory().path,fileName)
        val fos = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG,90,fos)
        fos.close()
        Log.e("saveBitmap","$fileName save success")
    }

    private fun saveYuvImageToFile(yuvImage: YuvImage,fileName: String){
        val file = File(Environment.getExternalStorageDirectory().path,fileName)
        try {
            val outputStream = FileOutputStream(file);
            yuvImage.compressToJpeg(Rect(0,0,yuvImage.width,yuvImage.height),90,outputStream);
            outputStream.close();
        } catch (e: IOException) {
            e.printStackTrace();
        }
        Log.e("saveBitmap","$fileName save success")
    }

    interface CameraOpenFailedCallback{

        fun openFailed(failedCode:Int)
    }
}