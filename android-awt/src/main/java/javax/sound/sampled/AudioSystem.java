package javax.sound.sampled;

import net.runelite.awt.impl.AndroidAudioBridge;
import net.runelite.awt.impl.AndroidClip;
import net.runelite.awt.impl.WavDecoder;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public final class AudioSystem {
    public static final int NOT_SPECIFIED = -1;

    private AudioSystem() {
    }

    public static Clip getClip() throws LineUnavailableException {
        return new AndroidClip();
    }

    public static Clip getClip(Mixer.Info mixerInfo) throws LineUnavailableException {
        return getClip();
    }

    public static AudioInputStream getAudioInputStream(InputStream stream) throws UnsupportedAudioFileException, IOException {
        if (stream == null) throw new NullPointerException("stream");
        return WavDecoder.decode(stream);
    }

    public static AudioInputStream getAudioInputStream(File file) throws UnsupportedAudioFileException, IOException {
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        try {
            return WavDecoder.decode(in);
        } catch (UnsupportedAudioFileException | IOException | RuntimeException e) {
            in.close();
            throw e;
        }
    }

    public static AudioInputStream getAudioInputStream(URL url) throws UnsupportedAudioFileException, IOException {
        InputStream in = new BufferedInputStream(url.openStream());
        try {
            return WavDecoder.decode(in);
        } catch (UnsupportedAudioFileException | IOException | RuntimeException e) {
            in.close();
            throw e;
        }
    }

    public static Line getLine(Line.Info info) throws LineUnavailableException {
        if (info != null && Clip.class.isAssignableFrom(info.getLineClass())) {
            return getClip();
        }
        if (info instanceof DataLine.Info) {
            DataLine.Info dli = (DataLine.Info) info;
            if (SourceDataLine.class.isAssignableFrom(info.getLineClass())) {
                AudioFormat[] formats = dli.getFormats();
                AudioFormat format = formats.length > 0 ? formats[0] : null;
                int bufferSize = dli.getMaxBufferSize() > 0 ? dli.getMaxBufferSize() : 4096;
                return AndroidAudioBridge.openSourceDataLine(format, bufferSize);
            }
        }
        throw new LineUnavailableException("Unsupported line type: " + info);
    }

    public static Mixer.Info[] getMixerInfo() {
        return new Mixer.Info[0];
    }

    public static boolean isLineSupported(Line.Info info) {
        return info instanceof DataLine.Info
            && (SourceDataLine.class.isAssignableFrom(info.getLineClass())
                || Clip.class.isAssignableFrom(info.getLineClass()));
    }
}
