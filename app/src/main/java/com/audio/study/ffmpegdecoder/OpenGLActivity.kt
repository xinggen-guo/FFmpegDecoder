package com.audio.study.ffmpegdecoder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.audio.study.ffmpegdecoder.databinding.ActivityOpenGlactivityBinding
import com.audio.study.ffmpegdecoder.opengl.GLType
import com.audio.study.ffmpegdecoder.opengl.GLType.Companion.IMAGE_FORMAT_RGBA
import com.audio.study.ffmpegdecoder.opengl.MyGLSurfaceView
import java.io.IOException
import java.nio.ByteBuffer

class OpenGLActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOpenGlactivityBinding
    private lateinit var myGLSurfaceView: MyGLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOpenGlactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        myGLSurfaceView = MyGLSurfaceView(this)
        binding.glContainer.addView(myGLSurfaceView)

        binding.glTriangle.setOnClickListener {
            myGLSurfaceView.setRenderTye(GLType.SAMPLE_TYPE_TRIANGLE)
            myGLSurfaceView.requestRender()
        }

        binding.glRectangle.setOnClickListener {
            myGLSurfaceView.setRenderTye(GLType.SAMPLE_TYPE_KEY_RECTANGLE)
            myGLSurfaceView.requestRender()
        }

        binding.glLoadImage.setOnClickListener {
            myGLSurfaceView.setRenderTye(GLType.SAMPLE_TYPE_TEXTURE_MAP)
            loadRGBAImage(R.drawable.texture_map_test2)
            myGLSurfaceView.requestRender()
        }
    }


    override fun onDestroy() {
        myGLSurfaceView.onDestroy()
        super.onDestroy()
    }

    private fun loadRGBAImage(resId: Int): Bitmap? {
        val `is` = this.resources.openRawResource(resId)
        val bitmap: Bitmap?
        try {
            bitmap = BitmapFactory.decodeStream(`is`)
            if (bitmap != null) {
                val bytes = bitmap.byteCount
                val buf = ByteBuffer.allocate(bytes)
                bitmap.copyPixelsToBuffer(buf)
                val byteArray = buf.array()
                myGLSurfaceView.setImageData(IMAGE_FORMAT_RGBA, bitmap.width, bitmap.height, byteArray)
            }
        } finally {
            try {
                `is`.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return bitmap
    }
}