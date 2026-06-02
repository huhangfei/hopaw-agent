package com.agent.hopaw.avatar.model;

import java.util.Collections;
import java.util.List;

public enum AvatarAction {
    IDLE("idle", "待机", List.of(
            "随时待命～",
            "需要我做什么吗？",
            "我在这里哦~",
            "今天也要加油鸭~",
            "摸鱼中…啊不是，我在思考人生",
            "今天天气不错~"
    )),
    THINKING("thinking", "思考中", List.of(
            "让我想想…",
            "嗯嗯，稍等一下~",
            "脑子转起来了…",
            "我在认真思考呢",
            "这个问题有点意思…",
            "容我组织一下语言…"
    )),
    TOOL_EXECUTING("tool_executing", "执行工具中", List.of(
            "让我去查一下！",
            "正在调用工具…",
            "我去看看文件里有什么~",
            "工具启动中，请稍候",
            "我把它跑起来咯~",
            "这就给你安排！"
    )),
    LEVEL_UP("level_up", "升级了", List.of(
            "我又变强了！✨",
            "升级啦，感谢一路陪伴~",
            "你用得越多，我就越厉害~",
            "感觉充满了力量！",
            "下一个等级，冲冲冲！",
            "我进化了！"
    )),
    EXCITED("excited", "兴奋", List.of(
            "太棒啦！",
            "好开心呀~",
            "我也超兴奋！",
            "太有意思了！",
            "嗨起来！",
            "哇哇哇~"
    )),
    CONFUSED("confused", "困惑", List.of(
            "诶？出问题了…",
            "我再试试看…",
            "有点奇怪呢…",
            "哪里不对呢？",
            "糟糕，我懵了",
            "让我重新理一下…"
    )),
    WAVE("wave", "挥手", List.of(
            "嗨~你好呀！",
            "欢迎回来！",
            "好久不见~",
            "今天也要一起加油哦！",
            "我在这里！",
            "见到你很开心~"
    )),
    SLEEP("sleep", "休眠", List.of(
            "Zzz…",
            "打个盹儿~",
            "充电中…",
            "先眯一会儿…",
            "哼…哼…",
            "需要的时候叫我哦"
    )),
    TYPING("typing", "打字中", List.of(
            "正在输入…",
            "字一个一个蹦出来…",
            "敲键盘中~",
            "我把想法写下来…",
            "打字速度拉满！",
            "我正在努力组织语言…"
    )),
    CELEBRATE("celebrate", "庆祝", List.of(
            "任务完成！撒花撒花~",
            "搞定啦！",
            "完美收工 ✨",
            "耶~我们做到了！",
            "给你点赞 👍",
            "圆满完成！"
    ));

    private final String code;
    private final String description;
    private final List<String> phrases;

    AvatarAction(String code, String description, List<String> phrases) {
        this.code = code;
        this.description = description;
        this.phrases = phrases == null ? Collections.emptyList() : phrases;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getPhrases() {
        return phrases;
    }

    public String getRandomPhrase() {
        if (phrases.isEmpty()) {
            return description;
        }
        return phrases.get((int) (Math.random() * phrases.size()));
    }

    public static AvatarAction fromMessageType(String type) {
        if (type == null) {
            return IDLE;
        }
        return switch (type) {
            case "received" -> THINKING;
            case "thinking" -> THINKING;
            case "tool_call" -> TOOL_EXECUTING;
            case "chunk" -> TYPING;
            case "done", "task-done" -> CELEBRATE;
            case "error", "warn" -> CONFUSED;
            default -> IDLE;
        };
    }
}
