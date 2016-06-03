package fr.delthas.lightmagique.shared;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Properties {

  public static final int DEFAULT_PORT = 22103; // has to be 1<=port<=65535
  public static final int TICK_TIME = 10;
  public static final int ENTITY_MESSAGE_LENGTH = 31;
  public static final int SHOOTER_MESSAGE_LENGTH = 9;

  public static int getNeededXpFor(int level) {
    return level;
  }

  private static final String[] PROPERTIES_NAMES = {"playerMax", "enemiesMax", "entitiesMax", "entitiesPersonalSize", "sendStateInterval", "maxLevel",
      "xpPerHit", "playerHitbox", "enemyHitbox", "ballHitbox"};
  private static final int[] PROPERTIES_DEFAULTS = {1, 300, 600, 50, 8, 20, 3, 50 * 100, 50 * 100, 5 * 100};

  public static final int PROPERTIES_MESSAGE_LENGTH = 4 * PROPERTIES_DEFAULTS.length;

  // variables ending with a _ are indexes for properties

  public static final int PLAYER_MAX_ = 0;
  public static final int ENEMIES_MAX_ = 1;
  public static final int ENTITIES_MAX_ = 2;
  public static final int ENTITIES_PERSONAL_SPACE_SIZE_ = 3;
  public static final int STATE_SEND_INTERVAL_ = 4;
  public static final int MAX_LEVEL_ = 5;
  public static final int XP_PER_HIT_ = 6;
  public static final int PLAYER_HITBOX_ = 7;
  public static final int ENEMY_HITBOX_ = 8;
  public static final int BALL_HITBOX_ = 9;

  private int[] properties = new int[PROPERTIES_NAMES.length];
  {
    for (int i = 0; i < PROPERTIES_NAMES.length; i++) {
      properties[i] = PROPERTIES_DEFAULTS[i];
    }
  }

  public Properties() {
    // default properties are already loaded
  }

  public Properties(Path propertiesFile) throws IOException {
    java.util.Properties properties = new java.util.Properties();
    try (Reader r = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
      properties.load(r);
    }
    for (int i = 0; i < PROPERTIES_NAMES.length; i++) {
      String propertyName = PROPERTIES_NAMES[i];
      String propertyValue = properties.getProperty(propertyName);
      if (propertyValue != null) {
        try {
          this.properties[i] = Integer.parseUnsignedInt(propertyValue);
        } catch (NumberFormatException e) {
          System.out.println("Ignored unknown value for property: " + propertyName + " with value " + propertyValue);
        }
      }
    }
  }

  public int get(int propertyIndex) {
    return properties[propertyIndex];
  }

  public Properties(ByteBuffer buffer) {
    for (int i = 0; i < properties.length; i++) {
      properties[i] = buffer.getInt();
    }
  }

  /**
   * note: flips the buffer
   */
  public void serialize(ByteBuffer buffer) {
    for (int propertie : properties) {
      buffer.putInt(propertie);
    }
    buffer.flip();
  }

  public static void writeDefaultProperties(Path propertiesFile) throws IOException {
    java.util.Properties defaultProperties = new java.util.Properties();
    for (int i = 0; i < PROPERTIES_NAMES.length; i++) {
      String propertyName = PROPERTIES_NAMES[i];
      int propertyValue = PROPERTIES_DEFAULTS[i];
      defaultProperties.put(propertyName, Integer.toUnsignedString(propertyValue));
    }
    try (Writer w = Files.newBufferedWriter(propertiesFile, StandardCharsets.UTF_8)) {
      defaultProperties.store(w, "Properties file for the light-magique server.");
    }
  }

}
