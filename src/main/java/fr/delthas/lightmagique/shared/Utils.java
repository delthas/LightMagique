package fr.delthas.lightmagique.shared;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Utils {

  private Utils() {
    throw new IllegalAccessError("This class cannot be instantiated.");
  }

  public static Path getCurrentFolder() {
    Path path;
    try {
      path = Paths.get(Utils.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    } catch (URISyntaxException e) {
      // should never happen
      throw new InternalError("Couldn't get an URI for the code location");
    }
    if (Files.isRegularFile(path)) { // we're in a jar
      return path.getParent();
    }
    return path;
  }

}
