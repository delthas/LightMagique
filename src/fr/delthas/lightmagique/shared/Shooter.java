package fr.delthas.lightmagique.shared;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Shooter extends Entity {

  // Levels
  private static final int BALL_SPEED = 0;
  private static final int BALL_COOLDOWN = 1;
  private static final int BALL_DAMAGE = 2;
  private static final int BASE_SPEED = 3;
  private static final int CHARGED_BALL_CHARGE = 4;
  private static final int CHARGED_BALL_MAX_CHARGE = 5;
  private static final int CHARGED_BALL_SLOWDOWN = 6;
  private static final int CHARGED_BALL_COOLDOWN = 7;
  private static final int DASH_SPEEDUP = 8;
  private static final int DASH_DURATION = 9;
  private static final int DASH_COOLDOWN = 10;
  private static final int FREEZE_DURATION = 11;
  private static final int MAX_HEALTH = 12;

  private static final String[] LEVEL_NAMES = {"Vitesse des boules", "Cooldown des boules", "Dommages des boules", "Vitesse du joueur",
      "Vitesse de charge de la boule chargée", "Charge maximale de la boule chargée", "Ralentissement dû au chargement de boule chargée",
      "Cooldown de la boule chargée", "Vitesse du dash", "Durée du dash", "Cooldown du dash", "Temps de freeze", "Vie maximale"};
  private static final int[] LEVEL_DEFAULTS = {1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1};
  private int[] levels = new int[13];

  // Cooldowns
  private int ballCooldown;
  private int chargedBallCooldown;
  private int dashCooldown;

  // Durations
  private double chargePower;
  private int freezeDuration;
  private int dashDuration;

  public Shooter() {
    for (int i = 0; i < levels.length; i++) {
      levels[i] = LEVEL_DEFAULTS[i];
    }
  }

  public void createPlayer(double x, double y, double angle) {
    super.create(x, y, Properties.getBaseSpeed(levels[BALL_SPEED]), angle, Properties.getMaxHealth(levels[MAX_HEALTH]), false);
    reset();
  }

  public void createEnemy(double x, double y, double angle, int level) {
    for (int i = 0; i < levels.length; i++) {
      levels[i] = level;
    }
    super.create(x, y, Properties.getBaseSpeed(levels[BALL_SPEED]), angle, Properties.getMaxHealth(levels[MAX_HEALTH]), true);
    reset();
  }

  private void reset() {
    ballCooldown = 0;
    chargedBallCooldown = 0;
    dashCooldown = 0;
    chargePower = -1;
    freezeDuration = 0;
    dashDuration = 0;
  }

  void update(ByteBuffer entity, ByteBuffer shooter) {
    super.update(entity);
    levels[BASE_SPEED] = shooter.get();
    levels[CHARGED_BALL_SLOWDOWN] = shooter.get();
    levels[DASH_SPEEDUP] = shooter.get();
    levels[MAX_HEALTH] = shooter.get();
    freezeDuration = shooter.getShort();
    dashDuration = shooter.getShort();
    chargePower = shooter.get() != 0 ? 0 : -1;
  }

  void serialize(ByteBuffer entity, ByteBuffer shooter) {
    super.serialize(entity);
    shooter.put((byte) levels[BASE_SPEED]);
    shooter.put((byte) levels[CHARGED_BALL_SLOWDOWN]);
    shooter.put((byte) levels[DASH_SPEEDUP]);
    shooter.put((byte) levels[MAX_HEALTH]);
    shooter.putShort((short) freezeDuration);
    shooter.putShort((short) dashDuration);
    shooter.put(isCharging() ? (byte) 1 : (byte) 0);
    shooter.flip();
  }

  public void logic() {
    --ballCooldown;
    --chargedBallCooldown;
    --dashCooldown;
    if (isFrozen()) {
      --freezeDuration;
      if (!isFrozen()) {
        recomputeSpeed();
        increaseHealth(1);
      }
    }
    if (isDashing()) {
      --dashDuration;
      if (!isDashing()) {
        recomputeSpeed();
      }
    }
    if (isCharging()) {
      chargePower += Properties.getChargedBallCharge(levels[CHARGED_BALL_CHARGE]);
      double max = Properties.getChargedBallMaxCharge(levels[CHARGED_BALL_MAX_CHARGE]);
      if (chargePower > max) {
        chargePower = max;
      }
    }
  }

  /**
   * @return si l'on a libéré une boule, sa vitesse et sa vie, sinon null
   */
  public Pair<Double, Integer> ball() {
    if (ballCooldown > 0) {
      return null;
    }
    if (isCharging()) {
      return null;
    }
    ballCooldown = Properties.getBallCooldown(levels[BALL_COOLDOWN]);
    if (isFrozen()) {
      // Faire perdre le cooldown même si on lance rien, si on est freeze
      return null;
    }
    return new Pair<>(Properties.getBallSpeed(levels[BALL_SPEED]), Properties.getBallDamage(levels[BALL_DAMAGE]));
  }

  /**
   * @return true si le dash a été lancé, false si le cooldown est pas fini.
   */
  public boolean dash() {
    if (dashCooldown > 0) {
      return false;
    }
    dashCooldown = Properties.getDashCooldown(levels[DASH_COOLDOWN]);
    dashDuration = Properties.getDashDuration(levels[DASH_DURATION]);
    recomputeSpeed();
    return true;
  }

  @Override
  public void increaseHealth(int amount) {
    int maxHealth = Properties.getMaxHealth(levels[MAX_HEALTH]);
    if (getHealth() + amount >= maxHealth) {
      super.increaseHealth(maxHealth - getHealth());
    } else {
      super.increaseHealth(amount);
    }
  }

  @Override
  protected void zeroHealth() {
    dashDuration = 0;
    chargePower = -1;
    freezeDuration = Properties.getFreezeDuration(levels[FREEZE_DURATION]);
    recomputeSpeed();
  }

  public boolean isFrozen() {
    return freezeDuration > 0;
  }

  public boolean isDashing() {
    return dashDuration > 0;
  }

  public boolean isCharging() {
    return chargePower >= 0;
  }

  /**
   * @return true si l'on commence à charger
   */
  public boolean charge() {
    if (isFrozen()) {
      return false;
    }
    if (isCharging()) {
      return false;
    }
    if (chargedBallCooldown > 0) {
      return false;
    }
    chargePower = 0;
    recomputeSpeed();
    return true;
  }

  /**
   * @return si l'on a libéré une boule, sa vitesse et sa vie, sinon null
   */
  public Pair<Double, Integer> stopCharge() {
    if (isFrozen()) {
      return null;
    }
    if (!isCharging()) {
      return null;
    }
    int ballHealth = (int) chargePower;
    chargePower = -1;
    chargedBallCooldown = Properties.getChargedBallCooldown(levels[CHARGED_BALL_COOLDOWN]);
    recomputeSpeed();
    if (ballHealth == 0) {
      return null;
    }
    return new Pair<>(Properties.getBallSpeed(levels[BALL_SPEED]), ballHealth);
  }

  private void recomputeSpeed() {
    if (isDestroyed()) {
      return;
    }
    if (isFrozen()) {
      setSpeed(0);
      return;
    }
    double newSpeed = Properties.getBaseSpeed(levels[BASE_SPEED]);
    if (isDashing()) {
      newSpeed *= Properties.getDashSpeedup(levels[DASH_SPEEDUP]);
    }
    if (isCharging()) {
      newSpeed *= Properties.getChargedBallSlowdown(levels[DASH_SPEEDUP]);
    }
    setSpeed(newSpeed);
  }

  public static String[] getLevelNames() {
    return Arrays.copyOf(LEVEL_NAMES, LEVEL_NAMES.length);
  }

  public int[] getLevels() {
    return Arrays.copyOf(levels, levels.length);
  }

  /**
   * @return true si le niveau a bien été monté
   */
  public boolean increaseLevel(int i) {
    if (levels[i] == Properties.MAX_LEVEL) {
      return false;
    }
    levels[i]++;
    recomputeSpeed();
    return true;
  }

  public float getHealthPercent() {
    return (float) getHealth() / Properties.getMaxHealth(levels[MAX_HEALTH]);
  }
}
