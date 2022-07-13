package com.audio.study.ffmpegdecoder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.audio.study.ffmpegdecoder.databinding.ActivityOpenGlactivityBinding
import com.audio.study.ffmpegdecoder.opengl.GLType
import com.audio.study.ffmpegdecoder.opengl.GLType.Companion.IMAGE_FORMAT_RGBA
import com.audio.study.ffmpegdecoder.opengl.MyGLSurfaceView
import java.io.IOException
import java.nio.ByteBuffer

class OpenGLActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding: ActivityOpenGlactivityBinding
    private lateinit var myGLSurfaceView: MyGLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOpenGlactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        myGLSurfaceView = MyGLSurfaceView(this)
        binding.glContainer.addView(myGLSurfaceView)

        binding.glTriangle.setOnClickListener(this)
        binding.glRectangle.setOnClickListener(this)
        binding.glLoadImage.setOnClickListener(this)

        setImageScaleListener()
    }

    private fun setImageScaleListener() {
        binding.imageScale.max = 100
        binding.imageScale.progress = 100
        binding.imageScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            var isChange = false

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (isChange) {
                    myGLSurfaceView.setImageScale(progress.toFloat() / 100f)
                    myGLSurfaceView.requestRender()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isChange = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isChange = false
            }

        })
    }

    override fun onDestroy() {
        myGLSurfaceView.onDestroy()
        super.onDestroy()
    }

    override fun onClick(view: View?) {
        binding.imageScale.visibility = View.GONE
        when(view){
            binding.glTriangle -> {
                myGLSurfaceView.setRenderTye(GLType.SAMPLE_TYPE_TRIANGLE)
            }
            binding.glRectangle -> {
                myGLSurfaceView.setRenderTye(GLType.SAMPLE_TYPE_KEY_RECTANGLE)
            }
            binding.glLoadImage -> {
                myGLSurfaceView.setRenderTye(GLType.SAMPLE_TYPE_TEXTURE_MAP)
                loadRGBAImage(R.drawable.texture_map_test2)
                binding.imageScale.visibility = View.VISIBLE
                setImageScaleListener()
            }
        }

        myGLSurfaceView.requestRender()
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