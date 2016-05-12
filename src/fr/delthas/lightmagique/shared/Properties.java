package fr.delthas.lightmagique.shared;

public class Properties {

  public static final int PLAYER_COUNT = 2;
  public static final int ENEMIES_MAX = 300;
  public static final int ENTITIES_MAX = 600;
  public static final int ENTITIES_PERSONAL_SPACE_SIZE = 50;

  public static final int SERVER_PORT = 22103;
  public static final int TICK_TIME = 10;
  public static final int STATE_SEND_INTERVAL = 10;

  public static final int ENTITY_MESSAGE_LENGTH = 29;
  public static final int SHOOTER_MESSAGE_LENGTH = 9;

  public static final int MAX_LEVEL = 20;

  public static final int XP_PER_HIT = 6;

  public static double getBallSpeed(int level) {
    return 3.0 + 0.5 * level;
  }

  public static int getBallCooldown(int level) {
    return (int) (300 / (float) level);
  }

  public static int getBallDamage(int level) {
    return level;
  }

  public static double getBaseSpeed(int level) {
    if (level == 0) {
      return 0;
    }
    return 2.0 + 0.4 * level;
  }

  public static double getChargedBallCharge(int level) {
    return 0.01 * 2.0 * level / 3.0;
  }

  public static double getChargedBallMaxCharge(int level) {
    return 4.0 * level;
  }

  public static double getChargedBallSlowdown(int level) {
    return Math.min(1, 0.1 * level);
  }

  public static int getChargedBallCooldown(int level) {
    return 600 - 30 * level;
  }

  public static double getDashSpeedup(int level) {
    return 1.0 + level;
  }

  public static int getDashDuration(int level) {
    return 50 + level * 5;
  }

  public static int getDashCooldown(int level) {
    return 500 - 10 * level;
  }

  public static int getFreezeDuration(int level) {
    if (level == 0) {
      return Short.MAX_VALUE;
    }
    return 500 - 5 * level;
  }

  public static int getMaxHealth(int level) {
    return level;
  }

  public static int getNeededXp(int level) {
    return level + 1;
  }

}
