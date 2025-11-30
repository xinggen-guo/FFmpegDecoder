FFmpegDecoder

A complete end-to-end live streaming system built from scratch — no FFmpeg binary, no RTMP, no WebRTC.

This project delivers a fully custom pipeline:

📱 Android (Kotlin)
→ capture camera & microphone
→ encode to H.264 + AAC
→ mux to FLV
→ push stream over TCP

🖥 Go Server
→ receives FLV stream
→ serves /live.flv via HTTP
→ supports browser playback

🌐 Web Player (flv.js)

→ ultra-low-latency playback in browser
→ uses MSE (MediaSource Extensions)

⸻

✨ Features

Android
	•	Camera2 capture
	•	OpenGL beauty rendering (GLSurface + filters)
	•	MediaCodec hardware encoding
	•	H.264 SPS/PPS extraction
	•	AAC AudioSpecificConfig extraction
	•	Precise timestamp synchronization
	•	Pure Kotlin FLV muxer (no JNI, no FFmpeg)
	•	Network streaming via TCP socket

Server
	•	Pure Go implementation
	•	Accepts FLV over TCP
	•	Stores header + metadata + frames
	•	Serves as HTTP-FLV endpoint (/live.flv)
	•	Supports multiple HTTP clients

Web Player
	•	flv.js
	•	Ultra low latency (buffer eliminated)
	•	Live mode (no seeking)
	•	Plays H.264 + AAC via MSE

⸻

📦 Project Structure

FFmpegDecoder/
│
├── android-app/                     # Android live streaming SDK / demo
│   ├── live/                        # core live capture
│   │   ├── AvLiveStreamer.kt        # camera + mic → encoder → sink
│   │   ├── NetworkFlvSink.kt        # TCP FLV sender
│   │   ├── FlvMuxer.kt              # pure Kotlin FLV writer
│   │   ├── GlFilterRenderer.kt      # OpenGL beauty filter
│   └── ...
│
├── server/                          # Go FLV streaming server
│   ├── main.go                      # handles push + /live.flv playback
│
└── web-player/                      # Browser HTML5 live player
    ├── index.html
    ├── flv.min.js


⸻

🧱 Architecture Overview

      Android
 ┌─────────────────────┐
 │ Camera2 + MIC        │
 │ MediaCodec H264/AAC  │
 │ FLV Muxer (Kotlin)   │
 └──────────┬──────────┘
            │ TCP
            ▼
      Go Streaming Server
 ┌───────────────────────────┐
 │ Accept FLV Push           │
 │ Serve /live.flv           │
 └──────────┬────────────────┘
            │ HTTP-FLV
            ▼
     Browser (flv.js + MSE)


⸻

🚀 Quick Start

1. Start Go Server

cd server
go run main.go

Server opens:
	•	TCP FLV input at :6000
	•	HTTP output at :8080/live.flv

⸻

2. Run Android App

Inside your Kotlin project:

val streamer = AvLiveStreamer(context, NetworkFlvSink("SERVER_IP", 6000))
streamer.start(previewView)


⸻

3. Open Web Player

Open in browser:

http://SERVER_IP:8080

This loads /web-player/index.html which plays /live.flv.

Player auto-starts:

const player = flvjs.createPlayer({
    type: 'flv',
    url: '/live.flv',
    isLive: true,
    hasAudio: true,
    hasVideo: true,
});
player.attachMediaElement(video);
player.load();
player.play();


⸻

🌐 Web Player Preview

(screenshot placeholder – you can upload your image later)

+-------------------------------------------+
|  ▷   LIVE STREAM                           |
|  [H264 Video + AAC Audio via MSE]         |
+-------------------------------------------+


⸻

🛠 Developer Notes

Timestamp Rules

This project required extremely careful timestamp design.

Video PTS
bufferInfo.presentationTimeUs from MediaCodec is used.

val ptsUs = rawPtsUs - videoPtsBaseUs

Audio PTS
Also uses MediaCodec timestamps (correct).

⸻

📄 API Overview

LiveStreamSink

Implemented by NetworkFlvSink:

interface LiveStreamSink {
    fun onVideoConfig(sps: ByteArray, pps: ByteArray)
    fun onVideoFrame(data: ByteArray, ptsUs: Long, isKeyFrame: Boolean)

    fun onAudioConfig(asc: ByteArray)
    fun onAudioFrame(data: ByteArray, ptsUs: Long)

    fun close()
}


⸻

🧪 Test: FFprobe Output

The FLV produced should show:

Stream #0:0 Video: h264
Stream #0:1 Audio: aac

If PTS is broken, browser logs errors like:

PipelineStatus::PIPELINE_ERROR_DECODE
Large audio timestamp gap detected
appendBuffer error

These issues are fixed by using MediaCodec timestamps only.

⸻

📅 Roadmap
	•	Multistream support
	•	FLV recording on server
	•	H265 support
	•	WebRTC output
	•	RTMP ingest
	•	Android → SRT streaming

⸻

📜 License

MIT License

⸻
