package fr.delthas.lightmagique.client;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class InputManager implements KeyListener, MouseListener {

  private Map<Integer, Boolean> keysState = new HashMap<>();
  private boolean[] mouseDown = new boolean[3];

  private Set<Integer> newKeys = new HashSet<>(2);

  public Set<Integer> flush() {
    if (newKeys.isEmpty()) {
      return Collections.emptySet();
    }
    Set<Integer> lastKeys = Collections.unmodifiableSet(newKeys);
    newKeys = new HashSet<>(2);
    return lastKeys;
  }

  @Override
  public void keyPressed(KeyEvent e) {
    newKeys.add(e.getKeyCode());
    keysState.put(e.getKeyCode(), true);
  }

  @Override
  public void keyReleased(KeyEvent e) {
    keysState.put(e.getKeyCode(), false);
  }

  public boolean isKeyDown(int keyCode) {
    return keysState.getOrDefault(keyCode, false);
  }

  @Override
  public void keyTyped(KeyEvent e) {}

  @Override
  public void mouseClicked(MouseEvent e) {}

  // TODO fix pour les oneshot genre dash et spell, flag d'utilisation
  // pas besoin dans l'immédiat grâce aux cooldowns

  @Override
  public void mousePressed(MouseEvent e) {
    if (e.getButton() > 3) {
      return;
    }
    mouseDown[e.getButton() - 1] = true;
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    if (e.getButton() > 3) {
      return;
    }
    mouseDown[e.getButton() - 1] = false;
  }

  public boolean isMouseDown(int button) {
    if (button >= 3) {
      return false;
    }
    return mouseDown[button];
  }

  @Override
  public void mouseEntered(MouseEvent e) {}

  @Override
  public void mouseExited(MouseEvent e) {}
}
