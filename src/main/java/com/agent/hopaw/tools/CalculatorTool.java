package com.agent.hopaw.tools;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Service;

@Service("calculator")
public class CalculatorTool implements AgentTool {

    @Tool("计算数学表达式的值，支持加减乘除等运算")
    public String calculate(String expression) {
        try {
            return String.valueOf(evalExpression(expression));
        } catch (Exception e) {
            return "计算错误: " + e.getMessage();
        }
    }

    private double evalExpression(String expression) {
        javax.script.ScriptEngineManager manager = new javax.script.ScriptEngineManager();
        javax.script.ScriptEngine engine = manager.getEngineByName("JavaScript");
        try {
            return Double.parseDouble(engine.eval(expression).toString());
        } catch (Exception e) {
            throw new RuntimeException("无效的表达式: " + expression);
        }
    }

    @Override
    public String getName() {
        return "calculator";
    }

    @Override
    public String getDescription() {
        return "计算数学表达式的值，支持加减乘除等运算";
    }
}
