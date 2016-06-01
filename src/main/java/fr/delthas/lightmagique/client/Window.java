package fr.delthas.lightmagique.client;

import static org.lwjgl.glfw.GLFW.GLFW_BLUE_BITS;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_GREEN_BITS;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RED_BITS;
import static org.lwjgl.glfw.GLFW.GLFW_REFRESH_RATE;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_CCW;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_LEQUAL;
import static org.lwjgl.opengl.GL11.GL_RGB;
import static org.lwjgl.opengl.GL11.GL_RGB8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_TRUE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glClearDepth;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDepthFunc;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDepthRange;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glFrontFace;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL20.glDeleteShader;
import static org.lwjgl.opengl.GL20.glDetachShader;
import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderi;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL20.glUniformMatrix3fv;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL31.glGetUniformBlockIndex;
import static org.lwjgl.opengl.GL31.glUniformBlockBinding;
import static org.lwjgl.opengl.GL32.GL_DEPTH_CLAMP;
import static org.lwjgl.opengl.GL44.GL_DYNAMIC_STORAGE_BIT;
import static org.lwjgl.opengl.GL45.glCreateBuffers;
import static org.lwjgl.opengl.GL45.glCreateTextures;
import static org.lwjgl.opengl.GL45.glCreateVertexArrays;
import static org.lwjgl.opengl.GL45.glEnableVertexArrayAttrib;
import static org.lwjgl.opengl.GL45.glNamedBufferStorage;
import static org.lwjgl.opengl.GL45.glNamedBufferSubData;
import static org.lwjgl.opengl.GL45.glTextureStorage2D;
import static org.lwjgl.opengl.GL45.glTextureSubImage2D;
import static org.lwjgl.opengl.GL45.glVertexArrayAttribBinding;
import static org.lwjgl.opengl.GL45.glVertexArrayAttribFormat;
import static org.lwjgl.opengl.GL45.glVertexArrayVertexBuffer;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
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

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

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
// import static org.lwjgl.opengl.GL40.*;
// import static org.lwjgl.opengl.GL41.*;
// import static org.lwjgl.opengl.GL42.*;
// import static org.lwjgl.opengl.GL43.*;
// import static org.lwjgl.opengl.GL44.*;
// import static org.lwjgl.opengl.GL45.*;
// import static org.lwjgl.system.MemoryUtil.*;

@SuppressWarnings({"unused", "static-method"})
class Window {

  public enum Model {
    PLAYER("player"), MONSTER("monster"), BALL("ball");

    private String modelFile;

    private Model(String modelFile) {
      this.modelFile = modelFile;
    }
  }

  private static class Mesh {
    public final int buffer;
    public final int bindingPoint;
    public final int vertices;

    public Mesh(int buffer, int bindingPoint, int vertices) {
      this.buffer = buffer;
      this.bindingPoint = bindingPoint;
      this.vertices = vertices;
    }
  }

  private long window;
  private GLFWKeyCallback keyCallback;
  private GLFWCursorPosCallback cursorPosCallback;
  private GLFWMouseButtonCallback mouseButtonCallback;

  private int vao;
  private int indexP, indexVM, indexVMNormal;
  private int indexTexMatrix;
  private int indexLight;
  private int backgroundBuffer, texture;
  private int program, textureProgram;


  private static final int maxNumberOfLights = 1000;
  private static final float cameraAngle = 0;
  private static final float cameraDistance = 5000;
  private float cameraX = 0, cameraY = 0;

  private EnumMap<Model, Mesh> meshes = new EnumMap<>(Model.class);

  private Set<Integer> keysState = new HashSet<>(20);
  private Set<Integer> mouseState = new HashSet<>(3);
  private Set<Integer> newKeys = new HashSet<>(2);
  private Set<Integer> newMouse = new HashSet<>(3);

  private double mouseX, mouseY;
  private int width, height;
  private BufferedImage image;

  private List<RenderEntity> entities;
  private List<Light> lights;

  // Reuse buffers to reduce RAM usage
  private FloatBuffer bufferMat4x4;
  private ByteBuffer bufferLights;

  public void renderLight(Light light) {
    lights.add(light);
  }

  public void renderEntity(RenderEntity entity) {
    entities.add(entity);
  }

  /**
   * @param x Coordonnée x du point au centre de l'écran
   * @param y Coordonnée y du point au centre de l'écran
   */
  public void beginRender(float x, float y) {
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    cameraX = x;
    cameraY = y;
    entities = new LinkedList<>();
    lights = new LinkedList<>();
  }

  public void endRender() {
    // actually start render here

    // 1. Update the light buffer

    Color ambientColor = new Color(0.2f, 0.2f, 0.2f);
    float midAttenuationDistance = 3000f;
    float maxIntensity = 1.5f;
    float gamma = 2.2f;

    bufferLights.clear();
    bufferLights.putFloat(ambientColor.getRed() / 256f).putFloat(ambientColor.getGreen() / 256f).putFloat(ambientColor.getBlue() / 256f)
        .putFloat(1.0f);
    bufferLights.putFloat(1 / (midAttenuationDistance * midAttenuationDistance));
    bufferLights.putFloat(maxIntensity);
    bufferLights.putFloat(1 / gamma);
    bufferLights.putInt(lights.size());

    for (Light light : lights) {
      Vector4f lightCameraPosition = calcLookAt(cameraX, cameraY).transform(new Vector4f(light.x, light.y, 150, 1));
      bufferLights.putFloat(lightCameraPosition.x).putFloat(lightCameraPosition.y).putFloat(lightCameraPosition.z).putFloat(lightCameraPosition.w);
      bufferLights.putFloat(light.color.getRed() / 256f).putFloat(light.color.getGreen() / 256f).putFloat(light.color.getBlue() / 256f)
          .putFloat(1.0f);
      bufferLights.putFloat(light.x).putFloat(light.y);
      bufferLights.putFloat(0).putFloat(0); // padding
    }

    bufferLights.flip();
    glNamedBufferSubData(indexLight, 0, bufferLights);

    // 2. Draw background

    glDisable(GL_CULL_FACE);
    glDisable(GL_DEPTH_TEST);
    glDepthMask(false);
    glUseProgram(textureProgram);
    glBindTexture(GL_TEXTURE_2D, texture);
    glVertexArrayAttribBinding(vao, 0, 0);
    glVertexArrayAttribFormat(vao, 0, 2, GL_FLOAT, false, 0);
    glBindBuffer(GL_ARRAY_BUFFER, backgroundBuffer);
    Matrix4f pTex = new Matrix4f().scale(2f / width, 2f / height, 0).translate(-cameraX, -cameraY, 0);
    bufferMat4x4.clear();
    glUniformMatrix4fv(indexTexMatrix, false, pTex.get(bufferMat4x4));
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glVertexArrayAttribFormat(vao, 0, 3, GL_FLOAT, false, 0);
    glVertexArrayAttribFormat(vao, 1, 3, GL_FLOAT, false, Float.BYTES * 3);
    glVertexArrayAttribFormat(vao, 2, 3, GL_FLOAT, false, Float.BYTES * 6);
    glUseProgram(program);
    glEnable(GL_CULL_FACE);
    glEnable(GL_DEPTH_TEST);
    glDepthMask(true);

    // 3. Draw entities

    Matrix4f p = new Matrix4f().setOrthoSymmetric(width, height, 0.1f, 10000);
    Matrix4f v = calcLookAt(cameraX, cameraY);
    bufferMat4x4.clear();
    glUniformMatrix4fv(indexP, false, p.get(bufferMat4x4));

    for (RenderEntity entity : entities) {
      Mesh mesh = meshes.get(entity.model);
      Matrix4f m = new Matrix4f().translate(entity.x, entity.y, 0).rotateX((float) (-Math.PI / 6)).rotateZ(entity.angle + (float) Math.PI / 2)
          .scale(entity.scale);
      Matrix4f vm = v.mul(m, new Matrix4f());
      Matrix3f vmNormal = new Matrix3f(vm);
      bufferMat4x4.clear();
      glUniformMatrix4fv(indexVM, false, vm.get(bufferMat4x4));
      glUniformMatrix3fv(indexVMNormal, false, (FloatBuffer) vmNormal.get(bufferMat4x4).limit(9));
      glVertexArrayAttribBinding(vao, 0, mesh.bindingPoint);
      glVertexArrayAttribBinding(vao, 1, mesh.bindingPoint);
      glVertexArrayAttribBinding(vao, 2, mesh.bindingPoint);
      glBindBuffer(GL_ARRAY_BUFFER, mesh.buffer);
      glDrawArrays(GL_TRIANGLES, 0, mesh.vertices);
    }

    glfwSwapBuffers(window);
  }

  private void initBuffers() throws IOException {
    glUseProgram(textureProgram);

    int imageWidth = image.getWidth();
    int imageHeight = image.getHeight();

    ByteBuffer imageBuffer = readImage(image);

    // we don't need the image anymore
    // remove the reference *and* call GC
    image = null;
    System.gc();

    texture = glCreateTextures(GL_TEXTURE_2D);
    glTextureStorage2D(texture, 1, GL_RGB8, imageWidth, imageHeight);
    glTextureSubImage2D(texture, 0, 0, 0, imageWidth, imageHeight, GL_RGB, GL_UNSIGNED_BYTE, imageBuffer);

    float[] backgroundPositions = {0, 0, 0, imageHeight, imageWidth, imageHeight, imageWidth, imageHeight, imageWidth, 0, 0, 0};
    backgroundBuffer = glCreateBuffers();
    FloatBuffer fb = BufferUtils.createFloatBuffer(backgroundPositions.length);
    fb.put(backgroundPositions);
    fb.flip();

    glNamedBufferStorage(backgroundBuffer, fb, 0);
    glVertexArrayVertexBuffer(vao, 0, backgroundBuffer, 0, Float.BYTES * 2);
    glVertexArrayAttribFormat(vao, 0, 2, GL_FLOAT, false, 0);
    glUseProgram(program);

    for (int i = 0; i < Model.values().length; i++) {
      loadModel(Model.values()[i], i + 1);
    }
    // clean data allocated when reading models
    System.gc();

    glVertexArrayAttribFormat(vao, 0, 3, GL_FLOAT, false, 0);
    glVertexArrayAttribFormat(vao, 1, 3, GL_FLOAT, false, Float.BYTES * 3);
    glVertexArrayAttribFormat(vao, 2, 3, GL_FLOAT, false, Float.BYTES * 6);
    glEnableVertexArrayAttrib(vao, 0);
    glEnableVertexArrayAttrib(vao, 1);
    glEnableVertexArrayAttrib(vao, 2);

    indexLight = glCreateBuffers();
    glNamedBufferStorage(indexLight, 32 + maxNumberOfLights * 48, GL_DYNAMIC_STORAGE_BIT);
    glBindBufferBase(GL_UNIFORM_BUFFER, 0, indexLight);

    bufferMat4x4 = BufferUtils.createFloatBuffer(16);
    bufferLights = BufferUtils.createByteBuffer(32 + 48 * maxNumberOfLights);
  }

  private void initUniforms() {
    indexP = glGetUniformLocation(program, "p");
    indexVM = glGetUniformLocation(program, "vm");
    indexVMNormal = glGetUniformLocation(program, "vmNormal");
    glUniformBlockBinding(program, glGetUniformBlockIndex(program, "Light"), 0);
    glUseProgram(textureProgram);
    indexTexMatrix = glGetUniformLocation(textureProgram, "texMatrix");
    glUniformBlockBinding(textureProgram, glGetUniformBlockIndex(textureProgram, "Light"), 0);
    glUseProgram(program);
  }

  private void initPrograms() {
    int vertShader = glCreateShader(GL_VERTEX_SHADER);
    glShaderSource(vertShader, readFile("tex.vert"));
    glCompileShader(vertShader);
    if (glGetShaderi(vertShader, GL_COMPILE_STATUS) != GL_TRUE) {
      throw new RuntimeException(glGetShaderInfoLog(vertShader));
    }
    int fragShader = glCreateShader(GL_FRAGMENT_SHADER);
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

    glClearColor(0, 0, 0, 0);
    glClearDepth(1);

    vao = glCreateVertexArrays();
    glBindVertexArray(vao);
  }

  private void initGLFW() {
    GLFWErrorCallback.createString((error, description) -> {
      System.err.println("Erreur GFLW : " + description);
    });
    if (glfwInit() != GL_TRUE) {
      throw new IllegalStateException("Unable to initialize GLFW");
    }

    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 5);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);

    GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
    glfwWindowHint(GLFW_RED_BITS, vidmode.redBits());
    glfwWindowHint(GLFW_GREEN_BITS, vidmode.greenBits());
    glfwWindowHint(GLFW_BLUE_BITS, vidmode.blueBits());
    glfwWindowHint(GLFW_REFRESH_RATE, vidmode.refreshRate());

    window = glfwCreateWindow(vidmode.width(), vidmode.height(), Client.GAME_NAME, glfwGetPrimaryMonitor(), NULL);
    width = vidmode.width();
    height = vidmode.height();

    if (window == NULL) {
      throw new RuntimeException("Failed to create the GLFW window");
    }

    glfwSetKeyCallback(window, keyCallback = new GLFWKeyCallback() {
      @Override
      public void invoke(long window, int key, int scancode, int action, int mods) {
        if (action == GLFW_PRESS) {
          newKeys.add(scancode);
          keysState.add(scancode);
        } else if (action == GLFW_RELEASE) {
          keysState.remove(scancode);
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

  public java.awt.Point.Double getMouse() {
    return new java.awt.Point.Double(mouseX, height - mouseY);
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public boolean closeRequested() {
    return glfwWindowShouldClose(window) == GL_TRUE;
  }

  public void start() throws IOException {
    initGLFW();
    initGL();
    initPrograms();
    initUniforms();
    initBuffers();
    glfwShowWindow(window);
  }

  public void exit() {
    glDeleteBuffers(backgroundBuffer);
    for (Mesh mesh : meshes.values()) {
      glDeleteBuffers(mesh.buffer);
    }
    glDeleteBuffers(indexLight);
    glDeleteTextures(texture);
    glDeleteVertexArrays(vao);
    glDeleteProgram(program);
    glDeleteProgram(textureProgram);
    glfwTerminate();
  }

  public Window(BufferedImage image) {
    this.image = image;
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
  private void loadModel(Model model, int binding) throws IOException {
    Path path = null;
    URL resource = Window.class.getResource("/models/" + model.modelFile + ".plyzip");
    if (resource != null) {
      try {
        path = Paths.get(resource.toURI());
      } catch (URISyntaxException e) {
        // silently ignore
      }
    }
    if (path == null) { // search in the parent directory (useful for jar files)
      try {
        Path jarPath = Paths.get(Window.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isRegularFile(jarPath)) {
          path = jarPath.getParent().resolve("models" + ".plyzip").resolve(model.modelFile);
        }
      } catch (URISyntaxException e) {
        // silently ignore
      }
    }
    if (path == null) {
      throw new IOException("Model " + model + " not found.");
    }

    // optimization: directly add elements to a buffer for opengl instead of storing them to a temporary array

    float[] vertices = null;
    FloatBuffer buffer = null;
    int bufferLength = -1;
    int verticesCurrent = 0;

    ZipInputStream is = new ZipInputStream(Files.newInputStream(path));
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
              buffer = BufferUtils.createFloatBuffer(bufferLength);
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
    int indexBuffer = glCreateBuffers();
    glNamedBufferStorage(indexBuffer, buffer, 0);
    glVertexArrayVertexBuffer(vao, binding, indexBuffer, 0, Float.BYTES * 9);
    meshes.put(model, new Mesh(indexBuffer, binding, bufferLength / 9));
  }

  private static String readFile(String name) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(Window.class.getResourceAsStream("/" + name)))) {
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

  private static ByteBuffer readImage(BufferedImage image) {
    // extract the pixel colors from the image and put it in a buffer for opengl
    // heavy optimization to avoid allocating 3 times the size of an image
    // based on BufferedImage#getRGB

    int imageHeight = image.getHeight();
    int imageWidth = image.getWidth();
    ByteBuffer buffer = BufferUtils.createByteBuffer(imageWidth * imageHeight * 3);
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
    for (int y = imageHeight - 1; y >= 0; y--) {
      for (int x = 0; x < imageWidth; x++) {
        raster.getDataElements(x, y, data);
        buffer.put((byte) colorModel.getRed(data));
        buffer.put((byte) colorModel.getGreen(data));
        buffer.put((byte) colorModel.getBlue(data));
      }
    }

    buffer.flip();

    return buffer;
  }

}
