package com.agent.hopaw.infra.chat;

import com.agent.hopaw.infra.constant.ModelCapabilityEnum;
import com.agent.hopaw.infra.model.dto.AiModelVO;
import com.agent.hopaw.infra.model.dto.ModelCapabilityTestResult;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class BaseChatModelFactory implements ChatModelFactory {

    private final Logger logger = org.slf4j.LoggerFactory.getLogger(BaseChatModelFactory.class);

    public String getThinkingContentKey(AiModelVO aiModelVO) {
        Object thinkingContentKey = getExtParams(aiModelVO, "thinkingContentKey");
        if (thinkingContentKey == null) {
            return "reasoning_content";
        }
        return (String) thinkingContentKey;
    }

    public Boolean getSendThinking(AiModelVO aiModelVO) {
        Object sendThinking = getExtParams(aiModelVO, "sendThinking");
        if (sendThinking == null) {
            return true;
        }
        return (Boolean) sendThinking;
    }

    public Boolean getReturnThinking(AiModelVO aiModelVO) {
        Object val = getExtParams(aiModelVO, "returnThinking");
        return val != null ? (Boolean) val : true;
    }

    public String getReasoningEffort(AiModelVO aiModelVO) {
        Object val = getExtParams(aiModelVO, "reasoningEffort");
        return val != null ? (String) val : "medium";
    }

    public Double getTemperature(AiModelVO aiModelVO) {
        Object val = getExtParams(aiModelVO, "temperature");
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return null;
    }

    public Long getTimeoutSeconds(AiModelVO aiModelVO) {
        Object val = getExtParams(aiModelVO, "timeoutSeconds");
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return 120L;
    }

    public Boolean getLogRequests(AiModelVO aiModelVO) {
        Object val = getExtParams(aiModelVO, "logRequests");
        return val != null ? (Boolean) val : false;
    }

    public Boolean getLogResponses(AiModelVO aiModelVO) {
        Object val = getExtParams(aiModelVO, "logResponses");
        return val != null ? (Boolean) val : false;
    }
    public Boolean getAccumulateToolCallId(AiModelVO aiModelVO) {
        Object val = getExtParams(aiModelVO, "accumulateToolCallId");
        return val != null ? (Boolean) val : false;
    }

    private Object getExtParams(AiModelVO aiModelVO, String paramName) {
        if(aiModelVO.getExtParams()!=null){
            JSONObject jsonObject = JSON.parseObject(aiModelVO.getExtParams());
            if(jsonObject!=null && jsonObject.containsKey(paramName)){
                return jsonObject.get(paramName);
            }
        }
        if(aiModelVO.getAiModelProvider()!=null && aiModelVO.getAiModelProvider().getExtParams()!=null){
            JSONObject jsonObject = JSON.parseObject(aiModelVO.getAiModelProvider().getExtParams());
            if(jsonObject!=null && jsonObject.containsKey(paramName)){
                return jsonObject.get(paramName);
            }
        }
        return null;
    }


    @Override
    public ModelCapabilityTestResult testModelCapability(ChatModel chatModel) {
        List<ModelCapabilityEnum> modelCapabilities = new ArrayList<>(0);
        List<String> errors = new ArrayList<>();

        try {
            ChatResponse chatResponse = chatModel.chat(new UserMessage("你好，请问1+2=几？直接给出结果数字。"));
            if (chatResponse.aiMessage().text().trim().equals("3")) {
                modelCapabilities.add(ModelCapabilityEnum.TEXT);
            } else {
                errors.add("文本能力测试未通过：返回结果不符合预期");
            }
        } catch (Exception e) {
            errors.add("文本能力测试异常：" + e.getMessage());
            logger.error("测试文本能力异常", e);
        }

        try {
            String imageBase64 = getImageAsBase64();
            if (!imageBase64.isEmpty()) {
                ChatResponse chatResponse = chatModel.chat(UserMessage.from(
                        TextContent.from("图片是个什么动物？直接回答：猪、猫、狗、鸡、鸭"),
                        ImageContent.from(imageBase64, "image/png")));
                if (chatResponse.aiMessage().text().trim().equals("猫")) {
                    modelCapabilities.add(ModelCapabilityEnum.IMAGE);
                } else {
                    errors.add("图片能力测试未通过：返回结果不符合预期");
                }
            } else {
                errors.add("图片能力测试跳过：无法读取测试图片");
            }
        } catch (Exception e) {
            errors.add("图片能力测试异常：" + e.getMessage());
            logger.error("测试图片能力异常", e);
        }

        boolean verified = !modelCapabilities.isEmpty();
        String message;
        if (verified) {
            String caps = modelCapabilities.stream()
                    .map(ModelCapabilityEnum::getName)
                    .collect(Collectors.joining("、"));
            message = "检测到能力：" + caps;
            if (!errors.isEmpty()) {
                message += "；" + String.join("；", errors);
            }
        } else {
            message = "能力验证未通过";
            if (!errors.isEmpty()) {
                message += "：" + String.join("；", errors);
            }
        }

        return new ModelCapabilityTestResult(verified, modelCapabilities, message);
    }

    private String getImageAsBase64() {
        try {
            // 读取资源目录下static/images/cat.png为base64
            ClassLoader classLoader = getClass().getClassLoader();
            java.io.InputStream inputStream = classLoader.getResourceAsStream("static/images/cat.png");
            if (inputStream == null) {
                logger.error("无法找到资源文件: static/images/cat.png");
                return "";
            }
            
            byte[] imageBytes = inputStream.readAllBytes();
            inputStream.close();
            
            // 转换为base64字符串
            return java.util.Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            logger.error("读取图片并转换为base64失败", e);
            return "";
        }
    }
}
