package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 宿主端可执行的动作类型，见 {@link HostActionRequest} 的工厂方法。
 */
public enum HostAction {

    /** 打开文件或文件夹，见 {@link HostActionRequest#openFile(String)} */
    OPEN_FILE("openFile"),

    /** 启动应用，见 {@link HostActionRequest#startApp(String, Integer)} */
    START_APP("startApp"),

    /** 打开浏览器访问网址，见 {@link HostActionRequest#openBrowser(String)} */
    OPEN_BROWSER("openBrowser"),

    /** 输入文本，见 {@link HostActionRequest#inputText(String)} */
    INPUT_TEXT("inputText"),

    /** 执行命令，见 {@link HostActionRequest#execCmd(String)} */
    EXEC_CMD("execCmd");

    private final String wireName;

    HostAction(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static HostAction fromWire(String wireName) {
        if (wireName != null) {
            for (HostAction action : values()) {
                if (action.wireName.equals(wireName)) {
                    return action;
                }
            }
        }
        return null;
    }
}
