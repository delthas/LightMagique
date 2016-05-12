package fr.delthas.lightmagique.shared;


/**
 *
 */
public enum Terrain {

  BLOCK(false, false), PASS(true, true), BALL(false, true);

  Terrain(boolean playerThrough, boolean ballThrough) {
    this.playerThrough = playerThrough;
    this.ballThrough = ballThrough;
  }

  public final boolean playerThrough;
  public final boolean ballThrough;

}
