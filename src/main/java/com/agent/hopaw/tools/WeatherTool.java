package com.agent.hopaw.tools;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Service;

@Service("weather")
public class WeatherTool implements AgentTool {

    @Tool("查询指定城市的天气信息")
    public String getWeather(String city) {
        return "城市: " + city + "\n天气: 晴\n温度: 25°C\n风力: 微风\n湿度: 60%";
    }

    @Override
    public String getName() {
        return "weather";
    }

    @Override
    public String getDescription() {
        return "查询指定城市的天气信息";
    }
}
