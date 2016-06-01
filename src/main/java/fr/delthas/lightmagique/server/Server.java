package fr.delthas.lightmagique.server;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Random;

import fr.delthas.lightmagique.shared.Properties;
import fr.delthas.lightmagique.shared.Shooter;
import fr.delthas.lightmagique.shared.State;
import fr.delthas.lightmagique.shared.Triplet;

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
  private ByteBuffer oneByteBuffer;
  private ByteBuffer waveStartBuffer;
  private int sendCount;

  private int wave = 0;
  private int ticksUntilNextWave = 0; // -1 = waiting for no enemy left

  private int clientThatSentExit = -1;

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
    try {
      new Server().start(port);
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
    for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
      types[i] = ByteBuffer.allocateDirect(1);
      buffers[i] = ByteBuffer.allocateDirect(Properties.ENTITY_MESSAGE_LENGTH);
      buffers2[i] = ByteBuffer.allocateDirect(Properties.SHOOTER_MESSAGE_LENGTH);
    }
    buffer = ByteBuffer.allocateDirect(1 + Properties.ENTITY_MESSAGE_LENGTH);
    buffer2 = ByteBuffer.allocateDirect(Properties.SHOOTER_MESSAGE_LENGTH);
    changeIdBuffer = ByteBuffer.allocateDirect(5);
    startBuffer = ByteBuffer.allocateDirect(2);
    oneByteBuffer = ByteBuffer.allocateDirect(1);
    waveStartBuffer = ByteBuffer.allocateDirect(5);

    try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
      serverSocketChannel.configureBlocking(true);
      serverSocketChannel.bind(new InetSocketAddress(port));
      for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
        channels[i] = serverSocketChannel.accept();
        channels[i].setOption(StandardSocketOptions.TCP_NODELAY, Boolean.TRUE);
        channels[i].configureBlocking(true);
      }
      for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
        startBuffer.clear();
        startBuffer.putShort((short) i).flip();
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
        oneByteBuffer.clear();
        oneByteBuffer.put((byte) 4).flip();
        for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
          write(channels[i], oneByteBuffer);
          oneByteBuffer.rewind();
        }
      });
      loop();
      oneByteBuffer.clear();
      oneByteBuffer.put((byte) 7).flip();
      for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
        if (i == clientThatSentExit) {
          continue;
        }
        write(channels[i], oneByteBuffer);
        oneByteBuffer.rewind();
      }

      // need to do a clumsy poll here b/c the channel is non blocking
      // wait for server to exit (5 seconds at most)
      outer: for (int i = 0; i < 50; i++) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          break;
        }
        for (int j = 0; j < Properties.PLAYER_COUNT; j++) {
          if (channels[j].isConnected()) {
            continue outer;
          }
        }
        break;
      }
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
        if (receiveState()) {
          return;
        }
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

  private boolean receiveState() throws IOException {
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
          case 7:
            clientThatSentExit = i;
            return true;
          default:
            throw new IOException("Unknown message: type " + type);
        }
        types[i].clear();
      }
    }
    return false;
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
        enemy.dash();
        if (player.isFrozen() && min < 50 * 50) {
          enemy.setMoving(false);
        } else {
          enemy.setMoving(true);
        }
        Triplet<Double, Integer, Integer> ball = enemy.ball();
        if (ball != null) {
          int id = state.getFreeEntityId(false);
          state.getEntity(id).create(enemy.getX(), enemy.getY(), ball.getFirst(), angle, ball.getThird(), ball.getSecond(), true);
          sendEntity(id);
        }
        if (enemy.getChargedBallChargePercent() == 1.0f) {
          ball = enemy.stopCharge();
          if (ball != null) {
            int id = state.getFreeEntityId(false);
            state.getEntity(id).create(enemy.getX(), enemy.getY(), ball.getFirst(), angle, ball.getThird(), ball.getSecond(), true);
            sendEntity(id);
          }
        }
        if (!enemy.isCharging() && enemy.canCharge() && random.nextInt(1000) == 0) {
          enemy.charge();
        }
      }
    }

    if (ticksUntilNextWave == 0) {
      ticksUntilNextWave = -1;
      wave++;
      waveStartBuffer.clear();
      waveStartBuffer.put((byte) 5).putInt(wave).flip();
      for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
        write(channels[i], waveStartBuffer);
        waveStartBuffer.rewind();
      }
      for (int i = 0; i < wave; i++) {
        int x;
        int y;
        do {
          x = random.nextInt(state.getMap().getWidth());
          y = random.nextInt(state.getMap().getHeight());
        } while (!state.getMap().getTerrain(x, y).canSpawn);
        for (int j = 0; j < random.nextInt((int) (1.5 * wave)); j++) {
          Shooter enemy = state.getEnemy(state.getFreeEnemyId());
          double angle = random.nextDouble() * Math.PI * 2;
          int nx = x + random.nextInt(101) - 50;
          int ny = y + random.nextInt(101) - 50;
          if (state.getMap().getTerrain(nx, ny).canSpawn) {
            enemy.createEnemy(nx, ny, angle, 2 * wave, true);
          }
        }
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
        oneByteBuffer.clear();
        oneByteBuffer.put((byte) 6).flip();
        for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
          write(channels[i], oneByteBuffer);
          oneByteBuffer.rewind();
        }
        ticksUntilNextWave = 1000;
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
