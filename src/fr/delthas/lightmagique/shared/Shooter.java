package fr.delthas.lightmagique.shared;

import java.nio.ByteBuffer;

public class Shooter extends Entity {

  private int health;
  private int ballLevel;
  private int ballCooldown = 0;
  private int dashLevel;
  private int dashCooldown = 0;
  private int freezeTime = 0;
  private int dashTime = 0;
  private double baseSpeed;

  public void create(double x, double y, double speed, double angle, int drawable, boolean enemy, int health, int ballLevel, int dashLevel,
      double baseSpeed) {
    super.create(x, y, speed, angle, drawable, enemy);
    this.health = health;
    this.ballLevel = ballLevel;
    this.dashLevel = dashLevel;
    this.baseSpeed = baseSpeed;
    ballCooldown = 0;
    dashCooldown = 0;
    freezeTime = 0;
    dashTime = 0;
  }

  void update(ByteBuffer entity, ByteBuffer shooter) {
    super.update(entity);
    health = shooter.getInt();
    ballLevel = shooter.getInt();
    ballCooldown = shooter.getInt();
    dashLevel = shooter.getInt();
    dashCooldown = shooter.getInt();
    freezeTime = shooter.getInt();
    dashTime = shooter.getInt();
    baseSpeed = shooter.getDouble();
  }

  void serialize(ByteBuffer entity, ByteBuffer shooter) {
    super.serialize(entity);
    shooter.putInt(health);
    shooter.putInt(ballLevel);
    shooter.putInt(ballCooldown);
    shooter.putInt(dashLevel);
    shooter.putInt(dashCooldown);
    shooter.putInt(freezeTime);
    shooter.putInt(dashTime);
    shooter.putDouble(baseSpeed);
    shooter.flip();
  }

  public void cooldown() {
    if (ballCooldown > 0) {
      --ballCooldown;
    }
    if (dashCooldown > 0) {
      --dashCooldown;
    }
    if (freezeTime > 0) {
      --freezeTime;
      if (freezeTime == 0) {
        setDrawable(getDrawable() - 1);
        setSpeed(baseSpeed);
        increaseHealth(1);
      }
    }
    if (dashTime > 0) {
      --dashTime;
      if (dashTime == 0) {
        setSpeed(baseSpeed);
      }
    }
  }

  /**
   * @return true si la balle a été lancée, false si le cooldown est pas fini.
   */
  public boolean ball() {
    if (isFreeze()) {
      return false;
    }
    if (ballCooldown > 0) {
      return false;
    }
    ballCooldown = Properties.getBallCooldown(ballLevel);
    return true;
  }

  /**
   * @return true si le dash a été lancé, false si le cooldown est pas fini.
   */
  public boolean dash() {
    if (isFreeze()) {
      return false;
    }
    if (dashCooldown > 0) {
      return false;
    }
    dashCooldown = Properties.getDashCooldown(dashLevel);
    dashTime = Properties.DASH_TIME;
    setSpeed(baseSpeed * Properties.DASH_MULTIPLY);
    return true;
  }

  public int getHealth() {
    return health;
  }

  public void increaseHealth(int amount) {
    if (isFreeze()) {
      return;
    }
    health += amount;
  }

  public void decreaseHealth(int amount) {
    if (getHealth() <= 0) {
      return;
    }
    if (health <= amount) {
      health = 0;
      freezeTime = Properties.FREEZE_TIME;
      setDrawable(getDrawable() + 1);
      setSpeed(0);
      dashTime = 0;
    } else {
      health = health - amount;
    }
  }

  /**
   * Outrepasse les déclenchements d'actions liées à la vie
   */
  public void setHealth(int amount) {
    health = amount;
  }

  public int getBallLevel() {
    return ballLevel;
  }

  public void increaseBallLevel() {
    ++ballLevel;
  }

  public int getDashLevel() {
    return dashLevel;
  }

  public void increaseDashLevel() {
    ++dashLevel;
  }

  public boolean isFreeze() {
    return freezeTime > 0;
  }

  public boolean isDashing() {
    return dashTime > 0;
  }

}
