package fr.delthas.lightmagique.server;

import fr.delthas.lightmagique.shared.*;
import fr.delthas.network.ServerConnection;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Server {

  private static final String PROPERTIES_FILE = "server.properties";
  private Properties properties;
  private Random random = new Random();
  private State state;
  private boolean stopRequested = false;
  private long sendCount;
  private ServerConnection serverConnection;

  private ByteBuffer sendBuffer;
  private ByteBuffer receiveBuffer;

  private int wave = 0;
  private int ticksUntilNextWave = 0; // -1 = waiting for no enemy left
  private int ticksSinceLastWave = 0;

  private List<Integer> disconnectedClients = new ArrayList<>();
  private List<Triplet<Double, Double, Long>> avoidPoints = new ArrayList<>(50);

  private Server(Properties properties) {
    this.properties = properties;
    serverConnection = new ServerConnection(Properties.PROTOCOL_ID, Properties.TIMEOUT, Properties.PACKET_MAX_SIZE);
    sendBuffer = serverConnection.getSendBuffer();
    receiveBuffer = serverConnection.getReceiveBuffer();
    state = new State(receiveBuffer, sendBuffer);
    state.initialize(properties);
    state.getMap().getAndForgetMapImage();
    Shooter.initialize(properties);
  }

  public static void main(String... args) {
    int port = Properties.DEFAULT_PORT;
    if (args.length > 1) {
      System.out.println("Arguments not recognized. Usage: [port] (port has to be an integer).");
    } else if (args.length == 1) {
      try {
        port = Integer.parseUnsignedInt(args[0]);
      } catch (NumberFormatException e) {
        System.out.println("Argument not recognized. Usage: [port] (port has to be an integer).");
      }
    }
    System.out.println("Starting on port: " + port);
    Path propertiesPath = Utils.getFile(PROPERTIES_FILE);
    Properties properties;
    if (Files.exists(propertiesPath)) {
      try {
        properties = new Properties(propertiesPath);
      } catch (IOException e) {
        System.out.println("Could not parse the preferences file. Starting with default preferences.");
        properties = new Properties();
      }
    } else {
      properties = new Properties();
      try {
        Properties.writeDefaultProperties(propertiesPath);
      } catch (IOException e) {
        System.out.println("Could not write the default properties file to " + propertiesPath.normalize());
      }
    }
    try {
      new Server(properties).start(port);
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

  private void start(int port) throws IOException {
    serverConnection.start(port);
    for (int i = 0; i < properties.get(Properties.PLAYER_MAX_); i++) {
      // TODO handle better connecting
      serverConnection.receivePacket(true);
      sendBuffer.clear();
      sendBuffer.put((byte) 14);
      properties.serialize(sendBuffer);
      serverConnection.sendPacket(i, true);
    }
    for (int i = 0; i < properties.get(Properties.PLAYER_MAX_); i++) {
      sendBuffer.clear();
      sendBuffer.put((byte) 15);
      sendBuffer.putShort((short) i);
      serverConnection.sendPacket(i, true);
    }
    for (int i = 0; i < properties.get(Properties.PLAYER_MAX_); i++) {
      Shooter entity = state.getPlayer(i);
      double angle = Math.random() * Math.PI * 2;
      double x = (double) state.getMap().getWidth() / 2;
      double y = (double) state.getMap().getHeight() / 2;
      entity.createPlayer(x, y, angle);
    }
    for (int i = 0; i < properties.get(Properties.PLAYER_MAX_); i++) {
      sendBuffer.clear();
      sendBuffer.put((byte) 0);
      state.serialize(i, true);
      serverConnection.sendPacket(i);
    }
    state.setDestroyEnemyListener(this::enemyDestroyed);
    state.setDestroyEntityListener(this::sendEntity);
    state.setPlayerKilledEnemyListener(_void -> {
      sendBuffer.clear();
      sendBuffer.put((byte) 4);
      sendToAll();
    });
    state.setHurtListener((player, id, damage) -> {
      sendBuffer.clear();
      sendBuffer.put(player ? (byte) 10 : (byte) 11).putShort(id.shortValue()).putShort(damage.shortValue());
      sendToAll();
    });
    loop();
    sendBuffer.clear();
    sendBuffer.put((byte) 7);
    sendToAllExcept(disconnectedClients);
  }


  private void loop() throws IOException {
    sendCount = 0;
    long lastFrame = System.nanoTime();
    long accumulator = 0;
    while (!stopRequested) {
      long newTime = System.nanoTime();
      long deltaTime = newTime - lastFrame;
      lastFrame = newTime;
      accumulator += deltaTime;
      while (accumulator >= Properties.TICK_TIME * 1000000) {
        List<Integer> timeouts = serverConnection.update(deltaTime / 1000000000f);
        if (!timeouts.isEmpty()) {
          disconnectedClients.addAll(timeouts);
          return;
        }
        if (receiveState()) {
          return;
        }
        logic();
        if (++sendCount % properties.get(Properties.STATE_SEND_INTERVAL_) == 0) {
          sendState();
        }
        accumulator -= Properties.TICK_TIME * 1000000;
      }
      long sleepMillis = Properties.TICK_TIME - (System.nanoTime() - newTime) / 1000000;
      if (sleepMillis >= 0) {
        try {
          Thread.sleep(sleepMillis);
        } catch (InterruptedException ignore) {
        }
      } else {
        System.err.println("Warning: Running behind clock");
      }
    }
  }

  private boolean receiveState() throws IOException {
    while (true) {
      int address = serverConnection.receivePacket();
      if (address == -1) {
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
            int id = receiveBuffer.getShort();
            if (id >= properties.get(Properties.ENTITIES_MAX_) - properties.get(Properties.ENTITIES_PERSONAL_SPACE_SIZE_)) {
              int freeId = state.getFreeEntityId(false);
              sendBuffer.clear();
              sendBuffer.put((byte) 3).putShort((short) id).putShort((short) freeId);
              serverConnection.sendPacket(address);
              id = freeId;
            }
            state.update(id); // override entity id
            sendEntity(id);
          }
          break;
        case 7:
          disconnectedClients.add(address);
          return true;
        case 8:
        case 12:
          sendBuffer.clear();
          sendBuffer.put((byte) type).putShort((short) address);
          sendToAllExcept(address);
          break;
        default:
          throw new IOException("Unknown message: type " + type);
      }
    }
    return false;
  }

  private void logic() throws IOException {
    if (sendCount % 5 == 0) {
      for (int i = 0; i < properties.get(Properties.ENEMIES_MAX_); i++) {
        Shooter enemy = state.getEnemy(i);
        if (enemy.isDestroyed()) {
          continue;
        }
        Shooter player = state.getPlayer(0);
        double min = State.distanceSq(enemy, player);
        for (int j = 1; j < properties.get(Properties.PLAYER_MAX_); j++) {
          Shooter player2 = state.getPlayer(j);
          double min2 = State.distanceSq(enemy, player2);
          if (min2 < min) {
            player = player2;
          }
        }
        double forceX, forceY;
        double deltaPlayerX = player.getX() - enemy.getX();
        double deltaPlayerY = player.getY() - enemy.getY();
        double deltaPlayerHypot = Math.hypot(deltaPlayerX, deltaPlayerY);
        forceX = deltaPlayerX / deltaPlayerHypot;
        forceY = deltaPlayerY / deltaPlayerHypot;
        for (Triplet<Double, Double, Long> avoidPoint : avoidPoints) {
          double deltaAvoidX = avoidPoint.getFirst() - enemy.getX();
          double deltaAvoidY = avoidPoint.getSecond() - enemy.getY();
          double deltaAvoidHypot = Math.hypot(deltaAvoidX, deltaAvoidY);
          if (deltaAvoidHypot > 600) {
            continue;
          }
          forceX -= deltaAvoidX / deltaAvoidHypot * 5;
          forceY -= deltaAvoidY / deltaAvoidHypot * 5;
        }
        double newAngle = Math.atan2(forceY, forceX);
        if (!Double.isNaN(newAngle)) {
          double currentAngle = enemy.getAngle();
          double deltaAngle = newAngle - currentAngle;
          deltaAngle += Math.PI;
          deltaAngle %= Math.PI * 2;
          if (deltaAngle < 0) {
            deltaAngle += Math.PI * 2;
          }
          deltaAngle -= Math.PI;
          if (deltaAngle >= 0) {
            if (deltaAngle <= Math.PI * 2 / 50) {
              enemy.setAngle(newAngle);
            } else {
              enemy.setAngle(currentAngle + Math.PI * 2 / 50);
            }
          } else {
            if (deltaAngle >= -Math.PI * 2 / 50) {
              enemy.setAngle(newAngle);
            } else {
              enemy.setAngle(currentAngle - Math.PI * 2 / 50);
            }
          }
        }
        double playerAngle = Math.atan2(player.getY() - enemy.getY(), player.getX() - enemy.getX());
        if (ticksSinceLastWave > 140 && enemy.dash()) {
          sendBuffer.clear();
          sendBuffer.put((byte) 13).putShort((short) i);
          sendToAll();
        }
        if (player.isFrozen() && min < 100 * 100) {
          enemy.setMoving(false);
        } else {
          enemy.setMoving(true);
        }
        Triplet<Double, Integer, Integer> ball = enemy.ball();
        if (ball != null) {
          int id = state.getFreeEntityId(false);
          state.getEntity(id).create(enemy.getX(), enemy.getY(), ball.getFirst(), playerAngle, ball.getThird(), ball.getSecond(), true);
          sendEntity(id);
          sendBuffer.clear();
          sendBuffer.put((byte) 9).putShort((short) i);
          sendToAll();
        }
        if (enemy.getChargedBallChargePercent() == 1.0f) {
          ball = enemy.stopCharge();
          if (ball != null) {
            int id = state.getFreeEntityId(false);
            state.getEntity(id).create(enemy.getX(), enemy.getY(), ball.getFirst(), playerAngle, ball.getThird(), ball.getSecond(), true);
            sendEntity(id);
            sendBuffer.clear();
            sendBuffer.put((byte) 9).putShort((short) i);
            sendToAll();
          }
        }
        if (!enemy.isCharging() && enemy.canCharge() && random.nextInt(1000) == 0) {
          enemy.charge();
        }
      }
      avoidPoints.removeIf(t -> t.getThird() < sendCount - 100);
    }
    ticksSinceLastWave++;
    if (ticksUntilNextWave == 0) {
      ticksUntilNextWave = -1;
      ticksSinceLastWave = 0;
      wave++;
      sendBuffer.clear();
      sendBuffer.put((byte) 5).putShort((short) wave);
      sendToAll();
      for (int i = 0; i < 1 + wave / 3; i++) {
        int x;
        int y;
        boolean farEnough = true;
        do {
          x = random.nextInt(state.getMap().getWidth());
          y = random.nextInt(state.getMap().getHeight());
          for (int j = 0; j < properties.get(Properties.PLAYER_MAX_); j++) {
            Shooter player = state.getPlayer(j);
            double deltaXSq = player.getX() - x;
            double deltaYSq = player.getY() - y;
            if (deltaXSq * deltaXSq + deltaYSq * deltaYSq < 200 * 200) {
              farEnough = false;
              break;
            }
          }
        } while (farEnough && !state.getMap().getTerrain(x, y).canSpawn);
        for (int j = 0; j < random.nextInt(wave); j++) {
          Shooter enemy = state.getEnemy(state.getFreeEnemyId());
          double angle = random.nextDouble() * Math.PI * 2;
          int nx = x + random.nextInt(101) - 50;
          int ny = y + random.nextInt(101) - 50;
          if (state.getMap().getTerrain(nx, ny).canSpawn) {
            enemy.createEnemy(nx, ny, angle, wave, true);
          }
        }
      }
    } else if (ticksUntilNextWave > 0) {
      ticksUntilNextWave--;
    } else {
      boolean allDestroyed = true;
      for (int i = 0; i < properties.get(Properties.ENEMIES_MAX_); i++) {
        if (!state.getEnemy(i).isDestroyed()) {
          allDestroyed = false;
          break;
        }
      }
      if (allDestroyed) {
        sendBuffer.clear();
        sendBuffer.put((byte) 6);
        sendToAll();
        ticksUntilNextWave = 1000;
      }
    }
    state.logic();
  }

  private void sendState() throws IOException {
    for (int i = 0; i < properties.get(Properties.PLAYER_MAX_); i++) {
      sendPlayer(i);
    }
    sendBuffer.clear();
    sendBuffer.put((byte) 1);
    int j = 0;
    for (int i = 0; i < properties.get(Properties.ENEMIES_MAX_); i++) {
      if (state.getEnemy(i).isDestroyed()) {
        continue;
      }
      state.serialize(i, false);
      if (++j > 30) {
        sendToAll();
        sendBuffer.clear();
        sendBuffer.put((byte) 1);
        j = 0;
      }
    }
    if (j > 0) {
      sendToAll();
    }
  }

  private void sendPlayer(int id) throws IOException {
    sendBuffer.clear();
    sendBuffer.put((byte) 0);
    state.serialize(id, true);
    sendToAllExcept(id);
  }

  private void sendEntity(int id) throws IOException {
    sendBuffer.clear();
    sendBuffer.put((byte) 2);
    state.serialize(id);
    sendToAll();
  }

  private void enemyDestroyed(int id) throws IOException {
    Entity entity = state.getEntity(id);
    avoidPoints.add(new Triplet<>(entity.getX(), entity.getY(), sendCount));
    sendEnemy(id);
  }

  private void sendEnemy(int id) throws IOException {
    sendBuffer.clear();
    sendBuffer.put((byte) 1);
    state.serialize(id, false);
    sendToAll();
  }

  private void sendToAll() throws IOException {
    for (int i = 0; i < properties.get(Properties.PLAYER_MAX_); i++) {
      serverConnection.sendPacket(i);
    }
  }

  private void sendToAllExcept(int j) throws IOException {
    for (int i = 0; i < properties.get(Properties.PLAYER_MAX_); i++) {
      if (i == j) {
        continue;
      }
      serverConnection.sendPacket(i);
    }
  }

  private void sendToAllExcept(List<Integer> list) throws IOException {
    for (int i = 0; i < properties.get(Properties.PLAYER_MAX_); i++) {
      if (list.contains(i)) {
        continue;
      }
      serverConnection.sendPacket(i);
    }
  }

}
