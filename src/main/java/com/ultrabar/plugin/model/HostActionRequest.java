package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;

/**
 * 宿主动作请求：插件 → 服务端，请求在宿主（PC）端执行指定动作。
 * <p>
 * 每个动作有专属的 Builder，只能设置该动作自己的参数（编译期隔离，无法交叉设置）：
 * <pre>
 * HostActionRequest.openFile().path("C:/Users/kid/Desktop/ap").build();
 * HostActionRequest.startApp().path("C:/.../SourceTree.exe")
 *         .launchMode(HostLaunchMode.ACTIVATE_OR_START).build();
 * HostActionRequest.openBrowser().url("google.com").build();
 * HostActionRequest.inputText().text("hello").build();
 * HostActionRequest.execCmd().cmd("notepad").build();
 * </pre>
 * 服务端通过 {@link #action} 准确区分动作类型，通过 {@link #params} 取参数。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HostActionRequest implements Payload {

    /**
     * 动作类型
     */
    public HostAction action;

    /**
     * 动作参数（键由各专属 Builder 保证，如 path / url / text / cmd / launchMode）
     */
    public Map<String, Object> params;

    public Long timestamp;

    /**
     * 仅用于反序列化，请使用各动作的 Builder 创建实例
     */
    public HostActionRequest() {
    }

    private HostActionRequest(HostAction action, Map<String, Object> params) {
        this.action = action;
        this.params = params;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 打开文件或文件夹 → {@link OpenBuilder}（必填 path）
     */
    public static OpenBuilder openFile() {
        return new OpenBuilder();
    }

    /**
     * 启动应用 → {@link StartAppBuilder}（必填 path，可选 launchMode）
     */
    public static StartAppBuilder startApp() {
        return new StartAppBuilder();
    }

    /**
     * 打开浏览器访问网址 → {@link OpenBrowserBuilder}（必填 url）
     */
    public static OpenBrowserBuilder openBrowser() {
        return new OpenBrowserBuilder();
    }

    /**
     * 输入文本 → {@link InputTextBuilder}（必填 text）
     */
    public static InputTextBuilder inputText() {
        return new InputTextBuilder();
    }

    /**
     * 执行命令 → {@link ExecCmdBuilder}（必填 cmd）
     */
    public static ExecCmdBuilder execCmd() {
        return new ExecCmdBuilder();
    }

    /**
     * 通用 build 校验：必填参数非空
     */
    private static String required(String key, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    /**
     * 应用启动方式，取值含义由宿主定义。
     */
    public enum HostLaunchMode {

        /**
         * 总是启动一个新的进程
         */
        NEW_PROCESS(0),

        /**
         * 只做激活（不启动新进程，仅激活已运行的进程）
         */
        ACTIVATE_ONLY(1),

        /**
         * 未启动过进程则启动；已有启动的进程则激活
         */
        ACTIVATE_OR_START(2);

        private final int value;

        HostLaunchMode(int value) {
            this.value = value;
        }

        @JsonValue
        public int value() {
            return value;
        }

        @JsonCreator
        public static HostLaunchMode fromValue(int value) {
            for (HostLaunchMode mode : values()) {
                if (mode.value == value) {
                    return mode;
                }
            }
            return null;
        }
    }

    /**
     * {@link HostActionRequest#openFile()} 专属 Builder：只能设置 path
     */
    public static final class OpenBuilder {

        private String path;

        private OpenBuilder() {
        }

        /**
         * 文件或文件夹路径（必填）
         */
        public OpenBuilder path(String path) {
            this.path = path;
            return this;
        }

        public HostActionRequest build() {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("path", required("path", path));
            return new HostActionRequest(HostAction.OPEN_FILE, params);
        }
    }

    /**
     * {@link HostActionRequest#startApp()} 专属 Builder：只能设置 path 与 launchMode
     */
    public static final class StartAppBuilder {

        private String path;
        private HostLaunchMode launchMode = HostLaunchMode.ACTIVATE_OR_START;

        private StartAppBuilder() {
        }

        /**
         * 可执行文件路径（必填）
         */
        public StartAppBuilder path(String path) {
            this.path = path;
            return this;
        }

        /**
         * 启动方式（可选，默认由宿主决定），见 {@link HostLaunchMode}
         */
        public StartAppBuilder launchMode(HostLaunchMode launchMode) {
            this.launchMode = launchMode;
            return this;
        }

        public HostActionRequest build() {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("path", required("path", path));
            if (launchMode != null) {
                params.put("launchMode", launchMode.value());
            }
            return new HostActionRequest(HostAction.START_APP, params);
        }
    }

    /**
     * {@link HostActionRequest#openBrowser()} 专属 Builder：只能设置 url
     */
    public static final class OpenBrowserBuilder {

        private String url;

        private OpenBrowserBuilder() {
        }

        /**
         * 要访问的网址（必填，必须以 http:// 或 https:// 开头）
         */
        public OpenBrowserBuilder url(String url) {
            if (url == null || url.isEmpty()) {
                throw new IllegalArgumentException("url must not be empty");
            }
            String lower = url.toLowerCase();
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                throw new IllegalArgumentException(
                        "url must start with http:// or https://, got: " + url);
            }
            this.url = url;
            return this;
        }

        public HostActionRequest build() {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("url", required("url", url));
            return new HostActionRequest(HostAction.OPEN_BROWSER, params);
        }
    }

    /**
     * {@link HostActionRequest#inputText()} 专属 Builder：只能设置 text
     */
    public static final class InputTextBuilder {

        private String text;

        private InputTextBuilder() {
        }

        /**
         * 要输入的文本（必填）
         */
        public InputTextBuilder text(String text) {
            this.text = text;
            return this;
        }

        public HostActionRequest build() {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("text", required("text", text));
            return new HostActionRequest(HostAction.INPUT_TEXT, params);
        }
    }

    /**
     * {@link HostActionRequest#execCmd()} 专属 Builder：只能设置 cmd
     */
    public static final class ExecCmdBuilder {

        private String cmd;

        private ExecCmdBuilder() {
        }

        /**
         * 要执行的命令（必填）
         */
        public ExecCmdBuilder cmd(String cmd) {
            this.cmd = cmd;
            return this;
        }

        public HostActionRequest build() {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("cmd", required("cmd", cmd));
            return new HostActionRequest(HostAction.EXEC_CMD, params);
        }
    }
}
