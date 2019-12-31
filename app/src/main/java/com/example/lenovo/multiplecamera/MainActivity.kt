package com.example.lenovo.multiplecamera

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import com.example.lenovo.multiplecamera.camera2.Camera2Utils
import kotlinx.android.synthetic.main.activity_main.*

@RequiresApi(Build.VERSION_CODES.P)
class MainActivity : AppCompatActivity() {

    var camera2Utils : Camera2Utils? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(Manifest.permission.CAMERA),0)
        }
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),0)
        }

        camera2Utils = Camera2Utils(this,object : Camera2Utils.CameraOpenFailedCallback{
            override fun openFailed(failedCode: Int) {
                val alertDialog = AlertDialog.Builder(this@MainActivity)
                when(failedCode){
                    Camera2Utils.OPEN_FAILED_NO_PERMISSION -> alertDialog.setMessage(R.string.please_open_camera_permission)
                    Camera2Utils.OPEN_FAILED_NOT_SUPPORT_MULTIPLE_CAMERA -> alertDialog.setMessage(R.string.not_support_multiple_camera)
                }
                alertDialog.setPositiveButton(R.string.ok) { _, _ ->
                    finish()
                }
                alertDialog.show()
            }
        })
    }

    public fun onClick(view:View){
        when(view.id){
            R.id.start_preview_bt -> {
                camera2Utils?.setPreviewSurfaces(listOf(preview_sv.holder.surface,preview2_sv.holder.surface,preview3_sv.holder.surface))

                camera2Utils?.selectCameraIdThenOpen()
            }
            R.id.take_photo_bt ->{
                camera2Utils?.takePicture()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (camera2Utils!=null)
            camera2Utils!!.releaseCamera()
    }

}
