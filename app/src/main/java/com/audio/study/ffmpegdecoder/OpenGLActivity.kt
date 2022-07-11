package com.audio.study.ffmpegdecoder

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.audio.study.ffmpegdecoder.databinding.ActivityOpenGlactivityBinding
import com.audio.study.ffmpegdecoder.opengl.GLType
import com.audio.study.ffmpegdecoder.opengl.MyGLSurfaceView

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
        }

        binding.glRectangle.setOnClickListener {
            myGLSurfaceView.setRenderTye(GLType.SAMPLE_TYPE_KEY_RECTANGLE)
        }
    }


    override fun onDestroy() {
        myGLSurfaceView.onDestroy()
        super.onDestroy()
    }
}