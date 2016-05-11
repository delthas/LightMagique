package fr.delthas.lightmagique.server;

import fr.delthas.lightmagique.shared.Entity;
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

public class Server {

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
  private int sendCount;

  public static void main(String... args) throws IOException {
    new Server().start();
  }

  private void start() throws IOException {
    for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
      types[i] = ByteBuffer.allocate(1);
      buffers[i] = ByteBuffer.allocate(Properties.ENTITY_MESSAGE_LENGTH);
      buffers2[i] = ByteBuffer.allocate(Properties.SHOOTER_MESSAGE_LENGTH);
    }
    buffer = ByteBuffer.allocate(1 + Properties.ENTITY_MESSAGE_LENGTH);
    buffer2 = ByteBuffer.allocate(Properties.SHOOTER_MESSAGE_LENGTH);
    changeIdBuffer = ByteBuffer.allocate(9);
    startBuffer = ByteBuffer.allocate(5);

    try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
      serverSocketChannel.configureBlocking(true);
      serverSocketChannel.bind(new InetSocketAddress(Properties.SERVER_PORT));
      for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
        channels[i] = serverSocketChannel.accept();
        channels[i].configureBlocking(true);
      }
      for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
        startBuffer.clear();
        startBuffer.put((byte) 4).putInt(i).flip();
        write(channels[i], startBuffer);
        channels[i].configureBlocking(false);
      }
      for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
        Shooter entity = state.getPlayer(i);
        double speed = Properties.PLAYER_SPEED;
        double angle = Math.random() * Math.PI * 2;
        double x = (double) state.getMap().getWidth() / 2;
        double y = (double) state.getMap().getHeight() / 2;
        entity.create(x, y, speed, angle, 0, false, Properties.PLAYER_HEALTH, 1, 1, Properties.PLAYER_SPEED);
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
      loop();
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
      try {
        Thread.sleep(Properties.TICK_TIME - (System.nanoTime() - newTime) / 1000000);
      } catch (InterruptedException e) {
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
            int id = buffers[i].getInt(0);
            if (id >= Properties.ENTITIES_MAX - Properties.ENTITIES_PERSONAL_SPACE_SIZE) {
              int freeId = state.getFreeEntityId(false);
              buffers[i].putInt(0, freeId);
              changeIdBuffer.clear();
              changeIdBuffer.put((byte) 3).putInt(id).putInt(freeId).flip();
              write(channels[i], changeIdBuffer);
            }
            state.update(buffers[i]);
            break;
          default:
            throw new IOException("Unknown message: type " + type);
        }
        types[i].clear();
      }
    }
  }

  private void logic() {
    // logique des monstres ici
    // pour l'instant j'ajoute des entités sava B-)
    if (sendCount % 400 == 0) {
      for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
        Entity entity = state.getEntity(state.getFreeEntityId(false));
        double x = state.getPlayer(i).getX() + 200;
        double y = state.getPlayer(i).getY() + 200;
        double speed = Properties.BALL_SPEED;
        double angle = Math.random() * Math.PI * 2;
        entity.create(x, y, speed, angle, 5, true);
      }
    }
    if ((sendCount + 200) % 1000 == 0) {
      for (int i = 0; i < Properties.PLAYER_COUNT; i++) {
        Shooter entity = state.getEnemy(state.getFreeEnemyId());
        double x = state.getPlayer(i).getX() + 200;
        double y = state.getPlayer(i).getY() + 200;
        double speed = Properties.PLAYER_SPEED;
        double angle = Math.random() * Math.PI * 2;
        entity.create(x, y, speed, angle, 2, true, Properties.PLAYER_HEALTH / 2, 1, 1, Properties.PLAYER_SPEED);
      }
    }

    state.logic();
  }

  private void sendState() throws IOException {
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
      buffer.clear();
      buffer.put((byte) 1);
      buffer2.clear();
      state.serialize(i, buffer, buffer2, false);
      for (int j = 0; j < Properties.PLAYER_COUNT; j++) {
        write(channels[j], buffer, buffer2);
        buffer.rewind();
        buffer2.rewind();
      }
    }
    for (int i = 0; i < Properties.ENTITIES_MAX; i++) {
      if (state.getEntity(i).isDestroyed()) {
        continue;
      }
      buffer.clear();
      buffer.put((byte) 2);
      state.serialize(i, buffer);
      for (int j = 0; j < Properties.PLAYER_COUNT; j++) {
        write(channels[j], buffer);
        buffer.rewind();
      }
    }
  }

  private void exit() {
    // nothing to do
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
