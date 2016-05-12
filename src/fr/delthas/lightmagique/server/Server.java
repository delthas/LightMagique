package fr.delthas.lightmagique.server;

import fr.delthas.lightmagique.shared.Pair;
import fr.delthas.lightmagique.shared.Properties;
import fr.delthas.lightmagique.shared.Shooter;
import fr.delthas.lightmagique.shared.State;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Random;

public class Server {

  private Random random = new Random();
  private State state = new State();
  private boolean stopRequested = false;
  private SocketChannel[] channels = new SocketChannel[Properties.PLAYER_COUNT];
  private ByteBuffer[] types = new ByteBuffer[Properties.PLAYER_COUNT];
  private ByteBuffer[] buffers = new ByteBuffer[Properties.PLAYER_COUNT];
  private ByteBuffer[] buffers2 = new ByteBuffer[Properties.PLAYER_COUNT];
  private ByteBuffer buffer;
  private ByteBuffer buffer2;
  private ByteBuffer changeIdBuffer;
  private ByteBuffer startBuffer;
  private ByteBuffer xpBuffer;
  private int sendCount;

  private int wave = 0;
  private int ticksUntilNextWave = 0; // -1 = waiting for no enemy left

  public static void main(String... args) {
    new Server().start();
  }

  private void start() {
    for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
      types[i] = ByteBuffer.allocateDirect(1);
      buffers[i] = ByteBuffer.allocateDirect(Properties.ENTITY_MESSAGE_LENGTH);
      buffers2[i] = ByteBuffer.allocateDirect(Properties.SHOOTER_MESSAGE_LENGTH);
    }
    buffer = ByteBuffer.allocateDirect(1 + Properties.ENTITY_MESSAGE_LENGTH);
    buffer2 = ByteBuffer.allocateDirect(Properties.SHOOTER_MESSAGE_LENGTH);
    changeIdBuffer = ByteBuffer.allocateDirect(5);
    startBuffer = ByteBuffer.allocateDirect(3);
    xpBuffer = ByteBuffer.allocateDirect(1);
    xpBuffer.put((byte) 4).flip();

    try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
      serverSocketChannel.configureBlocking(true);
      serverSocketChannel.bind(new InetSocketAddress(Properties.SERVER_PORT));
      for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
        channels[i] = serverSocketChannel.accept();
        channels[i].configureBlocking(true);
      }
      for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
        startBuffer.clear();
        startBuffer.put((byte) 4).putShort((short) i).flip();
        write(channels[i], startBuffer);
        channels[i].configureBlocking(false);
      }
      for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
        Shooter entity = state.getPlayer(i);
        double angle = Math.random() * Math.PI * 2;
        double x = (double) state.getMap().getWidth() / 2;
        double y = (double) state.getMap().getHeight() / 2;
        entity.createPlayer(x, y, angle);
      }
      for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
        buffer.clear();
        buffer.put((byte) 0);
        buffer2.clear();
        state.serialize(i, buffer, buffer2, true);
        write(channels[i], buffer, buffer2);
        buffer.rewind();
        buffer2.rewind();
      }
      state.setDestroyEnemyListener(this::sendEnemy);
      state.setDestroyEntityListener(this::sendEntity);
      state.setPlayerKilledEnemyListener(_void -> {
        for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
          xpBuffer.rewind();
          write(channels[i], xpBuffer);
        }
      });
      loop();
    } catch (IOException e) {
      System.out.println("Extinction du serveur.");
    } finally {
      exit();
    }
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
        receiveState();
        logic();
        if (++sendCount % Properties.STATE_SEND_INTERVAL == 0) {
          sendState();
        }
        accumulator -= Properties.TICK_TIME * 1000000;
      }
      long sleepMillis = Properties.TICK_TIME - (System.nanoTime() - newTime) / 1000000;
      if (sleepMillis >= 0) {
        try {
          Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
        }
      } else {
        System.err.println("Warning: Running behind clock");
      }
    }
  }

  private void receiveState() throws IOException {
    outer: for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
      while (true) {
        if (types[i].position() == 0) { // on sait pas le type du message suivant
          int read = channels[i].read(types[i]);
          if (read == 0) {
            break;
          }
          buffers[i].clear();
          buffers2[i].clear();
        }
        int type = types[i].get(0);
        switch (type) {
          case 0:
          case 1:
            channels[i].read(new ByteBuffer[] {buffers[i], buffers2[i]});
            if (buffers2[i].hasRemaining()) {
              continue outer;
            }
            buffers[i].flip();
            buffers2[i].flip();
            state.update(buffers[i], buffers2[i], type == 0);
            break;
          case 2:
            channels[i].read(buffers[i]);
            if (buffers[i].hasRemaining()) {
              continue outer;
            }
            buffers[i].flip();
            int id = buffers[i].getShort(0);
            if (id >= Properties.ENTITIES_MAX - Properties.ENTITIES_PERSONAL_SPACE_SIZE) {
              int freeId = state.getFreeEntityId(false);
              buffers[i].putShort(0, (short) freeId);
              changeIdBuffer.clear();
              changeIdBuffer.put((byte) 3).putShort((short) id).putShort((short) freeId).flip();
              write(channels[i], changeIdBuffer);
              id = freeId;
            }
            state.update(buffers[i]);
            sendEntity(id);
            break;
          default:
            throw new IOException("Unknown message: type " + type);
        }
        types[i].clear();
      }
    }
  }

  private void logic() {
    if (sendCount % 5 == 0) {
      for (int i = 0; i < Properties.ENEMIES_MAX; i++) {
        Shooter enemy = state.getEnemy(i);
        if (enemy.isDestroyed()) {
          continue;
        }
        Shooter player = state.getPlayer(0);
        double min = State.distanceSq(enemy, player);
        for (int j = 1; j < Properties.PLAYER_COUNT; j++) {
          Shooter player2 = state.getPlayer(j);
          double min2 = State.distanceSq(enemy, player2);
          if (min2 < min) {
            player = player2;
          }
        }
        double angle = Math.atan2(player.getY() - enemy.getY(), player.getX() - enemy.getX());
        enemy.setAngle(angle);
        if (player.isFrozen() && min < 50 * 50) {
          enemy.setMoving(false);
        } else {
          enemy.setMoving(true);
        }
        Pair<Double, Integer> ball = enemy.ball();
        if (ball != null) {
          int id = state.getFreeEntityId(false);
          state.getEntity(id).create(enemy.getX(), enemy.getY(), ball.getFirst(), angle, ball.getSecond(), true);
          sendEntity(id);
        }
      }
    }

    if (ticksUntilNextWave == 0) {
      ticksUntilNextWave = -1;
      wave++;
      for (int i = 0; i < wave; i++) {
        int x;
        int y;
        do {
          x = random.nextInt(state.getMap().getWidth());
          y = random.nextInt(state.getMap().getHeight());
        } while (!state.getMap().getTerrain(x, y).playerThrough);
        Shooter enemy = state.getEnemy(state.getFreeEnemyId());
        double angle = random.nextDouble() * Math.PI * 2;
        enemy.createEnemy(x, y, angle, 1 + wave / 3);
      }
    } else if (ticksUntilNextWave > 0) {
      ticksUntilNextWave--;
    } else {
      boolean allDestroyed = true;
      for (int i = 0; i < Properties.ENEMIES_MAX; i++) {
        if (!state.getEnemy(i).isDestroyed()) {
          allDestroyed = false;
          break;
        }
      }
      if (allDestroyed) {
        ticksUntilNextWave = 1500;
      }
    }
    state.logic();
  }

  private void sendState() {
    for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
      buffer.clear();
      buffer.put((byte) 0);
      buffer2.clear();
      state.serialize(i, buffer, buffer2, true);
      for (int j = 0; j < Properties.PLAYER_COUNT; j++) {
        if (i == j) {
          continue;
        }
        write(channels[j], buffer, buffer2);
        buffer.rewind();
        buffer2.rewind();
      }
    }
    for (int i = 0; i < Properties.ENEMIES_MAX; i++) {
      if (state.getEnemy(i).isDestroyed()) {
        continue;
      }
      sendEnemy(i);
    }
  }

  private void sendEntity(int id) {
    buffer.clear();
    buffer.put((byte) 2);
    state.serialize(id, buffer);
    for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
      write(channels[i], buffer);
      buffer.rewind();
    }
  }

  private void sendEnemy(int id) {
    buffer.clear();
    buffer.put((byte) 1);
    buffer2.clear();
    state.serialize(id, buffer, buffer2, false);
    for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
      write(channels[i], buffer, buffer2);
      buffer.rewind();
      buffer2.rewind();
    }
  }

  private void exit() {
    // nothing to do
  }

  private static void write(WritableByteChannel channel, ByteBuffer buf) {
    try {
      while (buf.hasRemaining()) {
        channel.write(buf);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void write(GatheringByteChannel channel, ByteBuffer... bufs) {
    try {
      while (bufs[bufs.length - 1].hasRemaining()) {
        channel.write(bufs);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
