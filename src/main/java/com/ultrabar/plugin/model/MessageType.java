package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Wire {@code type} values for {@link Envelope}. Request/response names are paired.
 */
public enum MessageType {
    REGISTER("register", RegisterPayload.class),
    REGISTER_RESULT("register_result", RegisterResultPayload.class),
    ACTIONS("actions", ActionsPayload.class),
    ACTIONS_RESULT("actions_result", ActionsResultPayload.class),
    ACTIONS_UPDATE("actions_update", ActionsPayload.class),
    DESCRIBE("describe", DescribePayload.class),
    DESCRIBE_RESULT("describe_result", DescribeResultPayload.class),
    CALL("call", CallPayload.class),

    CALL_RESULT("call_result", CallResultPayload.class),
    GET_OPTIONS("get_options", GetOptionsPayload.class),
    GET_OPTIONS_RESULT("get_options_result", GetOptionsResultPayload.class),
    HEARTBEAT("heartbeat", HeartbeatPayload.class),
    HEARTBEAT_ACK("heartbeat_ack", HeartbeatAckPayload.class),

    EVENT("event", EventPayload.class),
    EVENT_RESULT("event_result", EventResultPayload.class),

    MODULE_EVENT_REGISTER("module_event_register", ModuleEventRegisterPayload.class),
    MODULE_EVENT_UNREGISTER("module_event_unregister", ModuleEventUnregisterPayload.class),
    MODULE_EVENT("module_event", ModuleEventPayload.class),
    MODULE_EVENT_RESULT("module_event_result", ModuleEventResultPayload.class),

    AGENUI_DATA("agenui_data", AGenUIDataPayload.class),
    AGENUI_DATA_RESULT("agenui_data_result", AGenUIDataResultPayload.class),
    AGENUI_UPDATE_DATA_MODEL("agenui_update_data_model", AGenUIUpdateDataModelPayload.class),
    AGENUI_UPDATE_DATA_MODEL_RESULT("agenui_update_data_model_result", AGenUIUpdateDataModelResultPayload.class),
    AGENUI_ACTION_CALL("agenui_action_call", AGenUIActionCallPayload.class),
    AGENUI_ACTION_CALL_RESULT("agenui_action_call_result", AGenUIActionCallResultPayload.class),

    ;


    private final String wireName;
    private final Class<? extends Payload> payloadType;

    MessageType(String wireName, Class<? extends Payload> payloadType) {
        this.wireName = wireName;
        this.payloadType = payloadType;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    public Class<? extends Payload> payloadType() {
        return payloadType;
    }

    public static MessageType fromWire(String wireName) {
        if (wireName == null) {
            return null;
        }
        MessageType[] values = values();
        for (int i = 0; i < values.length; i++) {
            if (values[i].wireName.equals(wireName)) {
                return values[i];
            }
        }
        return null;
    }
}
