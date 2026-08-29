package jamiebalfour.zpeedy;

import jamiebalfour.zpe.core.IAST;
import jamiebalfour.zpe.core.ZPECompilerBytecodeBuilder;

/** Public entry point for compiling Zpeedy source. */
public final class ZpeedyCompiler {

  private final ZpeedyBytecodeAnalyser analyser;

  public ZpeedyCompiler() {
    // Use ZPE's byte code builder by default
    this(new ZPECompilerBytecodeBuilder());
  }

  // Allows tools such as ZIDE to provide their own byte code builder
  public ZpeedyCompiler(ZPECompilerBytecodeBuilder bytecodeBuilder) {
    analyser = new ZpeedyBytecodeAnalyser(bytecodeBuilder);
  }

  public IAST compile(String source) throws ZpeedyCompileException {
    try {
      return analyser.analyse(source);
    } catch (ZpeedyBytecodeAnalyser.ZpeedyCompileException exception) {
      // Convert the internal error to the public compiler error
      throw new ZpeedyCompileException(exception.getLine(), exception.getDetail());
    }
  }

  /** Compilation failure with a one-based source line. */
  public static final class ZpeedyCompileException extends Exception {
    private final int line;

    public ZpeedyCompileException(int line, String message) {
      super("Zpeedy compilation error on line " + line + ": " + message);
      this.line = line;
    }

    public int getLine() {
      return line;
    }
  }
}
