package fr.delthas.lightmagique.shared;

import java.nio.ByteBuffer;

/**
 *
 */
public class Entity {

  private boolean destroyed = true;
  private double x, y;
  private double speed, angle;
  private int drawable;
  private boolean enemy;

  public void create(double x, double y, double speed, double angle, int drawable, boolean enemy) {
    destroyed = false;
    this.x = x;
    this.y = y;
    this.speed = speed;
    this.angle = angle;
    this.drawable = drawable;
    this.enemy = enemy;
  }

  void update(ByteBuffer entity) {
    destroyed = entity.get() != 0;
    x = entity.getDouble();
    y = entity.getDouble();
    speed = entity.getDouble();
    angle = entity.getDouble();
    drawable = entity.getInt();
    enemy = entity.get() != 0;
  }

  void serialize(ByteBuffer entity) {
    entity.put(destroyed ? (byte) 1 : (byte) 0);
    entity.putDouble(x);
    entity.putDouble(y);
    entity.putDouble(speed);
    entity.putDouble(angle);
    entity.putInt(drawable);
    entity.put(enemy ? (byte) 1 : (byte) 0);
    entity.flip();
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

  public int getDrawable() {
    return drawable;
  }

  public void setDrawable(int drawable) {
    this.drawable = drawable;
  }

  public boolean isEnemy() {
    return enemy;
  }

}
