package com.ultrabar.plugin.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultrabar.plugin.callback.CallResponder;
import com.ultrabar.plugin.callback.DescribeResponder;
import com.ultrabar.plugin.callback.OptionsResponder;
import com.ultrabar.plugin.model.ActionsPayload;
import com.ultrabar.plugin.model.ActionsAckPayload;
import com.ultrabar.plugin.model.CallPayload;
import com.ultrabar.plugin.model.DescribePayload;
import com.ultrabar.plugin.model.GetOptionsPayload;
import com.ultrabar.plugin.model.RegisterResultPayload;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InboundDispatcher {
    private static final Logger log = LoggerFactory.getLogger(InboundDispatcher.class);

    private final ObjectMapper mapper;
    private final RequestTable requests;
    private final ListenerNotifier notifier;

    public InboundDispatcher(ObjectMapper mapper, RequestTable requests, ListenerNotifier notifier) {
        this.mapper = mapper;
        this.requests = requests;
        this.notifier = notifier;
    }

    public void onMessage(Channel channel, String raw) {
        try {
            JsonNode node = mapper.readTree(raw);
            String type = textOrNull(node, "type");
            String requestId = textOrNull(node, "requestId");
            JsonNode payload = node.has("payload") ? node.get("payload") : node;
            if (type == null) {
                log.warn("inbound message missing type: {}", raw);
                return;
            }
            dispatch(channel, type, requestId, payload);
        } catch (Exception e) {
            log.error("failed to parse inbound message", e);
        }
    }

    private void dispatch(Channel channel, String type, String requestId, JsonNode payload) {
        switch (type) {
            case "register_result":
                complete(requestId, payload, RegisterResultPayload.class);
                return;
            case "actions_ack":
                complete(requestId, payload, ActionsAckPayload.class);
                return;
            case "describe_result":
            case "options_result":
            case "result":
            case "heartbeat_ack":
                requests.complete(requestId, payload);
                return;
            case "actions_update":
                handleActionsUpdate(payload);
                return;
            case "describe":
                handleDescribe(channel, requestId, payload);
                return;
            case "call":
                handleCall(channel, requestId, payload);
                return;
            case "get_options":
                handleOptions(channel, requestId, payload);
                return;
            default:
                log.info("ignored inbound type={} requestId={}", type, requestId);
        }
    }

    private void complete(String requestId, JsonNode payload, Class<?> expectedType) {
        if (!requests.complete(requestId, payload)) {
            log.debug("no pending {} for requestId={}", expectedType.getSimpleName(), requestId);
        }
    }

    private void handleActionsUpdate(JsonNode payload) {
        try {
            ActionsPayload update = mapper.treeToValue(payload, ActionsPayload.class);
            notifier.onActionsUpdate(update);
        } catch (Exception e) {
            log.warn("invalid actions_update payload", e);
        }
    }

    private void handleDescribe(Channel channel, String requestId, JsonNode payload) {
        DescribeResponder responder = new DescribeResponder(channel, requestId, mapper);
        try {
            DescribePayload describe = mapper.treeToValue(payload, DescribePayload.class);
            notifier.onDescribe(describe, responder);
        } catch (Exception e) {
            responder.sendError("INVALID_PAYLOAD", e.getMessage(), false, null);
        }
    }

    private void handleCall(Channel channel, String requestId, JsonNode payload) {
        CallResponder responder = new CallResponder(channel, requestId, mapper);
        try {
            CallPayload call = mapper.treeToValue(payload, CallPayload.class);
            notifier.onCall(call, responder);
        } catch (Exception e) {
            responder.sendError("INVALID_PAYLOAD", e.getMessage(), false, null);
        }
    }

    private void handleOptions(Channel channel, String requestId, JsonNode payload) {
        OptionsResponder responder = new OptionsResponder(channel, requestId, mapper);
        try {
            GetOptionsPayload options = mapper.treeToValue(payload, GetOptionsPayload.class);
            notifier.onOptions(options, responder);
        } catch (Exception e) {
            responder.sendError("INVALID_PAYLOAD", e.getMessage(), false, null);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText(null) : null;
    }
}
