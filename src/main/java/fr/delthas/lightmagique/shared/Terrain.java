package fr.delthas.lightmagique.shared;


/**
 *
 */
public enum Terrain {

  BLOCK(false, false, false), PASS(true, true, true), PASS_NO_SPAWN(true, true, false), BALL(false, true, false);

  Terrain(boolean playerThrough, boolean ballThrough, boolean canSpawn) {
    this.playerThrough = playerThrough;
    this.ballThrough = ballThrough;
    this.canSpawn = canSpawn;
  }

  public final boolean playerThrough;
  public final boolean ballThrough;
  public final boolean canSpawn;

}
