package fr.delthas.lightmagique.shared;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Transparency;
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
          type = Terrain.PASS;
        }
        map[j * width + i] = type;
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

  public void makeImageCompatibleWith(GraphicsConfiguration targetConfiguration) {
    BufferedImage newImage = targetConfiguration.createCompatibleImage(width, height, Transparency.OPAQUE);
    Graphics2D g = (Graphics2D) newImage.getGraphics();
    g.drawImage(mapImage, 0, 0, null);
    g.dispose();
    mapImage.flush();
    mapImage = newImage;
  }

  public Image getMapImage() {
    return mapImage;
  }

}
