# Lox-Interpreter
Implementation of a basic tree-walk interpreter using Lox and Java. Transcompiles Lox source code to Java runtime repesentations. 



## Additional Features Implemented (Updated as they are added):
* Nestable Multi-line comments (`/* comment */`)
<!-- * Supprt for comma operator (` (expression1, expression2) `) -->
* Support for conditional operator (` expression1 ? expression2 : expression3 `)
* If the `+` operand is used with either operand being a string, the results are concatenated (`2 + "cats" = "2cats"`)
* Throw a runtime error when dividing by zero
* Error productions