package fr.delthas.lightmagique.shared;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class MultipleList<T> implements Iterable<T> {

  public MultipleList(List<T> list1, List<T> list2) {
    lists = new ArrayList<>(2);
    lists.add(list1);
    lists.add(list2);
  }

  private final List<List<T>> lists;

  @Override
  public Iterator<T> iterator() {

    return new Iterator<T>() {

      Iterator<List<T>> it = lists.iterator();
      Iterator<T> current = it.next().iterator();

      @Override
      public boolean hasNext() {
        if (current.hasNext()) {
          return true;
        }
        while (it.hasNext()) {
          current = it.next().iterator();
          if (current.hasNext()) {
            return true;
          }
        }
        return false;
      }

      @Override
      public T next() {
        if (current.hasNext()) {
          return current.next();
        }
        while (it.hasNext()) {
          current = it.next().iterator();
          if (current.hasNext()) {
            return current.next();
          }
        }
        throw new NoSuchElementException();
      }
    };

  }
}
