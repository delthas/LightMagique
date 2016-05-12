package fr.delthas.lightmagique.client;

import fr.delthas.lightmagique.shared.Entity;
import fr.delthas.lightmagique.shared.Map;
import fr.delthas.lightmagique.shared.Pair;
import fr.delthas.lightmagique.shared.Properties;
import fr.delthas.lightmagique.shared.Shooter;
import fr.delthas.lightmagique.shared.State;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferStrategy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

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
  private static final String SERVER_ADDRESS = "82.231.158.103";
  private State state = new State();
  private JFrame frame;
  private BufferStrategy strategy;
  private boolean closeRequested = false;
  private InputManager inputManager = new InputManager();
  private SocketChannel channel;
  private int screenWidth, screenHeight;
  private ByteBuffer rType;
  private ByteBuffer rBuffer;
  private ByteBuffer rBuffer2;
  private ByteBuffer rChangeIdBuffer;
  private ByteBuffer rStartBuffer;
  private ByteBuffer buffer;
  private ByteBuffer buffer2;
  private static final double lookAroundFactor = 0.2;

  private int playerId = -1;
  private int sendCount;

  private int levelUpPosition = 0;
  private int xp = 0;

  public static void main(String[] args) throws IOException {
    new Client().start();
  }

  private void start() throws IOException {
    rType = ByteBuffer.allocateDirect(1);
    rBuffer = ByteBuffer.allocateDirect(Properties.ENTITY_MESSAGE_LENGTH);
    rBuffer2 = ByteBuffer.allocateDirect(Properties.SHOOTER_MESSAGE_LENGTH);
    rChangeIdBuffer = ByteBuffer.allocateDirect(4);
    rStartBuffer = ByteBuffer.allocateDirect(3);
    buffer = ByteBuffer.allocateDirect(1 + Properties.ENTITY_MESSAGE_LENGTH);
    buffer2 = ByteBuffer.allocateDirect(Properties.SHOOTER_MESSAGE_LENGTH);
    initFrame();

    try {
      frame.setCursor(frame.getToolkit().createCustomCursor(ImageIO.read(Client.class.getResource("/cursor.png")), new Point(5, 0), "cursor"));
    } catch (HeadlessException e) { // n'arrivera jamais
      throw new RuntimeException(e);
    }

    try (SocketChannel channel = SocketChannel.open(new InetSocketAddress(SERVER_ADDRESS, Properties.SERVER_PORT))) {
      this.channel = channel;
      channel.configureBlocking(true);
      channel.read(rStartBuffer);
      rStartBuffer.flip();
      rStartBuffer.get();
      playerId = rStartBuffer.getShort();
      state.getPlayer(playerId).createPlayer(0, 0, 0); // init player to sane values
      channel.configureBlocking(false);
      loop();
    } finally {
      exit();
    }
  }

  private void loop() throws IOException {
    long lastFrame = System.nanoTime();
    long accumulator = 0;
    while (!closeRequested) {
      long newTime = System.nanoTime();
      long deltaTime = newTime - lastFrame;
      lastFrame = newTime;
      accumulator += deltaTime;
      while (accumulator >= Properties.TICK_TIME * 1000000) {
        receiveState();
        if (input()) {
          break;
        }
        logic();
        if (++sendCount % Properties.STATE_SEND_INTERVAL == 0) {
          sendState();
        }
        accumulator -= Properties.TICK_TIME * 1000000;
      }
      float alpha = (float) accumulator / (Properties.TICK_TIME * 1000000);
      render(alpha);
      frame.getToolkit().sync();
    }
  }

  private void receiveState() throws IOException {
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
            return;
          }
          rBuffer.flip();
          rBuffer2.flip();
          state.update(rBuffer, rBuffer2, type == 0);
          break;
        case 2:
          rBuffer.clear();
          channel.read(rBuffer);
          if (rBuffer.hasRemaining()) {
            return;
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
        default:
          throw new IOException("Unknown message: type " + type);
      }
      rType.clear();
    }
  }

  /**
   * @return true si l'utiisateur veut quit
   *
   */
  private boolean input() throws IOException {
    if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
      closeRequested = true;
      return true;
    }

    AxisState x;
    if (inputManager.isKeyDown(KeyEvent.VK_Q)) {
      x = inputManager.isKeyDown(KeyEvent.VK_D) ? AxisState.ZERO : AxisState.MINUS;
    } else {
      x = inputManager.isKeyDown(KeyEvent.VK_D) ? AxisState.PLUS : AxisState.ZERO;
    }
    AxisState y;
    if (inputManager.isKeyDown(KeyEvent.VK_Z)) {
      y = inputManager.isKeyDown(KeyEvent.VK_S) ? AxisState.ZERO : AxisState.MINUS;
    } else {
      y = inputManager.isKeyDown(KeyEvent.VK_S) ? AxisState.PLUS : AxisState.ZERO;
    }
    AxisState ball = inputManager.isMouseDown(0) ? AxisState.PLUS : AxisState.ZERO;
    AxisState chargedBall = inputManager.isMouseDown(2) ? AxisState.PLUS : AxisState.ZERO;
    AxisState dash = inputManager.isKeyDown(KeyEvent.VK_SPACE) ? AxisState.PLUS : AxisState.ZERO;

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
      Pair<Double, Integer> ballSpecs = player.stopCharge();
      if (ballSpecs != null) {
        int freeId = state.getFreeEntityId(true);
        Point mousePosition = MouseInfo.getPointerInfo().getLocation();
        double ballAngle = Math.atan2(mousePosition.y - screenWidth / 2, mousePosition.x - screenHeight / 2);
        state.getEntity(freeId).create(player.getX(), player.getY(), ballSpecs.getFirst(), ballAngle, ballSpecs.getSecond(), false);
        buffer.clear();
        buffer.put((byte) 2);
        state.serialize(freeId, buffer);
        write(channel, buffer);
      }
    }
    if (ball == AxisState.PLUS) {
      Pair<Double, Integer> ballSpecs = player.ball();
      if (ballSpecs != null) {
        int freeId = state.getFreeEntityId(true);
        Point mousePosition = MouseInfo.getPointerInfo().getLocation();
        double ballAngle = Math.atan2(mousePosition.y - screenHeight / 2, mousePosition.x - screenWidth / 2);
        state.getEntity(freeId).create(player.getX(), player.getY(), ballSpecs.getFirst(), ballAngle, ballSpecs.getSecond(), false);
        buffer.clear();
        buffer.put((byte) 2);
        state.serialize(freeId, buffer);
        write(channel, buffer);
      }
    }
    if (dash == AxisState.PLUS) {
      player.dash();
    }
    Set<Integer> keyPressed = inputManager.flush();
    if (keyPressed.contains(KeyEvent.VK_DOWN)) {
      if (levelUpPosition == Shooter.getLevelNames().length - 1) {
        levelUpPosition = 0;
      } else {
        levelUpPosition++;
      }
    }
    if (keyPressed.contains(KeyEvent.VK_UP)) {
      if (levelUpPosition == 0) {
        levelUpPosition = Shooter.getLevelNames().length - 1;
      } else {
        levelUpPosition--;
      }
    }
    if (keyPressed.contains(KeyEvent.VK_ENTER)) {
      int currentLevel = player.getLevels()[levelUpPosition];
      int neededXp = Properties.getNeededXp(currentLevel);
      if (neededXp <= xp) {
        xp -= neededXp;
        player.increaseLevel(levelUpPosition);
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
    frame.setVisible(false);
    frame.dispose();
  }

  private void render(@SuppressWarnings("unused") float alpha) {
    Graphics2D g = (Graphics2D) strategy.getDrawGraphics();
    g.setBackground(Color.BLACK);
    g.setColor(Color.WHITE);
    g.clearRect(0, 0, frame.getBounds().width, frame.getBounds().height);

    Point mousePosition = MouseInfo.getPointerInfo().getLocation();
    Shooter player = state.getPlayer(playerId);

    int minX = (int) (player.getX() - screenWidth / 2 + (mousePosition.x - screenWidth / 2) * lookAroundFactor);
    int minY = (int) (player.getY() - screenHeight / 2 + (mousePosition.y - screenHeight / 2) * lookAroundFactor);
    int maxX = minX + screenWidth - 1;
    int maxY = minY + screenHeight - 1;

    g.drawImage(state.getMap().getMapImage(), 0, 0, screenWidth - 1, screenHeight - 1, minX, minY, maxX, maxY, null);

    BiConsumer<Graphics2D, Entity> draw = (graphics, entity) -> {
      if (entity.isDestroyed()) {
        return;
      }
      if (entity.getX() < minX - 50 || entity.getX() > maxX + 50 || entity.getY() < minY - 50 || entity.getY() > maxY + 50) {
        return;
      }
      AffineTransform save = graphics.getTransform();
      graphics.translate(entity.getX() - minX, entity.getY() - minY);
      graphics.rotate(entity.getAngle());
      if (entity instanceof Shooter) {
        float healthPercent = ((Shooter) entity).getHealthPercent();
        if (entity.isEnemy()) { // enemy
          graphics.setColor(new Color(0.5f - 0.5f * healthPercent, 0.5f + 0.5f * healthPercent, 0.5f - 0.5f * healthPercent));
          graphics.fillPolygon(new int[] {-10, -10, 20}, new int[] {10, -10, 0}, 3);
        } else { // player
          if (((Shooter) entity).isFrozen()) {
            graphics.setColor(new Color(0, 0, 0.5f + 0.5f * healthPercent));
          } else {
            graphics.setColor(new Color(0.5f + 0.5f * healthPercent, 0.5f - 0.5f * healthPercent, 0.5f - 0.5f * healthPercent));
          }
          graphics.fillPolygon(new int[] {-10, -10, 20}, new int[] {10, -10, 0}, 3);
        }
      } else {
        float damagePercent = entity.getHealth() / (float) Properties.getChargedBallMaxCharge(Properties.MAX_LEVEL);
        graphics.setColor(Color.getHSBColor(damagePercent, 1, 1));
        if (entity.isEnemy()) { // enemy ball
          graphics.fillOval(-6, -6, 13, 13);
        } else { // player ball
          graphics.fillOval(-5, -5, 11, 11);
        }
      }
      graphics.setTransform(save);
    };

    for (int i = 0; i < Properties.ENTITIES_MAX; i++) {
      draw.accept(g, state.getEntity(i));
    }
    for (int i = 0; i < Properties.ENEMIES_MAX; i++) {
      draw.accept(g, state.getEnemy(i));
    }
    for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
      draw.accept(g, state.getPlayer(i));
    }

    if (inputManager.isKeyDown(KeyEvent.VK_SHIFT)) {
      String[] names = Shooter.getLevelNames();
      int[] levels = state.getPlayer(playerId).getLevels();
      int length = levels.length;
      g.setFont(Font.decode("Comic Sans MS"));
      g.setColor(Color.YELLOW);
      g.fillRect(0, 0, screenWidth, 50 * (length + 2));
      g.setColor(Color.BLACK);
      g.drawString("Xp actuelle : " + xp, 20, 50);
      for (int i = 0; i < length; i++) {
        g.drawString(names[i], 20, 100 + 50 * i);
        g.drawString("Niveau actuel : " + levels[i] + " ; Xp nécessaire pour upgrade : " + Properties.getNeededXp(levels[i]), 20, 115 + 50 * i);
      }
      g.setColor(Color.RED);
      g.fillRect(5, 100 + 50 * levelUpPosition - 5, 11, 11);
    }

    g.dispose();
    strategy.show();
  }

  private void initFrame() {
    frame = new JFrame(GAME_NAME);
    frame.setUndecorated(true);
    frame.setResizable(false);
    frame.setIgnoreRepaint(true);
    frame.setVisible(true);
    GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    device.setFullScreenWindow(frame);
    frame.createBufferStrategy(2);
    strategy = frame.getBufferStrategy();
    frame.addKeyListener(inputManager);
    frame.addMouseListener(inputManager);
    screenWidth = frame.getWidth();
    screenHeight = frame.getHeight();
    Map map = state.getMap();
    map.makeImageCompatibleWith(device.getDefaultConfiguration());
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
