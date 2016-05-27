package fr.delthas.lightmagique.shared;

import java.nio.ByteBuffer;
import java.util.Optional;
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
    for (int i = 0; i < Properties.ENTITIES_MAX; i++) {
      Entity entity = entities[i];
      if (entity.isDestroyed()) {
        continue;
      }
      if (entity.isMoving()) {
        if (entity.getHitbox() <= Properties.BALL_HITBOX) {
          // do not attempt to move it cleverly
          Pair<Double, Double> pair = tryMoveEntity(entity.getX(), entity.getY(), entity.getSpeed(), entity.getAngle(), false);
          if (pair != null) {
            entity.setX(pair.getFirst());
            entity.setY(pair.getSecond());
          } else {
            entity.destroy();
            if (destroyEntityListener != null) {
              destroyEntityListener.accept(i);
            }
            continue;
          }
        } else {
          // // attempt to move it cleverly
          // Optional<Boolean> result = tryMoveEntity(entity, entity.isEnemy());
          // if (result.isPresent() && result.get()) {
          // // had to change its direction, punish it by removing some hitbox
          // entity.setHitbox(entity.getHitbox() - 1);
          // }
          Pair<Double, Double> pair = tryMoveEntity(entity.getX(), entity.getY(), entity.getSpeed(), entity.getAngle(), false);
          if (pair != null) {
            entity.setX(pair.getFirst());
            entity.setY(pair.getSecond());
          } else {
            entity.setHitbox(entity.getHitbox() - 1);
          }
        }
      }
      for (Shooter shooter : entity.isEnemy() ? players : enemies) {
        if (shooter.isDestroyed()) {
          continue;
        }
        if (connects(entity.getX(), entity.getY(), shooter.getX(), shooter.getY(), (entity.getHitbox() + shooter.getHitbox()) / 100.0)) {
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

  private Pair<Double, Double> tryMoveEntity(double x, double y, double speed, double angle, boolean shooter) {
    double nx = x + speed * Math.cos(angle);
    double ny = y + speed * Math.sin(angle);
    Terrain terrain = map.getTerrain((int) nx, (int) ny);
    if (shooter && terrain.playerThrough || !shooter && terrain.ballThrough) {
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
    Pair<Double, Double> pair = tryMoveEntity(entity.getX(), entity.getY(), entity.getSpeed(), entity.getAngle(), entity instanceof Shooter);
    if (pair != null) {
      entity.setX(pair.getFirst());
      entity.setY(pair.getSecond());
      return Optional.of(Boolean.FALSE);
    } else {
      // try to make it move anyway by slightly changing its angle and speed
      for (double speed = entity.getSpeed(); speed >= 0; speed -= entity.getSpeed() / 5) {
        for (double angle = 0; angle < (1.5 - speed / entity.getSpeed()) * Math.PI / 1.2; angle += 0.1) {
          pair = tryMoveEntity(entity.getX(), entity.getY(), speed, entity.getAngle() + angle, true);
          if (pair != null) {
            entity.setX(pair.getFirst());
            entity.setY(pair.getSecond());
            return Optional.of(Boolean.TRUE);
          }
          pair = tryMoveEntity(entity.getX(), entity.getY(), speed, entity.getAngle() - angle, true);
          if (pair != null) {
            entity.setX(pair.getFirst());
            entity.setY(pair.getSecond());
            return Optional.of(Boolean.TRUE);
          }
        }
      }
    }
    return Optional.empty();
  }
}
