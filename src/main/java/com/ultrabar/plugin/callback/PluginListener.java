package com.ultrabar.plugin.callback;

import com.ultrabar.plugin.model.*;

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
    void onRegisterSuccess(RegisterResultPayload payload);

    default void onRegisterFailed(Throwable t) {
    }

    default void onActionsFailed(Throwable t) {

    }

    default void onActionsAck(ActionsResultPayload ack) {
    }

    default void onActionsUpdate(ActionsPayload update){}

    // incoming describe from server -> plugin should respond via responder (similar to call)
    default void onDescribe(DescribePayload payload, DescribeResponder responder) {
    }

    // incoming call from server -> plugin should respond via responder
    void onCall(CallPayload payload, CallResponder responder);

    // server publishes data -> Plugin receives
    @Deprecated
    default void onTopicUpdate(TopicPayload payload) {

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
     * 收到服务端发起的 agenui action call。
     * 需通过 {@link AgenuiActionCallResponder#sendSuccess(Object)} 或
     * {@link AgenuiActionCallResponder#sendError(String, String, boolean)} 响应。
     */
    default void onAgenuiActionCall(AgenuiActionCallPayload payload, AgenuiActionCallResponder responder) {
        responder.sendError(ErrorCodes.NO_HANDLER, "no agenui action handler registered", false);
    }
}
