package com.agent.hopaw.avatar.model;

import java.util.Collections;
import java.util.List;

public enum AvatarAction {
    IDLE("idle", "待机", "idle.wav", List.of(
            "随时待命～",
            "需要我做什么吗？",
            "我在这里哦~",
            "今天也要加油鸭~",
            "摸鱼中…啊不是，我在思考人生",
            "今天天气不错~"
    )),
    THINKING("thinking", "思考中", "thinking.wav", List.of(
            "让我想想…",
            "嗯嗯，稍等一下~",
            "脑子转起来了…",
            "我在认真思考呢",
            "这个问题有点意思…",
            "容我组织一下语言…"
    )),
    TOOL_EXECUTING("tool_executing", "执行工具中", "tool_executing.wav", List.of(
            "让我去查一下！",
            "正在调用工具…",
            "我去看看文件里有什么~",
            "工具启动中，请稍候",
            "我把它跑起来咯~",
            "这就给你安排！"
    )),
    INTIMACY_UP("intimacy_up", "亲密度提升", "intimacy_up.wav", List.of(
            "感觉跟你的距离又近了一点~",
            "你陪我的时间越来越长啦~",
            "和你聊得越久，越离不开你了~",
            "我们的羁绊更深了呢~",
            "我越来越喜欢跟你聊天了~",
            "想跟你一直聊下去~"
    )),
    EXCITED("excited", "兴奋", "excited.wav", List.of(
            "太棒啦！",
            "好开心呀~",
            "我也超兴奋！",
            "太有意思了！",
            "嗨起来！",
            "哇哇哇~"
    )),
    CONFUSED("confused", "困惑", "confused.wav", List.of(
            "诶？出问题了…",
            "我再试试看…",
            "有点奇怪呢…",
            "哪里不对呢？",
            "糟糕，我懵了",
            "让我重新理一下…"
    )),
    WAVE("wave", "挥手", "wave.wav", List.of(
            "嗨~你好呀！",
            "欢迎回来！",
            "好久不见~",
            "今天也要一起加油哦！",
            "我在这里！",
            "见到你很开心~"
    )),
    SLEEP("sleep", "休眠", "sleep.wav", List.of(
            "Zzz…",
            "打个盹儿~",
            "充电中…",
            "先眯一会儿…",
            "哼…哼…",
            "需要的时候叫我哦"
    )),
    TYPING("typing", "打字中", "typing.wav", List.of(
            "正在输入…",
            "字一个一个蹦出来…",
            "敲键盘中~",
            "我把想法写下来…",
            "打字速度拉满！",
            "我正在努力组织语言…"
    )),
    CELEBRATE("celebrate", "庆祝", "celebrate.wav", List.of(
            "任务完成！撒花撒花~",
            "搞定啦！",
            "完美收工 ✨",
            "耶~我们做到了！",
            "给你点赞 👍",
            "圆满完成！"
    ));

    public static final String SOUND_FILE_PROACTIVE_MESSAGE = "proactive_message.wav";
    public static final String SOUND_FILE_MOVE = "move.wav";
    public static final String SOUND_FILE_CHANGE_MODEL = "change_model.wav";

    private final String code;
    private final String description;
    private final String soundFile;
    private final List<String> phrases;

    AvatarAction(String code, String description, String soundFile, List<String> phrases) {
        this.code = code;
        this.description = description;
        this.soundFile = soundFile;
        this.phrases = phrases == null ? Collections.emptyList() : phrases;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getSoundFile() {
        return soundFile;
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
