package net.runelite.awt.impl;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/** Creates independent PCM lines with safe native-track reuse during rapid reopen. */
public final class AndroidAudioBridge {
    private AndroidAudioBridge() {
    }

    public static SourceDataLine openSourceDataLine(AudioFormat format, int bufferSize) throws LineUnavailableException {
        if (format == null) {
            throw new LineUnavailableException("AudioFormat required");
        }
        return new PooledSourceDataLine(format, bufferSize);
    }
}
