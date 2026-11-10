package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashMap;
import java.util.Map;

/**
 * 宿主动作请求：插件 → 服务端，请求在宿主（PC）端执行指定动作。
 * <p>
 * 使用 Builder 逐步设置参数，链式调用，参数键由 SDK 内聚，调用方无需关心协议字段名：
 * <pre>
 * HostActionRequest.open().path("C:/Users/kid/Desktop/ap").build();
 * HostActionRequest.startApp().path("C:/.../SourceTree.exe").launchMode(2).build();
 * HostActionRequest.openBrowser().url("google.com").build();
 * HostActionRequest.inputText().text("hello").build();
 * HostActionRequest.execCmd().cmd("notepad").build();
 * </pre>
 * 服务端通过 {@link #action} 准确区分动作类型，通过 {@link #params} 取参数。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HostActionRequest implements Payload {

    /** 动作类型 */
    public HostAction action;

    /** 动作参数（键由各 Builder 保证，如 path / url / text / cmd / launchMode） */
    public Map<String, Object> params;

    public Long timestamp;

    /** 仅用于反序列化，请使用 Builder 创建实例 */
    public HostActionRequest() {
    }

    private HostActionRequest(Builder builder) {
        this.action = builder.action;
        this.params = builder.params;
        this.timestamp = System.currentTimeMillis();
    }

    /** 打开文件或文件夹，Builder 需设置 {@code path(...)} */
    public static Builder open() {
        return new Builder(HostAction.OPEN_FILE, "path");
    }

    /** 启动应用，Builder 可设置 {@code path(...)}（必填）与 {@code launchMode(...)}（可选） */
    public static Builder startApp() {
        return new Builder(HostAction.START_APP, "path");
    }

    /** 打开浏览器访问网址，Builder 需设置 {@code url(...)} */
    public static Builder openBrowser() {
        return new Builder(HostAction.OPEN_BROWSER, "url");
    }

    /** 输入文本，Builder 需设置 {@code text(...)} */
    public static Builder inputText() {
        return new Builder(HostAction.INPUT_TEXT, "text");
    }

    /** 执行命令，Builder 需设置 {@code cmd(...)} */
    public static Builder execCmd() {
        return new Builder(HostAction.EXEC_CMD, "cmd");
    }

    /**
     * 链式参数构造器：逐个参数设置，{@link #build()} 时做必填校验。
     */
    public static final class Builder {

        private final HostAction action;
        private final String requiredKey;
        private final Map<String, Object> params = new HashMap<String, Object>();

        private Builder(HostAction action, String requiredKey) {
            this.action = action;
            this.requiredKey = requiredKey;
        }

        /** 文件或文件夹路径（{@link HostActionRequest#open()} / {@link HostActionRequest#startApp()} 必填） */
        public Builder path(String path) {
            params.put("path", path);
            return this;
        }

        /** 启动方式（可选，由宿主定义取值含义） */
        public Builder launchMode(Integer launchMode) {
            params.put("launchMode", launchMode);
            return this;
        }

        /** 要访问的网址（{@link HostActionRequest#openBrowser()} 必填） */
        public Builder url(String url) {
            params.put("url", url);
            return this;
        }

        /** 要输入的文本（{@link HostActionRequest#inputText()} 必填） */
        public Builder text(String text) {
            params.put("text", text);
            return this;
        }

        /** 要执行的命令（{@link HostActionRequest#execCmd()} 必填） */
        public Builder cmd(String cmd) {
            params.put("cmd", cmd);
            return this;
        }

        /**
         * 构建请求对象；校验该动作的必填参数非空。
         *
         * @throws IllegalArgumentException 必填参数缺失或为空
         */
        public HostActionRequest build() {
            Object required = params.get(requiredKey);
            if (required == null || (required instanceof String && ((String) required).isEmpty())) {
                throw new IllegalArgumentException(requiredKey + " is required for " + action.wireName());
            }
            return new HostActionRequest(this);
        }
    }
}
