package fr.delthas.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

class Socket {

  private DatagramChannel channel;
  private SelectionKey key;

  public void open() throws IOException {
    channel = DatagramChannel.open();
    channel.configureBlocking(false);
    channel.bind(null);
    channel.setOption(StandardSocketOptions.IP_TOS, 0b1000);
    key = channel.register(Selector.open(), SelectionKey.OP_WRITE | SelectionKey.OP_READ);
  }

  public void open(int port) throws IOException {
    channel = DatagramChannel.open();
    channel.configureBlocking(false);
    channel.bind(new InetSocketAddress(port));
    key = channel.register(Selector.open(), SelectionKey.OP_WRITE | SelectionKey.OP_READ);
  }

  public void close() throws IOException {
    if (channel != null) {
      channel.close();
      channel = null;
    }
  }

  public void send(SocketAddress destination, ByteBuffer data) throws IOException {
    do {
      key.selector().select();
    } while (!key.isWritable());
    if (channel.send(data, destination) == 0) {
      throw new IOException("Couldn't send datagram to ready channel.");
    }
  }

  public SocketAddress receive(ByteBuffer data) throws IOException {
    return channel.receive(data);
  }

  public SocketAddress receiveBlocking(ByteBuffer data) throws IOException {
    do {
      key.selector().select();
    } while (!key.isReadable());
    SocketAddress sender = channel.receive(data);
    if (sender == null) {
      throw new IOException("Couldn't read datagram from ready channel.");
    }
    return sender;
  }

}
