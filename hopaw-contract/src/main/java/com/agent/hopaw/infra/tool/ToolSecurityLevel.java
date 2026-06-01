package com.agent.hopaw.infra.tool;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ToolSecurityLevel {
    Level value() default Level.SAFE;

    enum Level {
        SAFE(1, "安全，不需要审核"),
        ALL_REQUIRE_APPROVAL(2, "全部需要审核"),
        PARAM_REQUIRE_APPROVAL(3, "仅特殊参数需要审核");

        private final int code;
        private final String description;

        Level(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public int getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }
}