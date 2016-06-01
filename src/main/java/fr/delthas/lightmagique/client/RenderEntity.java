package fr.delthas.lightmagique.client;

import fr.delthas.lightmagique.client.Window.Model;

class RenderEntity {

  public final Model model;

  public final float x, y;
  public final float angle, scale;


  public RenderEntity(Model model, float x, float y, float angle, float scale) {
    this.model = model;
    this.x = x;
    this.y = y;
    this.angle = angle;
    this.scale = scale;
  }



}
