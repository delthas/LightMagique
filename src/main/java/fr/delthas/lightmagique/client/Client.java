package fr.delthas.lightmagique.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.delthas.lightmagique.client.Window.Model;
import fr.delthas.lightmagique.shared.Entity;
import fr.delthas.lightmagique.shared.Properties;
import fr.delthas.lightmagique.shared.Shooter;
import fr.delthas.lightmagique.shared.State;
import fr.delthas.lightmagique.shared.Terrain;
import fr.delthas.lightmagique.shared.Triplet;

public class Client {

  private enum AxisState {
    PLUS, MINUS, ZERO;

    public static double getAngle(AxisState x, AxisState y) {
      double angle = Math.PI / 4;
      if (x == AxisState.PLUS) {
        if (y == AxisState.PLUS) {
          return angle * 1;
        }
        if (y == AxisState.ZERO) {
          return angle * 0;
        }
        if (y == AxisState.MINUS) {
          return angle * 7;
        }
      }
      if (x == AxisState.ZERO) {
        if (y == AxisState.PLUS) {
          return angle * 2;
        }
        if (y == AxisState.MINUS) {
          return angle * 6;
        }
      }
      if (x == AxisState.MINUS) {
        if (y == AxisState.PLUS) {
          return angle * 3;
        }
        if (y == AxisState.ZERO) {
          return angle * 4;
        }
        if (y == AxisState.MINUS) {
          return angle * 5;
        }
      }
      throw new IllegalArgumentException();
    }
  }

  public static final String GAME_NAME = "LightMagique";
  private static final String DEFAULT_ADDRESS = "82.231.158.103";
  private State state = new State();
  private Window window;
  private SocketChannel channel;
  private int screenWidth, screenHeight;
  private ByteBuffer rType;
  private ByteBuffer rBuffer;
  private ByteBuffer rBuffer2;
  private ByteBuffer rChangeIdBuffer;
  private ByteBuffer rStartBuffer;
  private ByteBuffer rWaveStartBuffer;
  private ByteBuffer buffer;
  private ByteBuffer buffer2;
  private ByteBuffer oneByteBuffer;
  private static final double lookAroundFactor = 0.2;

  private int playerId = -1;
  private int sendCount;

  private int levelUpPosition = 0;
  private int xp = 0;

  private int wave = 0;
  private boolean waveActive = false;
  private long changeWaveStateTime;

  private boolean closeWithoutSendingExit = false;
  private boolean exited = false;

  private Font waveFont = Font.decode("Verdana").deriveFont(30.0f);

  public static void main(String[] args) {
    InetAddress address = null;
    try {
      address = InetAddress.getByName(DEFAULT_ADDRESS);
    } catch (UnknownHostException e) {
      // silently ignore (will be caught below)
    }
    int port = Properties.DEFAULT_PORT;

    String usageString = "Arguments not recognized. Usage: [adress:[port]] (address has to be a hostname or IP address, and 1<=port<=65535).";
    if (args.length > 1) {
      System.out.println(usageString);
    } else if (args.length == 1) {
      Pattern patternAddress = Pattern.compile("([0-9a-zA-Z\\.]+)(?:\\:([0-9]+)?)");
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
        System.out.println(usageString);
      }
    }
    try {
      if (address == null) {
        throw new IOException("No valid IP or hostname found in the default or in the eventually passed arguments. "
            + "Try checking your connection if you're connecting to a remote server.");
      }
      InetSocketAddress socketAddress = new InetSocketAddress(address, port);
      System.out.println("Starting client on address: " + address.toString() + " port:" + port);
      new Client().start(socketAddress);
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

  private void start(InetSocketAddress serverAddress) throws IOException {
    rType = ByteBuffer.allocateDirect(1);
    rBuffer = ByteBuffer.allocateDirect(Properties.ENTITY_MESSAGE_LENGTH);
    rBuffer2 = ByteBuffer.allocateDirect(Properties.SHOOTER_MESSAGE_LENGTH);
    rChangeIdBuffer = ByteBuffer.allocateDirect(4);
    rStartBuffer = ByteBuffer.allocateDirect(2);
    rWaveStartBuffer = ByteBuffer.allocateDirect(4);
    buffer = ByteBuffer.allocateDirect(1 + Properties.ENTITY_MESSAGE_LENGTH);
    buffer2 = ByteBuffer.allocateDirect(Properties.SHOOTER_MESSAGE_LENGTH);
    oneByteBuffer = ByteBuffer.allocateDirect(1);
    initFrame();

    try (SocketChannel channel = SocketChannel.open(serverAddress)) {
      this.channel = channel;
      channel.setOption(StandardSocketOptions.TCP_NODELAY, Boolean.TRUE);
      channel.configureBlocking(true);
      channel.read(rStartBuffer);
      rStartBuffer.flip();
      playerId = rStartBuffer.getShort();
      state.getPlayer(playerId).createPlayer(0, 0, 0); // init player to sane values
      channel.configureBlocking(false);
      loop();
      exit();

      if (!closeWithoutSendingExit) {
        oneByteBuffer.clear();
        oneByteBuffer.put((byte) 7).flip();
        write(channel, oneByteBuffer);

        // need to do a clumsy poll here b/c the channel is non blocking
        // wait for server to exit (5 seconds at most)
        for (int i = 0; i < 50; i++) {
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            break;
          }
          if (!channel.isConnected()) {
            break;
          }
        }
      }
    } finally {
      exit();
    }
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
        if (receiveState()) {
          return;
        }
        if (input()) {
          return;
        }
        logic();
        if (++sendCount % Properties.STATE_SEND_INTERVAL == 0) {
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
      if (rType.position() == 0) { // on sait pas le type du message suivant
        int read = channel.read(rType);
        if (read == 0) {
          break;
        }
      }
      int type = rType.get(0);
      switch (type) {
        case 0:
        case 1:
          rBuffer.clear();
          rBuffer2.clear();
          channel.read(new ByteBuffer[] {rBuffer, rBuffer2});
          if (rBuffer2.hasRemaining()) {
            return false;
          }
          rBuffer.flip();
          rBuffer2.flip();
          state.update(rBuffer, rBuffer2, type == 0);
          break;
        case 2:
          rBuffer.clear();
          channel.read(rBuffer);
          if (rBuffer.hasRemaining()) {
            return false;
          }
          rBuffer.flip();
          state.update(rBuffer);
          break;
        case 3:
          rChangeIdBuffer.clear();
          channel.read(rChangeIdBuffer);
          rChangeIdBuffer.flip();
          int oldId = rChangeIdBuffer.getShort();
          int newId = rChangeIdBuffer.getShort();
          state.swapEntities(oldId, newId);
          break;
        case 4:
          xp += Properties.XP_PER_HIT;
          break;
        case 5:
          rWaveStartBuffer.clear();
          channel.read(rWaveStartBuffer);
          rWaveStartBuffer.flip();
          waveActive = true;
          wave = rWaveStartBuffer.getInt();
          changeWaveStateTime = System.nanoTime();
          break;
        case 6:
          waveActive = false;
          changeWaveStateTime = System.nanoTime();
          break;
        case 7:
          closeWithoutSendingExit = true;
          return true;
        default:
          throw new IOException("Unknown message: type " + type);
      }
      rType.clear();
    }
    return false;
  }

  /**
   * @return true si l'utilisateur veut quit
   *
   */
  private boolean input() throws IOException {
    window.pollInput();

    if (window.isKeyDown(1)) {
      return true;
    }

    AxisState x;
    if (window.isKeyDown(30)) {
      x = window.isKeyDown(32) ? AxisState.ZERO : AxisState.MINUS;
    } else {
      x = window.isKeyDown(32) ? AxisState.PLUS : AxisState.ZERO;
    }
    AxisState y;
    if (window.isKeyDown(17)) {
      y = window.isKeyDown(31) ? AxisState.ZERO : AxisState.PLUS;
    } else {
      y = window.isKeyDown(31) ? AxisState.MINUS : AxisState.ZERO;
    }
    AxisState ball = window.isMouseDown(0) ? AxisState.PLUS : AxisState.ZERO;
    AxisState chargedBall = window.isMouseDown(1) ? AxisState.PLUS : AxisState.ZERO;
    AxisState dash = window.isKeyDown(57) ? AxisState.PLUS : AxisState.ZERO;

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
        buffer.clear();
        buffer.put((byte) 2);
        state.serialize(freeId, buffer);
        write(channel, buffer);
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
        buffer.clear();
        buffer.put((byte) 2);
        state.serialize(freeId, buffer);
        write(channel, buffer);
      }
    }
    if (dash == AxisState.PLUS) {
      player.dash();
    }
    Set<Integer> keyPressed = window.flushKeys();
    if (keyPressed.contains(336)) {
      if (levelUpPosition == Shooter.getLevelNames().length - 1) {
        levelUpPosition = 0;
      } else {
        levelUpPosition++;
      }
    }
    if (keyPressed.contains(328)) {
      if (levelUpPosition == 0) {
        levelUpPosition = Shooter.getLevelNames().length - 1;
      } else {
        levelUpPosition--;
      }
    }
    if (keyPressed.contains(28)) {
      int currentLevel = player.getLevels()[levelUpPosition];
      int neededXp = Properties.getNeededXp(currentLevel);
      if (neededXp <= xp) {
        if (player.increaseLevel(levelUpPosition)) {
          xp -= neededXp;
        }
      }
    }
    return false;
  }

  private void logic() {
    state.logic();
  }

  private void sendState() throws IOException {
    buffer.clear();
    buffer2.clear();
    buffer.put((byte) 0);
    state.serialize(playerId, buffer, buffer2, true);
    write(channel, buffer, buffer2);
  }

  private void exit() {
    if (exited) {
      return;
    }
    exited = true;
    window.exit();
  }

  private void render(float alpha) {

    Point.Double mousePosition = window.getMouse();
    Shooter player = state.getPlayer(playerId);

    float minX = (float) (player.getX() - screenWidth / 2.0 + (mousePosition.x - screenWidth / 2.0) * lookAroundFactor);
    float minY = (float) (player.getY() - screenHeight / 2.0 + (mousePosition.y - screenHeight / 2.0) * lookAroundFactor);
    float maxX = minX + screenWidth - 1;
    float maxY = minY + screenHeight - 1;

    window.beginRender(minX + screenWidth / 2, minY + screenHeight / 2);

    Consumer<Entity> draw = (entity) -> {
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
          scale = 100;
          color = new Color(0.5f - 0.4f * healthPercent, 0.5f - 0.15f * healthPercent, 0.5f - 0.4f * healthPercent);
          window.renderLight(new Light(color, x, y));
          window.renderEntity(new RenderEntity(Model.MONSTER, x, y, angle, scale));
        } else { // player
          if (((Shooter) entity).isFrozen()) {
            color = new Color(0, 0, 0.5f + 0.5f * healthPercent);
          } else {
            color = new Color(0.5f - 0.15f * healthPercent, 0.5f - 0.4f * healthPercent, 0.5f - 0.4f * healthPercent);
          }
          scale = 100;
          window.renderLight(new Light(color, x, y));
          window.renderEntity(new RenderEntity(Model.PLAYER, x, y, angle, scale));
        }
      } else {
        if (terrain.ballThrough) {
          x = nx;
          y = ny;
        }
        float damagePercent = entity.getHealth() / (float) Shooter.getMaxDamage();
        if (entity.isEnemy()) {
          color = Color.getHSBColor(damagePercent, 0.3f, 0.5f);
        } else {
          color = Color.getHSBColor(damagePercent, 0.5f, 0.6f);
        }
        scale = 10 * entity.getHitbox() / 100f;
        window.renderLight(new Light(color, x, y));
        window.renderEntity(new RenderEntity(Model.BALL, x, y, angle, scale));
      }
    };

    for (int i = 0; i < Properties.ENTITIES_MAX; i++) {
      draw.accept(state.getEntity(i));
    }
    for (int i = 0; i < Properties.ENEMIES_MAX; i++) {
      draw.accept(state.getEnemy(i));
    }
    for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
      draw.accept(state.getPlayer(i));
    }

    // // draw cooldowns
    // g.setColor(Color.BLUE);
    // g.fillRect(0, screenHeight - 120, (int) (player.getBallCooldownPercent() * screenWidth), 40);
    // if (player.getChargedBallChargePercent() > 0) {
    // g.setColor(Color.RED);
    // g.fillRect(0, screenHeight - 80, (int) (player.getChargedBallChargePercent() * screenWidth), 40);
    // } else {
    // g.setColor(Color.ORANGE);
    // g.fillRect(0, screenHeight - 80, (int) (player.getChargedBallCooldownPercent() * screenWidth), 40);
    // }
    // g.setColor(Color.GREEN);
    // g.fillRect(0, screenHeight - 40, (int) (player.getDashCooldownPercent() * screenWidth), 40);
    //
    // // draw wave state
    // if (System.nanoTime() - changeWaveStateTime < 4 * 1000000000L /* seconds */) {
    // g.setColor(Color.BLACK);
    // g.setFont(waveFont);
    // String text;
    // if (waveActive) {
    // text = "Début de la vague " + wave;
    // } else {
    // text = "Fin de la vague " + wave;
    // }
    // g.drawString(text, screenWidth / 2 - 200, screenHeight / 2 - 50);
    // }
    //
    // // draw shop
    // if (window.isKeyDown(42)) {
    // String[] names = Shooter.getLevelNames();
    // int[] levels = state.getPlayer(playerId).getLevels();
    // int length = levels.length;
    // g.setFont(Font.decode("Comic Sans MS"));
    // g.setColor(Color.YELLOW);
    // g.fillRect(0, 0, screenWidth, 50 * (length + 2));
    // g.setColor(Color.BLACK);
    // g.drawString("Xp actuelle : " + xp, 20, 50);
    // for (int i = 0; i < length; i++) {
    // g.drawString(names[i], 20, 100 + 50 * i);
    // g.drawString("Niveau actuel : " + levels[i] + " ; Xp nécessaire pour upgrade : " + Properties.getNeededXp(levels[i]), 20, 115 + 50 * i);
    // }
    // g.setColor(Color.RED);
    // g.fillRect(5, 100 + 50 * levelUpPosition - 5, 11, 11);
    // }

    window.endRender();
  }

  private void initFrame() throws IOException {
    window = new Window(state.getMap().getMapImage());
    window.start();
    screenWidth = window.getWidth();
    screenHeight = window.getHeight();
  }

  private static void write(WritableByteChannel channel, ByteBuffer buf) throws IOException {
    while (buf.hasRemaining()) {
      channel.write(buf);
    }
  }

  private static void write(GatheringByteChannel channel, ByteBuffer... bufs) throws IOException {
    while (bufs[bufs.length - 1].hasRemaining()) {
      channel.write(bufs);
    }
  }
}
