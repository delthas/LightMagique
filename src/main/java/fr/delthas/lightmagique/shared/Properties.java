package fr.delthas.lightmagique.shared;

public class Properties {

  public static final int PLAYER_COUNT = 2;
  public static final int ENEMIES_MAX = 300;
  public static final int ENTITIES_MAX = 600;
  public static final int ENTITIES_PERSONAL_SPACE_SIZE = 50;

  public static final int DEFAULT_PORT = 22103; // has to be 1<=port<=65535
  public static final int TICK_TIME = 10;
  public static final int STATE_SEND_INTERVAL = 8;

  public static final int ENTITY_MESSAGE_LENGTH = 31;
  public static final int SHOOTER_MESSAGE_LENGTH = 9;

  public static final int MAX_LEVEL = 20;

  public static final int XP_PER_HIT = 3;

  public static final int PLAYER_HITBOX = 50 * 100;
  public static final int ENEMY_HITBOX = 50 * 100;
  public static final int BALL_HITBOX = 5 * 100;

  public static int getNeededXp(int level) {
    return level + 1;
  }

}
