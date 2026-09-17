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
    default void onTopicUpdate(TopicPayload payload) {
    }

    default void onOptions(GetOptionsPayload payload, OptionsResponder responder) {
    }
}
