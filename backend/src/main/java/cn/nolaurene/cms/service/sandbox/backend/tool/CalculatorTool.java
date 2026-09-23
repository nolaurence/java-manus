package cn.nolaurene.cms.service.sandbox.backend.tool;


import java.util.Map;

/**
 * @author nolau
 * @date 2025/6/24
 * @description
 */
public class CalculatorTool implements Tool {

    public String name() {
        return "calculator";
    }

    public String description() {
        return "Evaluate arithmetic expressions.";
    }

    public String run(String input, Map<String, Object> context) {
        try {
            return String.valueOf(eval(input));
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    private double eval(String expr) {
        if (expr == null || expr.isBlank()) {
            throw new IllegalArgumentException("expression must not be blank");
        }
        return new ArithmeticParser(expr).parse();
    }

    /** Small dependency-free arithmetic parser; Nashorn is unavailable on JDK 15+. */
    private static final class ArithmeticParser {
        private final String source;
        private int index;

        private ArithmeticParser(String source) {
            this.source = source;
        }

        private double parse() {
            double value = expression();
            skipWhitespace();
            if (index != source.length()) {
                throw new IllegalArgumentException("unexpected character at position " + index);
            }
            return value;
        }

        private double expression() {
            double value = term();
            while (true) {
                skipWhitespace();
                if (match('+')) value += term();
                else if (match('-')) value -= term();
                else return value;
            }
        }

        private double term() {
            double value = factor();
            while (true) {
                skipWhitespace();
                if (match('*')) value *= factor();
                else if (match('/')) {
                    double divisor = factor();
                    if (divisor == 0) throw new ArithmeticException("division by zero");
                    value /= divisor;
                } else return value;
            }
        }

        private double factor() {
            skipWhitespace();
            if (match('+')) return factor();
            if (match('-')) return -factor();
            if (match('(')) {
                double value = expression();
                skipWhitespace();
                if (!match(')')) throw new IllegalArgumentException("missing closing parenthesis");
                return value;
            }
            int start = index;
            while (index < source.length()
                    && (Character.isDigit(source.charAt(index)) || source.charAt(index) == '.')) {
                index++;
            }
            if (index < source.length()
                    && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                index++;
                if (index < source.length()
                        && (source.charAt(index) == '+' || source.charAt(index) == '-')) {
                    index++;
                }
                int exponentStart = index;
                while (index < source.length() && Character.isDigit(source.charAt(index))) {
                    index++;
                }
                if (exponentStart == index) {
                    throw new IllegalArgumentException("exponent expected at position " + index);
                }
            }
            if (start == index) throw new IllegalArgumentException("number expected at position " + index);
            return Double.parseDouble(source.substring(start, index));
        }

        private boolean match(char expected) {
            if (index < source.length() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
        }
    }
}
