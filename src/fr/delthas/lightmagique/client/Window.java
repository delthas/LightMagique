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
import static org.lwjgl.opengl.GL40.*;
import static org.lwjgl.opengl.GL41.*;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL44.*;
import static org.lwjgl.opengl.GL45.*;
import static org.lwjgl.system.MemoryUtil.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.lwjgl.system.MemoryUtil;

@SuppressWarnings({"unused", "static-method"})
class Window {

  private GLDebugMessageCallback debugCallback = new GLDebugMessageCallback() {
    @Override
    public void invoke(int source, int type, int id, int severity, int length, long message, long userParam) {
      String sourceS;
      switch (source) {
        case GL_DEBUG_SOURCE_API:
          sourceS = "API";
          break;
        case GL_DEBUG_SOURCE_APPLICATION:
          sourceS = "Application";
          break;
        case GL_DEBUG_SOURCE_SHADER_COMPILER:
          sourceS = "Shader_compiler";
          break;
        case GL_DEBUG_SOURCE_THIRD_PARTY:
          sourceS = "Third_party";
          break;
        case GL_DEBUG_SOURCE_WINDOW_SYSTEM:
          sourceS = "Window_system";
          break;
        case GL_DEBUG_SOURCE_OTHER:
        default:
          sourceS = "Autre";
          break;
      }
      String typeS;
      switch (type) {
        case GL_DEBUG_TYPE_ERROR:
          typeS = "Error";
          break;
        case GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR:
          typeS = "Deprecated_bhvr";
          break;
        case GL_DEBUG_TYPE_MARKER:
          typeS = "Marker";
          break;
        case GL_DEBUG_TYPE_PERFORMANCE:
          typeS = "Performance";
          break;
        case GL_DEBUG_TYPE_POP_GROUP:
          typeS = "Pop_group";
          break;
        case GL_DEBUG_TYPE_PUSH_GROUP:
          typeS = "Push_group";
          break;
        case GL_DEBUG_TYPE_PORTABILITY:
          typeS = "Portability";
          break;
        case GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR:
          typeS = "Undefined_bhvr";
          break;
        case GL_DEBUG_TYPE_OTHER:
        default:
          typeS = "Autre";
          break;
      }
      String severityS;
      switch (severity) {
        case GL_DEBUG_SEVERITY_HIGH:
          severityS = "Haute";
          break;
        case GL_DEBUG_SEVERITY_MEDIUM:
          severityS = "Moyen";
          break;
        case GL_DEBUG_SEVERITY_LOW:
          severityS = "Bas";
          break;
        case GL_DEBUG_SEVERITY_NOTIFICATION:
          severityS = "Notification";
          break;
        default:
          severityS = "Autre";
          break;
      }
      String printMessage = "Source: " + sourceS + " - Type: " + typeS + " - Sévérité: " + severityS + " - Message: "
          + MemoryUtil.memDecodeUTF8(MemoryUtil.memByteBuffer(message, length));
      if (severity == GL_DEBUG_SEVERITY_NOTIFICATION) {
        System.out.println(printMessage);
      } else {
        System.err.println(printMessage);
        Thread.dumpStack();
      }
    }
  };

  private long window;
  private int vao, circleBuffer, triangleBuffer, backgroundBuffer;
  private int indexX, indexY, indexRotateMatrix, indexScale, indexColor, indexOffset;
  private int program, textureProgram;
  int texture;

  private static final float[] circlePositions;
  static {
    int max = 20;
    float[] positions = new float[2 * (max + 2)];
    positions[0] = 0.0f;
    positions[1] = 0.0f;
    for (int i = 0; i <= max; i++) {
      positions[2 * i + 2] = (float) Math.cos(Math.PI * 2 * i / max);
      positions[2 * i + 3] = (float) Math.sin(Math.PI * 2 * i / max);
    }
    circlePositions = positions;
  }
  private static final float[] trianglePositions = {-0.5f, -0.5f, -0.5f, 0.5f, 1.0f, 0.0f};

  private static final float[] backgroundPositions = {-1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f};

  private Set<Integer> keysState = new HashSet<>(20);
  private Set<Integer> mouseState = new HashSet<>(3);
  private Set<Integer> newKeys = new HashSet<>(2);
  private Set<Integer> newMouse = new HashSet<>(3);
  private double mouseX, mouseY;
  private int width, height;

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

  public void beginRender() {
    glClear(GL_COLOR_BUFFER_BIT);
  }

  private void prepareUniforms(float x, float y, float angle, float scale, Color color) {
    glUniform1f(indexX, x);
    glUniform1f(indexY, y);
    float cosine = (float) Math.cos(angle);
    float sine = (float) Math.sin(angle);
    float[] matrixData = {cosine, sine, -sine, cosine};
    glUniformMatrix2fv(indexRotateMatrix, false, (FloatBuffer) BufferUtils.createFloatBuffer(matrixData.length).put(matrixData).flip());
    glUniform1f(indexScale, scale);
    glUniform3f(indexColor, color.getRed() / 256f, color.getGreen() / 256f, color.getBlue() / 256f);
  }

  public void renderTriangle(float x, float y, float angle, float scale, Color color) {
    prepareUniforms(x, y, angle, scale, color);
    glVertexArrayAttribBinding(vao, 0, 1);
    glBindBuffer(GL_ARRAY_BUFFER, triangleBuffer);
    glDrawArrays(GL_TRIANGLES, 0, trianglePositions.length / 2);
  }

  public void renderCircle(float x, float y, float angle, float scale, Color color) {
    prepareUniforms(x, y, angle, scale, color);
    glVertexArrayAttribBinding(vao, 0, 0);
    glBindBuffer(GL_ARRAY_BUFFER, circleBuffer);
    glDrawArrays(GL_TRIANGLE_FAN, 0, (circlePositions.length - 4) / 2);
  }

  /**
   * @param x Le début de l'image en x (coin en bas à gauche)
   * @param y Le début de l'image en y (coin en bas à gauche)
   */
  public void renderImage(int x, int y) {
    glUseProgram(textureProgram);
    glBindTexture(GL_TEXTURE_2D, texture);
    glVertexArrayAttribBinding(vao, 0, 2);
    glBindBuffer(GL_ARRAY_BUFFER, backgroundBuffer);
    glUniform2i(indexOffset, x, y);
    glDrawArrays(GL_TRIANGLES, 0, backgroundPositions.length / 2);
    glUseProgram(program);
  }

  public void endRender() {
    glfwSwapBuffers(window);
  }

  public void start() {
    GLFWErrorCallback.createString((error, description) -> {
      System.err.println("hou " + description);
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

    glfwSetKeyCallback(window, new GLFWKeyCallback() {
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

    glfwSetCursorPosCallback(window, new GLFWCursorPosCallback() {
      @Override
      public void invoke(long window, double xpos, double ypos) {
        mouseX = xpos;
        mouseY = ypos;
      }
    });

    glfwSetMouseButtonCallback(window, new GLFWMouseButtonCallback() {
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

    GL.createCapabilities(true);

    // TODO disable debug in later "production" builds
    glEnable(GL_DEBUG_OUTPUT);
    glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS);
    glDebugMessageCallback(debugCallback, 0L);

    glClearColor(0, 0, 0, 0);

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

    indexOffset = glGetUniformLocation(textureProgram, "offset");

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

    vao = glCreateVertexArrays();
    glBindVertexArray(vao);

    glEnableVertexArrayAttrib(vao, 0);

    circleBuffer = glCreateBuffers();
    glNamedBufferStorage(circleBuffer, (FloatBuffer) BufferUtils.createFloatBuffer(circlePositions.length).put(circlePositions).flip(), 0);
    glVertexArrayVertexBuffer(vao, 0, circleBuffer, 0, Float.BYTES * 2);
    glVertexArrayAttribBinding(vao, 0, 0);
    glVertexArrayAttribFormat(vao, 0, 2, GL_FLOAT, false, 0);

    triangleBuffer = glCreateBuffers();
    glNamedBufferStorage(triangleBuffer, (FloatBuffer) BufferUtils.createFloatBuffer(trianglePositions.length).put(trianglePositions).flip(), 0);
    glVertexArrayVertexBuffer(vao, 1, triangleBuffer, 0, Float.BYTES * 2);
    glVertexArrayAttribBinding(vao, 0, 1);
    glVertexArrayAttribFormat(vao, 0, 2, GL_FLOAT, false, 0);

    backgroundBuffer = glCreateBuffers();
    glNamedBufferStorage(backgroundBuffer, (FloatBuffer) BufferUtils.createFloatBuffer(backgroundPositions.length).put(backgroundPositions).flip(),
        0);
    glVertexArrayVertexBuffer(vao, 2, backgroundBuffer, 0, Float.BYTES * 2);
    glVertexArrayAttribBinding(vao, 0, 2);
    glVertexArrayAttribFormat(vao, 2, 2, GL_FLOAT, false, 0);

    indexX = glGetUniformLocation(program, "x");
    indexY = glGetUniformLocation(program, "y");
    indexRotateMatrix = glGetUniformLocation(program, "rotateMatrix");
    indexScale = glGetUniformLocation(program, "scale");
    indexColor = glGetUniformLocation(program, "color");

    int indexProjectMatrix = glGetUniformLocation(program, "projectMatrix");
    float[] projectMatrixData = {2.0f / width, 0, 0, -1, 0, 2.0f / height, 0, -1, 0, 0, 1.0f, 0, 0, 0, 0, 1.0f};
    glUniformMatrix4fv(indexProjectMatrix, true, (FloatBuffer) BufferUtils.createFloatBuffer(projectMatrixData.length).put(projectMatrixData).flip());

    glfwShowWindow(window);

  }

  public void setBackground(BufferedImage image) {
    glUseProgram(textureProgram);
    int[] pixels = new int[image.getWidth() * image.getHeight()];
    image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());

    ByteBuffer buffer = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 3);

    for (int y = image.getHeight() - 1; y >= 0; y--) {
      for (int x = 0; x < image.getWidth(); x++) {
        int pixel = pixels[y * image.getWidth() + x];
        buffer.put((byte) (pixel >> 16 & 0xFF)); // Red component
        buffer.put((byte) (pixel >> 8 & 0xFF)); // Green component
        buffer.put((byte) (pixel & 0xFF)); // Blue component
      }
    }
    buffer.flip();

    texture = glCreateTextures(GL_TEXTURE_2D);
    glTextureStorage2D(texture, 1, GL_RGB8, image.getWidth(), image.getHeight());
    glTextureSubImage2D(texture, 0, 0, 0, image.getWidth(), image.getHeight(), GL_RGB, GL_UNSIGNED_BYTE, buffer);
  }

  public void exit() {
    glDeleteBuffers(circleBuffer);
    glDeleteBuffers(triangleBuffer);
    glDeleteBuffers(backgroundBuffer);
    glDeleteVertexArrays(vao);
    glDeleteProgram(program);
    glDeleteProgram(textureProgram);
    glfwTerminate();
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

}
