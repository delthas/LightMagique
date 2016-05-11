package fr.delthas.lightmagique.client;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashMap;

public class InputManager implements KeyListener, MouseListener {

  private HashMap<Integer, Boolean> keysState = new HashMap<>();
  private boolean[] mouseDown = new boolean[3];

  @Override
  public void keyPressed(KeyEvent e) {
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
