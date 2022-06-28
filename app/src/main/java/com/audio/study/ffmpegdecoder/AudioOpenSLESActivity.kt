package com.audio.study.ffmpegdecoder

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.audio.study.ffmpegdecoder.databinding.ActivityAudioOpenSlesactivityBinding
import com.audio.study.ffmpegdecoder.databinding.ActivityAudioTrackeBinding

class AudioOpenSLESActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudioOpenSlesactivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioOpenSlesactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

    }
}