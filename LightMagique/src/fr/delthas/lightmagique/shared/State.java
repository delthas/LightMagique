package fr.delthas.lightmagique.shared;

import java.nio.ByteBuffer;

public class State {

  private Map map = new Map();
  private Shooter[] players = new Shooter[Properties.PLAYER_COUNT];
  private Entity[] entities = new Entity[Properties.ENTITIES_MAX];
  private Shooter[] enemies = new Shooter[Properties.ENEMIES_MAX];

  public State() {
    for (int i = 0; i < players.length; i++) {
      players[i] = new Shooter();
      players[i].setDestroyed(false);
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
    for (Shooter shooter : enemies) {
      if (shooter.isDestroyed()) {
        continue;
      }
      if (shooter.isFreeze()) {
        shooter.destroy();
      }
    }
  }

  public void update(ByteBuffer entity) {
    entities[entity.getInt()].update(entity);
  }

  public void update(ByteBuffer entity, ByteBuffer shooter, boolean player) {
    (player ? players : enemies)[entity.getInt()].update(entity, shooter);
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
    entity.putInt(id);
    entities[id].serialize(entity);
  }

  public void serialize(int id, ByteBuffer entity, ByteBuffer shooter, boolean player) {
    entity.putInt(id);
    (player ? players : enemies)[id].serialize(entity, shooter);
  }

  private void moveEntities() {
    // pour l'instant je fais pas intelligemment le déplacement
    for (Shooter shooter : players) {
      if (shooter.isDestroyed()) {
        continue;
      }
      shooter.cooldown();
      double nx = shooter.getX() + shooter.getSpeed() * Math.cos(shooter.getAngle());
      double ny = shooter.getY() + shooter.getSpeed() * Math.sin(shooter.getAngle());
      Terrain terrain = map.getTerrain((int) nx, (int) ny);
      if (terrain.playerThrough) {
        shooter.setX(nx);
        shooter.setY(ny);
      }
    }
    for (Shooter shooter : enemies) {
      if (shooter.isDestroyed()) {
        continue;
      }
      shooter.cooldown();
      double nx = shooter.getX() + shooter.getSpeed() * Math.cos(shooter.getAngle());
      double ny = shooter.getY() + shooter.getSpeed() * Math.sin(shooter.getAngle());
      Terrain terrain = map.getTerrain((int) nx, (int) ny);
      if (terrain.playerThrough) {
        shooter.setX(nx);
        shooter.setY(ny);
      }
    }
    for (Entity entity : entities) {
      if (entity.isDestroyed()) {
        continue;
      }
      double nx = entity.getX() + entity.getSpeed() * Math.cos(entity.getAngle());
      double ny = entity.getY() + entity.getSpeed() * Math.sin(entity.getAngle());
      Terrain terrain = map.getTerrain((int) nx, (int) ny);
      if (terrain.ballThrough) {
        entity.setX(nx);
        entity.setY(ny);
      } else {
        entity.destroy();
        continue;
      }
      for (Shooter shooter : entity.isEnemy() ? players : enemies) {
        if (connects(entity.getX(), entity.getY(), shooter.getX(), shooter.getY(), 10)) {
          entity.destroy();
          shooter.decreaseHealth(3);
          if (!entity.isEnemy()) {
            for (Shooter player : players) {
              player.increaseHealth(1);
            }
          }
          break;
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
}
