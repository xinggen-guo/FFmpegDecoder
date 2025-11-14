#include "audio_visualizer.h"
#include <cmath>
#include <algorithm>

AudioVisualizer& AudioVisualizer::instance() {
    static AudioVisualizer inst;
    return inst;
}

AudioVisualizer::AudioVisualizer()
        : sampleRate_(0),
          pcmBuffer_(),
          fftMag_(FFT_SIZE / 2, 0.0f),
          bands_(32, 0.0f),
          waveBuffer_(WAVE_SIZE, 0.0f){
    pcmBuffer_.reserve(FFT_SIZE * 2);
}

void AudioVisualizer::onPcmData(short const* data, int sampleCount, int sampleRate) {
    if (!data || sampleCount <= 0) return;

    std::lock_guard<std::mutex> lock(mutex_);
    sampleRate_ = sampleRate;

    // ---- waveform (time-domain) ----
    for (int i = 0; i < sampleCount; ++i) {
        float v = data[i] / 32768.0f; // [-1,1]

        // push into rolling buffer
        waveBuffer_.erase(waveBuffer_.begin());
        waveBuffer_.push_back(v);
    }

    // ---- FFT path ---
    for (int i = 0; i < sampleCount; ++i) {
        pcmBuffer_.push_back(data[i] / 32768.0f);
    }

    while ((int)pcmBuffer_.size() >= FFT_SIZE) {
        computeFft();
        pcmBuffer_.erase(pcmBuffer_.begin(), pcmBuffer_.begin() + FFT_SIZE / 2);
    }
}

void AudioVisualizer::computeFft() {
    static float re[FFT_SIZE];
    static float im[FFT_SIZE];

    for (int i = 0; i < FFT_SIZE; ++i) {
        float w = 0.5f * (1.0f - std::cos(2.0f * M_PI * i / (FFT_SIZE - 1)));
        re[i] = pcmBuffer_[i] * w;
        im[i] = 0.0f;
    }

    for (int len = 2; len <= FFT_SIZE; len <<= 1) {
        float angle = -2.0f * M_PI / len;
        float wlenRe = std::cos(angle);
        float wlenIm = std::sin(angle);

        for (int i = 0; i < FFT_SIZE; i += len) {
            float wRe = 1.0f;
            float wIm = 0.0f;
            int half = len >> 1;

            for (int j = 0; j < half; ++j) {
                int u = i + j;
                int v = i + j + half;

                float ur = re[u];
                float ui = im[u];
                float vr = re[v] * wRe - im[v] * wIm;
                float vi = re[v] * wIm + im[v] * wRe;

                re[u] = ur + vr;
                im[u] = ui + vi;
                re[v] = ur - vr;
                im[v] = ui - vi;

                float tmpRe = wRe * wlenRe - wIm * wlenIm;
                float tmpIm = wRe * wlenIm + wIm * wlenRe;
                wRe = tmpRe;
                wIm = tmpIm;
            }
        }
    }

    for (int i = 0; i < FFT_SIZE / 2; ++i) {
        fftMag_[i] = std::sqrt(re[i] * re[i] + im[i] * im[i]);
    }

    int bandCount = (int)bands_.size();
    std::fill(bands_.begin(), bands_.end(), 0.0f);

    int binsPerBand = (FFT_SIZE / 2) / bandCount;
    if (binsPerBand <= 0) binsPerBand = 1;

    for (int b = 0; b < bandCount; ++b) {
        int start = b * binsPerBand;
        int end   = std::min(start + binsPerBand, FFT_SIZE / 2);
        float sum = 0.0f;
        for (int i = start; i < end; ++i) {
            sum += fftMag_[i];
        }
        float avg = sum / (float)(end - start);
        bands_[b] = std::log10(1.0f + avg * 10.0f);
    }
}

void AudioVisualizer::getSpectrum(float* bandsOut, int bandCount) {
    if (!bandsOut || bandCount <= 0) return;
    std::lock_guard<std::mutex> lock(mutex_);

    int n = std::min(bandCount, (int)bands_.size());
    for (int i = 0; i < n; ++i) {
        bandsOut[i] = bands_[i];
    }
    for (int i = n; i < bandCount; ++i) {
        bandsOut[i] = 0.0f;
    }
}

void AudioVisualizer::getWaveform(float* samplesOut, int sampleCount) {
    if (!samplesOut || sampleCount <= 0) return;
    std::lock_guard<std::mutex> lock(mutex_);

    int n = std::min(sampleCount, (int)waveBuffer_.size());
    int start = waveBuffer_.size() - n;
    for (int i = 0; i < n; ++i) {
        samplesOut[i] = waveBuffer_[start + i]; // already [-1,1]
    }
    for (int i = n; i < sampleCount; ++i) {
        samplesOut[i] = 0.0f;
    }
}