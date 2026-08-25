package jamiebalfour.zpeedy;

import jamiebalfour.zpe.core.IAST;
import jamiebalfour.zpe.core.ZPECompilerBytecodeBuilder;
import jamiebalfour.zpe.parser.v6.ZenithParsingEngine;

import java.util.ArrayList;
import java.util.List;

/** Analyses Zenith tokens and emits executable ZPE IAST through the bytecode builder. */
final class ZpeedyBytecodeAnalyser {

  private final ZPECompilerBytecodeBuilder bytecodeBuilder;

  ZpeedyBytecodeAnalyser() {
    this(new ZPECompilerBytecodeBuilder());
  }

  ZpeedyBytecodeAnalyser(ZPECompilerBytecodeBuilder bytecodeBuilder) {
    if (bytecodeBuilder == null) throw new IllegalArgumentException("Bytecode builder cannot be null.");
    this.bytecodeBuilder = bytecodeBuilder;
  }

  /** Compiles a complete Zpeedy source string. */
  IAST analyse(String source) throws ZpeedyCompileException {
    if (source == null) throw error(1, "Source cannot be null.");
    List<SourceLine> lines = parseWithZenith(source);
    if (lines.isEmpty()) return bytecodeBuilder.program();
    if (lines.get(0).indent != 0) throw error(lines.get(0).number, "The first statement cannot be indented.");
    return new IndentationCompiler(lines).compileProgram();
  }

  private List<SourceLine> parseWithZenith(String source) throws ZpeedyCompileException {
    ZenithParsingEngine parser = new ZenithParsingEngine(source, true, new ZpeedyTokeniser());
    List<SourceLine> lines = new ArrayList<>();
    SourceLine current = null;
    while (parser.getNextSymbol() != -2) {
      String whitespace = parser.getWhitespace();
      boolean hasLineBreak = whitespace.indexOf('\n') >= 0 || whitespace.indexOf('\r') >= 0;
      boolean newLine = current == null || parser.getCurrentLine() != current.number || hasLineBreak;
      if (newLine) {
        int indent = hasLineBreak ? indentationAfterLastNewline(whitespace, parser.getCurrentLine()) : 0;
        current = new SourceLine(parser.getCurrentLine(), indent);
        lines.add(current);
      }
      current.tokens.add(new Token(parser.getCurrentSymbol(), parser.getCurrentWord(), parser.getCurrentWord(false)));
    }
    return lines;
  }

  private int indentationAfterLastNewline(String whitespace, int line) throws ZpeedyCompileException {
    int start = Math.max(whitespace.lastIndexOf('\n'), whitespace.lastIndexOf('\r')) + 1;
    int indent = 0;
    for (int i = start; i < whitespace.length(); i++) {
      char character = whitespace.charAt(i);
      if (character == '\t') throw error(line, "Tabs cannot be used for indentation; use spaces.");
      if (character == ' ') indent++;
    }
    return indent;
  }

  private IAST compileStatement(SourceLine line) throws ZpeedyCompileException {
    TokenCursor tokens = new TokenCursor(line);
    if (tokens.match(ZpeedyTokeniser.STOP)) {
      tokens.require(ZpeedyTokeniser.LOOP, "Expected 'loop' after 'stop'.");
      tokens.requireEnd();
      return bytecodeBuilder.breakStatement();
    }
    if (tokens.match(ZpeedyTokeniser.CONTINUE)) {
      throw error(line.number, "continue loop is not yet supported by the ZPE runtime.");
    }
    if (tokens.match(ZpeedyTokeniser.DISPLAY)) {
      if (tokens.atEnd()) throw error(line.number, "display requires exactly one value.");
      IAST value = compileExpression(tokens);
      tokens.requireEnd();
      return bytecodeBuilder.print(value);
    }
    if (tokens.match(ZpeedyTokeniser.SET)) {
      String name = tokens.requireIdentifier("Expected a variable name after 'set'.");
      tokens.require(ZpeedyTokeniser.TO, "Expected 'to' in the variable assignment.");
      if (tokens.atEnd()) throw error(line.number, "Expected a value after 'to'.");
      IAST value = compileExpression(tokens);
      tokens.requireEnd();
      return bytecodeBuilder.assign(name, value);
    }
    throw error(line.number, "Unsupported statement: " + line.words());
  }

  private IAST compileCondition(TokenCursor tokens) throws ZpeedyCompileException {
    IAST left = compileExpressionUntil(tokens, ZpeedyTokeniser.IS, "Expected 'is' in the condition.");
    tokens.require(ZpeedyTokeniser.IS, "Expected 'is' in the condition.");
    if (tokens.match(ZpeedyTokeniser.NOT)) return bytecodeBuilder.notEquals(left, compileExpression(tokens));
    if (tokens.match(ZpeedyTokeniser.LESS)) {
      tokens.require(ZpeedyTokeniser.THAN, "Expected 'than' after 'less'.");
      return bytecodeBuilder.lessThan(left, compileExpression(tokens));
    }
    if (tokens.match(ZpeedyTokeniser.GREATER)) {
      tokens.require(ZpeedyTokeniser.THAN, "Expected 'than' after 'greater'.");
      return bytecodeBuilder.greaterThan(left, compileExpression(tokens));
    }
    if (tokens.match(ZpeedyTokeniser.AT)) {
      if (tokens.match(ZpeedyTokeniser.MOST)) return bytecodeBuilder.lessThanOrEqual(left, compileExpression(tokens));
      if (tokens.match(ZpeedyTokeniser.LEAST)) return bytecodeBuilder.greaterThanOrEqual(left, compileExpression(tokens));
      throw error(tokens.line.number, "Expected 'most' or 'least' after 'at'.");
    }
    return bytecodeBuilder.equals(left, compileExpression(tokens));
  }

  private IAST compileExpressionUntil(TokenCursor tokens, byte terminator, String message)
      throws ZpeedyCompileException {
    int end = tokens.findAtCurrentDepth(terminator);
    if (end < 0) throw error(tokens.line.number, message);
    TokenCursor part = tokens.slice(end);
    IAST expression = compileExpression(part);
    part.requireEnd();
    tokens.position = end;
    return expression;
  }

  private IAST compileExpression(TokenCursor tokens) throws ZpeedyCompileException {
    return compileAddition(tokens);
  }

  private IAST compileAddition(TokenCursor tokens) throws ZpeedyCompileException {
    IAST value = compileMultiplication(tokens);
    while (true) {
      if (tokens.match(ZpeedyTokeniser.PLUS)) value = bytecodeBuilder.add(value, compileMultiplication(tokens));
      else if (tokens.match(ZpeedyTokeniser.MINUS)) value = bytecodeBuilder.subtract(value, compileMultiplication(tokens));
      else return value;
    }
  }

  private IAST compileMultiplication(TokenCursor tokens) throws ZpeedyCompileException {
    IAST value = compilePrimary(tokens);
    while (tokens.match(ZpeedyTokeniser.TIMES)) value = bytecodeBuilder.multiply(value, compilePrimary(tokens));
    return value;
  }

  private IAST compilePrimary(TokenCursor tokens) throws ZpeedyCompileException {
    Token token = tokens.take("Expected a value.");
    switch (token.symbol) {
      case ZpeedyTokeniser.STRING: return bytecodeBuilder.string(unescape(token.word, tokens.line.number));
      case ZpeedyTokeniser.INTEGER:
        try { return bytecodeBuilder.integer(Long.parseLong(token.word)); }
        catch (NumberFormatException exception) { throw error(tokens.line.number, "Number is outside the supported range: " + token.word); }
      case ZpeedyTokeniser.DECIMAL:
        try { return bytecodeBuilder.decimal(Double.parseDouble(token.word)); }
        catch (NumberFormatException exception) { throw error(tokens.line.number, "Number is outside the supported range: " + token.word); }
      case ZpeedyTokeniser.TRUE: return bytecodeBuilder.bool(true);
      case ZpeedyTokeniser.FALSE: return bytecodeBuilder.bool(false);
      case ZpeedyTokeniser.NULL: return bytecodeBuilder.nullValue();
      case ZpeedyTokeniser.UNDEFINED: return bytecodeBuilder.undefined();
      case ZpeedyTokeniser.IDENTIFIER: return bytecodeBuilder.variable(token.word);
      case ZpeedyTokeniser.LIST_OPEN:
        List<IAST> values = new ArrayList<>();
        if (!tokens.match(ZpeedyTokeniser.LIST_CLOSE)) {
          do { values.add(compileExpression(tokens)); }
          while (tokens.match(ZpeedyTokeniser.COMMA));
          tokens.require(ZpeedyTokeniser.LIST_CLOSE, "Expected ']' after the list values.");
        }
        return bytecodeBuilder.list(values.toArray(new IAST[0]));
      default: throw error(tokens.line.number, "Expected a value, found '" + token.rawWord + "'.");
    }
  }

  private static String unescape(String value, int line) throws ZpeedyCompileException {
    StringBuilder output = new StringBuilder();
    boolean escaped = false;
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (!escaped && character == '\\') escaped = true;
      else if (escaped) {
        switch (character) {
          case 'n': output.append('\n'); break;
          case 'r': output.append('\r'); break;
          case 't': output.append('\t'); break;
          case '"': output.append('"'); break;
          case '\\': output.append('\\'); break;
          default: throw error(line, "Unsupported escape sequence: \\" + character);
        }
        escaped = false;
      } else output.append(character);
    }
    if (escaped) throw error(line, "String cannot end with an escape character.");
    return output.toString();
  }

  private static ZpeedyCompileException error(int line, String message) {
    return new ZpeedyCompileException(line, message);
  }

  private static final class Token {
    final byte symbol;
    final String word;
    final String rawWord;
    Token(byte symbol, String word, String rawWord) {
      this.symbol = symbol;
      this.word = word;
      this.rawWord = rawWord;
    }
  }

  private static final class SourceLine {
    final int number;
    final int indent;
    final List<Token> tokens = new ArrayList<>();
    SourceLine(int number, int indent) { this.number = number; this.indent = indent; }
    String words() {
      StringBuilder value = new StringBuilder();
      for (Token token : tokens) { if (value.length() > 0) value.append(' '); value.append(token.rawWord); }
      return value.toString();
    }
  }

  private static final class TokenCursor {
    final SourceLine line;
    final int limit;
    int position;
    TokenCursor(SourceLine line) { this(line, 0, line.tokens.size()); }
    TokenCursor(SourceLine line, int position, int limit) { this.line = line; this.position = position; this.limit = limit; }
    boolean atEnd() { return position >= limit; }
    boolean check(byte symbol) { return !atEnd() && line.tokens.get(position).symbol == symbol; }
    boolean match(byte symbol) { if (!check(symbol)) return false; position++; return true; }
    Token take(String message) throws ZpeedyCompileException { if (atEnd()) throw error(line.number, message); return line.tokens.get(position++); }
    void require(byte symbol, String message) throws ZpeedyCompileException { if (!match(symbol)) throw error(line.number, message); }
    String requireIdentifier(String message) throws ZpeedyCompileException {
      if (!check(ZpeedyTokeniser.IDENTIFIER)) throw error(line.number, message);
      return line.tokens.get(position++).word;
    }
    void requireEnd() throws ZpeedyCompileException {
      if (!atEnd()) throw error(line.number, "Unexpected '" + line.tokens.get(position).rawWord + "'.");
    }
    int findAtCurrentDepth(byte symbol) {
      int depth = 0;
      for (int i = position; i < limit; i++) {
        byte current = line.tokens.get(i).symbol;
        if (current == ZpeedyTokeniser.LIST_OPEN) depth++;
        else if (current == ZpeedyTokeniser.LIST_CLOSE) depth--;
        else if (current == symbol && depth == 0) return i;
      }
      return -1;
    }
    TokenCursor slice(int end) { return new TokenCursor(line, position, end); }
  }

  private final class IndentationCompiler {
    private final List<SourceLine> lines;
    private int position;
    IndentationCompiler(List<SourceLine> lines) { this.lines = lines; }

    IAST compileProgram() throws ZpeedyCompileException {
      List<IAST> statements = compileBlock(0);
      if (position != lines.size()) throw error(lines.get(position).number, "Unexpected indentation.");
      return bytecodeBuilder.program(statements.toArray(new IAST[0]));
    }

    List<IAST> compileBlock(int expectedIndent) throws ZpeedyCompileException {
      List<IAST> statements = new ArrayList<>();
      while (position < lines.size()) {
        SourceLine line = lines.get(position);
        if (line.indent < expectedIndent) break;
        if (line.indent > expectedIndent) throw error(line.number, "Unexpected indentation.");
        if (line.tokens.get(0).symbol == ZpeedyTokeniser.OTHERWISE) break;
        byte first = line.tokens.get(0).symbol;
        if (first == ZpeedyTokeniser.IF) statements.add(compileIf(line));
        else if (first == ZpeedyTokeniser.WHEN) statements.add(compileWhen(line));
        else if (first == ZpeedyTokeniser.ATTEMPT) statements.add(compileAttempt(line));
        else if (first == ZpeedyTokeniser.LOOP) statements.add(compileLoopWhile(line));
        else if (first == ZpeedyTokeniser.REPEAT) statements.add(compileRepeat(line));
        else if (first == ZpeedyTokeniser.FOR) statements.add(compileForEvery(line));
        else { statements.add(compileStatement(line)); position++; }
      }
      return statements;
    }

    IAST compileIf(SourceLine header) throws ZpeedyCompileException {
      TokenCursor tokens = new TokenCursor(header);
      tokens.require(ZpeedyTokeniser.IF, "Expected 'if'.");
      int then = tokens.findAtCurrentDepth(ZpeedyTokeniser.THEN);
      if (then < 0) throw error(header.number, "Expected 'then' after the condition.");
      TokenCursor conditionTokens = tokens.slice(then);
      IAST condition = compileCondition(conditionTokens);
      conditionTokens.requireEnd();
      tokens.position = then + 1;
      tokens.requireEnd();
      IAST trueBranch = compileIndentedBody(header, "Expected an indented statement after 'then'.");
      List<ZPECompilerBytecodeBuilder.ElseIfBranch> alternatives = new ArrayList<>();
      IAST falseBranch = null;
      while (position < lines.size()) {
        SourceLine alternative = lines.get(position);
        if (alternative.indent != header.indent) break;
        if (alternative.tokens.get(0).symbol == ZpeedyTokeniser.ALTERNATIVELY) {
          TokenCursor alternativeTokens = new TokenCursor(alternative);
          alternativeTokens.require(ZpeedyTokeniser.ALTERNATIVELY, "Expected 'alternatively'.");
          alternativeTokens.require(ZpeedyTokeniser.IF, "Expected 'if' after 'alternatively'.");
          int alternativeThen = alternativeTokens.findAtCurrentDepth(ZpeedyTokeniser.THEN);
          if (alternativeThen < 0) throw error(alternative.number, "Expected 'then' after the condition.");
          TokenCursor alternativeConditionTokens = alternativeTokens.slice(alternativeThen);
          IAST alternativeCondition = compileCondition(alternativeConditionTokens);
          alternativeConditionTokens.requireEnd();
          alternativeTokens.position = alternativeThen + 1;
          alternativeTokens.requireEnd();
          IAST alternativeBody = compileIndentedBody(alternative,
              "Expected an indented statement after 'alternatively if'.");
          alternatives.add(bytecodeBuilder.elseIf(alternativeCondition, alternativeBody));
        } else if (alternative.tokens.get(0).symbol == ZpeedyTokeniser.OTHERWISE) {
          TokenCursor otherwise = new TokenCursor(alternative);
          otherwise.require(ZpeedyTokeniser.OTHERWISE, "Expected 'otherwise'.");
          otherwise.requireEnd();
          falseBranch = compileIndentedBody(alternative, "Expected an indented statement after 'otherwise'.");
          break;
        } else break;
      }
      return bytecodeBuilder.ifStatement(condition, trueBranch, alternatives, falseBranch);
    }

    IAST compileWhen(SourceLine header) throws ZpeedyCompileException {
      TokenCursor tokens = new TokenCursor(header);
      tokens.require(ZpeedyTokeniser.WHEN, "Expected 'when'.");
      String variable = tokens.requireIdentifier("Expected a variable after 'when'.");
      tokens.requireEnd();

      position++;
      if (position >= lines.size() || lines.get(position).indent <= header.indent) {
        throw error(header.number, "Expected an indented value branch after 'when'.");
      }
      int branchIndent = lines.get(position).indent;
      List<ZPECompilerBytecodeBuilder.WhenBranch> branches = new ArrayList<>();
      IAST otherwiseBody = null;

      while (position < lines.size()) {
        SourceLine branch = lines.get(position);
        if (branch.indent < branchIndent) break;
        if (branch.indent > branchIndent) throw error(branch.number, "Unexpected indentation.");
        if (branch.tokens.get(0).symbol == ZpeedyTokeniser.OTHERWISE) {
          TokenCursor otherwise = new TokenCursor(branch);
          otherwise.require(ZpeedyTokeniser.OTHERWISE, "Expected 'otherwise'.");
          otherwise.requireEnd();
          otherwiseBody = compileIndentedBody(branch, "Expected an indented statement after 'otherwise'.");
          break;
        }

        TokenCursor branchTokens = new TokenCursor(branch);
        branchTokens.require(ZpeedyTokeniser.IS, "Expected 'is' before the branch value.");
        int then = branchTokens.findAtCurrentDepth(ZpeedyTokeniser.THEN);
        if (then < 0) throw error(branch.number, "Expected 'then' after the branch value.");
        TokenCursor valueTokens = branchTokens.slice(then);
        Object value = compileWhenValue(valueTokens);
        valueTokens.requireEnd();
        branchTokens.position = then + 1;
        branchTokens.requireEnd();
        IAST body = compileIndentedBody(branch, "Expected an indented statement after 'then'.");
        branches.add(bytecodeBuilder.whenBranch(value, body));
      }
      if (branches.isEmpty()) throw error(header.number, "when requires at least one value branch.");
      return bytecodeBuilder.whenStatement(variable, branches, otherwiseBody);
    }

    IAST compileAttempt(SourceLine header) throws ZpeedyCompileException {
      TokenCursor tokens = new TokenCursor(header);
      tokens.require(ZpeedyTokeniser.ATTEMPT, "Expected 'attempt'.");
      tokens.requireEnd();
      IAST attemptedBody = compileIndentedBody(header, "Expected an indented statement after 'attempt'.");

      if (position >= lines.size()) {
        throw error(header.number, "Expected 'otherwise on error' after the attempted block.");
      }
      SourceLine handler = lines.get(position);
      if (handler.indent != header.indent) {
        throw error(handler.number, "Expected 'otherwise on error' at the same indentation as 'attempt'.");
      }
      TokenCursor handlerTokens = new TokenCursor(handler);
      handlerTokens.require(ZpeedyTokeniser.OTHERWISE, "Expected 'otherwise on error'.");
      handlerTokens.require(ZpeedyTokeniser.ON, "Expected 'on' after 'otherwise'.");
      handlerTokens.require(ZpeedyTokeniser.ERROR, "Expected 'error' after 'otherwise on'.");
      String errorVariable = handlerTokens.requireIdentifier("Expected an error-message variable.");
      handlerTokens.requireEnd();
      IAST errorBody = compileIndentedBody(handler,
          "Expected an indented statement after 'otherwise on error'.");
      return bytecodeBuilder.attemptStatement(attemptedBody, errorVariable, errorBody);
    }

    Object compileWhenValue(TokenCursor tokens) throws ZpeedyCompileException {
      Token token = tokens.take("Expected a branch value.");
      switch (token.symbol) {
        case ZpeedyTokeniser.STRING: return unescape(token.word, tokens.line.number);
        case ZpeedyTokeniser.INTEGER:
          try { return Long.parseLong(token.word); }
          catch (NumberFormatException exception) { throw error(tokens.line.number, "Number is outside the supported range: " + token.word); }
        case ZpeedyTokeniser.DECIMAL:
          try { return Double.parseDouble(token.word); }
          catch (NumberFormatException exception) { throw error(tokens.line.number, "Number is outside the supported range: " + token.word); }
        case ZpeedyTokeniser.TRUE: return true;
        case ZpeedyTokeniser.FALSE: return false;
        default: throw error(tokens.line.number, "when branch values must be strings, numbers, or booleans.");
      }
    }

    IAST compileLoopWhile(SourceLine header) throws ZpeedyCompileException {
      TokenCursor tokens = new TokenCursor(header);
      tokens.require(ZpeedyTokeniser.LOOP, "Expected 'loop'.");
      tokens.require(ZpeedyTokeniser.WHILE, "Expected 'while' after 'loop'.");
      IAST condition = compileCondition(tokens);
      tokens.requireEnd();
      return bytecodeBuilder.whileLoop(condition, compileIndentedBody(header, "Expected an indented loop body."));
    }

    IAST compileRepeat(SourceLine header) throws ZpeedyCompileException {
      TokenCursor tokens = new TokenCursor(header);
      tokens.require(ZpeedyTokeniser.REPEAT, "Expected 'repeat'.");
      boolean forever = tokens.match(ZpeedyTokeniser.FOREVER);
      IAST count = null;
      if (!forever) {
        count = compileExpressionUntil(tokens, ZpeedyTokeniser.TIMES,
            "Expected 'times' after the repetition count.");
        tokens.require(ZpeedyTokeniser.TIMES, "Expected 'times' after the repetition count.");
      }
      tokens.requireEnd();
      IAST body = compileIndentedBody(header, "Expected an indented loop body.");
      return forever ? bytecodeBuilder.whileLoop(bytecodeBuilder.bool(true), body)
          : bytecodeBuilder.repeatLoop(count, body);
    }

    IAST compileForEvery(SourceLine header) throws ZpeedyCompileException {
      TokenCursor tokens = new TokenCursor(header);
      tokens.require(ZpeedyTokeniser.FOR, "Expected 'for'.");
      tokens.require(ZpeedyTokeniser.EVERY, "Expected 'every' after 'for'.");
      String variable = tokens.requireIdentifier("Expected a variable after 'for every'.");
      tokens.require(ZpeedyTokeniser.IN, "Expected 'in' in the for every loop.");
      IAST iterable = compileExpression(tokens);
      tokens.requireEnd();
      return bytecodeBuilder.forEveryLoop(variable, iterable, compileIndentedBody(header, "Expected an indented loop body."));
    }

    IAST compileIndentedBody(SourceLine header, String message) throws ZpeedyCompileException {
      position++;
      if (position >= lines.size() || lines.get(position).indent <= header.indent) throw error(header.number, message);
      return bytecodeBuilder.statements(compileBlock(lines.get(position).indent).toArray(new IAST[0]));
    }
  }

  /** Compilation failure with a one-based source line. */
  public static final class ZpeedyCompileException extends Exception {
    private final int line;
    private final String detail;
    public ZpeedyCompileException(int line, String message) {
      super("Zpeedy compilation error on line " + line + ": " + message);
      this.line = line;
      this.detail = message;
    }
    public int getLine() { return line; }
    String getDetail() { return detail; }
  }
}
