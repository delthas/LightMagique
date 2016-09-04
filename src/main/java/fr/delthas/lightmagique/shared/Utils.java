package fr.delthas.lightmagique.shared;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public class Utils {

  private static Path mainPath;

  static {
    try {
      mainPath = Paths.get(Utils.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    } catch (URISyntaxException e) {
      // should never happen
      throw new InternalError("Couldn't get an URI for the code location");
    }
    if (Files.isRegularFile(mainPath)) { // we're in a jar
      try {
        FileSystems.newFileSystem(new URI("jar:file:" + mainPath.toUri().getPath().toString() + "!/"), Collections.emptyMap());
      } catch (IOException | URISyntaxException e) {
        throw new InternalError("Couldn't install jar filesystem");
      }
      mainPath = mainPath.getParent();
    }
  }

  private Utils() {
    throw new IllegalAccessError("This class cannot be instantiated.");
  }

  public static Path getFile(String path) {
    URL url = Utils.class.getResource("/" + path);
    if (url == null)
      return mainPath.resolve(path);
    try {
      return Paths.get(Utils.class.getResource("/" + path).toURI());
    } catch (URISyntaxException e) {
      // should never happen
      throw new InternalError(e);
    }
  }

  public static int modulo(int a, int b) {
    int m = a % b;
    if (m >= 0) {
      return m;
    }
    return b + m;
  }

}
