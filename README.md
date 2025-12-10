FFmpegDecoder

FFmpegDecoder is an audio/video engineering laboratory project.

It began as an exploration of FFmpeg decoding, MediaCodec, audio engines, and AV synchronization.
Later, it grew into a fully custom live streaming pipeline:
	•	Android → MediaCodec H.264 + AAC
	•	Pure Kotlin FLV muxer
	•	TCP transport (no RTMP, no WebRTC)
	•	Go streaming server
	•	Browser playback via flv.js + MSE

This repository documents the entire journey — from decoding videos manually to building a working live streaming system.

⸻

📦 Features Overview

1. FFmpeg Decoder & Player (Android + Native)

A complete custom A/V player stack:

Video Engines
	•	FfmpegVideoEngine
	•	Uses FFmpeg via JNI
	•	Software H264 decoding
	•	Renders frames to Surface / OpenGL
	•	MediaCodecVideoEngine
	•	Hardware decoding
	•	Demonstrates codec differences & behavior

Audio Engines
	•	AudioTrackAudioEngine – plays PCM via AudioTrack
	•	OpenSlAudioEngine – low-latency OpenSL ES PCM playback

AV Synchronization
	•	AVSyncEngine
	•	AvSyncController
Manages timestamps, clocks, and video/audio drift.

Player Abstraction
	•	XMediaPlayer
	•	XMediaPlayerFactory
A full custom media player architecture.

⸻

2. Live Streaming Pipeline (Android → Go → Web)

A minimal but complete streaming system built from scratch.

Android (Live Capture)
	•	Camera2 + OpenGL preview pipeline
	•	Video encoding: MediaCodec (H.264)
	•	Audio encoding: MediaCodec (AAC)
	•	SPS/PPS extraction
	•	AudioSpecificConfig extraction
	•	Precise PTS computation
	•	Pure Kotlin FLV muxer (no FFmpeg)
	•	TCP streaming via socket

Key classes:

Component	Class
Orchestration	AvLiveStreamer
Video capture → encode	VideoEncoder / Camera2 pipeline
Audio capture → encode	AudioEncoder
FLV muxing	FlvMuxSink
TCP streaming	NetworkFlvSink
File recording	FlvMuxSink(outputStream)


⸻

Go Streaming Server

Located in:

stream-server/

Responsibilities:
	•	Accept live FLV stream via TCP
	•	Store metadata + sequence headers
	•	Serve /live.flv as HTTP-FLV
	•	Work with flv.js for ultra-low-latency playback

Start it:

cd stream-server
go run ./cmd/server


⸻

Web Player (flv.js + MSE)

Static HTML page:

const player = flvjs.createPlayer({
    type: 'flv',
    url: '/live.flv',
    isLive: true,
});
player.attachMediaElement(video);
player.load();
player.play();

Open in browser:

http://SERVER_IP:8080


⸻
## 📁 Project Structure

```text
FFmpegDecoder/
│
├── android-app/                                        # Android client (player + live streamer)
│   ├── build.gradle / AndroidManifest.xml / ...
│   │
│   ├── src/
│   │   └── main/
│   │       ├── java/com.audio.study.ffmpegdecoder/     # Java/Kotlin layer
│   │       │   │
│   │       │   ├── audiotracke/                        # AudioTrack-based decoding & playback
│   │       │   │   ├── AudioDecoder
│   │       │   │   ├── AudioDecoderImpl
│   │       │   │   ├── AudioPlayer
│   │       │   │   └── NativePlayController            # JNI bridge to native audio decoder
│   │       │   │
│   │       │   ├── common/                             # Shared types, constants, media state
│   │       │   │   ├── AudioClockProvider
│   │       │   │   ├── Constants
│   │       │   │   └── MediaStatus
│   │       │   │
│   │       │   ├── live/                               # Live streaming pipeline (Kotlin)
│   │       │   │   ├── engine/
│   │       │   │   │   ├── AvLiveStreamer              # Orchestrates live capture → encode → FLV → TCP
│   │       │   │   │   ├── AvRecorder
│   │       │   │   │   ├── CameraVideoRecorder         # Camera2 + MediaCodec recorder
│   │       │   │   │   ├── FlvMuxSink                  # Pure Kotlin FLV muxer
│   │       │   │   │   ├── GLFilterRenderer            # GL filters for preview
│   │       │   │   │   └── OpenSILiveAudioEngine       # Live audio engine (OpenSL ES, Kotlin side)
│   │       │   │   │
│   │       │   │   ├── interfaces/
│   │       │   │   │   ├── LiveAudioEngine
│   │       │   │   │   └── LiveStreamSink              # FLV output abstraction (TCP/file)
│   │       │   │   │
│   │       │   │   ├── net/
│   │       │   │   │   └── NetworkFlvSink              # TCP FLV sender to Go server
│   │       │   │   │
│   │       │   │   └── opengl/
│   │       │   │       ├── GLType
│   │       │   │       ├── MyGLRenderer
│   │       │   │       ├── MyGLSurfaceView
│   │       │   │       └── MyNativeRender              # JNI bridge to native GL renderer
│   │       │   │
│   │       │   ├── opensles/                           # OpenSL ES Kotlin wrapper
│   │       │   │   ├── OnSoundTrackListener
│   │       │   │   ├── OpenSlesAudioPlayer
│   │       │   │   └── SoundTrackController.kt
│   │       │   │
│   │       │   ├── player/                             # Full A/V player (FFmpeg + MediaCodec)
│   │       │   │   ├── data/
│   │       │   │   │   └── SyncDecision                # AV timestamp correction logic
│   │       │   │   │
│   │       │   │   ├── engine/
│   │       │   │   │   ├── AudioTrackAudioEngine       # AudioTrack playback engine
│   │       │   │   │   ├── AvSyncController
│   │       │   │   │   ├── AVSyncEngine                # Core AV sync logic
│   │       │   │   │   ├── FfmpegVideoEngine           # Software decode via FFmpeg JNI
│   │       │   │   │   ├── MediaCodecVideoEngine       # Hardware decode
│   │       │   │   │   └── OpenSIAudioEngine           # OpenSL ES audio backend
│   │       │   │   │
│   │       │   │   ├── enum/
│   │       │   │   │   ├── AudioBackend                # AUDIO_TRACK / OPENSL
│   │       │   │   │   └── DecodeType                  # SOFTWARE / HARDWARE
│   │       │   │   │
│   │       │   │   ├── interfaces/
│   │       │   │   │   ├── AudioEngine
│   │       │   │   │   ├── VideoEngine
│   │       │   │   │   ├── VideoRenderer
│   │       │   │   │   └── XMediaPlayerListener        # Playback callbacks
│   │       │   │   │
│   │       │   │   ├── render/
│   │       │   │   │   └── SoftwareCanvasRender        # CPU-side rendering to Canvas
│   │       │   │   │
│   │       │   │   ├── XMediaPlayer                    # Player façade
│   │       │   │   └── XMediaPlayerFactory             # Factory to build player instances
│   │       │   │
│   │       │   ├── utils/
│   │       │   │   ├── AvFileMixer
│   │       │   │   ├── FileUtil
│   │       │   │   ├── LogUtil
│   │       │   │   ├── ResourceUtils
│   │       │   │   ├── TimeUtils.kt
│   │       │   │   ├── ToastUtils
│   │       │   │   └── WavWriter                       # Writes PCM data to .wav
│   │       │   │
│   │       │   ├── video/
│   │       │   │   └── VideoPlayer                     # Simple example player
│   │       │   │
│   │       │   ├── views/
│   │       │   │   ├── AudioVideoSyncView
│   │       │   │   ├── SimpleVideoView
│   │       │   │   ├── VisualizerView
│   │       │   │   └── WaveformView
│   │       │   │
│   │       │   └── App + *Activity classes*            # Entry points / demo screens
│   │       │       ├── AudioOpenSLESActivity
│   │       │       ├── AudioTrackerActivity
│   │       │       ├── AudioVideoSyncActivity
│   │       │       ├── AvRecordActivity
│   │       │       ├── LiveAVMutiActivity
│   │       │       ├── LiveStreamActivity
│   │       │       ├── LiveWatchActivity
│   │       │       ├── MainActivity
│   │       │       ├── OpenGLActivity
│   │       │       ├── VideoPlayerActivity
│   │       │       └── XMediaPlayerActivity
│   │       │
│   │       └── cpp/                                   # Native C/C++ layer (FFmpeg, OpenGL, OpenSL)
│   │           ├── common/
│   │           │   ├── CommonTools.h
│   │           │   ├── ffmpeg_time.h                  # AV time helpers
│   │           │   ├── GLUtils.cpp/.h                 # OpenGL helpers
│   │           │   ├── ImageDef.h                     # Image/frame definitions
│   │           │   └── MediaStatus.h                  # Media state enums/structs
│   │           │
│   │           ├── decoder/                           # FFmpeg-based audio/video decoders
│   │           │   ├── audio_decoder.cpp/.h
│   │           │   ├── audio_decoder_controller.cpp/.h
│   │           │   ├── audio_visualizer.cpp/.h
│   │           │   ├── video_decoder.cpp/.h
│   │           │   ├── video_decoder_controller.cpp/.h
│   │           │   └── video_frame.h
│   │           │
│   │           ├── ffmpeg/
│   │           │   ├── include/                       # avcodec.h, avformat.h, ...
│   │           │   └── libs/                          # Prebuilt FFmpeg .so/.a (per ABI)
│   │           │
│   │           ├── libopensl/                         # Native OpenSL ES backend
│   │           │   ├── opensl_es_context.cpp/.h
│   │           │   ├── opensl_es_util.h
│   │           │   ├── sound_service.cpp/.h
│   │           │
│   │           ├── live/                              # Native live audio engine impl
│   │           │   └── LiveAudioEngineImpl.cpp/.h
│   │           │
│   │           └── render/                            # Native OpenGL renderer + JNI bridges
│   │               ├── GLImageTextureMapSample.cpp/.h
│   │               ├── GLRectangleSample.cpp/.h
│   │               ├── GLSampleBase.h
│   │               ├── GLTriangleSample.cpp/.h
│   │               ├── MyGLRenderContext.cpp/.h
│   │               ├── AudioDecoderBridge.cpp         # JNI: Java ↔ native audio decoder
│   │               ├── AudioOpenSLBridge.cpp          # JNI: Java ↔ native OpenSL engine
│   │               ├── NativeAudioEngine.cpp
│   │               ├── NativeAudioTrackEngine.cpp
│   │               ├── NativeVidioEngine.cpp          # (typo in name kept as-is)
│   │               ├── OpenGLBridge.cpp               # JNI: Java ↔ native GL renderer
│   │               ├── OpenSLLiveAudioEngine.cpp
│   │               ├── VideoDecoderBridge.cpp         # JNI: Java ↔ native video decoder
│   │               └── CMakeLists.txt                 # Native build configuration
│   │
│   └── ... (other Android module files)
│
├── stream-server/                                    # Go HTTP-FLV server
│   ├── cmd/server/main.go                            # Entry point
│   ├── internal/ingest                               # TCP ingest of live FLV
│   ├── internal/store                                # In-memory stream state / metadata
│   └── internal/httpflv                              # /live.flv HTTP-FLV output for flv.js
│
└── web-player/                                       # Browser player (flv.js + MSE)
    ├── index.html                                    # Minimal HTML page with <video> + JS
    └── flv.min.js                                    # flv.js library
```
⸻

🚀 Quick Start

1. Run Go Streaming Server

cd stream-server
go run ./cmd/server

Default ports:
	•	TCP ingest: 6000
	•	HTTP output: 8080 (/live.flv)

⸻

2. Run Android App

Configure streaming host:

val sink = NetworkFlvSink(BuildConfig.STREAM_HOST, BuildConfig.STREAM_PORT)
val streamer = AvLiveStreamer(this, sink)
streamer.start(previewView)

Stop streaming:

streamer.stop()
sink.close()


⸻

3. Open Browser Player

Visit:

http://SERVER_IP:8080

The player loads automatically with flv.js tuning for low latency.

⸻

📝 Notes on Timestamp Design

This project fixes the common FLV/MSE playback errors:
	•	PIPELINE_ERROR_DECODE
	•	Large audio timestamp gap detected
	•	appendBuffer failed

Fixes were achieved by:
	•	Using MediaCodec’s PTS directly
	•	Independent audio/video base timestamps
	•	Converting μs → ms when muxing FLV
	•	Guaranteed monotonic timestamp progression

⸻

🛣 Roadmap
	•	Multi-channel streaming
	•	Server-side FLV recording
	•	RTMP ingest mode
	•	SRT output / relay
	•	H.265 HEVC live streaming
	•	Web player UI improvements
	•	FFmpeg filter graph study modules

⸻

📜 License

MIT License.

⸻
