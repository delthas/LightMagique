package fr.delthas.lightmagique.shared;

import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

public class Map {

  private Terrain[] map;
  private int width;
  private int height;

  public Map() {
    BufferedImage image;
    try {
      image = ImageIO.read(Map.class.getResource("/map.png"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    width = image.getWidth();
    height = image.getHeight();
    map = new Terrain[image.getWidth() * image.getHeight()];
    for (int j = 0; j < image.getHeight(); j++) {
      for (int i = 0; i < image.getWidth(); i++) {
        int c = image.getRGB(i, j);
        map[j * width + i] = Terrain.BLACK;
        for (int k = 0; k < Terrain.values().length; k++) {
          if (c == Terrain.values()[k].color.getRGB()) {
            map[j * width + i] = Terrain.values()[k];
            break;
          }
        }
      }
    }

  }

  public Terrain getTerrain(int x, int y) {
    if (x < 0 || y < 0 || x >= width || y >= height) {
      return Terrain.BLACK;
    }
    return map[x * width + y];
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

}
