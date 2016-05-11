package fr.delthas.lightmagique.client;

import fr.delthas.lightmagique.shared.Entity;
import fr.delthas.lightmagique.shared.Map;
import fr.delthas.lightmagique.shared.Properties;
import fr.delthas.lightmagique.shared.Shooter;
import fr.delthas.lightmagique.shared.State;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Transparency;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
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
  private static final String SERVER_ADDRESS = "localhost";
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
  private Image mapImage;
  private static final double lookAroundFactor = 0.2;

  private int playerId = -1;
  private int sendCount;

  public static void main(String[] args) throws IOException {
    new Client().start();
  }

  private void start() throws IOException {
    rType = ByteBuffer.allocate(1);
    rBuffer = ByteBuffer.allocate(Properties.ENTITY_MESSAGE_LENGTH);
    rBuffer2 = ByteBuffer.allocate(Properties.SHOOTER_MESSAGE_LENGTH);
    rChangeIdBuffer = ByteBuffer.allocate(8);
    rStartBuffer = ByteBuffer.allocate(5);
    buffer = ByteBuffer.allocate(1 + Properties.ENTITY_MESSAGE_LENGTH);
    buffer2 = ByteBuffer.allocate(Properties.SHOOTER_MESSAGE_LENGTH);
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
      playerId = rStartBuffer.getInt();
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
          int oldId = rChangeIdBuffer.getInt();
          int newId = rChangeIdBuffer.getInt();
          state.swapEntities(oldId, newId);
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
    Point mousePosition = MouseInfo.getPointerInfo().getLocation();
    mousePosition.translate(-screenWidth / 2, -screenHeight / 2);

    if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
      closeRequested = true;
      return true;
    }

    AxisState x;
    if (inputManager.isKeyDown(KeyEvent.VK_Q)) {
      if (!inputManager.isKeyDown(KeyEvent.VK_D)) {
        x = AxisState.MINUS;
      } else {
        x = AxisState.ZERO;
      }
    } else {
      if (!inputManager.isKeyDown(KeyEvent.VK_D)) {
        x = AxisState.ZERO;
      } else {
        x = AxisState.PLUS;
      }
    }

    AxisState y;
    if (inputManager.isKeyDown(KeyEvent.VK_Z)) {
      if (!inputManager.isKeyDown(KeyEvent.VK_S)) {
        y = AxisState.MINUS;
      } else {
        y = AxisState.ZERO;
      }
    } else {
      if (!inputManager.isKeyDown(KeyEvent.VK_S)) {
        y = AxisState.ZERO;
      } else {
        y = AxisState.PLUS;
      }
    }

    AxisState ball;
    if (inputManager.isKeyDown(KeyEvent.VK_SPACE)) {
      ball = AxisState.PLUS;
    } else {
      ball = AxisState.ZERO;
    }

    AxisState dash;
    if (inputManager.isMouseDown(1)) {
      dash = AxisState.PLUS;
    } else {
      dash = AxisState.ZERO;
    }

    Shooter player = state.getPlayer(playerId);
    if (x != AxisState.ZERO || y != AxisState.ZERO) {
      player.setAngle(AxisState.getAngle(x, y));
      if (!player.isDashing()) {
        player.setSpeed(Properties.PLAYER_SPEED);
      }
    } else {
      if (!player.isDashing()) {
        player.setSpeed(0);
      }
    }
    if (!player.isDestroyed()) {
      if (ball == AxisState.PLUS) {
        if (player.ball()) {
          int freeId = state.getFreeEntityId(true);
          double ballAngle = Math.atan2(mousePosition.y, mousePosition.x);
          state.getEntity(freeId).create(player.getX(), player.getY(), Properties.BALL_SPEED, ballAngle, 4, false);
          buffer.clear();
          buffer.put((byte) 2);
          state.serialize(freeId, buffer);
          write(channel, buffer);
        }
      }
      if (dash == AxisState.PLUS) {
        player.dash();
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
    System.exit(0);
  }

  private void render(float alpha) {
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

    g.drawImage(mapImage, 0, 0, screenWidth - 1, screenHeight - 1, minX, minY, maxX, maxY, null);

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
      graphics.scale(2.8, 2.8);
      switch (entity.getDrawable()) {
        case 0:
          graphics.setColor(Color.PINK);
          graphics.fillPolygon(new int[] {-5, -5, 10}, new int[] {5, -5, 0}, 3);
          break;
        case 1:
          graphics.setColor(Color.RED);
          graphics.fillPolygon(new int[] {-5, -5, 10}, new int[] {5, -5, 0}, 3);
          break;
        case 2:
          graphics.setColor(Color.GREEN);
          graphics.fillPolygon(new int[] {-5, -5, 10}, new int[] {5, -5, 0}, 3);
          break;
        case 3:
          graphics.setColor(Color.YELLOW);
          graphics.fillPolygon(new int[] {-5, -5, 10}, new int[] {5, -5, 0}, 3);
          break;
        case 4:
          graphics.setColor(Color.BLUE);
          graphics.fillOval(-5, -5, 10, 10);
          break;
        case 5:
          graphics.setColor(Color.RED);
          graphics.fillOval(-5, -5, 10, 10);
          break;
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
    BufferedImage image = device.getDefaultConfiguration().createCompatibleImage(map.getWidth(), map.getHeight(), Transparency.OPAQUE);
    int[] pixels = new int[map.getWidth() * map.getHeight()];
    for (int y = 0; y < map.getHeight(); y++) {
      for (int x = 0; x < map.getWidth(); x++) {
        pixels[y * map.getWidth() + x] = map.getTerrain(x, y).color.getRGB();
      }
    }
    image.getRaster().setDataElements(0, 0, map.getWidth(), map.getHeight(), pixels);
    mapImage = image;
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
