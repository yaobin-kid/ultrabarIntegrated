package com.ultrabar.server;

import com.ultrabar.plugin.model.ModuleEventPayload;

/**
 * 模块事件分发结果。
 */
public final class ModuleEventDispatchResult {
    private final boolean handled;
    private final boolean fallback;
    private final String packageName;
    private final ModuleEventPayload event;

    private ModuleEventDispatchResult(boolean handled, boolean fallback, String packageName, ModuleEventPayload event) {
        this.handled = handled;
        this.fallback = fallback;
        this.packageName = packageName;
        this.event = event;
    }

    public static ModuleEventDispatchResult handled(String packageName, ModuleEventPayload event) {
        return new ModuleEventDispatchResult(true, false, packageName, event);
    }

    public static ModuleEventDispatchResult fallback(ModuleEventPayload event) {
        return new ModuleEventDispatchResult(false, true, null, event);
    }

    /** 是否有插件消费了该事件 */
    public boolean handled() {
        return handled;
    }

    /** 是否进入了兜底方法 */
    public boolean fallback() {
        return fallback;
    }

    /** 消费该事件的插件包名；未消费时为 null */
    public String packageName() {
        return packageName;
    }

    /** 实际分发/兜底使用的事件 */
    public ModuleEventPayload event() {
        return event;
    }
}
