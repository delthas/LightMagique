package fr.delthas.lightmagique.shared;

import java.nio.ByteBuffer;

public class Entity {

  private int entityId;
  private boolean destroyed = true;
  private boolean enemy;
  private boolean moving = true;
  private int hitbox; // in hundred units
  private double x, y;
  private double speed, angle;
  private int health;

  public Entity(int entityId) {
    this.entityId = entityId;
  }

  public void create(double x, double y, double speed, double angle, int hitbox, int health, boolean enemy) {
    destroyed = false;
    this.x = x;
    this.y = y;
    this.speed = speed;
    this.angle = angle;
    this.hitbox = hitbox;
    this.health = health;
    this.enemy = enemy;
  }

  void update(ByteBuffer entity) {
    byte bitfield = entity.get();
    destroyed = (bitfield & 0b100) != 0;
    enemy = (bitfield & 0b10) != 0;
    moving = (bitfield & 0b1) != 0;
    x = entity.getFloat();
    y = entity.getFloat();
    hitbox = entity.getShort();
    speed = entity.getDouble();
    angle = entity.getDouble();
    health = entity.getShort();
  }

  void serialize(ByteBuffer entity) {
    byte bitfield = 0;
    if (destroyed) {
      bitfield |= 0b100;
    }
    if (enemy) {
      bitfield |= 0b10;
    }
    if (moving) {
      bitfield |= 0b1;
    }
    entity.put(bitfield);
    entity.putFloat((float) x);
    entity.putFloat((float) y);
    entity.putShort((short) hitbox);
    entity.putDouble(speed);
    entity.putDouble(angle);
    entity.putShort((short) health);
  }

  int getEntityId() {
    return entityId;
  }

  public boolean isDestroyed() {
    return destroyed;
  }

  public void destroy() {
    destroyed = true;
  }

  void setDestroyed(boolean destroyed) {
    this.destroyed = destroyed;
  }

  public double getX() {
    return x;
  }

  public void setX(double x) {
    this.x = x;
  }

  public double getY() {
    return y;
  }

  public void setY(double y) {
    this.y = y;
  }

  public double getSpeed() {
    return speed;
  }

  public void setSpeed(double speed) {
    this.speed = speed;
  }

  public double getAngle() {
    return angle;
  }

  public void setAngle(double angle) {
    this.angle = angle;
  }

  public int getHitbox() {
    return hitbox;
  }

  public void setHitbox(int hitbox) {
    this.hitbox = hitbox;
  }

  public int getHealth() {
    return health;
  }

  public void increaseHealth(int amount) {
    if (amount < 0) {
      throw new IllegalArgumentException("Montant négatif : " + amount);
    }
    health += amount;
  }

  public void decreaseHealth(int amount) {
    if (amount < 0) {
      throw new IllegalArgumentException("Montant négatif : " + amount);
    }
    if (health <= amount) {
      health = 0;
      zeroHealth();
    } else {
      health = health - amount;
    }
  }

  protected void zeroHealth() {
    destroy();
  }

  /**
   * Outrepasse les déclenchements d'actions liées à la vie
   */
  public void setHealth(int amount) {
    health = amount;
  }

  public boolean isEnemy() {
    return enemy;
  }

  public boolean isMoving() {
    return moving;
  }

  public void setMoving(boolean moving) {
    this.moving = moving;
  }

}
