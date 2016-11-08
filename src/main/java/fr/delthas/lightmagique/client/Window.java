package fr.delthas.lightmagique.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.system.MemoryStack.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWScrollCallbackI;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import fr.delthas.lightmagique.shared.Shooter;
import fr.delthas.lightmagique.shared.Utils;

// import static org.lwjgl.glfw.GLFW.*;
// import static org.lwjgl.opengl.GL11.*;
// import static org.lwjgl.opengl.GL12.*;
// import static org.lwjgl.opengl.GL13.*;
// import static org.lwjgl.opengl.GL14.*;
// import static org.lwjgl.opengl.GL15.*;
// import static org.lwjgl.opengl.GL20.*;
// import static org.lwjgl.opengl.GL21.*;
// import static org.lwjgl.opengl.GL30.*;
// import static org.lwjgl.opengl.GL31.*;
// import static org.lwjgl.opengl.GL32.*;
// import static org.lwjgl.opengl.GL33.*;
// import static org.lwjgl.system.MemoryUtil.*;


@SuppressWarnings({"unused", "static-method"})
class Window {

  public enum Model {
    PLAYER("player"), MONSTER("monster"), BALL("ball"), SMALL_BALL("smallball");

    private String modelFile;

    private Model(String modelFile) {
      this.modelFile = modelFile;
    }
  }

  private static class Mesh {
    public final int buffer;
    public final int vertices;

    public Mesh(int buffer, int vertices) {
      this.buffer = buffer;
      this.vertices = vertices;
    }
  }

  private static class Texture {
    public final int texture;
    public final int width;
    public final int height;

    public Texture(int texture, int width, int height) {
      this.texture = texture;
      this.width = width;
      this.height = height;
    }
  }

  private boolean compatibility = false;
  private long window;
  private GLFWKeyCallback keyCallback;
  private GLFWCursorPosCallback cursorPosCallback;
  private GLFWMouseButtonCallback mouseButtonCallback;
  private GLFWScrollCallback scrollCallback;

  private int rawVao, backgroundVao, vao;
  private int indexP, indexVM, indexVMNormal;
  private int indexTexMatrix;
  private int indexImageSize, indexRawMatrix;
  private int indexLight;
  private int backgroundBuffer, texture;
  private int rawBuffer;
  private int program, textureProgram, rawProgram;

  private static final int maxNumberOfLights = 1000;
  private static final float cameraAngle = 0;
  private static final float cameraDistance = 5000;
  private float cameraX = 0, cameraY = 0;

  private Mesh[] meshes = new Mesh[Model.values().length];

  private Set<Integer> keysState = new HashSet<>(20);
  private Set<Integer> mouseState = new HashSet<>(3);
  private Set<Integer> newKeys = new HashSet<>(2);
  private Set<Integer> newMouse = new HashSet<>(3);
  private double mouseX, mouseY;
  private int scroll = 0;

  private int width, height;

  private List<RenderEntity> entities;
  private List<Light> lights;
  private int[] levels = null;
  private int position;
  private int level;
  private int wave = -1;
  private boolean waveEnd;

  private Texture levelBackground;
  private Texture[] levelsTextures = new Texture[Shooter.LEVELS_AMOUNT];
  private Texture levelPointTexture;
  private Texture levelCursorTexture;

  private Texture cooldownBallTexture;
  private Texture cooldownPowerBallTexture;
  private Texture cooldownChargeBallTexture;
  private Texture cooldownDashTexture;

  private Texture freezeTexture;
  private Texture healthTexture;

  private Texture waveStartTexture;
  private Texture waveEndTexture;
  private Texture[] digitTextures = new Texture[10];

  private float cooldownBall = Float.NaN;
  private float cooldownPowerBall;
  private boolean chargingPowerBall;
  private float cooldownDash;

  private float health = Float.NaN;
  private boolean isHealth = true;

  private int start0, end0 = -1;

  // Reuse buffers to reduce RAM usage
  private FloatBuffer bufferMat4x4;
  private ByteBuffer bufferLights;

  private List<Integer> texturesIndexes = new LinkedList<>();

  private boolean hasMaximized = false;

  public void renderLight(Light light) {
    lights.add(light);
  }

  public void renderEntity(RenderEntity entity) {
    entities.add(entity);
  }

  public void renderHealth(float health, boolean isHealth) {
    this.health = Float.max(health, 0);
    this.isHealth = isHealth;
  }

  public void renderShop(int[] levels, int position, int level) {
    this.levels = levels;
    this.position = position;
    this.level = level;
  }

  public void renderCooldowns(float cooldownBall, float cooldownPowerBall, float cooldownDash, boolean chargingPowerBall) {
    this.cooldownBall = Float.max(cooldownBall, 0);
    this.cooldownPowerBall = Float.max(cooldownPowerBall, 0);
    this.cooldownDash = Float.max(cooldownDash, 0);
    this.chargingPowerBall = chargingPowerBall;
  }

  public void renderWave(int wave, boolean waveEnd) {
    this.wave = wave;
    this.waveEnd = waveEnd;
  }

  /**
   * @param x Coordonnée x du point au centre de l'écran
   * @param y Coordonnée y du point au centre de l'écran
   */
  public void beginRender(float x, float y) {
    glDepthMask(true);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    glDepthMask(false);
    cameraX = x;
    cameraY = y;
    entities = new LinkedList<>();
    lights = new LinkedList<>();
  }

  public void endRender() {

    // actually start render here

    // 1. Update the light buffer

    Color ambientColor = new Color(0.1f, 0.1f, 0.1f);
    float midAttenuationDistance = 70f;
    Color fogColor = new Color(0.0f, 0.0f, 0.0f);
    float fogAttenuationDistance = 100f;
    float maxIntensity = 2.0f;
    float gamma = 2.2f;

    bufferLights.clear();

    bufferLights.putFloat(ambientColor.getRed() / 256f).putFloat(ambientColor.getGreen() / 256f).putFloat(ambientColor.getBlue() / 256f);
    bufferLights.putFloat(1 / (midAttenuationDistance * midAttenuationDistance));
    bufferLights.putFloat(maxIntensity);
    bufferLights.putFloat(1 / gamma);
    bufferLights.putInt(lights.size());
    bufferLights.putFloat(0); // padding
    bufferLights.putFloat(fogColor.getRed() / 256f).putFloat(fogColor.getGreen() / 256f).putFloat(fogColor.getBlue() / 256f);
    bufferLights.putFloat(fogAttenuationDistance);

    for (Light light : lights) {
      Vector4f lightCameraPosition = calcLookAt(cameraX, cameraY).transform(new Vector4f(light.x, light.y, 150, 1));
      bufferLights.putFloat(lightCameraPosition.x).putFloat(lightCameraPosition.y).putFloat(lightCameraPosition.z).putFloat(lightCameraPosition.w);
      bufferLights.putFloat(light.color.getRed() / 256f).putFloat(light.color.getGreen() / 256f).putFloat(light.color.getBlue() / 256f)
          .putFloat(1.0f);
      bufferLights.putFloat(light.x).putFloat(light.y);
      bufferLights.putFloat(0).putFloat(0); // padding
    }

    bufferLights.flip();

    int start1 = glGenQueries();
    int end1 = glGenQueries();
    glQueryCounter(start1, GL_TIMESTAMP);

    glBindBuffer(GL_ARRAY_BUFFER, indexLight);
    glBufferSubData(GL_ARRAY_BUFFER, 0, bufferLights);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    // 2. Draw background

    glUseProgram(textureProgram);
    glBindVertexArray(backgroundVao);

    glBindTexture(GL_TEXTURE_2D, texture);

    Matrix4f pTex = new Matrix4f().scale(2f / width, 2f / height, 0).translate(-cameraX, -cameraY, 0);
    bufferMat4x4.clear();
    glUniformMatrix4fv(indexTexMatrix, false, pTex.get(bufferMat4x4));
    glDrawArrays(GL_TRIANGLES, 0, 6);

    // 3. Draw entities

    glUseProgram(program);
    glBindVertexArray(vao);
    glEnable(GL_CULL_FACE);
    glEnable(GL_DEPTH_TEST);
    glDepthMask(true);

    Matrix4f p = new Matrix4f().setOrthoSymmetric(width, height, 0.1f, 10000);
    Matrix4f v = calcLookAt(cameraX, cameraY);
    bufferMat4x4.clear();
    glUniformMatrix4fv(indexP, false, p.get(bufferMat4x4));

    for (RenderEntity entity : entities) {
      Mesh mesh = meshes[entity.model.ordinal()];
      Matrix4f m = new Matrix4f().translate(entity.x, entity.y, 0).rotateX((float) (-Math.PI / 6)).rotateZ(entity.angle + (float) Math.PI / 2)
          .scale(entity.scale);
      Matrix4f vm = v.mul(m, new Matrix4f());
      Matrix3f vmNormal = new Matrix3f(vm);
      bufferMat4x4.clear();
      glUniformMatrix4fv(indexVM, false, vm.get(bufferMat4x4));
      glUniformMatrix3fv(indexVMNormal, false, (FloatBuffer) vmNormal.get(bufferMat4x4).limit(9));

      glBindBuffer(GL_ARRAY_BUFFER, mesh.buffer);
      glVertexAttribPointer(0, 3, GL_FLOAT, false, Float.BYTES * 9, 0);
      glVertexAttribPointer(1, 3, GL_FLOAT, false, Float.BYTES * 9, Float.BYTES * 3);
      glVertexAttribPointer(2, 3, GL_FLOAT, false, Float.BYTES * 9, Float.BYTES * 6);
      glDrawArrays(GL_TRIANGLES, 0, mesh.vertices);
    }

    if (!hasMaximized) {
      glfwMaximizeWindow(window);
      hasMaximized = true;
    }

    glUseProgram(rawProgram);
    glBindVertexArray(rawVao);
    glDisable(GL_CULL_FACE);
    glDisable(GL_DEPTH_TEST);
    glDepthMask(false);

    // 4. Draw cooldowns

    if (!Float.isNaN(cooldownBall)) {

      Matrix4f matrix = new Matrix4f();

      glBindTexture(GL_TEXTURE_2D, cooldownBallTexture.texture);
      bufferMat4x4.clear();
      matrix.translation(-1, -1, 0).scale(2, 2, 1).translate(0, 0.06f, 1).scale(cooldownBall, 0.03f, 1);
      glUniformMatrix4fv(indexRawMatrix, false, matrix.get(bufferMat4x4));
      glUniform2f(indexImageSize, 0, 0);
      glDrawArrays(GL_TRIANGLES, 0, 6);
      glBindTexture(GL_TEXTURE_2D, (chargingPowerBall ? cooldownChargeBallTexture : cooldownPowerBallTexture).texture);
      bufferMat4x4.clear();
      matrix.translation(-1, -1, 0).scale(2, 2, 1).translate(0, 0.03f, 1).scale(cooldownPowerBall, 0.03f, 1);
      glUniformMatrix4fv(indexRawMatrix, false, matrix.get(bufferMat4x4));
      glUniform2f(indexImageSize, 0, 0);
      glDrawArrays(GL_TRIANGLES, 0, 6);
      glBindTexture(GL_TEXTURE_2D, cooldownDashTexture.texture);
      bufferMat4x4.clear();
      matrix.translation(-1, -1, 0).scale(2, 2, 1).translate(0, 0.00f, 1).scale(cooldownDash, 0.03f, 1);
      glUniformMatrix4fv(indexRawMatrix, false, matrix.get(bufferMat4x4));
      glUniform2f(indexImageSize, 0, 0);
      glDrawArrays(GL_TRIANGLES, 0, 6);

      cooldownBall = Float.NaN;
    }

    // 5. Draw health

    if (!Float.isNaN(health)) {

      Matrix4f matrix = new Matrix4f();

      glBindTexture(GL_TEXTURE_2D, (isHealth ? healthTexture : freezeTexture).texture);
      bufferMat4x4.clear();
      matrix.translation(+1, -1, 0).scale(2, 2, 1).translate(-0.03f, 0, 1).scale(0.03f, health, 1);
      glUniformMatrix4fv(indexRawMatrix, false, matrix.get(bufferMat4x4));
      glUniform2f(indexImageSize, 0, 0);
      glDrawArrays(GL_TRIANGLES, 0, 6);

      health = Float.NaN;
    }

    // 6. Draw shop

    if (levels != null) {

      Matrix4f matrix = new Matrix4f();

      glBindTexture(GL_TEXTURE_2D, levelBackground.texture);
      bufferMat4x4.clear();
      matrix.translation(-1, -1, 0).scale(2, 2, 1);
      glUniformMatrix4fv(indexRawMatrix, false, matrix.get(bufferMat4x4));
      glUniform2f(indexImageSize, 0, 0);
      glDrawArrays(GL_TRIANGLES, 0, 6);

      int y = height - 50;
      for (int i = 0; i < levels.length; i++) {
        glBindTexture(GL_TEXTURE_2D, levelsTextures[i].texture);
        bufferMat4x4.clear();
        y -= levelsTextures[i].height / 2;
        matrix.translation(-1, -1, 0).scale(2f / width, 2f / height, 1).translate(150, y, 0)
            .scale(levelsTextures[i].width, levelsTextures[i].height, 1).translate(-0.5f, -0.5f, 0);
        glUniformMatrix4fv(indexRawMatrix, false, matrix.get(bufferMat4x4));
        glUniform2f(indexImageSize, levelsTextures[i].width, levelsTextures[i].height);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindTexture(GL_TEXTURE_2D, levelPointTexture.texture);
        glUniform2f(indexImageSize, levelPointTexture.width, levelPointTexture.height);
        for (int j = 0; j < levels[i]; j++) {
          bufferMat4x4.clear();
          matrix.translation(-1, -1, 0).scale(2f / width, 2f / height, 1).translate(250 + j * levelPointTexture.width, y, 0)
              .scale(levelPointTexture.width, levelPointTexture.height, 1).translate(-0.5f, -0.5f, 0);
          glUniformMatrix4fv(indexRawMatrix, false, matrix.get(bufferMat4x4));
          glDrawArrays(GL_TRIANGLES, 0, 6);
        }
        if (position == i) {
          glBindTexture(GL_TEXTURE_2D, levelCursorTexture.texture);
          glUniform2f(indexImageSize, levelCursorTexture.width, levelCursorTexture.height);
          bufferMat4x4.clear();
          matrix.translation(-1, -1, 0).scale(2f / width, 2f / height, 1).translate(50, y, 0)
              .scale(levelCursorTexture.width, levelCursorTexture.height, 1).translate(-0.5f, -0.5f, 0);
          glUniformMatrix4fv(indexRawMatrix, false, matrix.get(bufferMat4x4));
          glDrawArrays(GL_TRIANGLES, 0, 6);
        }
        y -= levelsTextures[i].height / 2;
      }

      y -= 150;

      glBindTexture(GL_TEXTURE_2D, levelPointTexture.texture);
      glUniform2f(indexImageSize, levelPointTexture.width, levelPointTexture.height);
      for (int i = 0; i < level; i++) {
        bufferMat4x4.clear();
        matrix.translation(-1, -1, 0).scale(2f / width, 2f / height, 1)
            .translate(190 + i % 20 * levelPointTexture.width, y - i / 20 * levelPointTexture.height, 0)
            .scale(levelPointTexture.width, levelPointTexture.height, 1).translate(-0.5f, -0.5f, 0);
        glUniformMatrix4fv(indexRawMatrix, false, matrix.get(bufferMat4x4));
        glDrawArrays(GL_TRIANGLES, 0, 6);
      }

      levels = null;
    }

    // 7. Draw wave

    if (wave != -1) {

      Matrix4f matrix = new Matrix4f();

      Texture waveTexture = waveEnd ? waveEndTexture : waveStartTexture;
      glBindTexture(GL_TEXTURE_2D, waveTexture.texture);
      glUniform2f(indexImageSize, waveTexture.width, waveTexture.height);
      bufferMat4x4.clear();
      matrix.scaling(2f / width, 2f / height, 1).scale(waveTexture.width, waveTexture.height, 1).translate(-0.5f, -0.5f, 1);
      glUniformMatrix4fv(indexRawMatrix, false, matrix.get(bufferMat4x4));
      glDrawArrays(GL_TRIANGLES, 0, 6);

      int[] digits = getDigits(wave);
      int digitWidth = -digitTextures[0].width / 2 - digitTextures[digits.length - 1].width / 2;
      for (int digit : digits) {
        digitWidth += digitTextures[digit].width;
      }

      int x = -digitWidth / 2 - digitTextures[0].width / 2;
      for (int digit : digits) {
        Texture digitTexture = digitTextures[digit];
        glBindTexture(GL_TEXTURE_2D, digitTexture.texture);
        glUniform2f(indexImageSize, digitTexture.width, digitTexture.height);
        bufferMat4x4.clear();
        matrix.scaling(2f / width, 2f / height, 1).translate(x, -waveTexture.height * 0.6f, 0).scale(digitTexture.width, digitTexture.height, 1)
            .translate(0, -0.5f, 0);
        glUniformMatrix4fv(indexRawMatrix, false, matrix.get(bufferMat4x4));
        glDrawArrays(GL_TRIANGLES, 0, 6);
        x += digitTexture.width;
      }

      wave = -1;
    }

    glQueryCounter(end1, GL_TIMESTAMP);

    if (!hasMaximized) {
      glfwMaximizeWindow(window);
      hasMaximized = true;
    }


    glfwSwapBuffers(window);

    if (end0 == -1) {
      start0 = start1;
      end0 = end1;
    }

    if (glGetQueryObjectui(end0, GL_QUERY_RESULT_AVAILABLE) == GL_TRUE) {
      long startTime = glGetQueryObjectui64(start0, GL_QUERY_RESULT);
      long endTime = glGetQueryObjectui64(end0, GL_QUERY_RESULT);
      long timeTaken = endTime - startTime;
      // System.out.println("Temps d'affichage en millisecondes: " + timeTaken / 1e6);
      start0 = start1;
      end0 = end1;
    }

  }

  private void initBuffers() throws IOException {
    for (Model model : Model.values()) {
      loadModel(model);
    }
    // clean data allocated when reading models
    System.gc();

    glEnableVertexAttribArray(0);
    glEnableVertexAttribArray(1);
    glEnableVertexAttribArray(2);

    indexLight = glGenBuffers();
    glBindBuffer(GL_ARRAY_BUFFER, indexLight);
    glBufferData(GL_ARRAY_BUFFER, 48 + maxNumberOfLights * 48, GL_DYNAMIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    glBindBufferBase(GL_UNIFORM_BUFFER, 0, indexLight);

    bufferMat4x4 = MemoryUtil.memAllocFloat(16);
    bufferLights = MemoryUtil.memAlloc(48 + 48 * maxNumberOfLights);

    glUseProgram(rawProgram);
    rawBuffer = glGenBuffers();
    glBindBuffer(GL_ARRAY_BUFFER, rawBuffer);
    float[] rawPositions = {0f, 0f, 1f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, 1f, 1f};
    try (MemoryStack stack = stackPush()) {
      FloatBuffer fb = stack.mallocFloat(16);
      fb.put(rawPositions);
      fb.flip();
      glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
    }
    glBindVertexArray(rawVao);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);
    glBindVertexArray(vao);

    levelBackground = loadImage("levelbackground");
    levelCursorTexture = loadImage("levelcursor");
    levelPointTexture = loadImage("levelpoint");
    for (int i = 0; i < levelsTextures.length; i++) {
      levelsTextures[i] = loadImage("levels" + i);
    }

    cooldownBallTexture = loadImage("cooldownball");
    cooldownChargeBallTexture = loadImage("cooldownchargeball");
    cooldownPowerBallTexture = loadImage("cooldownpowerball");
    cooldownDashTexture = loadImage("cooldowndash");

    healthTexture = loadImage("health");
    freezeTexture = loadImage("freeze");

    waveStartTexture = loadImage("wavestart");
    waveEndTexture = loadImage("waveend");
    for (int i = 0; i <= 9; i++)
      digitTextures[i] = loadImage("digit" + i);

  }

  private Texture loadImage(String name) throws IOException {
    BufferedImage image;
    try (InputStream is = Files.newInputStream(Utils.getFile(name + ".png"))) {
      if (is == null) {
        throw new IOException("Image " + name + " not found.");
      }
      image = ImageIO.read(is);
    }
    int imageWidth = image.getWidth();
    int imageHeight = image.getHeight();
    ByteBuffer imageBuffer = readImage(image, true, true);
    int texture = glGenTextures();
    glBindTexture(GL_TEXTURE_2D, texture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, imageWidth, imageHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, imageBuffer);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
    memFree(imageBuffer);

    texturesIndexes.add(texture);
    return new Texture(texture, imageWidth, imageHeight);
  }

  private void initUniforms() {
    indexP = glGetUniformLocation(program, "p");
    indexVM = glGetUniformLocation(program, "vm");
    indexVMNormal = glGetUniformLocation(program, "vmNormal");
    glUniformBlockBinding(program, glGetUniformBlockIndex(program, "Light"), 0);
    indexTexMatrix = glGetUniformLocation(textureProgram, "texMatrix");
    glUniformBlockBinding(textureProgram, glGetUniformBlockIndex(textureProgram, "Light"), 0);
    indexImageSize = glGetUniformLocation(rawProgram, "imageSize");
    indexRawMatrix = glGetUniformLocation(rawProgram, "rawMatrix");
  }

  private void initPrograms() {
    int vertShader = glCreateShader(GL_VERTEX_SHADER);
    glShaderSource(vertShader, readFile("raw.vert"));
    glCompileShader(vertShader);
    if (glGetShaderi(vertShader, GL_COMPILE_STATUS) != GL_TRUE) {
      throw new RuntimeException(glGetShaderInfoLog(vertShader));
    }
    int fragShader = glCreateShader(GL_FRAGMENT_SHADER);
    glShaderSource(fragShader, readFile("raw.frag"));
    glCompileShader(fragShader);
    if (glGetShaderi(fragShader, GL_COMPILE_STATUS) != GL_TRUE) {
      throw new RuntimeException(glGetShaderInfoLog(fragShader));
    }
    rawProgram = glCreateProgram();
    glAttachShader(rawProgram, vertShader);
    glAttachShader(rawProgram, fragShader);
    glLinkProgram(rawProgram);
    if (glGetProgrami(rawProgram, GL_LINK_STATUS) != GL_TRUE) {
      throw new RuntimeException(glGetProgramInfoLog(rawProgram));
    }
    glDetachShader(rawProgram, vertShader);
    glDetachShader(rawProgram, fragShader);
    glDeleteShader(vertShader);
    glDeleteShader(fragShader);

    vertShader = glCreateShader(GL_VERTEX_SHADER);
    glShaderSource(vertShader, readFile("tex.vert"));
    glCompileShader(vertShader);
    if (glGetShaderi(vertShader, GL_COMPILE_STATUS) != GL_TRUE) {
      throw new RuntimeException(glGetShaderInfoLog(vertShader));
    }
    fragShader = glCreateShader(GL_FRAGMENT_SHADER);
    glShaderSource(fragShader, readFile("tex.frag"));
    glCompileShader(fragShader);
    if (glGetShaderi(fragShader, GL_COMPILE_STATUS) != GL_TRUE) {
      throw new RuntimeException(glGetShaderInfoLog(fragShader));
    }
    textureProgram = glCreateProgram();
    glAttachShader(textureProgram, vertShader);
    glAttachShader(textureProgram, fragShader);
    glLinkProgram(textureProgram);
    if (glGetProgrami(textureProgram, GL_LINK_STATUS) != GL_TRUE) {
      throw new RuntimeException(glGetProgramInfoLog(textureProgram));
    }
    glDetachShader(textureProgram, vertShader);
    glDetachShader(textureProgram, fragShader);
    glDeleteShader(vertShader);
    glDeleteShader(fragShader);

    vertShader = glCreateShader(GL_VERTEX_SHADER);
    glShaderSource(vertShader, readFile("std.vert"));
    glCompileShader(vertShader);
    if (glGetShaderi(vertShader, GL_COMPILE_STATUS) != GL_TRUE) {
      throw new RuntimeException(glGetShaderInfoLog(vertShader));
    }
    fragShader = glCreateShader(GL_FRAGMENT_SHADER);
    glShaderSource(fragShader, readFile("std.frag"));
    glCompileShader(fragShader);
    if (glGetShaderi(fragShader, GL_COMPILE_STATUS) != GL_TRUE) {
      throw new RuntimeException(glGetShaderInfoLog(fragShader));
    }
    program = glCreateProgram();
    glAttachShader(program, vertShader);
    glAttachShader(program, fragShader);
    glLinkProgram(program);
    if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
      throw new RuntimeException(glGetProgramInfoLog(program));
    }
    glDetachShader(program, vertShader);
    glDetachShader(program, fragShader);
    glDeleteShader(vertShader);
    glDeleteShader(fragShader);
    glUseProgram(program);
  }

  private void initGL() {
    GL.createCapabilities(true);

    glEnable(GL_CULL_FACE);
    glCullFace(GL_BACK);
    glFrontFace(GL_CCW);

    glEnable(GL_DEPTH_TEST);
    glDepthMask(true);
    glDepthFunc(GL_LEQUAL);
    glDepthRange(0, 1);
    glEnable(GL_DEPTH_CLAMP);

    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glEnable(GL_BLEND);

    glClearColor(0, 0, 0, 0);
    glClearDepth(1);

    rawVao = glGenVertexArrays();

    backgroundVao = glGenVertexArrays();

    vao = glGenVertexArrays();
    glBindVertexArray(vao);
  }

  @SuppressWarnings("resource")
  private void initGLFW() throws IOException {
    Configuration.EGL_EXPLICIT_INIT.set(Boolean.FALSE);
    Configuration.OPENCL_EXPLICIT_INIT.set(Boolean.FALSE);
    Configuration.OPENGLES_EXPLICIT_INIT.set(Boolean.FALSE);
    Configuration.VULKAN_EXPLICIT_INIT.set(Boolean.FALSE);

    if (!glfwInit()) {
      throw new IllegalStateException("Unable to initialize GLFW");
    }

    glfwSetErrorCallback(GLFWErrorCallback.createPrint());

    glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
    glfwWindowHint(GLFW_AUTO_ICONIFY, GLFW_TRUE);

    // do NOT change this to a try-with block
    GLFWVidMode vidmode;
    vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
    glfwWindowHint(GLFW_RED_BITS, vidmode.redBits());
    glfwWindowHint(GLFW_GREEN_BITS, vidmode.greenBits());
    glfwWindowHint(GLFW_BLUE_BITS, vidmode.blueBits());
    glfwWindowHint(GLFW_REFRESH_RATE, vidmode.refreshRate());
    width = vidmode.width();
    height = vidmode.height();

    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    window = glfwCreateWindow(width, height, Client.GAME_NAME, glfwGetPrimaryMonitor(), NULL);
    glfwIconifyWindow(window);

    BufferedImage image;
    try (InputStream is = Files.newInputStream(Utils.getFile("cursor.png"))) {
      if (is == null) {
        throw new IOException("Cursor image not found.");
      }
      image = ImageIO.read(is);
    }

    // do NOT change this to a try-with block
    GLFWImage cursorImage = GLFWImage.create();
    ByteBuffer cursorIconImage = readImage(image, true, false);
    cursorImage.set(image.getWidth(), image.getHeight(), cursorIconImage);
    long cursor = glfwCreateCursor(cursorImage, 6, 2);
    glfwSetCursor(window, cursor);
    memFree(cursorIconImage);


    try (InputStream is = Files.newInputStream(Utils.getFile("icon.png"))) {
      if (is == null) {
        throw new IOException("Icon image not found.");
      }
      image = ImageIO.read(is);
    }

    // do NOT change this to a try-with block
    GLFWImage iconImage = GLFWImage.create();
    ByteBuffer windowIconImage = readImage(image, true, false);
    iconImage.set(image.getWidth(), image.getHeight(), windowIconImage);
    glfwSetWindowIcon(window, GLFWImage.create(iconImage.sizeof()).put(iconImage).flip());
    memFree(windowIconImage);

    if (window == NULL) {
      throw new RuntimeException("Failed to create the GLFW window. Update your graphics card drivers.");
    }

    glfwSetScrollCallback(window, scrollCallback = new GLFWScrollCallback() {
      @Override
      public void invoke(long window, double xoffset, double yoffset) {
        scroll -= (int) yoffset;
      }
    });

    glfwSetKeyCallback(window, keyCallback = new GLFWKeyCallback() {
      @Override
      public void invoke(long window, int key, int scancode, int action, int mods) {
        if (action == GLFW_PRESS) {
          newKeys.add(key);
          keysState.add(key);
        } else if (action == GLFW_RELEASE) {
          keysState.remove(key);
        }
      }
    });

    glfwSetCursorPosCallback(window, cursorPosCallback = new GLFWCursorPosCallback() {
      @Override
      public void invoke(long window, double xpos, double ypos) {
        mouseX = xpos;
        mouseY = ypos;
      }
    });

    glfwSetMouseButtonCallback(window, mouseButtonCallback = new GLFWMouseButtonCallback() {
      @Override
      public void invoke(long window, int button, int action, int mods) {
        if (action == GLFW_PRESS) {
          newMouse.add(button);
          mouseState.add(button);
        } else if (action == GLFW_RELEASE) {
          mouseState.remove(button);
        }
      }
    });

    glfwMakeContextCurrent(window);
    glfwSwapInterval(1);
  }

  public void pollInput() {
    glfwPollEvents();
  }

  public Set<Integer> flushKeys() {
    if (newKeys.isEmpty()) {
      return Collections.emptySet();
    }
    Set<Integer> lastKeys = Collections.unmodifiableSet(newKeys);
    newKeys = new HashSet<>(2);
    return lastKeys;
  }

  public Set<Integer> flushMouse() {
    if (newMouse.isEmpty()) {
      return Collections.emptySet();
    }
    Set<Integer> lastMouse = Collections.unmodifiableSet(newMouse);
    newMouse = new HashSet<>(3);
    return lastMouse;
  }

  public boolean isKeyDown(int scancode) {
    return keysState.contains(scancode);
  }

  public boolean isMouseDown(int button) {
    return mouseState.contains(button);
  }

  public int flushScroll() {
    int scroll = this.scroll;
    this.scroll = 0;
    return scroll;
  }

  public java.awt.Point.Double getMouse() {
    return new java.awt.Point.Double(mouseX, height - mouseY);
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public void closeRequested() {
    glfwWindowShouldClose(window);
  }

  public void start(BufferedImage image) {
    int imageWidth = image.getWidth();
    int imageHeight = image.getHeight();
    ByteBuffer imageBuffer = readImage(image, false, true);
    texture = glGenTextures();
    glBindTexture(GL_TEXTURE_2D, texture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB8, imageWidth, imageHeight, 0, GL_RGB, GL_UNSIGNED_BYTE, imageBuffer);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
    memFree(imageBuffer);
    float[] backgroundPositions = {0, 0, 0, imageHeight, imageWidth, imageHeight, imageWidth, imageHeight, imageWidth, 0, 0, 0};
    backgroundBuffer = glGenBuffers();

    glBindVertexArray(backgroundVao);
    glBindBuffer(GL_ARRAY_BUFFER, backgroundBuffer);
    try (MemoryStack stack = stackPush()) {
      FloatBuffer fb = stack.mallocFloat(backgroundPositions.length);
      fb.put(backgroundPositions);
      fb.flip();
      glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
    }
    glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);
    glEnableVertexAttribArray(0);
    glBindVertexArray(vao);
  }

  public Window() throws IOException {
    initGLFW();
    initGL();
    initPrograms();
    initUniforms();
    initBuffers();
  }

  public void exit() {
    glDeleteBuffers(backgroundBuffer);
    for (Mesh mesh : meshes) {
      glDeleteBuffers(mesh.buffer);
    }
    for (Integer textureIndex : texturesIndexes) {
      glDeleteTextures(textureIndex);
    }
    glDeleteBuffers(indexLight);
    glDeleteTextures(texture);
    glDeleteVertexArrays(rawVao);
    glDeleteVertexArrays(backgroundVao);
    glDeleteVertexArrays(vao);
    glDeleteProgram(rawProgram);
    glDeleteProgram(program);
    glDeleteProgram(textureProgram);
    memFree(bufferMat4x4);
    memFree(bufferLights);
    glfwDestroyWindow(window);
    glfwTerminate();
  }

  private static Matrix4f calcLookAt(float x, float y) {
    Vector3f eye = new Vector3f(x, (float) (y - cameraDistance * Math.sin(cameraAngle)), (float) (cameraDistance * Math.cos(cameraAngle)));
    Vector3f center = new Vector3f(x, y, 0);
    Vector3f temp = new Vector3f(1, 0, 0);
    temp.cross(eye.sub(center, new Vector3f())).negate();
    Matrix4f v = new Matrix4f().setLookAt(eye, center, temp);
    return v;
  }

  @SuppressWarnings("null")
  private void loadModel(Model model) throws IOException {
    Path resourcePath = Utils.getFile("models/" + model.modelFile + ".plyzip");
    if (resourcePath == null) {
      throw new IOException("Model " + model + " not found.");
    }

    // optimization: directly add elements to a buffer for opengl instead of storing them to a temporary array

    float[] vertices = null;
    FloatBuffer buffer = null;
    int bufferLength = -1;
    int verticesCurrent = 0;

    ZipInputStream is = new ZipInputStream(Files.newInputStream(resourcePath));
    is.getNextEntry();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      while (true) {
        String line = reader.readLine();
        if (line == null) {
          break;
        }
        String[] components = line.split("\\s+");
        switch (components[0]) {
          case "element":
            if (components[1].equals("vertex")) {
              vertices = new float[Integer.parseUnsignedInt(components[2]) * 9];
            } else if (components[1].equals("face")) {
              bufferLength = Integer.parseUnsignedInt(components[2]) * 3 * 9;
              buffer = memAllocFloat(bufferLength);
            }
            break;
          case "3":
            for (int i = 1; i <= 3; i++) {
              int vertex = Integer.parseUnsignedInt(components[i]);
              for (int j = 0; j < 9; j++) {
                buffer.put(vertices[vertex * 9 + j]);
              }
            }
            break;
          default:
            try {
              Float.parseFloat(components[0]);
            } catch (NumberFormatException e) {
              break;
            }
            for (int i = 0; i < 6; i++) {
              vertices[verticesCurrent++] = Float.parseFloat(components[i]);
            }
            for (int i = 6; i < 9; i++) {
              vertices[verticesCurrent++] = Integer.parseUnsignedInt(components[i]) / 256.0f;
            }
            break;
        }
      }
    }
    buffer.flip();
    int indexBuffer = glGenBuffers();
    glBindBuffer(GL_ARRAY_BUFFER, indexBuffer);
    glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    memFree(buffer);
    meshes[model.ordinal()] = new Mesh(indexBuffer, bufferLength / 9);
  }

  private static String readFile(String name) {
    try (BufferedReader reader = Files.newBufferedReader(Utils.getFile(name))) {
      StringBuilder file = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        file.append(line + "\n");
      }
      return file.toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static ByteBuffer readImage(BufferedImage image, boolean alpha, boolean inverted) {
    // extract the pixel colors from the image and put it in a buffer for opengl
    // heavy optimization to avoid allocating 3 times the size of an image
    // based on BufferedImage#getRGB

    int imageHeight = image.getHeight();
    int imageWidth = image.getWidth();
    ByteBuffer buffer = MemoryUtil.memAlloc(imageWidth * imageHeight * (alpha ? 4 : 3));
    Raster raster = image.getRaster();
    int nbands = raster.getNumBands();
    int dataType = raster.getDataBuffer().getDataType();
    Object data;
    ColorModel colorModel = image.getColorModel();
    switch (dataType) {
      case DataBuffer.TYPE_BYTE:
        data = new byte[nbands];
        break;
      case DataBuffer.TYPE_USHORT:
        data = new short[nbands];
        break;
      case DataBuffer.TYPE_INT:
        data = new int[nbands];
        break;
      case DataBuffer.TYPE_FLOAT:
        data = new float[nbands];
        break;
      case DataBuffer.TYPE_DOUBLE:
        data = new double[nbands];
        break;
      default:
        throw new IllegalArgumentException("Unknown data buffer type: " + dataType);
    }
    for (int y = 0; y < imageHeight; y++) {
      for (int x = 0; x < imageWidth; x++) {
        if (inverted) {
          raster.getDataElements(x, imageHeight - 1 - y, data);
        } else {
          raster.getDataElements(x, y, data);
        }
        buffer.put((byte) colorModel.getRed(data));
        buffer.put((byte) colorModel.getGreen(data));
        buffer.put((byte) colorModel.getBlue(data));
        if (alpha) {
          buffer.put((byte) colorModel.getAlpha(data));
        }
      }
    }

    buffer.flip();

    return buffer;
  }

  private static int[] getDigits(int a) {
    int[] digits = new int[10];
    int i = a;
    int j = 0;
    while (i != 0) {
      digits[j++] = i % 10;
      i /= 10;
    }
    int[] ndigits = new int[j];
    for (int k = 0; k < j; k++) {
      ndigits[j - k - 1] = digits[k];
    }
    return ndigits;
  }

}
