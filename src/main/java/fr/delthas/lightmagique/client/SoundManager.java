package fr.delthas.lightmagique.client;

import fr.delthas.lightmagique.shared.Entity;
import fr.delthas.lightmagique.shared.Utils;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.stb.STBVorbisInfo;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.alListeneriv;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.stb.STBVorbis.*;
import static org.lwjgl.system.MemoryUtil.NULL;

@SuppressWarnings("unused")
class SoundManager {

  private int[] buffers = new int[Sound.values().length];
  private int[] globalSources = new int[50];
  private int[] sources = new int[50];
  private int globalSourceId = -1;
  private int sourceId = -1;
  private int backgroundSource = -1;
  private boolean disabled = false;

  public SoundManager() throws IOException {
    long device = alcOpenDevice((ByteBuffer) null);
    if (device == NULL) {
      System.err.println("No sound device found. Disabling sound.");
      disabled = true;
      return;
    }
    ALCCapabilities deviceCaps = ALC.createCapabilities(device);
    long context = alcCreateContext(device, (IntBuffer) null);
    alcMakeContextCurrent(context);
    AL.createCapabilities(deviceCaps);
    checkALError();
    alGenBuffers(buffers);
    checkALError();

    byte[] buf = new byte[16384];
    for (int i = 0; i < buffers.length; i++) {
      try (STBVorbisInfo info = STBVorbisInfo.malloc()) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (
                BufferedInputStream is = new BufferedInputStream(Files.newInputStream(Utils.getFile("sounds/" + Sound.values()[i].soundFile + ".ogg")))) {
          int n;
          while ((n = is.read(buf)) != -1) {
            os.write(buf, 0, n);
          }
        }
        byte[] data = os.toByteArray();
        ByteBuffer buffer = BufferUtils.createByteBuffer(data.length).put(data);
        buffer.flip();
        IntBuffer error = BufferUtils.createIntBuffer(1);
        long decoder = stb_vorbis_open_memory(buffer, error, null);
        if (decoder == NULL) {
          throw new RuntimeException("Failed reading ogg file " + Sound.values()[i].soundFile + ". Error: " + error.get(0));
        }
        stb_vorbis_get_info(decoder, info);
        int channels = info.channels();
        int lengthSamples = stb_vorbis_stream_length_in_samples(decoder);
        ShortBuffer pcm = BufferUtils.createShortBuffer(lengthSamples);
        stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);
        stb_vorbis_close(decoder);
        alBufferData(buffers[i], AL_FORMAT_MONO16, pcm, info.sample_rate());
        checkALError();
      }
    }
    buf = null;
    System.gc();
  }

  private static void checkALError() {
    int err = alGetError();
    if (err != AL_NO_ERROR) {
      throw new RuntimeException(alGetString(err));
    }
  }

  public void playSound(Sound sound) {
    if (disabled) {
      return;
    }
    globalSourceId = (globalSourceId + 1) % globalSources.length;
    alSourceStop(globalSources[globalSourceId]);
    checkALError();
    alSourcei(globalSources[globalSourceId], AL_BUFFER, buffers[sound.ordinal()]);
    checkALError();
    alSourcePlay(globalSources[globalSourceId]);
    checkALError();
  }

  public void playSound(Sound sound, Entity entity) {
    if (disabled) {
      return;
    }
    sourceId = (sourceId + 1) % sources.length;
    alSourceStop(sources[sourceId]);
    checkALError();
    alSource3f(sources[sourceId], AL_POSITION, (float) entity.getX(), (float) entity.getY(), 0f);
    checkALError();
    alSource3f(sources[sourceId], AL_VELOCITY, (float) (entity.getSpeed() * Math.cos(entity.getAngle())),
            (float) (entity.getSpeed() * Math.sin(entity.getAngle())), 0f);
    checkALError();
    alSourcei(sources[sourceId], AL_BUFFER, buffers[sound.ordinal()]);
    checkALError();
    alSourcePlay(sources[sourceId]);
    checkALError();
  }

  public void updateListener(float x, float y) {
    if (disabled) {
      return;
    }
    alListener3f(AL_POSITION, x, y, 0f);
    checkALError();
  }

  public void start() {
    if (disabled) {
      return;
    }
    alGenSources(globalSources);
    checkALError();
    alGenSources(sources);
    checkALError();
    backgroundSource = alGenSources();
    checkALError();
    for (int i = 0; i < sources.length; i++) {
      alSourcei(globalSources[i], AL_SOURCE_RELATIVE, AL_TRUE);
      checkALError();
      alSource3f(globalSources[i], AL_POSITION, 0f, 0f, 0f);
      checkALError();
      alSource3f(globalSources[i], AL_VELOCITY, 0f, 0f, 0f);
      checkALError();
    }
    alSourcei(backgroundSource, AL_SOURCE_RELATIVE, AL_TRUE);
    checkALError();
    alSource3f(backgroundSource, AL_POSITION, 0f, 0f, 0f);
    checkALError();
    alSource3f(backgroundSource, AL_VELOCITY, 0f, 0f, 0f);
    checkALError();
    alSourcei(backgroundSource, AL_LOOPING, AL_TRUE);
    checkALError();
    alSourcef(backgroundSource, AL_GAIN, 0.7f);
    checkALError();
    for (int i = 0; i < sources.length; i++) {
      alSourcef(sources[i], AL_REFERENCE_DISTANCE, 600);
      checkALError();
    }
    int[] orientation = {0, 0, -1, 0, 1, 0};
    alListeneriv(AL_ORIENTATION, orientation);
    checkALError();
    alSourcei(backgroundSource, AL_BUFFER, buffers[Sound.BACKGROUND.ordinal()]);
    checkALError();
    alSourcePlay(backgroundSource);
    checkALError();
  }

  public void exit() {
    if (disabled) {
      return;
    }
    if (sources != null) {
      alSourceStopv(sources);
      alDeleteSources(sources);
      alDeleteSources(backgroundSource);
    }
    alDeleteBuffers(buffers);
    long context = alcGetCurrentContext();
    long device = alcGetContextsDevice(context);
    alcMakeContextCurrent(NULL);
    alcDestroyContext(context);
    alcCloseDevice(device);
  }

  public enum Sound {
    BACKGROUND("background"), BALL_LAUNCH("balllaunch"), PLAYER_DASH("dash"), ENEMY_DASH("enemydash"), ENEMY_HURT("enemyhurt"), PLAYER_HURT(
            "playerhurt"), LEVEL_UP("levelup"), SPAWN("spawn");
    private String soundFile;

    Sound(String soundFile) {
      this.soundFile = soundFile;
    }
  }

}
