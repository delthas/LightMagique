package fr.delthas.network;

class PacketData {

  public int sequence;
  public float time;
  public int size;
  public int saveId;

  public PacketData(int sequence, float time, int size, int saveId) {
    this.sequence = sequence;
    this.time = time;
    this.size = size;
    this.saveId = saveId;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof PacketData)) {
      return false;
    }
    PacketData other = (PacketData) obj;
    if (sequence != other.sequence) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    return sequence;
  }
}
