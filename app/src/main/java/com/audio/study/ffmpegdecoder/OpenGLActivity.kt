package com.audio.study.ffmpegdecoder

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.audio.study.ffmpegdecoder.databinding.ActivityOpenGlactivityBinding

class OpenGLActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOpenGlactivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOpenGlactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}