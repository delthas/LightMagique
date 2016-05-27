package fr.delthas.lightmagique.shared;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

public class Shooter extends Entity {

  private static class Balance {

    public static double getBallSpeed(Shooter shooter) {
      return 3.0 + 0.5 * shooter.levels[BALL_SPAMITY];
    }

    public static int getBallCooldown(Shooter shooter) {
      return (int) (300 / Math.pow(shooter.levels[BALL_SPAMITY], 1.3));
    }

    public static int getBallDamage(Shooter shooter) {
      return 0 * (int) (0.4 + 0.6 * shooter.levels[BALL_POWER]); // FIXME cc
    }

    public static double getBaseSpeed(Shooter shooter) {
      if (shooter.levels[PLAYER_SPAMITY] == 0) {
        return 0;
      }
      return 2.0 + 0.38 * shooter.levels[PLAYER_SPAMITY];
    }

    public static double getChargedBallCharge(Shooter shooter) {
      return 0.01 * 7.5 * shooter.levels[CHARGED_BALL_POWER];
    }

    public static double getChargedBallSpeed(Shooter shooter) {
      return 1.7 + 0.3 * shooter.levels[CHARGED_BALL_SPAMITY];
    }

    public static int getChargedBallMaxCharge(Shooter shooter) {
      return 15 * shooter.levels[CHARGED_BALL_POWER];
    }

    // Warning: update this when updating getChargedBallMaxCharge
    public static int getMaxDamage() {
      return 15 * Properties.MAX_LEVEL;
    }

    public static double getChargedBallSlowdown(Shooter shooter) {
      return Math.min(1, 0.035 * shooter.levels[CHARGED_BALL_SPAMITY]);
    }

    public static int getChargedBallCooldown(Shooter shooter) {
      return 800 - 8 * shooter.levels[CHARGED_BALL_SPAMITY];
    }

    public static double getChargedBallHitboxCharge(Shooter shooter) {
      return 100 * 0.01 * 4 * shooter.levels[CHARGED_BALL_SPAMITY];
    }

    public static int getChargedBallMaxHitbox(Shooter shooter) {
      return 100 * 10 * shooter.levels[CHARGED_BALL_POWER];
    }

    public static double getDashSpeedup(Shooter shooter) {
      return 0.8 + 0.3 * shooter.levels[DASH_POWER];
    }

    public static int getDashDuration(Shooter shooter) {
      return 50 + shooter.levels[DASH_SPAMITY] * 8;
    }

    public static int getDashCooldown(Shooter shooter) {
      return 500 - 15 * shooter.levels[DASH_SPAMITY];
    }

    public static int getFreezeDuration(Shooter shooter) {
      if (shooter.levels[PLAYER_POWER] == 0) {
        return Short.MAX_VALUE;
      }
      return 500 - 5 * shooter.levels[PLAYER_POWER];
    }

    public static int getMaxHealth(Shooter shooter) {
      return 2 * shooter.levels[PLAYER_POWER];
    }

  }

  private static Random random = new Random();

  // Levels
  private static final int BALL_SPAMITY = 0;
  private static final int BALL_POWER = 1;
  private static final int CHARGED_BALL_SPAMITY = 2;
  private static final int CHARGED_BALL_POWER = 3;
  private static final int DASH_SPAMITY = 4;
  private static final int DASH_POWER = 5;
  private static final int PLAYER_SPAMITY = 6;
  private static final int PLAYER_POWER = 7;

  private static final String[] LEVEL_NAMES = {"Boule: Spaméïté", "Boule: Puissance", "Boule chargée: Spaméïté", "Boule chargée: Puissance",
      "Dash: Spaméïté", "Dash: Puissance", "Joueur: Spaméïté", "Joueur: Puissance"};
  private static final int[] LEVEL_DEFAULTS = {1, 1, 0, 0, 0, 0, 1, 1};
  private int[] levels = new int[LEVEL_DEFAULTS.length];

  // Cooldowns
  private int ballCooldown;
  private int chargedBallCooldown;
  private int dashCooldown;

  // Durations
  private int chargeTicks;
  private int freezeDuration;
  private int dashDuration;

  public Shooter() {
    for (int i = 0; i < levels.length; i++) {
      levels[i] = LEVEL_DEFAULTS[i];
    }
  }

  public void createPlayer(double x, double y, double angle) {
    super.create(x, y, Balance.getBaseSpeed(this), angle, Properties.PLAYER_HITBOX, Balance.getMaxHealth(this), false);
    reset();
  }

  public void createEnemy(double x, double y, double angle, int globalLevel, boolean randomize) {
    if (!randomize) {
      Arrays.fill(levels, 1 + globalLevel);
    } else {
      Arrays.fill(levels, 1);
      levels[PLAYER_POWER] = 1 + (int) (0.8 * globalLevel);
      for (int i = 0; i < globalLevel; i++) {
        levels[random.nextInt(levels.length)]++;
      }
      for (int i = 0; i < levels.length; i++) {
        levels[i] = Math.min(levels[i], Properties.MAX_LEVEL);
      }
    }
    super.create(x, y, Balance.getBaseSpeed(this), angle, Properties.ENEMY_HITBOX, Balance.getMaxHealth(this), true);
    reset();
  }

  private void reset() {
    ballCooldown = 0;
    chargedBallCooldown = 0;
    dashCooldown = 0;
    chargeTicks = -1;
    freezeDuration = 0;
    dashDuration = 0;
  }

  void update(ByteBuffer entity, ByteBuffer shooter) {
    super.update(entity);
    levels[PLAYER_SPAMITY] = shooter.get();
    levels[CHARGED_BALL_SPAMITY] = shooter.get();
    levels[DASH_POWER] = shooter.get();
    levels[PLAYER_POWER] = shooter.get();
    freezeDuration = shooter.getShort();
    dashDuration = shooter.getShort();
    chargeTicks = shooter.get() != 0 ? 0 : -1;
  }

  void serialize(ByteBuffer entity, ByteBuffer shooter) {
    super.serialize(entity);
    shooter.put((byte) levels[PLAYER_SPAMITY]);
    shooter.put((byte) levels[CHARGED_BALL_SPAMITY]);
    shooter.put((byte) levels[DASH_POWER]);
    shooter.put((byte) levels[PLAYER_POWER]);
    shooter.putShort((short) freezeDuration);
    shooter.putShort((short) dashDuration);
    shooter.put(isCharging() ? (byte) 1 : (byte) 0);
    shooter.flip();
  }

  public void logic() {
    if (ballCooldown > 0) {
      --ballCooldown;
    }
    if (chargedBallCooldown > 0) {
      --chargedBallCooldown;
    }
    if (dashCooldown > 0) {
      --dashCooldown;
    }
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
      chargeTicks++;
    }
  }

  /**
   * @return si l'on a libéré une boule, sa vitesse et sa vie et sa hitbox, sinon null
   */
  public Triplet<Double, Integer, Integer> ball() {
    if (ballCooldown > 0) {
      return null;
    }
    if (isCharging()) {
      return null;
    }
    ballCooldown = Balance.getBallCooldown(this);
    if (isFrozen()) {
      // Faire perdre le cooldown même si on lance rien, si on est freeze
      return null;
    }
    return new Triplet<>(Balance.getBallSpeed(this), Balance.getBallDamage(this), Properties.BALL_HITBOX);
  }

  /**
   * @return true si le dash a été lancé, false si le cooldown est pas fini.
   */
  public boolean dash() {
    if (dashCooldown > 0) {
      return false;
    }
    dashCooldown = Balance.getDashCooldown(this);
    dashDuration = Balance.getDashDuration(this);
    recomputeSpeed();
    return true;
  }

  @Override
  public void increaseHealth(int amount) {
    int maxHealth = Balance.getMaxHealth(this);
    if (getHealth() + amount >= maxHealth) {
      super.increaseHealth(maxHealth - getHealth());
    } else {
      super.increaseHealth(amount);
    }
  }

  @Override
  protected void zeroHealth() {
    dashDuration = 0;
    chargeTicks = -1;
    freezeDuration = Balance.getFreezeDuration(this);
    recomputeSpeed();
  }

  public boolean isFrozen() {
    return freezeDuration > 0;
  }

  public boolean isDashing() {
    return dashDuration > 0;
  }

  public boolean isCharging() {
    return chargeTicks >= 0;
  }

  public boolean canCharge() {
    return levels[CHARGED_BALL_POWER] > 0 && levels[CHARGED_BALL_SPAMITY] > 0;
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
    chargeTicks = 0;
    recomputeSpeed();
    return true;
  }

  /**
   * @return si l'on a libéré une boule, sa vitesse et sa vie et sa hitbox, sinon null
   */
  public Triplet<Double, Integer, Integer> stopCharge() {
    if (isFrozen()) {
      return null;
    }
    if (!isCharging()) {
      return null;
    }
    int ballHealth;
    int charge = (int) (Balance.getChargedBallCharge(this) * chargeTicks);
    if (charge >= Balance.getChargedBallMaxCharge(this)) {
      ballHealth = 2 * Balance.getChargedBallMaxCharge(this);
    } else {
      ballHealth = charge;
    }
    int ballHitbox =
        Properties.BALL_HITBOX + (int) Math.min(Balance.getChargedBallHitboxCharge(this) * chargeTicks, Balance.getChargedBallMaxHitbox(this));
    chargeTicks = -1;
    chargedBallCooldown = Balance.getChargedBallCooldown(this);
    recomputeSpeed();
    if (ballHealth == 0) {
      return null;
    }
    return new Triplet<>(Balance.getChargedBallSpeed(this), ballHealth, ballHitbox);
  }

  private void recomputeSpeed() {
    if (isDestroyed()) {
      return;
    }
    if (isFrozen()) {
      setSpeed(0);
      return;
    }
    double newSpeed = Balance.getBaseSpeed(this);
    if (isDashing()) {
      newSpeed *= Balance.getDashSpeedup(this);
    }
    if (isCharging()) {
      newSpeed *= Balance.getChargedBallSlowdown(this);
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

  public float getDashCooldownPercent() {
    return 1.0f - (float) dashCooldown / Balance.getDashCooldown(this);
  }

  public float getBallCooldownPercent() {
    return 1.0f - (float) ballCooldown / Balance.getBallCooldown(this);
  }

  public float getChargedBallCooldownPercent() {
    return 1.0f - (float) chargedBallCooldown / Balance.getChargedBallCooldown(this);
  }

  public float getChargedBallChargePercent() {
    if (chargeTicks < 0) {
      return 0.0f;
    }
    return (float) Math.min(Balance.getChargedBallHitboxCharge(this) * chargeTicks / Balance.getChargedBallMaxHitbox(this), 1.0f);
  }

  public float getHealthPercent() {
    return (float) getHealth() / Balance.getMaxHealth(this);
  }

  public static final int getMaxDamage() {
    return Balance.getMaxDamage();
  }
}
