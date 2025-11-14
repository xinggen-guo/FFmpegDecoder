#pragma once

#include <vector>
#include <mutex>

class AudioVisualizer {
public:
    static AudioVisualizer& instance();

    void onPcmData(short const* data, int sampleCount, int sampleRate);

    //  spectrum
    void getSpectrum(float* bandsOut, int bandCount);

    //waveform (time-domain)
    void getWaveform(float* samplesOut, int sampleCount);

private:
    AudioVisualizer();  // constructor

    void computeFft();

private:
    std::mutex mutex_;
    int sampleRate_;

    static const int FFT_SIZE = 1024;

    std::vector<float> pcmBuffer_; // for FFT
    std::vector<float> fftMag_;
    std::vector<float> bands_;

    // NEW: rolling waveform buffer (e.g. last 2048 samples)
    std::vector<float> waveBuffer_;
    static const int WAVE_SIZE = 2048;
};