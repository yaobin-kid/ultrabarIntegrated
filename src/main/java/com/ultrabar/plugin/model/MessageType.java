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

    A2UI_DATA("a2ui_data", A2UIDataPayload.class),
    A2UI_DATA_RESULT("a2ui_data_result", A2UIDataResultPayload.class),
    A2UI_DATA_REQUEST("a2ui_data_request", A2UIDataRequestPayload.class),
    A2UI_DATA_REQUEST_RESULT("a2ui_data_request_result", A2UIDataRequestResultPayload.class),
    A2UI_UPDATE_DATA_MODEL("a2ui_update_data_model", A2UIUpdateDataModelPayload.class),
    A2UI_UPDATE_DATA_MODEL_RESULT("a2ui_update_data_model_result", A2UIUpdateDataModelResultPayload.class),
    A2UI_ACTION_CALL("a2ui_action_call", A2UIActionCallPayload.class),
    A2UI_ACTION_CALL_RESULT("a2ui_action_call_result", A2UIActionCallResultPayload.class),
    A2UI_ARGS_SCHEMA_REQUEST("a2ui_args_schema_request", A2UIArgsSchemaRequestPayload.class),
    A2UI_ARGS_SCHEMA_RESULT("a2ui_args_schema_result", A2UIArgsSchemaResultPayload.class),

    QUERY_MODULES("query_modules", QueryModulesPayload.class),
    QUERY_MODULES_RESULT("query_modules_result", QueryModulesResultPayload.class),

    HOTKEY("hotkey", HotkeyPayload.class),
    HOTKEY_RESULT("hotkey_result", HotkeyResultPayload.class),

    MESSAGE("message", MessagePayload.class),
    MESSAGE_RESULT("message_result", MessageResultPayload.class),
    HOST_ACTION("host_action", HostActionRequest.class),
    HOST_ACTION_RESULT("host_action_result", HostActionResultPayload.class),

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
