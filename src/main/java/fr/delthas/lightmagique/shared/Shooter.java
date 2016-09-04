package fr.delthas.lightmagique.shared;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

public class Shooter extends Entity {

  private static class Balance {

    private static final int BALL_SPEED = 0;
    private static final int BALL_COOLDOWN = 1;
    private static final int BALL_DAMAGE = 2;
    private static final int BASE_SPEED = 3;
    private static final int CHARGED_BALL_CHARGE = 4;
    private static final int CHARGED_BALL_SPEED = 5;
    private static final int CHARGED_BALL_MAX_CHARGE = 6;
    private static final int CHARGED_BALL_SLOWDOWN = 7;
    private static final int CHARGED_BALL_COOLDOWN = 8;
    private static final int CHARGED_BALL_HITBOX_CHARGE = 9;
    private static final int CHARGED_BALL_HITBOX_MAX_CHARGE = 10;
    private static final int DASH_SPEEDUP = 11;
    private static final int DASH_DURATION = 12;
    private static final int DASH_COOLDOWN = 13;
    private static final int FREEZE_DURATION = 14;
    private static final int MAX_HEALTH = 15;

    private static final int MAX_DAMAGE = 16;
    private static final int XP_NEEDED = 17;

    private static final int MAX_INDEX = XP_NEEDED;

    private static final int[] LEVEL_TABLE =
        {BALL_SPAMITY, BALL_SPAMITY, BALL_POWER, PLAYER_SPAMITY, CHARGED_BALL_POWER, CHARGED_BALL_SPAMITY, CHARGED_BALL_POWER, CHARGED_BALL_SPAMITY,
            CHARGED_BALL_SPAMITY, CHARGED_BALL_SPAMITY, CHARGED_BALL_POWER, DASH_POWER, DASH_SPAMITY, DASH_SPAMITY, PLAYER_POWER, PLAYER_POWER};


    private static double getValue(int type, int level) {
      switch (type) {
        case BALL_SPEED:
          return 3.0 + 0.6 * level;
        case BALL_COOLDOWN:
          return 200 * Math.pow(1.07, -Math.pow(level, 1.1));
        case BALL_DAMAGE:
          return 1.0 + 0.8 * level;
        case BASE_SPEED:
          if (level == 0) {
            return 0;
          }
          return 2.0 + 0.38 * level;
        case CHARGED_BALL_CHARGE:
          return 0.01 * 6 * level;
        case CHARGED_BALL_SPEED:
          return 1.7 + 0.3 * level;
        case CHARGED_BALL_MAX_CHARGE:
          return 15 * level;
        case CHARGED_BALL_SLOWDOWN:
          return Math.min(1, 0.035 * level);
        case CHARGED_BALL_COOLDOWN:
          return 800 - 8 * level;
        case CHARGED_BALL_HITBOX_CHARGE:
          return 100 * 0.01 * 4 * level;
        case CHARGED_BALL_HITBOX_MAX_CHARGE:
          return 100 * 10 * level;
        case DASH_SPEEDUP:
          return 0.8 + 0.3 * level;
        case DASH_DURATION:
          return 50 + level * 8;
        case DASH_COOLDOWN:
          return 500 - 15 * level;
        case FREEZE_DURATION:
          if (level == 0) {
            return Short.MAX_VALUE;
          }
          return 5 * 500 / (level + 5);
        case MAX_HEALTH:
          return 4 * level;
        case MAX_DAMAGE:
          return getValue(CHARGED_BALL_MAX_CHARGE, level);
        case XP_NEEDED:
          return 1 + (int) (level / 2.5);
        default:
          throw new IllegalArgumentException("Pas de type correspondant à " + type);
      }
    }


    private static final double[][] data = new double[MAX_INDEX + 1][];

    public static double get(int type, Shooter shooter) {
      return data[type][shooter.levels[LEVEL_TABLE[type]]];
    }

    public static double get(int type, int level) {
      return data[type][level];
    }

    public static void generate(int maxLevel) {
      if (data[0] == null || data[0].length < maxLevel + 1) {
        for (int i = 0; i < MAX_INDEX + 1; i++) {
          double[] newData = new double[maxLevel + 1];
          int startPos = 0;
          if (data[i] != null) {
            System.arraycopy(data[i], 0, newData, 0, data[i].length);
            startPos = data[i].length;
          }
          data[i] = newData;
          for (int j = startPos; j < data[i].length; j++) {
            data[i][j] = getValue(i, j);
          }
        }
      }
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

  private static final int[] LEVEL_DEFAULTS = {1, 0, 0, 0, 0, 0, 1, 1};
  public static final int LEVELS_AMOUNT = LEVEL_DEFAULTS.length;
  private int[] levels = new int[LEVEL_DEFAULTS.length];

  // Cooldowns
  private int ballCooldown;
  private int chargedBallCooldown;
  private int dashCooldown;

  // Durations
  private int chargeTicks;
  private int freezeDuration;
  private int dashDuration;

  private Properties properties;

  public Shooter(Properties properties, int entityId) {
    super(entityId);
    this.properties = properties;
    for (int i = 0; i < levels.length; i++) {
      levels[i] = LEVEL_DEFAULTS[i];
    }
  }

  public void createPlayer(double x, double y, double angle) {
    super.create(x, y, Balance.get(Balance.BASE_SPEED, this), angle, properties.get(Properties.PLAYER_HITBOX_),
        (int) Balance.get(Balance.MAX_HEALTH, this), false);
    setMoving(false);
    reset();
  }

  public void createEnemy(double x, double y, double angle, int globalLevel, boolean randomize) {
    if (!randomize) {
      Arrays.fill(levels, 1 + globalLevel);
    } else {
      Arrays.fill(levels, 1);
      levels[PLAYER_POWER] = 1 + (int) (0.2 * globalLevel);
      for (int i = 0; i < globalLevel; i++) {
        levels[random.nextInt(levels.length)]++;
      }
      for (int i = 0; i < levels.length; i++) {
        levels[i] = Math.min(levels[i], properties.get(Properties.MAX_LEVEL_));
      }
    }
    super.create(x, y, Balance.get(Balance.BASE_SPEED, this), angle, properties.get(Properties.ENEMY_HITBOX_),
        (int) Balance.get(Balance.MAX_HEALTH, this), true);
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

  @Override
  void update(ByteBuffer shooter) {
    super.update(shooter);
    levels[PLAYER_SPAMITY] = shooter.get();
    levels[CHARGED_BALL_SPAMITY] = shooter.get();
    levels[DASH_POWER] = shooter.get();
    levels[PLAYER_POWER] = shooter.get();
    freezeDuration = shooter.getShort();
    dashDuration = shooter.getShort();
    chargeTicks = shooter.get() != 0 ? 0 : -1;
  }

  @Override
  void serialize(ByteBuffer shooter) {
    super.serialize(shooter);
    shooter.put((byte) levels[PLAYER_SPAMITY]);
    shooter.put((byte) levels[CHARGED_BALL_SPAMITY]);
    shooter.put((byte) levels[DASH_POWER]);
    shooter.put((byte) levels[PLAYER_POWER]);
    shooter.putShort((short) freezeDuration);
    shooter.putShort((short) dashDuration);
    shooter.put(isCharging() ? (byte) 1 : (byte) 0);
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
    ballCooldown = (int) Balance.get(Balance.BALL_COOLDOWN, this);
    if (isFrozen()) {
      // Faire perdre le cooldown même si on lance rien, si on est freeze
      return null;
    }
    return new Triplet<>(Balance.get(Balance.BALL_SPEED, this), (int) Balance.get(Balance.BALL_DAMAGE, this),
        properties.get(Properties.BALL_HITBOX_));
  }

  /**
   * @return true si le dash a été lancé, false si le cooldown est pas fini.
   */
  public boolean dash() {
    if (dashCooldown > 0) {
      return false;
    }
    dashCooldown = (int) Balance.get(Balance.DASH_COOLDOWN, this);
    dashDuration = (int) Balance.get(Balance.DASH_DURATION, this);
    recomputeSpeed();
    return true;
  }

  @Override
  public void increaseHealth(int amount) {
    int maxHealth = (int) Balance.get(Balance.MAX_HEALTH, this);
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
    freezeDuration = (int) Balance.get(Balance.FREEZE_DURATION, this);
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
    int charge = (int) (Balance.get(Balance.CHARGED_BALL_CHARGE, this) * chargeTicks);
    if (charge >= Balance.get(Balance.CHARGED_BALL_MAX_CHARGE, this)) {
      ballHealth = (int) (2 * Balance.get(Balance.CHARGED_BALL_MAX_CHARGE, this));
    } else {
      ballHealth = charge;
    }
    int ballHitbox = properties.get(Properties.BALL_HITBOX_) + (int) Math.min(Balance.get(Balance.CHARGED_BALL_HITBOX_CHARGE, this) * chargeTicks,
        Balance.get(Balance.CHARGED_BALL_HITBOX_MAX_CHARGE, this));
    chargeTicks = -1;
    chargedBallCooldown = (int) Balance.get(Balance.CHARGED_BALL_COOLDOWN, this);
    recomputeSpeed();
    if (ballHealth == 0) {
      return null;
    }
    return new Triplet<>(Balance.get(Balance.CHARGED_BALL_SPEED, this), ballHealth, ballHitbox);
  }

  private void recomputeSpeed() {
    if (isDestroyed()) {
      return;
    }
    if (isFrozen()) {
      setSpeed(0);
      return;
    }
    double newSpeed = Balance.get(Balance.BASE_SPEED, this);
    if (isDashing()) {
      newSpeed *= Balance.get(Balance.DASH_SPEEDUP, this);
    }
    if (isCharging()) {
      newSpeed *= Balance.get(Balance.CHARGED_BALL_SLOWDOWN, this);
    }
    setSpeed(newSpeed);
  }

  public int[] getLevels() {
    return Arrays.copyOf(levels, levels.length);
  }

  /**
   * @return l'xp restante, ou -1 si le niveau n'a pas été monté
   */
  public int increaseLevel(int i, int xp) {
    if (levels[i] == properties.get(Properties.MAX_LEVEL_) || xp < (int) Balance.get(Balance.XP_NEEDED, levels[i] + 1)) {
      return -1;
    }
    levels[i]++;
    recomputeSpeed();
    return xp - (int) Balance.get(Balance.XP_NEEDED, levels[i]);
  }

  public float getDashCooldownPercent() {
    return (float) (1.0 - dashCooldown / Balance.get(Balance.DASH_COOLDOWN, this));
  }

  public float getBallCooldownPercent() {
    return (float) (1.0 - ballCooldown / Balance.get(Balance.BALL_COOLDOWN, this));
  }

  public float getChargedBallCooldownPercent() {
    return (float) (1.0 - chargedBallCooldown / Balance.get(Balance.CHARGED_BALL_COOLDOWN, this));
  }

  public float getChargedBallChargePercent() {
    if (chargeTicks < 0) {
      return 0.0f;
    }
    return (float) Math
        .min(Balance.get(Balance.CHARGED_BALL_HITBOX_CHARGE, this) * chargeTicks / Balance.get(Balance.CHARGED_BALL_HITBOX_MAX_CHARGE, this), 1.0);
  }

  public float getHealthPercent() {
    return (float) (getHealth() / Balance.get(Balance.MAX_HEALTH, this));
  }

  public float getFreezePercent() {
    return (float) (1.0 - freezeDuration / Balance.get(Balance.FREEZE_DURATION, this));
  }

  public static int getMaxDamage(Properties properties) {
    return (int) Balance.get(Balance.MAX_DAMAGE, properties.get(Properties.MAX_LEVEL_));
  }

  public static void initialize(Properties properties) {
    Balance.generate(properties.get(Properties.MAX_LEVEL_));
  }
}
