package fr.delthas.lightmagique.shared;

public class Properties {

  public static final int PLAYER_COUNT = 2;
  public static final int ENEMIES_MAX = 1000;
  public static final int ENTITIES_MAX = 2000;
  public static final int ENTITIES_PERSONAL_SPACE_SIZE = 50;
  public static final int TICK_TIME = 10;

  public static final int STATE_SEND_INTERVAL = 20;

  public static final int SERVER_PORT = 22103;

  public static final int PLAYER_HEALTH = 20;
  public static final double PLAYER_SPEED = 2;
  public static final double BALL_SPEED = 4;

  public static final int FREEZE_TIME = 2500;

  public static final int ENTITY_MESSAGE_LENGTH = 42;
  public static final int SHOOTER_MESSAGE_LENGTH = 36;

  public static int getBallCooldown(int level) {
    if (level < 25) {
      return 500 - level;
    }
    return 250 - (int) Math.log1p(-25 + (level > 75 ? 75 : level));
  }

  public static int getDashCooldown(int level) {
    // mdr
    return 2 * getBallCooldown(level);
  }

  public static final int DASH_TIME = 80;
  public static final double DASH_MULTIPLY = 2.0;
}
