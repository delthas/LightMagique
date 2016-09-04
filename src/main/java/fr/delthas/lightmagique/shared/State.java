package fr.delthas.lightmagique.shared;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

public class State {

  // TODO tout remplacer avec getEntityd

  public interface ConsumerIOException<T> {
    void accept(T t) throws IOException;
  }

  public interface TriConsumerIOException<T, U, V> {
    void accept(T t, U u, V v) throws IOException;
  }

  private Map map = new Map();
  private Properties properties;
  private Shooter[] players;
  private Shooter[] enemies;
  private Entity[] entities;

  private ConsumerIOException<Integer> destroyEntityListener = null;
  private ConsumerIOException<Integer> destroyEnemyListener = null;
  private ConsumerIOException<Void> playerKilledEnemyListener = null;
  private TriConsumerIOException<Boolean, Integer, Integer> hurtListener = null;

  private ByteBuffer receiveBuffer;
  private ByteBuffer sendBuffer;

  public State(ByteBuffer receiveBuffer, ByteBuffer sendBuffer) {
    this.receiveBuffer = receiveBuffer;
    this.sendBuffer = sendBuffer;
  }

  public void initialize(Properties properties) {
    this.properties = properties;
    players = new Shooter[properties.get(Properties.PLAYER_MAX_)];
    for (int i = 0; i < players.length; i++) {
      players[i] = new Shooter(properties, i);
    }
    enemies = new Shooter[properties.get(Properties.ENEMIES_MAX_)];
    for (int i = 0; i < enemies.length; i++) {
      enemies[i] = new Shooter(properties, i);
    }
    entities = new Entity[properties.get(Properties.ENTITIES_MAX_)];
    for (int i = 0; i < entities.length; i++) {
      entities[i] = new Entity(i);
    }
  }

  public void logic() throws IOException {
    moveEntities();
    for (int i = 0; i < properties.get(Properties.ENEMIES_MAX_); i++) {
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

  public void update() {
    entities[receiveBuffer.getShort()].update(receiveBuffer);
  }

  public void update(int entityId) {
    entities[entityId].update(receiveBuffer);
  }

  public void update(boolean player) {
    (player ? players : enemies)[receiveBuffer.getShort()].update(receiveBuffer);
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

  public void serialize(int id) {
    sendBuffer.putShort((short) id);
    entities[id].serialize(sendBuffer);
  }

  public void serialize(int id, boolean player) {
    sendBuffer.putShort((short) id);
    (player ? players : enemies)[id].serialize(sendBuffer);
  }

  private void moveEntities() throws IOException {
    // pour l'instant je fais pas intelligemment le déplacement
    for (Shooter shooter : players) {
      if (shooter.isDestroyed()) {
        continue;
      }
      shooter.logic();
      if (shooter.isMoving()) {
        tryMoveEntity(shooter, false);
      }
    }
    for (Shooter shooter : enemies) {
      if (shooter.isDestroyed()) {
        continue;
      }
      shooter.logic();
      if (shooter.isMoving()) {
        tryMoveEntity(shooter, false);
      }
    }
    for (int i = 0; i < properties.get(Properties.ENTITIES_MAX_); i++) {
      Entity entity = entities[i];
      if (entity.isDestroyed()) {
        continue;
      }
      if (entity.isMoving()) {
        Pair<Double, Double> pair = tryMoveEntity(entity, 0);
        if (pair != null) {
          entity.setX(pair.getFirst());
          entity.setY(pair.getSecond());
        } else {
          entity.setHitbox(entity.getHitbox() - 1);
          if (entity.getHitbox() <= properties.get(Properties.BALL_HITBOX_)) {
            entity.destroy();
            if (destroyEntityListener != null) {
              destroyEntityListener.accept(i);
            }
            continue;
          }
        }
      }
      Shooter[] shooters = entity.isEnemy() ? players : enemies;
      for (int j = 0; j < shooters.length; j++) {
        Shooter shooter = shooters[j];
        if (shooter.isDestroyed()) {
          continue;
        }
        if (connects(entity.getX(), entity.getY(), shooter.getX(), shooter.getY(), (entity.getHitbox() + shooter.getHitbox()) / 100.0)) {
          if (hurtListener != null) {
            hurtListener.accept(entity.isEnemy(), j, entity.getHealth());
          }
          int shooterHealth = shooter.getHealth();
          int entityHealth = entity.getHealth();
          int decreaseHealth;
          if (!shooter.isFrozen() && shooterHealth <= entityHealth) {
            decreaseHealth = shooterHealth;
            if (!entity.isEnemy()) {
              if (playerKilledEnemyListener != null) {
                playerKilledEnemyListener.accept(null);
              }
            }
          } else {
            decreaseHealth = entityHealth;
          }
          if (shooter.isEnemy()) {
            shooter.decreaseHealth(decreaseHealth);
          }
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
    for (int i = 0; i < properties.get(Properties.ENEMIES_MAX_); i++) {
      if (enemies[i].isDestroyed()) {
        return i;
      }
    }
    throw new RuntimeException("Plus d'id disponibles. (ennemis)");
  }

  public int getFreeEntityId(boolean personal) {
    if (personal) {
      for (int i = properties.get(Properties.ENTITIES_MAX_) - properties.get(Properties.ENTITIES_PERSONAL_SPACE_SIZE_); i < properties
          .get(Properties.ENTITIES_MAX_); i++) {
        if (entities[i].isDestroyed()) {
          return i;
        }
      }
    } else {
      for (int i = 0; i < properties.get(Properties.ENTITIES_MAX_) - properties.get(Properties.ENTITIES_PERSONAL_SPACE_SIZE_); i++) {
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
    return distanceSq(e1.getX(), e1.getY(), e2.getX(), e2.getY());
  }

  public static double distanceSq(double x1, double y1, double x2, double y2) {
    double deltaX = x1 - x2;
    double deltaY = y1 - y2;
    return deltaX * deltaX + deltaY * deltaY;
  }

  public void setDestroyEnemyListener(ConsumerIOException<Integer> destroyEnemyListener) {
    this.destroyEnemyListener = destroyEnemyListener;
  }

  public void setDestroyEntityListener(ConsumerIOException<Integer> destroyEntityListener) {
    this.destroyEntityListener = destroyEntityListener;
  }

  public void setHurtListener(TriConsumerIOException<Boolean, Integer, Integer> hurtListener) {
    this.hurtListener = hurtListener;
  }

  public void setPlayerKilledEnemyListener(ConsumerIOException<Void> playerKilledEnemyListener) {
    this.playerKilledEnemyListener = playerKilledEnemyListener;
  }

  private Pair<Double, Double> tryMoveEntity(Entity entity, double angleOffset) {
    double nx = entity.getX() + entity.getSpeed() * Math.cos(entity.getAngle() + angleOffset);
    double ny = entity.getY() + entity.getSpeed() * Math.sin(entity.getAngle() + angleOffset);
    Terrain terrain = map.getTerrain((int) nx, (int) ny);
    if ((entity instanceof Shooter) && terrain.playerThrough) {
      double distance = (properties.get(Properties.ENEMY_HITBOX_)
          + (entity.isEnemy() ? properties.get(Properties.ENEMY_HITBOX_) : properties.get(Properties.PLAYER_HITBOX_))) / 100;
      distance *= distance;
      for (Shooter other : enemies) {
        if (other.isDestroyed() || (entity.isEnemy() && entity.getEntityId() == other.getEntityId()))
          continue;
        double afterDistance = distanceSq(nx, ny, other.getX(), other.getY());
        if (afterDistance <= distance) {
          double beforeDistance = distanceSq(entity.getX(), entity.getY(), other.getX(), other.getY());
          if (afterDistance <= beforeDistance) {
            return null;
          }
        }
      }
      distance = (properties.get(Properties.PLAYER_HITBOX_)
          + (entity.isEnemy() ? properties.get(Properties.ENEMY_HITBOX_) : properties.get(Properties.PLAYER_HITBOX_))) / 100;
      distance *= distance;
      for (Shooter other : players) {
        if (other.isDestroyed() || (!entity.isEnemy() && entity.getEntityId() == other.getEntityId()))
          continue;
        double afterDistance = distanceSq(nx, ny, other.getX(), other.getY());
        if (afterDistance <= distance) {
          double beforeDistance = distanceSq(entity.getX(), entity.getY(), other.getX(), other.getY());
          if (afterDistance <= beforeDistance) {
            return null;
          }
        }
      }
      return new Pair<>(nx, ny);
    } else if (!(entity instanceof Shooter) && terrain.ballThrough) {
      return new Pair<>(nx, ny);
    }
    return null;
  }

  private Optional<Boolean> tryMoveEntity(Entity entity, boolean disableChecks) {
    if (disableChecks) {
      double nx = entity.getX() + entity.getSpeed() * Math.cos(entity.getAngle());
      double ny = entity.getY() + entity.getSpeed() * Math.sin(entity.getAngle());
      Terrain terrain = map.getTerrain((int) nx, (int) ny);
      entity.setX(nx);
      entity.setY(ny);
      if (terrain.ballThrough) {
        return Optional.of(Boolean.FALSE);
      } else {
        return Optional.of(Boolean.TRUE);
      }
    }
    Pair<Double, Double> pair = tryMoveEntity(entity, 0);
    if (pair != null) {
      entity.setX(pair.getFirst());
      entity.setY(pair.getSecond());
      return Optional.of(Boolean.FALSE);
    } else {
      // try to make it move anyway by slightly changing its angle and speed
      double speed = entity.getSpeed();
      for (int i = 0; i < 5; i++) {
        for (double angle = 0; angle < (1.5 - Double.min(0, speed / entity.getSpeed())) * Math.PI / 1.2; angle += 0.1) {
          pair = tryMoveEntity(entity, angle);
          if (pair != null) {
            entity.setX(pair.getFirst());
            entity.setY(pair.getSecond());
            return Optional.of(Boolean.TRUE);
          }
          pair = tryMoveEntity(entity, -angle);
          if (pair != null) {
            entity.setX(pair.getFirst());
            entity.setY(pair.getSecond());
            return Optional.of(Boolean.TRUE);
          }
        }
        speed -= entity.getSpeed() / 5;
      }
    }
    return Optional.empty();
  }
}
