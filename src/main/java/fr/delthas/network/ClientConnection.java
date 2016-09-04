package fr.delthas.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

/**
 * optimizations:
 * * no synchronized access
 * * one buffer for sending
 * * one buffer for receiving
 * * data has to start at position == 0 in sendBuffer
 * guarantees:
 * * buffers are unchanged by sendPacket
 * * position == 0 on receivePacket
 */
public class ClientConnection {

  private byte protocolId;
  private float timeout;
  private Socket socket;
  private float timeoutAccumulator = 0f;
  private InetSocketAddress address;
  private ReliabilitySystem reliabilitySystem;
  private ByteBuffer receiveBuffer;
  private ByteBuffer receiveBufferSlice;
  private ByteBuffer sendBuffer;
  private ByteBuffer sendBufferSlice;

  private int saveIndex = -1;
  private ByteBuffer[] saves = new ByteBuffer[10];

  public ClientConnection(byte protocolId, float timeout, int packetMaxSize) {
    this.protocolId = protocolId;
    this.timeout = timeout;
    socket = new Socket();
    receiveBuffer = ByteBuffer.allocateDirect(packetMaxSize + 9);
    receiveBuffer.position(9);
    receiveBufferSlice = receiveBuffer.slice();
    sendBuffer = ByteBuffer.allocateDirect(packetMaxSize + 9);
    sendBuffer.position(9);
    sendBufferSlice = sendBuffer.slice();
    reliabilitySystem = new ReliabilitySystem() {
      @Override
      protected void packetLost(int saveId) throws IOException {
        ClientConnection.this.packetLost(saveId);
      }
    };
    for (int i = 0; i < saves.length; i++) {
      saves[i] = ByteBuffer.allocateDirect(packetMaxSize + 9);
    }
  }

  public void start(InetSocketAddress address) throws IOException {
    this.address = address;
    timeoutAccumulator = 0f;
    reliabilitySystem.reset();
    socket.open();
  }

  public void stop() throws IOException {
    socket.close();
  }

  /**
   * this will flip the buffer if not already flipped
   * then will CLEAR it
   */
  public void sendPacket(boolean retry) throws IOException {
    if (sendBufferSlice.position() != 0) {
      sendBufferSlice.flip();
    }
    if (retry) {
      saveIndex = (saveIndex + 1) % saves.length;
      saves[saveIndex].clear();
      saves[saveIndex].position(9);
      saves[saveIndex].put(sendBufferSlice);
      saves[saveIndex].flip();
      sendBufferSlice.position(0);
    }
    int size = sendBufferSlice.remaining();
    sendBufferSlice.clear();
    sendBuffer.clear();
    sendBuffer.put(protocolId);
    sendBuffer.putShort((short) reliabilitySystem.getLocalSequence());
    sendBuffer.putShort((short) reliabilitySystem.getRemoteSequence());
    sendBuffer.putInt(reliabilitySystem.generateAckBits());
    sendBuffer.position(0);
    sendBuffer.limit(9 + size);
    socket.send(address, sendBuffer);
    reliabilitySystem.packetSent(size, retry ? saveIndex : -1);
  }

  /**
   * this will flip the buffer if not already flipped
   * then will CLEAR it
   */
  public void sendPacket() throws IOException {
    sendPacket(false);
  }

  private void packetLost(int saveId) throws IOException {
    if (saveId == -1)
      return;
    saves[saveId].put(protocolId);
    saves[saveId].putShort((short) reliabilitySystem.getLocalSequence());
    saves[saveId].putShort((short) reliabilitySystem.getRemoteSequence());
    saves[saveId].putInt(reliabilitySystem.generateAckBits());
    saves[saveId].position(0);
    socket.send(address, saves[saveId]);
    reliabilitySystem.packetSent(saves[saveId].limit() - 9, saveId);
  }

  /**
   * (always true if blocking is true)
   *
   * @return true if a message has been received (first message received implies connection has been established)
   */
  public boolean receivePacket(boolean blocking) throws IOException {
    receiveBuffer.clear();
    while (true) {
      SocketAddress sender = blocking ? socket.receiveBlocking(receiveBuffer) : socket.receive(receiveBuffer);
      receiveBuffer.flip();
      if (!address.equals(sender) || receiveBuffer.remaining() <= 9 || receiveBuffer.get() != protocolId) {
        if (!blocking) {
          return false;
        }
      } else {
        break;
      }
    }
    int packetSequence = Short.toUnsignedInt(receiveBuffer.getShort());
    int packetAck = Short.toUnsignedInt(receiveBuffer.getShort());
    int packetAckBits = receiveBuffer.getInt();
    reliabilitySystem.packetReceived(packetSequence, receiveBuffer.remaining());
    reliabilitySystem.processAck(packetAck, packetAckBits);
    timeoutAccumulator = 0.0f;
    receiveBufferSlice.clear();
    receiveBufferSlice.limit(receiveBuffer.remaining());
    return true;
  }

  /**
   * @return true if a message has been received (first message received implies connection has been established)
   */
  public boolean receivePacket() throws IOException {
    return receivePacket(false);
  }

  public ByteBuffer getReceiveBuffer() {
    return receiveBufferSlice;
  }

  public ByteBuffer getSendBuffer() {
    return sendBufferSlice;
  }

  /**
   * @return true if the connection has timed out
   */
  public boolean update(float deltaTime) throws IOException {
    timeoutAccumulator += deltaTime;
    if (timeoutAccumulator > timeout) {
      return true;
    }
    reliabilitySystem.update(deltaTime);
    return false;
  }

  public int getSentPackets() {
    return reliabilitySystem.getSentPackets();
  }

  public int getRecvPackets() {
    return reliabilitySystem.getRecvPackets();
  }

  public int getLostPackets() {
    return reliabilitySystem.getLostPackets();
  }

  public int getAckedPackets() {
    return reliabilitySystem.getAckedPackets();
  }

  public float getRtt() {
    return reliabilitySystem.getRtt();
  }

}
