package fr.delthas.lightmagique.shared;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

public class Map {

  private BufferedImage mapImage;
  private Terrain[] map;
  private int width;
  private int height;

  public Map() {
    try {
      mapImage = ImageIO.read(Map.class.getResource("/map.png"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    width = mapImage.getWidth();
    height = mapImage.getHeight();
    map = new Terrain[width * height];
    for (int j = 0; j < height; j++) {
      for (int i = 0; i < width; i++) {
        Color color = new Color(mapImage.getRGB(i, j), false);
        Terrain type;
        if (color.equals(Color.BLACK)) {
          type = Terrain.BLOCK;
        } else if (color.equals(Color.RED)) {
          type = Terrain.BALL;
        } else {
          if (new Color(mapImage.getRGB(i, j), true).getAlpha() == 0) {
            type = Terrain.PASS_NO_SPAWN;
          } else {
            type = Terrain.PASS;
          }
        }
        mapImage.setRGB(i, j, color.getRGB());
        map[(height - 1 - j) * width + i] = type;
      }
    }

  }

  public Terrain getTerrain(int x, int y) {
    if (x < 0 || y < 0 || x >= width || y >= height) {
      return Terrain.BLOCK;
    }
    return map[y * width + x];
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public BufferedImage getMapImage() {
    return mapImage;
  }

}