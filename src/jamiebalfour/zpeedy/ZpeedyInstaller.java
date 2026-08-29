package jamiebalfour.zpeedy;

import jamiebalfour.helpers.HelperFunctions;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Installs the packaged Zpeedy JAR and its command-line launcher. */
final class ZpeedyInstaller {

  private static final String DEFAULT_MEMORY = "2048M";

  private ZpeedyInstaller() {
  }

  static void install(String memory) throws IOException {
    // Install the JAR which is currently running
    Path sourceJar = runningJar();
    Path installationDirectory = HelperFunctions.getAppDataDirectory(
        "jamiebalfour/zpeedy", System.getProperty("user.home") + "/jb/zpeedy").toPath();
    Files.createDirectories(installationDirectory);

    Path installedJar = installationDirectory.resolve("zpeedy.jar").toAbsolutePath().normalize();
    if (!sourceJar.equals(installedJar)) {
      Files.copy(sourceJar, installedJar, StandardCopyOption.REPLACE_EXISTING);
    }

    Path launcher = installLauncher(installedJar, normaliseMemory(memory));
    System.out.println("Installed Zpeedy JAR: " + installedJar);
    System.out.println("Installed Zpeedy command: " + launcher);
    System.out.println("Open a new terminal and run: zpeedy");
  }

  private static Path runningJar() throws IOException {
    try {
      Path location = Paths.get(Zpeedy.class.getProtectionDomain().getCodeSource().getLocation().toURI())
          .toAbsolutePath().normalize();
      if (!Files.isRegularFile(location) || !location.getFileName().toString().endsWith(".jar")) {
        // The installer only works from the packaged JAR and not the IDE
        throw new IOException("Zpeedy installation must be run from the packaged zpeedy.jar file.");
      }
      return location;
    } catch (URISyntaxException exception) {
      throw new IOException("Could not locate the running Zpeedy JAR.", exception);
    }
  }

  private static String normaliseMemory(String memory) {
    if (memory == null || memory.isBlank()) return DEFAULT_MEMORY;

    String value = memory.trim().toUpperCase(Locale.ROOT);

    // A number without a suffix is treated as megabytes
    if (value.matches("[1-9]\\d*")) return value + "M";
    if (value.matches("[1-9]\\d*[MG]")) return value;
    return DEFAULT_MEMORY;
  }

  private static Path installLauncher(Path jar, String memory) throws IOException {
    boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    String pathEnvironment = System.getenv("PATH");
    if (pathEnvironment == null || pathEnvironment.isBlank()) {
      throw new IOException("PATH is empty; cannot install the zpeedy launcher.");
    }

    Path targetDirectory = firstWritablePathDirectory(pathEnvironment, windows);
    if (targetDirectory == null) {
      throw new IOException("No writable directory was found on PATH. Add a user-writable bin directory and try again.");
    }

    Path launcher = targetDirectory.resolve(windows ? "zpeedy.cmd" : "zpeedy");

    // Install the correct launcher for the operating system
    if (windows) {
      atomicWrite(launcher, windowsLauncher(jar, memory));
    } else {
      atomicWrite(launcher, unixLauncher(jar, memory));
      try {
        Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwxr-xr-x");
        Files.setPosixFilePermissions(launcher, permissions);
      } catch (UnsupportedOperationException exception) {
        // Some filesystems do not support POSIX permissions
        if (!launcher.toFile().setExecutable(true, false)) {
          throw new IOException("Could not make the zpeedy launcher executable: " + launcher);
        }
      }
    }
    return launcher;
  }

  private static Path firstWritablePathDirectory(String pathEnvironment, boolean windows) {
    String separator = windows ? ";" : ":";
    List<Path> paths = new ArrayList<>();
    for (String entry : pathEnvironment.split(java.util.regex.Pattern.quote(separator))) {
      String value = entry.trim();
      if (windows && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
        value = value.substring(1, value.length() - 1);
      }
      if (value.isEmpty()) continue;
      try {
        paths.add(Paths.get(value));
      } catch (InvalidPathException ignored) {
        // Ignore this entry and try the rest of PATH
      }
    }
    for (Path path : paths) {
      if (Files.isDirectory(path) && Files.isWritable(path)) return path;
    }
    return null;
  }

  private static String unixLauncher(Path jar, String memory) {
    // Memory can be changed for one run without reinstalling Zpeedy
    return "#!/bin/sh\n"
        + "MEMORY=\"" + escapeForShell(memory) + "\"\n"
        + "if [ \"$1\" = \"--zpeedy-memory\" ]; then\n"
        + "  MEMORY=\"$2\"\n"
        + "  shift 2\n"
        + "elif [ \"${1#--zpeedy-memory=}\" != \"$1\" ]; then\n"
        + "  MEMORY=\"${1#--zpeedy-memory=}\"\n"
        + "  shift\n"
        + "fi\n"
        + "exec java -Xmx\"$MEMORY\" -XX:TieredStopAtLevel=1 -jar \""
        + escapeForShell(jar.toString()) + "\" \"$@\"\n";
  }

  private static String windowsLauncher(Path jar, String memory) {
    return "@echo off\r\n"
        + "set MEMORY=" + memory + "\r\n"
        + "if /I \"%~1\"==\"--zpeedy-memory\" (\r\n"
        + "  set MEMORY=%~2\r\n"
        + "  shift\r\n"
        + "  shift\r\n"
        + ")\r\n"
        + "java -Xmx%MEMORY% -XX:TieredStopAtLevel=1 -jar \""
        + jar.toString().replace("\"", "\"\"") + "\" %*\r\n";
  }

  private static String escapeForShell(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("$", "\\$").replace("`", "\\`");
  }

  private static void atomicWrite(Path target, String content) throws IOException {
    // Write a temporary launcher before replacing the old one
    Path temporary = target.getParent().resolve(target.getFileName() + ".tmp_" + UUID.randomUUID());
    Files.write(temporary, content.getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    try {
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      // Fall back to a normal move when atomic moves are not supported
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
