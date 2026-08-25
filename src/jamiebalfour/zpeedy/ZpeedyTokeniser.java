package jamiebalfour.zpeedy;

import jamiebalfour.zpe.parser.Tokeniser;
import jamiebalfour.zpe.parser.ZPEComment;

import java.util.ArrayList;

/** Defines Zpeedy's vocabulary for the Zenith Parsing Engine. */
final class ZpeedyTokeniser implements Tokeniser {

  static final byte IDENTIFIER = 1;
  static final byte INTEGER = 2;
  static final byte DECIMAL = 3;
  static final byte STRING = 4;
  static final byte SET = 5;
  static final byte TO = 6;
  static final byte IF = 7;
  static final byte IS = 8;
  static final byte THEN = 9;
  static final byte OTHERWISE = 10;
  static final byte DISPLAY = 11;
  static final byte WHILE = 12;
  static final byte REPEAT = 13;
  static final byte TIMES = 14;
  static final byte FOREVER = 15;
  static final byte FOR = 16;
  static final byte EVERY = 17;
  static final byte IN = 18;
  static final byte STOP = 19;
  static final byte LOOP = 20;
  static final byte CONTINUE = 21;
  static final byte TRUE = 22;
  static final byte FALSE = 23;
  static final byte NULL = 24;
  static final byte UNDEFINED = 25;
  static final byte PLUS = 26;
  static final byte MINUS = 27;
  static final byte LESS = 28;
  static final byte THAN = 29;
  static final byte GREATER = 30;
  static final byte AT = 31;
  static final byte MOST = 32;
  static final byte LEAST = 33;
  static final byte NOT = 34;
  static final byte LIST_OPEN = 35;
  static final byte LIST_CLOSE = 36;
  static final byte COMMA = 37;
  static final byte WHEN = 38;
  static final byte ALTERNATIVELY = 39;
  static final byte ATTEMPT = 40;
  static final byte ON = 41;
  static final byte ERROR = 42;

  @Override
  public byte stringToByteCode(String word) {
    if (word == null || word.isEmpty()) return -2;
    switch (word) {
      case "set": return SET;
      case "to": return TO;
      case "if": return IF;
      case "is": return IS;
      case "then": return THEN;
      case "otherwise": return OTHERWISE;
      case "display": return DISPLAY;
      case "while": return WHILE;
      case "repeat": return REPEAT;
      case "times": return TIMES;
      case "forever": return FOREVER;
      case "for": return FOR;
      case "every": return EVERY;
      case "in": return IN;
      case "stop": return STOP;
      case "loop": return LOOP;
      case "continue": return CONTINUE;
      case "true": return TRUE;
      case "false": return FALSE;
      case "null": return NULL;
      case "undefined": return UNDEFINED;
      case "plus": return PLUS;
      case "minus": return MINUS;
      case "less": return LESS;
      case "than": return THAN;
      case "greater": return GREATER;
      case "at": return AT;
      case "most": return MOST;
      case "least": return LEAST;
      case "not": return NOT;
      case "[": return LIST_OPEN;
      case "]": return LIST_CLOSE;
      case ",": return COMMA;
      case "when": return WHEN;
      case "alternatively": return ALTERNATIVELY;
      case "attempt": return ATTEMPT;
      case "on": return ON;
      case "error": return ERROR;
      default:
        if (word.charAt(0) == '"') return STRING;
        if (word.matches("[+-]?\\d+")) return INTEGER;
        if (word.matches("[+-]?(?:\\d+\\.\\d*|\\d*\\.\\d+)")) return DECIMAL;
        return IDENTIFIER;
    }
  }

  @Override
  public String symbolToString(int symbol) {
    return Byte.toString((byte) symbol);
  }

  @Override
  public String delimiterCharacters() {
    return " [],\r\n\t";
  }

  @Override
  public String quoteTypes() {
    return "\"";
  }

  @Override
  public String[] listOfSubsequentCharacters() {
    return new String[0];
  }

  @Override
  public String[] listOfBoundWords() {
    return new String[0];
  }

  @Override
  public String[] listOfWhitespaces() {
    return new String[]{" ", "\t", "\n", "\r", "\r\n"};
  }

  @Override
  public ArrayList<ZPEComment> listOfComments() {
    ArrayList<ZPEComment> comments = new ArrayList<>();
    comments.add(new ZPEComment("//", "\n"));
    return comments;
  }
}
