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

📁 Project Structure

FFmpegDecoder/
│
├── app/                                # Android app module
│   ├── ffmpeg/                         # FFmpeg decoding engines
│   │   ├── FfmpegVideoEngine.kt
│   │   ├── OpenSlAudioEngine.kt
│   │   ├── AudioTrackAudioEngine.kt
│   │   └── Native JNI FFmpeg bindings
│   │
│   ├── mediacodec/                     # MediaCodec playback engines
│   │   ├── MediaCodecVideoEngine.kt
│   │   └── MediaCodecAudioEngine.kt
│   │
│   ├── avsync/                         # AV sync module
│   │   ├── AVSyncEngine.kt
│   │   └── AvSyncController.kt
│   │
│   ├── live/                           # Live streaming implementation
│   │   ├── AvLiveStreamer.kt
│   │   ├── FlvMuxSink.kt
│   │   ├── NetworkFlvSink.kt
│   │   └── FLV writer (pure Kotlin)
│   │
│   └── ui/                             # Demo activities
│       ├── LiveStreamActivity.kt
│       └── FFmpegPlayerActivity.kt
│
├── stream-server/                      # Go HTTP-FLV server
│   ├── cmd/server/main.go
│   ├── internal/ingest
│   ├── internal/store
│   └── internal/httpflv
│
└── web-player/                         # Browser player
    ├── index.html
    └── flv.min.js


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
