package fr.delthas.network;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Iterator;

abstract class ReliabilitySystem {

  private static final int maxSequence = 0xFFFF;
  private int localSequence;
  private int remoteSequence;

  private int sentPackets;
  private int recvPackets;
  private int lostPackets;
  private int ackedPackets;

  private float rtt;
  private float rttMaximum;

  private ArrayDeque<PacketData> pendingAckQueue = new ArrayDeque<>();
  private ArrayDeque<PacketData> receivedQueue = new ArrayDeque<>();

  public ReliabilitySystem() {
    reset();
  }

  public void reset() {
    localSequence = 0;
    remoteSequence = 0;
    receivedQueue.clear();
    pendingAckQueue.clear();
    sentPackets = 0;
    recvPackets = 0;
    lostPackets = 0;
    ackedPackets = 0;
    rtt = 0.0f;
    rttMaximum = 1.0f;
  }

  public void packetSent(int size, int saveId) {
    PacketData data = new PacketData(localSequence, 0.0f, size, saveId);
    pendingAckQueue.addLast(data);
    sentPackets++;
    localSequence++;
    if (localSequence > maxSequence) {
      localSequence = 0;
    }
  }

  public void packetReceived(int sequence, int size) {
    recvPackets++;
    for (PacketData packet : receivedQueue) {
      if (sequence == packet.sequence) {
        return;
      }
    }
    PacketData data = new PacketData(sequence, 0.0f, size, -1);
    receivedQueue.addLast(data);
    if (sequenceMoreRecent(sequence, remoteSequence, maxSequence)) {
      remoteSequence = sequence;
    }
  }

  public int generateAckBits() {
    int ackBits = 0;
    Iterator<PacketData> it = receivedQueue.iterator();
    while (it.hasNext()) {
      PacketData data = it.next();
      if (data.sequence == remoteSequence || sequenceMoreRecent(data.sequence, remoteSequence, maxSequence)) {
        break;
      }
      int bitIndex = bitIndexForSequence(data.sequence, remoteSequence, maxSequence);
      if (bitIndex <= 31) {
        ackBits |= 1 << bitIndex;
      }
    }
    return ackBits;
  }

  public void processAck(int ack, int ackBits) {
    if (pendingAckQueue.isEmpty()) {
      return;
    }
    Iterator<PacketData> it = pendingAckQueue.iterator();
    while (it.hasNext()) {
      PacketData data = it.next();
      boolean acked = false;
      if (data.sequence == ack) {
        acked = true;
      } else if (!sequenceMoreRecent(data.sequence, ack, maxSequence)) {
        int bitIndex = bitIndexForSequence(data.sequence, ack, maxSequence);
        if (bitIndex <= 31) {
          acked = (ackBits >> bitIndex & 1) == 1 ? true : false;
        }
      }
      if (acked) {
        rtt += (data.time - rtt) * 0.1f;
        ackedPackets++;
        it.remove();
      }
    }
  }

  public void update(float deltaTime) throws IOException {
    advanceQueueTime(deltaTime);
    updateQueues();
  }

  private static final int bitIndexForSequence(int sequence, int ack, int maxSequence) {
    if (sequence > ack) {
      return ack + maxSequence - sequence;
    } else {
      return ack - 1 - sequence;
    }
  }

  private void advanceQueueTime(float deltaTime) {
    for (PacketData data : receivedQueue) {
      data.time += deltaTime;
    }
    for (PacketData data : pendingAckQueue) {
      data.time += deltaTime;
    }
  }

  private void updateQueues() throws IOException {
    float epsilon = 0.001f;
    if (!receivedQueue.isEmpty()) {
      int latestSequence = receivedQueue.getLast().sequence;
      int minimumSequence = latestSequence >= 34 ? latestSequence - 34 : maxSequence - (34 - latestSequence);
      while (!receivedQueue.isEmpty() && !sequenceMoreRecent(receivedQueue.getFirst().sequence, minimumSequence, maxSequence)) {
        receivedQueue.pop();
      }
    }
    while (!pendingAckQueue.isEmpty() && pendingAckQueue.getFirst().time > rttMaximum + epsilon) {
      packetLost(pendingAckQueue.pop().saveId);
      lostPackets++;
    }
  }

  protected abstract void packetLost(int saveId) throws IOException;

  public int getSentPackets() {
    return sentPackets;
  }

  public int getRecvPackets() {
    return recvPackets;
  }

  public int getLostPackets() {
    return lostPackets;
  }

  public int getAckedPackets() {
    return ackedPackets;
  }

  public float getRtt() {
    return rtt;
  }

  public int getLocalSequence() {
    return localSequence;
  }

  public int getRemoteSequence() {
    return remoteSequence;
  }

  private static boolean sequenceMoreRecent(int s1, int s2, int max) {
    return (s1 > s2) && (s1 - s2 <= max / 2) || (s2 > s1) && (s2 - s1 > max / 2);
  }

}
