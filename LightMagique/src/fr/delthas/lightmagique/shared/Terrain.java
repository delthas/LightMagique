package fr.delthas.lightmagique.shared;

import java.awt.Color;

/**
 *
 */
public enum Terrain {

  BLACK(Color.BLACK, false, false), WHITE(Color.WHITE, true, true), BALL(new Color(50, 50, 50), false, true);

  Terrain(Color color, boolean playerThrough, boolean ballThrough) {
    this.color = color;
    this.playerThrough = playerThrough;
    this.ballThrough = ballThrough;
  }

  public final Color color;
  public final boolean playerThrough;
  public final boolean ballThrough;

}
