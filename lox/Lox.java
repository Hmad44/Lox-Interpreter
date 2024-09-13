package lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
    private static final Interpreter interpreter = new Interpreter();
    static boolean hadError = false;
    static boolean hadRuntimeError = false;
    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Command Usage: jlox [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    // Script mode: Giving jlox a path to file via command line
    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));

        // Indicate error in exit code
        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    // Interactive mode: Run jlox command, then execute code one line at a time
    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);
    
        for (;;) {
            hadError = false;
        
            System.out.print("> ");
            Scanner scanner = new Scanner(reader.readLine());
            List<Token> tokens = scanner.scanTokens();
        
            Parser parser = new Parser(tokens);
            Object syntax = parser.parseRepl();
        
            // Ignore if syntax error.
            if (hadError) continue;
        
            if (syntax instanceof List) {
                interpreter.interpret((List<Stmt>)syntax);
            } else if (syntax instanceof Expr) {
                String result = interpreter.interpret((Expr)syntax);
                if (result != null) {
                    System.out.println("= " + result);
                }
            }
        }
    }
      
    
    private static void run(String source) {
        // COALESCE ERRORS HERE LATER
        if (hadError) System.exit(65);

        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        // Stop if syntax error occurs
        if (hadError) return;

        interpreter.interpret(statements);
    }

    // Error handling
    static void error(int line, String message) {
        report(line, "", message);
    }
    private static void report(int line, String where, String message) {
        System.err.println("[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }

    static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message);
        }
    }

    static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() + " In [line " + error.token.line + "]");
        hadRuntimeError = true;
    }

}