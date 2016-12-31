package fr.delthas.lightmagique.client;

import fr.delthas.lightmagique.client.SoundManager.Sound;
import fr.delthas.lightmagique.client.Window.Model;
import fr.delthas.lightmagique.shared.*;
import fr.delthas.network.ClientConnection;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Client {

  public static final String GAME_NAME = "LightMagique";
  private static final String DEFAULT_ADDRESS = "192.168.0.11";
  private static final double lookAroundFactor = 0.2;
  private Properties properties;
  private State state;
  private Window window;
  private SoundManager soundManager;
  private int screenWidth, screenHeight;
  private ClientConnection clientConnection;
  private ByteBuffer sendBuffer;
  private ByteBuffer receiveBuffer;
  private int playerId = -1;
  private int sendCount;
  private int levelUpPosition = 0;
  private int xp;
  private int wave = 0;
  private boolean waveActive = false;
  private long changeWaveStateTime;
  private boolean closeWithoutSendingExit = false;
  private boolean exited = false;

  public static void main(String[] args) {
    Utils.getFile("cursor.png");
    InetAddress address = null;
    try {
      address = InetAddress.getByName(DEFAULT_ADDRESS);
    } catch (UnknownHostException e) {
      // silently ignore (will be caught below)
    }
    int port = Properties.DEFAULT_PORT;

    String usageString = "Arguments not recognized. Usage: [address:[port]] (address has to be a hostname or IP address, and 1<=port<=65535).";
    if (args.length > 1) {
      System.out.println(usageString);
    } else if (args.length == 1) {
      Pattern patternAddress = Pattern.compile("([0-9a-zA-Z\\.]+)(?:\\:([0-9]+))?");
      Matcher matcher = patternAddress.matcher(args[0]);
      if (matcher.matches()) {
        try {
          address = InetAddress.getByName(matcher.group(1));
        } catch (UnknownHostException e) {
          System.out.println("IP address not recognized or hostname not reachable.");
        }
        String portString = matcher.group(2);
        if (portString != null) {
          try {
            port = Integer.parseUnsignedInt(portString);
            if (port <= 0 || port >= 65536) {
              throw new NumberFormatException("Port in wrong range");
            }
          } catch (NumberFormatException e) {
            System.out.println(usageString);
          }
        }
      } else {
        // if we get a path, ignore it
        try {
          Paths.get(args[0]);
        } catch (InvalidPathException e) {
          System.out.println(usageString);
        }
      }
    }
    try {
      if (address == null) {
        throw new IOException("No valid IP or hostname found in the default or in the eventually passed arguments. "
                + "Try checking your connection if you're connecting to a remote server.");
      }
      InetSocketAddress socket = new InetSocketAddress(address, port);
      System.out.println("Starting client on address: " + address + " port:" + port);
      new Client().start(socket);
    } catch (Exception e) {
      System.err.println("An exception has occured. This stack trace has been copied to your clipboard.");
      e.printStackTrace(System.err);
      StringWriter stringWriter = new StringWriter();
      @SuppressWarnings("resource")
      PrintWriter printWriter = new PrintWriter(stringWriter);
      e.printStackTrace(printWriter);
      printWriter.flush();
      printWriter.close();
      StringSelection stringSelection = new StringSelection(stringWriter.toString());
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
    }
  }

  private void start(InetSocketAddress socket) throws IOException {
    window = new Window();
    screenWidth = window.getWidth();
    screenHeight = window.getHeight();
    soundManager = new SoundManager();
    clientConnection = new ClientConnection(Properties.PROTOCOL_ID, Properties.TIMEOUT, Properties.PACKET_MAX_SIZE);
    sendBuffer = clientConnection.getSendBuffer();
    receiveBuffer = clientConnection.getReceiveBuffer();
    state = new State(receiveBuffer, sendBuffer);
    soundManager.start();
    window.start(state.getMap().getAndForgetMapImage());
    System.gc();
    clientConnection.start(socket);
    sendBuffer.put((byte) 0);
    clientConnection.sendPacket();
    boolean propertiesReceived = false;
    boolean startReceived = false;
    while (!startReceived || !propertiesReceived) {
      clientConnection.receivePacket(true);
      switch (receiveBuffer.get()) {
        case (byte) 14:
          if (!propertiesReceived) {
            properties = new Properties(receiveBuffer);
            state.initialize(properties);
            Shooter.initialize(properties);
            propertiesReceived = true;
          }
          break;
        case (byte) 15:
          if (!startReceived) {
            playerId = receiveBuffer.getShort();
            startReceived = true;
          }
          break;
        default:
      }
    }
    state.getPlayer(playerId).createPlayer(0, 0, 0); // init player to sane values
    xp = properties.get(Properties.START_XP_);
    loop();

    if (!closeWithoutSendingExit) {
      sendBuffer.put((byte) 7);
      clientConnection.sendPacket();
    }
    exit();
  }

  private void loop() throws IOException {
    long lastFrame = System.nanoTime();
    long accumulator = 0;
    while (true) {
      long newTime = System.nanoTime();
      long deltaTime = newTime - lastFrame;
      lastFrame = newTime;
      accumulator += deltaTime;
      while (accumulator >= Properties.TICK_TIME * 1000000) {
        if (clientConnection.update(deltaTime / 1000000000f)) {
          return;
        }
        if (receiveState()) {
          return;
        }
        if (input()) {
          return;
        }
        logic();
        if (++sendCount % properties.get(Properties.STATE_SEND_INTERVAL_) == 0) {
          sendState();
        }
        accumulator -= Properties.TICK_TIME * 1000000;
      }
      float alpha = (float) accumulator / (Properties.TICK_TIME * 1000000);
      render(alpha);
    }
  }

  private boolean receiveState() throws IOException {
    while (true) {
      if (!clientConnection.receivePacket()) {
        break;
      }
      int type = receiveBuffer.get();
      switch (type) {
        case 0:
        case 1:
          while (receiveBuffer.hasRemaining()) {
            state.update(type == 0);
          }
          break;
        case 2:
          while (receiveBuffer.hasRemaining()) {
            state.update();
          }
          break;
        case 3:
          state.swapEntities(receiveBuffer.getShort(), receiveBuffer.getShort());
          break;
        case 4:
          xp += properties.get(Properties.XP_PER_HIT_);
          state.getPlayer(playerId)
                  .increaseHealth((int) (0.07 * state.getPlayer(playerId).getHealth() / state.getPlayer(playerId).getHealthPercent()));
          break;
        case 5:
          waveActive = true;
          wave = receiveBuffer.getShort();
          changeWaveStateTime = System.nanoTime();
          state.getPlayer(playerId)
                  .increaseHealth((int) (0.5 * state.getPlayer(playerId).getHealth() / state.getPlayer(playerId).getHealthPercent()));
          break;
        case 6:
          waveActive = false;
          changeWaveStateTime = System.nanoTime();
          soundManager.playSound(Sound.SPAWN);
          break;
        case 7:
          closeWithoutSendingExit = true;
          return true;
        case 8:
          int player = receiveBuffer.getShort();
          soundManager.playSound(Sound.BALL_LAUNCH, state.getPlayer(player));
          break;
        case 9:
          int enemy = receiveBuffer.getShort();
          soundManager.playSound(Sound.BALL_LAUNCH, state.getEnemy(enemy));
          break;
        case 10:
          player = receiveBuffer.getShort();
          soundManager.playSound(Sound.PLAYER_HURT, state.getPlayer(player));
          state.getPlayer(player).decreaseHealth(receiveBuffer.getShort());
          break;
        case 11:
          enemy = receiveBuffer.getShort();
          soundManager.playSound(Sound.ENEMY_HURT, state.getEnemy(enemy));
          break;
        case 12:
          player = receiveBuffer.getShort();
          soundManager.playSound(Sound.PLAYER_DASH, state.getPlayer(player));
          break;
        case 13:
          enemy = receiveBuffer.getShort();
          soundManager.playSound(Sound.ENEMY_DASH, state.getEnemy(enemy));
          break;
        case 14:
        case 15:
          // ignore unused codes
          break;
        default:
          throw new IOException("Unknown message: type " + type);
      }
    }
    return false;
  }

  /**
   * @return true si l'utilisateur veut quit
   */
  private boolean input() throws IOException {
    window.pollInput();

    if (window.isKeyDown(GLFW.GLFW_KEY_ESCAPE)) {
      return true;
    }

    AxisState x;
    if (window.isKeyDown(GLFW.GLFW_KEY_A)) {
      x = window.isKeyDown(GLFW.GLFW_KEY_D) ? AxisState.ZERO : AxisState.MINUS;
    } else {
      x = window.isKeyDown(GLFW.GLFW_KEY_D) ? AxisState.PLUS : AxisState.ZERO;
    }
    AxisState y;
    if (window.isKeyDown(GLFW.GLFW_KEY_W)) {
      y = window.isKeyDown(GLFW.GLFW_KEY_S) ? AxisState.ZERO : AxisState.PLUS;
    } else {
      y = window.isKeyDown(GLFW.GLFW_KEY_S) ? AxisState.MINUS : AxisState.ZERO;
    }
    AxisState ball = window.isMouseDown(0) ? AxisState.PLUS : AxisState.ZERO;
    AxisState chargedBall = window.isMouseDown(1) ? AxisState.PLUS : AxisState.ZERO;
    AxisState dash = window.isKeyDown(GLFW.GLFW_KEY_SPACE) ? AxisState.PLUS : AxisState.ZERO;

    Shooter player = state.getPlayer(playerId);
    if (x != AxisState.ZERO || y != AxisState.ZERO) {
      player.setMoving(true);
      player.setAngle(AxisState.getAngle(x, y));
    } else {
      player.setMoving(false);
    }
    if (chargedBall == AxisState.PLUS) {
      player.charge();
    } else {
      Triplet<Double, Integer, Integer> ballSpecs = player.stopCharge();
      if (ballSpecs != null) {
        int freeId = state.getFreeEntityId(true);
        Point.Double mousePosition = window.getMouse();
        double ballAngle = Math.atan2(mousePosition.y - screenHeight / 2.0, mousePosition.x - screenWidth / 2.0);
        state.getEntity(freeId).create(player.getX(), player.getY(), ballSpecs.getFirst(), ballAngle, ballSpecs.getThird(), ballSpecs.getSecond(),
                false);
        soundManager.playSound(Sound.BALL_LAUNCH);
        sendBuffer.put((byte) 8);
        clientConnection.sendPacket();
        sendBuffer.put((byte) 2);
        state.serialize(freeId);
        clientConnection.sendPacket();
      }
    }
    if (ball == AxisState.PLUS) {
      Triplet<Double, Integer, Integer> ballSpecs = player.ball();
      if (ballSpecs != null) {
        int freeId = state.getFreeEntityId(true);
        Point.Double mousePosition = window.getMouse();
        double ballAngle = Math.atan2(mousePosition.y - screenHeight / 2.0, mousePosition.x - screenWidth / 2.0);
        state.getEntity(freeId).create(player.getX(), player.getY(), ballSpecs.getFirst(), ballAngle, ballSpecs.getThird(), ballSpecs.getSecond(),
                false);
        soundManager.playSound(Sound.BALL_LAUNCH);
        sendBuffer.put((byte) 8);
        clientConnection.sendPacket();
        sendBuffer.put((byte) 2);
        state.serialize(freeId);
        clientConnection.sendPacket();
      }
    }
    if (dash == AxisState.PLUS) {
      if (player.dash()) {
        soundManager.playSound(Sound.PLAYER_DASH);
        sendBuffer.put((byte) 8);
        clientConnection.sendPacket();
        sendBuffer.put((byte) 12);
        clientConnection.sendPacket();
      }
    }
    int scroll = window.flushScroll();
    levelUpPosition = Utils.modulo(levelUpPosition + scroll, player.getLevels().length);

    Set<Integer> mouseButtons = window.flushMouse();

    if (mouseButtons.contains(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)) {
      int newXp = player.increaseLevel(levelUpPosition, xp);
      if (newXp != -1) {
        soundManager.playSound(Sound.LEVEL_UP);
        xp = newXp;
      }
    }
    return false;
  }

  private void logic() throws IOException {
    state.logic();
  }

  private void sendState() throws IOException {
    sendBuffer.put((byte) 0);
    state.serialize(playerId, true);
    clientConnection.sendPacket();
  }

  private void exit() throws IOException {
    if (exited) {
      return;
    }
    exited = true;
    window.exit();
    soundManager.exit();
    clientConnection.stop();
  }

  private void render(float alpha) {

    Point.Double mousePosition = window.getMouse();
    Shooter player = state.getPlayer(playerId);

    float minX = (float) (player.getX() - screenWidth / 2.0 + (mousePosition.x - screenWidth / 2.0) * lookAroundFactor);
    float minY = (float) (player.getY() - screenHeight / 2.0 + (mousePosition.y - screenHeight / 2.0) * lookAroundFactor);
    float maxX = minX + screenWidth - 1;
    float maxY = minY + screenHeight - 1;

    window.beginRender(minX + screenWidth / 2f, minY + screenHeight / 2f);
    soundManager.updateListener(minX + screenWidth / 2f, minY + screenHeight / 2f);

    Consumer<Entity> draw = entity -> {
      if (entity.isDestroyed()) {
        return;
      }
      if (entity.getX() < minX - 500 || entity.getX() > maxX + 500 || entity.getY() < minY - 500 || entity.getY() > maxY + 500) {
        return;
      }
      float angle = (float) entity.getAngle();
      float scale;
      float x = (float) entity.getX();
      float y = (float) entity.getY();
      float nx;
      float ny;
      if (entity.isMoving()) {
        nx = (float) (entity.getSpeed() * alpha * Math.cos(angle) + x);
        ny = (float) (entity.getSpeed() * alpha * Math.sin(angle) + y);
      } else {
        nx = x;
        ny = y;
      }
      Terrain terrain = state.getMap().getTerrain((int) nx, (int) ny);
      Color color;

      if (entity instanceof Shooter) {
        if (terrain.playerThrough) {
          x = nx;
          y = ny;
        }
        float healthPercent = ((Shooter) entity).getHealthPercent();
        if (entity.isEnemy()) { // enemy
          scale = 60;
          color = new Color(0.5f - 0.4f * healthPercent, 0.5f - 0.15f * healthPercent, 0.5f - 0.4f * healthPercent);
          // window.renderLight(new Light(color, x, y));
          window.renderEntity(new RenderEntity(Model.MONSTER, x, y, angle, scale));
        } else { // player
          if (((Shooter) entity).isFrozen()) {
            color = new Color(0, 0, 0.5f + 0.5f * healthPercent);
          } else {
            color = new Color(0.5f - 0.15f * healthPercent, 0.5f - 0.4f * healthPercent, 0.5f - 0.4f * healthPercent);
          }
          scale = 60;
          window.renderLight(new Light(color, x, y));
          window.renderEntity(new RenderEntity(Model.PLAYER, x, y, angle, scale));
        }
      } else {
        if (terrain.ballThrough) {
          x = nx;
          y = ny;
        }
        float damagePercent = entity.getHealth() / (float) Shooter.getMaxDamage(properties);
        if (entity.isEnemy()) {
          color = Color.getHSBColor(damagePercent, 0.3f, 0.5f);
        } else {
          color = Color.getHSBColor(damagePercent, 0.5f, 0.6f);
        }
        scale = entity.getHitbox() / 100f / 1.3f;
        if (!entity.isEnemy())
          window.renderLight(new Light(color, x, y));
        window.renderEntity(
                new RenderEntity(entity.getHitbox() == properties.get(Properties.BALL_HITBOX_) ? Model.SMALL_BALL : Model.BALL, x, y, angle, scale));
      }
    };

    for (int i = 0; i < properties.get(Properties.ENTITIES_MAX_); i++) {
      draw.accept(state.getEntity(i));
    }
    for (int i = 0; i < properties.get(Properties.ENEMIES_MAX_); i++) {
      draw.accept(state.getEnemy(i));
    }
    for (int i = 0; i < properties.get(Properties.PLAYER_MAX_); i++) {
      draw.accept(state.getPlayer(i));
    }

    if (player.getChargedBallChargePercent() > 0) {
      window.renderCooldowns(player.getBallCooldownPercent(), player.getChargedBallChargePercent(), player.getDashCooldownPercent(), true);
    } else {
      window.renderCooldowns(player.getBallCooldownPercent(), player.getChargedBallCooldownPercent(), player.getDashCooldownPercent(), false);
    }

    if (player.isFrozen()) {
      window.renderHealth(player.getFreezePercent(), false);
    } else {
      window.renderHealth(player.getHealthPercent(), true);
    }

    if (System.nanoTime() - changeWaveStateTime < 3500 * 1000000L /* milliseconds */) {
      window.renderWave(wave, !waveActive);
    }

    if (window.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT)) {
      int[] levels = state.getPlayer(playerId).getLevels();
      window.renderShop(levels, levelUpPosition, xp);
    }

    window.endRender();
  }

  private enum AxisState {
    PLUS, MINUS, ZERO;

    public static double getAngle(AxisState x, AxisState y) {
      double angle = Math.PI / 4;
      if (x == PLUS) {
        if (y == PLUS) {
          return angle * 1;
        }
        if (y == ZERO) {
          return angle * 0;
        }
        if (y == MINUS) {
          return angle * 7;
        }
      }
      if (x == ZERO) {
        if (y == PLUS) {
          return angle * 2;
        }
        if (y == MINUS) {
          return angle * 6;
        }
      }
      if (x == MINUS) {
        if (y == PLUS) {
          return angle * 3;
        }
        if (y == ZERO) {
          return angle * 4;
        }
        if (y == MINUS) {
          return angle * 5;
        }
      }
      throw new IllegalArgumentException();
    }
  }

}
