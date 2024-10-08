package lox;

import java.util.ArrayList;
import java.util.List;

import javax.management.RuntimeErrorException;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    final Environment globals = new Environment();
    private Environment environment = globals;
    private static Object uninitialized = new Object();
    private static class BreakException extends RuntimeException {}

    Interpreter() {
        globals.define("clock", new LoxCallable() {
            @Override
            public int arity() { return 0; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double)System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() { return "<native fn>"; }
        });
    }

    // Takes in syntax tree of expression, evaluates, and converts the value to a string
    void interpret(List<Stmt> statements) {
        try{
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    String interpret(Expr expression) {
        try {
            Object value = evaluate(expression);
            return stringify(value);
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
            return null;
        }
    }
      
    // Evaluate literals
    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    // Evaluate logical expressions
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
            if (isTruthful(left)) return left;
        } else {
            if (!isTruthful(left)) return left;
        }

        return evaluate(expr.right);
    }

    // Evaluate conditional
    @Override
    public Object visitConditionalExpr(Expr.Conditional expr) {

        Object condition = evaluate(expr.condition);

        if (isTruthful(condition)) {
           return evaluate(expr.thenBranch);
        } else if (!isTruthful(condition)) {
            return evaluate(expr.elseBranch);
        } else {
            return null;
        } 
    }

    // Evaluate parentheses
    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    // Evalute unary expressions
    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthful(right);
            case MINUS:
            checkNumberOperand(expr.operator, right);
                return -(double)right;
        }

        // If unreachable
        return null;
    }

    // Evaluate variable expression
    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        Object value = environment.get(expr.name);
        if (value == uninitialized) {
            throw new RuntimeError(expr.name, "Variable must be initialized before use.");
        }
        return value;
    }

    // Evaluate binary operators
    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            case BANG_EQUAL: 
                return !isEqual(left, right);
            case EQUAL_EQUAL: 
                return isEqual(left, right);          
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                if ((double) right == 0.0) {
                    throw new RuntimeError(expr.operator, "Cannot divide by zero");
                }
                return (double)left / (double)right;
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                } 
        
                if (left instanceof String && right instanceof String) {
                    return (String)left + (String)right;
                }
                
                throw new RuntimeError(expr.operator, "Operands must be two numbers or numbers and a string.");
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren, "Can only call functions and classes.");
        }

        LoxCallable function = (LoxCallable)callee;

        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " + function.arity() + " arguments but got " + arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    // Helper methods

    // "False" and "nil" are falseful, everything else is truthful
    private boolean isTruthful(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    // Converts Lox values to string
    private String stringify(Object object) {
        if (object == null) return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;

        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private Object evaluate (Expr expr) {
        return expr.accept(this);
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;

            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        throw new BreakException();
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        String fnName = stmt.name.lexeme;
        environment.define(fnName, new LoxFunction(fnName, stmt.function, environment));
        return null;
    }

    @Override
    public Object visitFunctionExpr(Expr.Function expr) {
        return new LoxFunction(null, expr, environment);
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthful(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }

        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);

        throw new Return(value);
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = uninitialized;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        try {
            while (isTruthful(evaluate(stmt.condition))) {
                execute(stmt.body);
            }
        } catch (BreakException ex) {}
        
        return null;
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value;
    }
}
