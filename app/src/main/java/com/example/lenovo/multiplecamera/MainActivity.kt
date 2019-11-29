package com.example.lenovo.multiplecamera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.AsyncTask
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*

@RequiresApi(Build.VERSION_CODES.P)
class MainActivity : AppCompatActivity() {

    var cameraManager : CameraManager? = null
    var cameraCharacteristics : CameraCharacteristics? = null

    var cameraDevice : CameraDevice? = null
    var captureSession : CameraCaptureSession? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(Manifest.permission.CAMERA),0)
        }
    }

    public fun onClick(view:View){
        when(view.id){
            R.id.start_preview_bt -> selectCameraIdThenOpen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        releaseCamera()
    }

    private fun releaseCamera() {
        if (captureSession!=null){
            captureSession!!.close()
            captureSession = null
        }

        if (cameraDevice!=null){
            cameraDevice!!.close()
            cameraDevice = null
        }
    }

    private fun selectCameraIdThenOpen() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            Toast.makeText(this,R.string.please_open_camera_permission,Toast.LENGTH_SHORT).show()
            return
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager?

        for (cameraID in cameraManager!!.cameraIdList){
            val cameraCharacteristics = cameraManager!!.getCameraCharacteristics(cameraID)
            val cameraLensFace = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
            if (cameraLensFace == CameraCharacteristics.LENS_FACING_BACK
                    && cameraCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES).contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)){
                this.cameraCharacteristics = cameraCharacteristics

                val physicalCameraIds =  cameraCharacteristics!!.physicalCameraIds
                openCamera(cameraID,physicalCameraIds.toTypedArray())

                break
            }
        }
    }

    private fun openCamera(logicalCameraId:String,physicalCameraIds:Array<String>){
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission( Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            //没有权限，抛出异常
            return
        }

        if (cameraManager==null)
            return

        if (Build.VERSION.SDK_INT >=28){
            cameraManager!!.openCamera(logicalCameraId,AsyncTask.SERIAL_EXECUTOR,object : CameraDevice.StateCallback(){
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
        val allSurfaces = ArrayList<Surface>()

        val outputConfigLogical = OutputConfiguration(preview_sv.holder.surface)
        allOutputConfigs.add(outputConfigLogical)
        allSurfaces.add(preview_sv.holder.surface)

        if (physicalCameraIds.isNotEmpty()){
            val outputConfigPhysical1 = OutputConfiguration(preview2_sv.holder.surface).apply {
                setPhysicalCameraId(physicalCameraIds[0])
            }
            allOutputConfigs.add(outputConfigPhysical1)
            allSurfaces.add(preview2_sv.holder.surface)

            if (physicalCameraIds.size>=2){
                val outputConfigPhysical2 = OutputConfiguration(preview3_sv.holder.surface).apply {
                    setPhysicalCameraId(physicalCameraIds[2])
                }
                allOutputConfigs.add(outputConfigPhysical2)
                allSurfaces.add(preview3_sv.holder.surface)
            }
        }else
            throw RuntimeException("have no physical camera")

        val sessionConfiguration = SessionConfiguration(SessionConfiguration.SESSION_REGULAR
                ,allOutputConfigs,AsyncTask.SERIAL_EXECUTOR
                ,object :CameraCaptureSession.StateCallback(){

            override fun onConfigured(session: CameraCaptureSession?) {
                if (session!=null){
                    captureSession = session
                    val captureRequest = session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        allSurfaces.forEach { addTarget(it) }
                    }.build()

                    session.setRepeatingRequest(captureRequest,null,null)
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession?) {
                Log.e("cameraTip","onConfigureFailed")
            }

        })

        cameraDevice.createCaptureSession(sessionConfiguration)
    }

}
