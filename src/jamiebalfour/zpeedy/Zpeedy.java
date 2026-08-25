package jamiebalfour.zpeedy;

import jamiebalfour.zpe.core.IAST;
import jamiebalfour.zpe.core.ZPEKit;
import jamiebalfour.zpe.core.ZPERuntimeEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
/*
  Zpeedy Script is copyright J Balfour 2026.
 */
/** Command-line entry point for Zpeedy Script. */
public final class Zpeedy {

  public static final String VERSION = "0.1.0";

  private Zpeedy() {
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      printIntroduction();
      printUsage();
      return;
    }

    if ("--version".equals(args[0]) || "-v".equals(args[0])) {
      System.out.println("Zpeedy Script " + VERSION);
      return;
    }

    if ("--install".equals(args[0])) {
      String memory = null;
      if (args.length > 1) {
        if (args.length != 3 || !"-memory".equals(args[1])) {
          System.err.println("Usage: zpeedy --install [-memory <megabytes>]");
          System.exit(1);
        }
        memory = args[2];
      }
      try {
        ZpeedyInstaller.install(memory);
      } catch (Exception exception) {
        System.err.println("Zpeedy installation failed: " + exception.getMessage());
        System.exit(3);
      }
      return;
    }

    if (!"-r".equals(args[0])) {
      System.err.println("Unknown option: " + args[0]);
      printUsage();
      System.exit(1);
    }

    if (args.length < 2) {
      System.err.println("The -r option requires a Zpeedy source file.");
      printUsage();
      System.exit(1);
    }

    Path sourceFile = Paths.get(args[1]);
    if (!Files.isRegularFile(sourceFile)) {
      System.err.println("File " + sourceFile + " not found.");
      System.exit(1);
    }

    try {
      String source = new String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8);
      ZpeedyCompiler compiler = new ZpeedyCompiler();

      long compileStart = System.nanoTime();
      IAST program = compiler.compile(source);
      long compileEnd = System.nanoTime();
      System.out.printf("%nCompile finished in %.3fms%n%n", (compileEnd - compileStart) / 1_000_000.0);

      ZPERuntimeEnvironment runtime = new ZPERuntimeEnvironment(5);
      long executionStart = System.nanoTime();
      ZPEKit.runCode(runtime, program, new HashMap<>());
      long executionEnd = System.nanoTime();

      System.out.printf("%nExecution completed in %.3fms%n", (executionEnd - executionStart) / 1_000_000.0);
    } catch (ZpeedyCompiler.ZpeedyCompileException exception) {
      System.err.println(exception.getMessage());
      System.exit(2);
    } catch (Exception exception) {
      System.err.println("Zpeedy execution failed: " + exception.getMessage());
      System.exit(3);
    }
  }

  private static void printIntroduction() {
    System.out.println("Zpeedy Script " + VERSION + " — powered by ZPE");
  }

  private static void printUsage() {
    System.out.println("Usage: zpeedy -r <source-file>");
    System.out.println("       zpeedy --install [-memory <megabytes>]");
  }
}
