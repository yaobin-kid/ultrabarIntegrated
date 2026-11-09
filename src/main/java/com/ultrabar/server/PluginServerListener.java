package com.ultrabar.server;

import com.ultrabar.plugin.model.A2UIDataPayload;
import com.ultrabar.plugin.model.A2UIDataResultPayload;
import com.ultrabar.plugin.model.A2UIUpdateDataModelPayload;
import com.ultrabar.plugin.model.A2UIUpdateDataModelResultPayload;
import com.ultrabar.plugin.model.EventPayload;
import com.ultrabar.plugin.model.HotkeyPayload;
import com.ultrabar.plugin.model.HotkeyResultPayload;
import com.ultrabar.plugin.model.MessagePayload;
import com.ultrabar.plugin.model.MessageResultPayload;
import com.ultrabar.plugin.model.ModuleEventPayload;
import com.ultrabar.plugin.model.ModuleInfo;

import java.util.Collections;
import java.util.List;

public interface PluginServerListener {
    default void onRegistered(PluginSession session) {
    }

    default void onUnregistered(PluginSession session) {
    }

    default void onActionsUpdated(PluginSession session) {
    }

    default void onEventReceived(PluginSession session, EventPayload event) {

    }

    /**
     * 模块事件没有被任何插件消费时的兜底回调。
     */
    default void onModuleEventFallback(ModuleEventPayload event) {

    }

    /**
     * 插件端上报 A2UI 数据时触发。
     * 此时数据已存入 {@link PluginSession} 中。
     * 返回值会作为 ack 回传给插件；返回 null 视为成功。
     * 若宿主处理失败（如渲染异常），可返回 success=false 的结果告知插件。
     */
    default A2UIDataResultPayload onA2UIData(PluginSession session, A2UIDataPayload payload) {
        A2UIDataResultPayload r = new A2UIDataResultPayload();
        r.success = true;
        return r;
    }

    /**
     * 插件端上报 A2UI updateDataModel 时触发。
     * 此时 dataModel 已存入 {@link PluginSession} 中。
     * 返回值会作为 ack 回传给插件；返回 null 视为成功。
     * 若宿主处理失败（如应用 dataModel 出错），可返回 success=false 的结果告知插件。
     */
    default A2UIUpdateDataModelResultPayload onA2UIUpdateDataModel(PluginSession session, A2UIUpdateDataModelPayload payload) {
        A2UIUpdateDataModelResultPayload r = new A2UIUpdateDataModelResultPayload();
        r.success = true;
        return r;
    }

    /**
     * 插件查询模块列表时触发。宿主实现此方法返回模块数据，
     * 在线/离线过滤由服务端统一完成。
     *
     * @param session 发起查询的插件会话
     * @return 模块列表；返回全部模块即可。默认返回空列表。
     */
    default List<ModuleInfo> onQueryModules(PluginSession session) {
        return Collections.emptyList();
    }

    /**
     * 插件上报热键时触发（{@code PluginClient.sendHotkey / hotkeyDown / hotkeyUp}）。
     * <p>
     * {@code payload.action} 只有 down / up 两种，<b>每个按键至少回调 2 次</b>（down + up）；
     * {@code payload.pressType} 区分语义：
     * <ul>
     *   <li>short：插件调用 sendHotkey 的短按，down+up 由 SDK 自动补齐，
     *       组合键 down 按按下顺序、up 按逆序到达</li>
     *   <li>long：插件调用 hotkeyDown/hotkeyUp 的长按；按住期间可能收到重复的 down
     *       （模拟键盘自动重复，如 PS 中 Ctrl+] 持续放大画笔）；插件 SDK 保证
     *       down 后长时间未 up 会自动补发一次 up</li>
     * </ul>
     * {@code payload.keys} 为原始按键码（与宿主平台一致，Windows 为 VK 码），
     * 一条消息通常只含一个按键码。
     * <p>
     * 返回值会作为 ack 回传给插件；返回 null 视为成功。
     * 若宿主执行失败，可返回 success=false 的结果告知插件。
     */
    default HotkeyResultPayload onHotkey(PluginSession session, HotkeyPayload payload) {
        HotkeyResultPayload r = new HotkeyResultPayload();
        r.success = true;
        return r;
    }

    /**
     * 插件向指定实例发送文本消息时触发（{@code PluginClient.sendMessage}）。
     * <p>
     * {@code payload.destination} 为消息透传目的地（{@code MessageDestination} 枚举，
     * 默认 ULTRA_WORKSPACE_PLUGIN，即透传到 Ultra WorkSpace PLUGIN 端；其他透传场景由插件在 payload 中指定）；
     * {@code payload.instanceId} 为目标实例 id（由接收端定义与解释），
     * {@code payload.message} 为文本内容（协议仅支持文本）。
     * <p>
     * 返回值会作为 ack 回传给插件；返回 null 视为成功。
     * 若处理失败，可返回 success=false 的结果告知插件。
     */
    default MessageResultPayload onMessage(PluginSession session, MessagePayload payload) {
        MessageResultPayload r = new MessageResultPayload();
        r.success = true;
        return r;
    }

}
