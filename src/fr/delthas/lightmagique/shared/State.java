package fr.delthas.lightmagique.shared;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class State {

  private Map map = new Map();
  private Shooter[] players = new Shooter[Properties.PLAYER_COUNT];
  private Entity[] entities = new Entity[Properties.ENTITIES_MAX];
  private Shooter[] enemies = new Shooter[Properties.ENEMIES_MAX];

  private Consumer<Integer> destroyEntityListener = null;
  private Consumer<Integer> destroyEnemyListener = null;
  private Consumer<Void> playerKilledEnemyListener = null;

  public State() {
    for (int i = 0; i < players.length; i++) {
      players[i] = new Shooter();
    }
    for (int i = 0; i < enemies.length; i++) {
      enemies[i] = new Shooter();
    }
    for (int i = 0; i < entities.length; i++) {
      entities[i] = new Entity();
    }
  }

  public void logic() {
    moveEntities();
    for (int i = 0; i < Properties.ENEMIES_MAX; i++) {
      Shooter shooter = enemies[i];
      if (shooter.isDestroyed()) {
        continue;
      }
      if (shooter.isFrozen()) {
        shooter.destroy();
        if (destroyEnemyListener != null) {
          destroyEnemyListener.accept(i);
        }
      }
    }
  }

  public void update(ByteBuffer entity) {
    entities[entity.getShort()].update(entity);
  }

  public void update(ByteBuffer entity, ByteBuffer shooter, boolean player) {
    (player ? players : enemies)[entity.getShort()].update(entity, shooter);
  }

  public Map getMap() {
    return map;
  }

  public Shooter getPlayer(int id) {
    return players[id];
  }

  public Shooter getEnemy(int id) {
    return enemies[id];
  }

  public Entity getEntity(int id) {
    return entities[id];
  }

  public void serialize(int id, ByteBuffer entity) {
    entity.putShort((short) id);
    entities[id].serialize(entity);
  }

  public void serialize(int id, ByteBuffer entity, ByteBuffer shooter, boolean player) {
    entity.putShort((short) id);
    (player ? players : enemies)[id].serialize(entity, shooter);
  }

  private void moveEntities() {
    // pour l'instant je fais pas intelligemment le déplacement
    for (Shooter shooter : players) {
      if (shooter.isDestroyed()) {
        continue;
      }
      shooter.logic();
      if (shooter.isMoving()) {
        double nx = shooter.getX() + shooter.getSpeed() * Math.cos(shooter.getAngle());
        double ny = shooter.getY() + shooter.getSpeed() * Math.sin(shooter.getAngle());
        Terrain terrain = map.getTerrain((int) nx, (int) ny);
        if (terrain.playerThrough) {
          shooter.setX(nx);
          shooter.setY(ny);
        }
      }
    }
    for (Shooter shooter : enemies) {
      if (shooter.isDestroyed()) {
        continue;
      }
      shooter.logic();
      if (shooter.isMoving()) {
        double nx = shooter.getX() + shooter.getSpeed() * Math.cos(shooter.getAngle());
        double ny = shooter.getY() + shooter.getSpeed() * Math.sin(shooter.getAngle());
        Terrain terrain = map.getTerrain((int) nx, (int) ny);
        if (terrain.playerThrough) {
          shooter.setX(nx);
          shooter.setY(ny);
        }
      }
    }
    for (int i = 0; i < Properties.ENTITIES_MAX; i++) {
      Entity entity = entities[i];
      if (entity.isDestroyed()) {
        continue;
      }
      if (entity.isMoving()) {
        double nx = entity.getX() + entity.getSpeed() * Math.cos(entity.getAngle());
        double ny = entity.getY() + entity.getSpeed() * Math.sin(entity.getAngle());
        Terrain terrain = map.getTerrain((int) nx, (int) ny);
        if (terrain.ballThrough) {
          entity.setX(nx);
          entity.setY(ny);
        } else {
          entity.destroy();
          if (destroyEntityListener != null) {
            destroyEntityListener.accept(i);
          }
          continue;
        }
      }
      for (Shooter shooter : entity.isEnemy() ? players : enemies) {
        if (shooter.isDestroyed()) {
          continue;
        }
        if (connects(entity.getX(), entity.getY(), shooter.getX(), shooter.getY(), 10)) {
          int shooterHealth = shooter.getHealth();
          int entityHealth = entity.getHealth();
          int decreaseHealth;
          if (!shooter.isFrozen() && shooterHealth <= entityHealth) {
            decreaseHealth = shooterHealth;
            if (!entity.isEnemy()) {
              for (Shooter player : players) {
                player.increaseHealth(1);
              }
              if (playerKilledEnemyListener != null) {
                playerKilledEnemyListener.accept(null);
              }
            }
          } else {
            decreaseHealth = entityHealth;
          }
          shooter.decreaseHealth(decreaseHealth);
          entity.decreaseHealth(decreaseHealth);
          if (entity.isDestroyed()) {
            if (destroyEntityListener != null) {
              destroyEntityListener.accept(i);
            }
            break;
          }
        }
      }
    }
  }

  public int getFreeEnemyId() {
    for (int i = 0; i < Properties.ENEMIES_MAX; i++) {
      if (enemies[i].isDestroyed()) {
        return i;
      }
    }
    throw new RuntimeException("Plus d'id disponibles. (ennemis)");
  }

  public int getFreeEntityId(boolean personal) {
    if (personal) {
      for (int i = Properties.ENTITIES_MAX - Properties.ENTITIES_PERSONAL_SPACE_SIZE; i < Properties.ENTITIES_MAX; i++) {
        if (entities[i].isDestroyed()) {
          return i;
        }
      }
    } else {
      for (int i = 0; i < Properties.ENTITIES_MAX - Properties.ENTITIES_PERSONAL_SPACE_SIZE; i++) {
        if (entities[i].isDestroyed()) {
          return i;
        }
      }
    }
    throw new RuntimeException("Plus d'id disponibles. (entités) (Privés ? :" + personal + ")");
  }

  public void swapEntities(int id1, int id2) {
    Entity temp = entities[id1];
    entities[id1] = entities[id2];
    entities[id2] = temp;
  }

  private static boolean connects(double x1, double y1, double x2, double y2, double radius) {
    return (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) <= radius * radius;
  }

  public static double distanceSq(Entity e1, Entity e2) {
    double deltaX = e2.getX() - e1.getX();
    double deltaY = e2.getY() - e1.getY();
    return deltaX * deltaX + deltaY * deltaY;
  }

  public void setDestroyEnemyListener(Consumer<Integer> destroyEnemyListener) {
    this.destroyEnemyListener = destroyEnemyListener;
  }

  public void setDestroyEntityListener(Consumer<Integer> destroyEntityListener) {
    this.destroyEntityListener = destroyEntityListener;
  }

  public void setPlayerKilledEnemyListener(Consumer<Void> playerKilledEnemyListener) {
    this.playerKilledEnemyListener = playerKilledEnemyListener;
  }
}
