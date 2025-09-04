package cn.nolaurene.cms.service.sandbox.backend.tool;


import javax.script.ScriptException;
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

    private double eval(String expr) throws ScriptException {
        Object result = new javax.script.ScriptEngineManager().getEngineByName("JavaScript").eval(expr);
        if (result instanceof Number) {
            return ((Number) result).doubleValue();
        }
        return 0;
    }
}
