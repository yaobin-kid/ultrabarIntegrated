package com.ultrabar.plugin.callback;

import com.ultrabar.plugin.model.*;

import java.util.Collections;
import java.util.List;

/**
 * Single global listener interface for plugin events.
 * Implement this to receive register/actions/describe failures and incoming calls.
 */
public interface PluginListener {
    /**
     * 注册成功回调
     *
     * @param payload
     */
    default void onRegisterSuccess(RegisterResultPayload payload) {
    }

    default void onRegisterFailed(Throwable t) {
    }

    default void onActionsFailed(Throwable t) {

    }

    default void onActionsAck(ActionsResultPayload ack) {
    }

    default void onActionsUpdate(ActionsPayload update) {
    }

    // incoming describe from server -> plugin should respond via responder (similar to call)
    default void onDescribe(DescribePayload payload, DescribeResponder responder) {
    }

    // incoming call from server -> plugin should respond via responder
    default void onCall(CallPayload payload, CallResponder responder) {
    }

    /**
     * 收到服务端透传的模块事件。
     * 消费时调用 {@code responder.consume()}，不消费时调用 {@code responder.ignore(reason)}；
     * 两者只能调用一次。
     */
    default void onModuleEvent(ModuleEventPayload payload, ModuleEventResponder responder) {
        responder.ignore("no handler");
    }

    default void onOptions(GetOptionsPayload payload, OptionsResponder responder) {
    }

    /**
     * 收到服务端发起的 a2ui action call。
     * 需通过 {@link A2UIActionCallResponder#sendSuccess(Object)} 或
     * {@link A2UIActionCallResponder#sendError(String, String, boolean)} 响应。
     */
    default void onA2UIActionCall(A2UIActionCallPayload payload, A2UIActionCallResponder responder) {
        responder.sendError(ErrorCodes.NO_HANDLER, "no a2ui action handler registered", false);
    }

    /**
     * 服务端主动拉取 a2ui 数据时触发。
     * 返回当前插件持有的 a2ui 布局列表；返回 null 或空列表表示没有数据。
     */
    default List<A2UIItem> onA2UIDataRequest() {
        return Collections.emptyList();
    }

    /**
     * 收到服务端下发的文本消息（{@code PluginServer.sendMessage}）。
     * {@code payload.instanceId} 为目标实例 id，{@code payload.message} 为文本内容；
     * {@code payload.destination} 仅用于插件→服务端方向，此方向为 null。
     * 需通过 {@link MessageResponder#sendSuccess()} 或
     * {@link MessageResponder#sendError(String, String, boolean)} 响应。
     */
    default void onMessage(MessagePayload payload, MessageResponder responder) {
        responder.sendError(ErrorCodes.NO_HANDLER, "no message handler registered", false);
    }
}
