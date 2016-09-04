package fr.delthas.network;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

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
public class ServerConnection {

  private static class ConnectionData {
    public float timeoutAccumulator = 0f;
    public SocketAddress address;
    public ReliabilitySystem reliabilitySystem;
    public int id;

    public ConnectionData(ServerConnection serverConnection, SocketAddress address, int id) {
      this.address = address;
      this.id = id;
      reliabilitySystem = new ReliabilitySystem() {
        @Override
        protected void packetLost(int saveId) throws IOException {
          serverConnection.packetLost(ConnectionData.this, saveId);
        }
      };
    }
  }

  private byte protocolId;
  private float timeout;
  private int highestId = -1;
  private Socket socket;
  private List<ConnectionData> connections = new LinkedList<>();
  private ByteBuffer receiveBuffer;
  private ByteBuffer receiveBufferSlice;
  private ByteBuffer sendBuffer;
  private ByteBuffer sendBufferSlice;
  private int sentPacketsClosedConnections = 0;
  private int recvPacketsClosedConnections = 0;
  private int lostPacketsClosedConnections = 0;
  private int ackedPacketsClosedConnections = 0;

  private int saveIndex = -1;
  private ByteBuffer[] saves = new ByteBuffer[10];

  public ServerConnection(byte protocolId, float timeout, int packetMaxSize) {
    this.protocolId = protocolId;
    this.timeout = timeout;
    socket = new Socket();
    receiveBuffer = ByteBuffer.allocateDirect(packetMaxSize + 9);
    receiveBuffer.position(9);
    receiveBufferSlice = receiveBuffer.slice();
    sendBuffer = ByteBuffer.allocateDirect(packetMaxSize + 9);
    sendBuffer.position(9);
    sendBufferSlice = sendBuffer.slice();
    for (int i = 0; i < saves.length; i++) {
      saves[i] = ByteBuffer.allocateDirect(packetMaxSize + 9);
    }
  }

  public void start(int port) throws IOException {
    connections.clear();
    socket.open(port);
  }

  public void stop() throws IOException {
    socket.close();
  }

  /**
   * this will flip the buffer if not already flipped
   */
  public void sendPacket(int address, boolean retry) throws IOException {
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
    ConnectionData connection = connections.get(address);
    sendBuffer.clear();
    sendBuffer.put(protocolId);
    sendBuffer.putShort((short) connection.reliabilitySystem.getLocalSequence());
    sendBuffer.putShort((short) connection.reliabilitySystem.getRemoteSequence());
    sendBuffer.putInt(connection.reliabilitySystem.generateAckBits());
    sendBuffer.position(0);
    sendBuffer.limit(9 + size);
    socket.send(connection.address, sendBuffer);
    connection.reliabilitySystem.packetSent(size, retry ? saveIndex : -1);
  }

  /**
   * this will flip the buffer if not already flipped
   * then will CLEAR it
   */
  public void sendPacket(int address) throws IOException {
    sendPacket(address, false);
  }

  private void packetLost(ConnectionData connectionData, int saveId) throws IOException {
    if (saveId == -1)
      return;
    saves[saveId].put(protocolId);
    saves[saveId].putShort((short) connectionData.reliabilitySystem.getLocalSequence());
    saves[saveId].putShort((short) connectionData.reliabilitySystem.getRemoteSequence());
    saves[saveId].putInt(connectionData.reliabilitySystem.generateAckBits());
    saves[saveId].position(0);
    socket.send(connectionData.address, saves[saveId]);
    saves[saveId].position(0);
    connectionData.reliabilitySystem.packetSent(saves[saveId].limit() - 9, saveId);
  }

  /**
   * (never returns -1 if blocking is true)
   *
   * @return the address that sent the message (or -1 if no message was received)
   */
  public int receivePacket(boolean blocking) throws IOException {
    receiveBuffer.clear();
    SocketAddress sender;
    while (true) {
      sender = blocking ? socket.receiveBlocking(receiveBuffer) : socket.receive(receiveBuffer);
      receiveBuffer.flip();
      if (receiveBuffer.remaining() <= 9 || receiveBuffer.get() != protocolId) {
        if (blocking)
          return -1;
      } else {
        break;
      }
    }
    int packetSequence = Short.toUnsignedInt(receiveBuffer.getShort());
    int packetAck = Short.toUnsignedInt(receiveBuffer.getShort());
    int packetAckBits = receiveBuffer.getInt();
    ConnectionData connection = null;
    for (ConnectionData connection_ : connections) {
      if (sender.equals(connection_.address)) {
        connection = connection_;
        break;
      }
    }
    if (connection == null) {
      connections.add(connection = new ConnectionData(this, sender, ++highestId));
    }
    connection.reliabilitySystem.packetReceived(packetSequence, receiveBuffer.remaining());
    connection.reliabilitySystem.processAck(packetAck, packetAckBits);
    connection.timeoutAccumulator = 0.0f;
    receiveBufferSlice.clear();
    receiveBufferSlice.limit(receiveBuffer.remaining());
    return connection.id;
  }

  /**
   * @return the address that sent the message (or -1) (first message received implies connection has been established)
   */
  public int receivePacket() throws IOException {
    return receivePacket(false);
  }

  public ByteBuffer getReceiveBuffer() {
    return receiveBufferSlice;
  }

  public ByteBuffer getSendBuffer() {
    return sendBufferSlice;
  }

  /**
   * @return a list of connections that have timed out (may be empty)
   */
  public List<Integer> update(float deltaTime) throws IOException {
    List<Integer> timedOut = new ArrayList<>(1);
    Iterator<ConnectionData> it = connections.iterator();
    while (it.hasNext()) {
      ConnectionData connection = it.next();
      connection.timeoutAccumulator += deltaTime;
      if (connection.timeoutAccumulator > timeout) {
        sentPacketsClosedConnections += connection.reliabilitySystem.getSentPackets();
        recvPacketsClosedConnections += connection.reliabilitySystem.getRecvPackets();
        lostPacketsClosedConnections += connection.reliabilitySystem.getLostPackets();
        ackedPacketsClosedConnections += connection.reliabilitySystem.getAckedPackets();
        timedOut.add(connection.id);
        it.remove();
      } else {
        connection.reliabilitySystem.update(deltaTime);
      }
    }
    return timedOut;
  }

  public int getSentPackets() {
    int sum = sentPacketsClosedConnections;
    for (ConnectionData connection : connections) {
      sum += connection.reliabilitySystem.getSentPackets();
    }
    return sum;
  }

  public int getRecvPackets() {
    int sum = recvPacketsClosedConnections;
    for (ConnectionData connection : connections) {
      sum += connection.reliabilitySystem.getRecvPackets();
    }
    return sum;
  }

  public int getLostPackets() {
    int sum = lostPacketsClosedConnections;
    for (ConnectionData connection : connections) {
      sum += connection.reliabilitySystem.getLostPackets();
    }
    return sum;
  }

  public int getAckedPackets() {
    int sum = ackedPacketsClosedConnections;
    for (ConnectionData connection : connections) {
      sum += connection.reliabilitySystem.getAckedPackets();
    }
    return sum;
  }

  public float getMeanRtt() {
    double rtt = 0;
    for (ConnectionData connection : connections) {
      rtt += connection.reliabilitySystem.getRtt();
    }
    return (float) (rtt / connections.size());
  }

}
