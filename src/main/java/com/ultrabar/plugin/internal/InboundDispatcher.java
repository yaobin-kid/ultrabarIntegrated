package com.ultrabar.plugin.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultrabar.plugin.callback.A2UIActionCallResponder;
import com.ultrabar.plugin.callback.CallResponder;
import com.ultrabar.plugin.callback.DescribeResponder;
import com.ultrabar.plugin.callback.EnvelopeWriter;
import com.ultrabar.plugin.callback.MessageResponder;
import com.ultrabar.plugin.callback.ModuleEventResponder;
import com.ultrabar.plugin.callback.OptionsResponder;
import com.ultrabar.plugin.model.ActionsPayload;
import com.ultrabar.plugin.model.A2UIActionCallPayload;
import com.ultrabar.plugin.model.A2UIArgsSchemaRequestPayload;
import com.ultrabar.plugin.model.A2UIArgsSchemaResultPayload;
import com.ultrabar.plugin.model.A2UIDataRequestResultPayload;
import com.ultrabar.plugin.model.A2UIDataResultPayload;
import com.ultrabar.plugin.model.A2UIUpdateDataModelResultPayload;
import com.ultrabar.plugin.model.CallPayload;
import com.ultrabar.plugin.model.DescribePayload;
import com.ultrabar.plugin.model.Envelope;
import com.ultrabar.plugin.model.ErrorCodes;
import com.ultrabar.plugin.model.ErrorInfo;
import com.ultrabar.plugin.model.GetOptionsPayload;
import com.ultrabar.plugin.model.MessagePayload;
import com.ultrabar.plugin.model.MessageType;
import com.ultrabar.plugin.model.ModuleEventPayload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.Channel;

public final class InboundDispatcher {
    private static final Logger log = LoggerFactory.getLogger(InboundDispatcher.class);

    private final ObjectMapper mapper;
    private final RequestTable requests;
    private final ListenerNotifier notifier;
    private final SessionState session;

    public InboundDispatcher(ObjectMapper mapper, RequestTable requests, ListenerNotifier notifier, SessionState session) {
        this.mapper = mapper;
        this.requests = requests;
        this.notifier = notifier;
        this.session = session;
    }

    public void onMessage(Channel channel, String raw) {
        Envelope envelope;
        try {
            envelope = mapper.readValue(raw, Envelope.class);
        } catch (Exception e) {
            log.error("failed to parse inbound envelope", e);
            return;
        }
        if (envelope.getType() == null) {
            log.warn("inbound envelope missing or unknown type");
            return;
        }
        dispatch(channel, envelope);
    }

    private void dispatch(Channel channel, Envelope envelope) {
        MessageType type = envelope.getType();
        switch (type) {
            case REGISTER_RESULT:
            case ACTIONS_RESULT:
            case DESCRIBE_RESULT:
            case GET_OPTIONS_RESULT:
            case CALL_RESULT:
            case HEARTBEAT_ACK:
            case EVENT_RESULT:
            case A2UI_DATA_RESULT:
            case A2UI_DATA_REQUEST_RESULT:
            case A2UI_UPDATE_DATA_MODEL_RESULT:
            case A2UI_ACTION_CALL_RESULT:
            case A2UI_ARGS_SCHEMA_RESULT:
            case QUERY_MODULES_RESULT:
            case HOTKEY_RESULT:
            case MESSAGE_RESULT:
            case HOST_ACTION_RESULT:
                if (!requests.complete(envelope.getRequestId(), envelope.getPayload())) {
                    log.debug("no pending request for type={} requestId={}", type, envelope.getRequestId());
                }
                return;
            case ACTIONS_UPDATE:
                notifier.onActionsUpdate(envelope.payloadAs(ActionsPayload.class));
                return;
            case DESCRIBE:
                handleDescribe(channel, envelope);
                return;
            case CALL:
                handleCall(channel, envelope);
                return;
            case GET_OPTIONS:
                handleOptions(channel, envelope);
                return;
            case MODULE_EVENT:
                handleModuleEvent(channel, envelope);
                return;
            case A2UI_ACTION_CALL:
                handleA2UIActionCall(channel, envelope);
                return;
            case A2UI_DATA_REQUEST:
                handleA2UIDataRequest(channel, envelope);
                return;
            case A2UI_ARGS_SCHEMA_REQUEST:
                handleA2UIArgsSchemaRequest(channel, envelope);
                return;
            case MESSAGE:
                handleMessage(channel, envelope);
                return;
            default:
                log.info("ignored inbound type={} requestId={}", type, envelope.getRequestId());
        }
    }

    private void handleDescribe(Channel channel, Envelope envelope) {
        DescribeResponder responder = new DescribeResponder(
                channel, envelope.getRequestId(), mapper, session.sessionId(), session.sessionToken());
        try {
            notifier.onDescribe(envelope.payloadAs(DescribePayload.class), responder);
        } catch (Exception e) {
            responder.sendError(ErrorCodes.INVALID_PAYLOAD, e.getMessage(), false, null);
        }
    }

    private void handleCall(Channel channel, Envelope envelope) {
        CallResponder responder = new CallResponder(
                channel, envelope.getRequestId(), mapper, session.sessionId(), session.sessionToken());
        try {
            notifier.onCall(envelope.payloadAs(CallPayload.class), responder);
        } catch (Exception e) {
            responder.sendError(ErrorCodes.INVALID_PAYLOAD, e.getMessage(), false, null);
        }
    }

    private void handleOptions(Channel channel, Envelope envelope) {
        OptionsResponder responder = new OptionsResponder(channel, envelope.getRequestId(), mapper, session.sessionId(), session.sessionToken());
        try {
            notifier.onOptions(envelope.payloadAs(GetOptionsPayload.class), responder);
        } catch (Exception e) {
            responder.sendError(ErrorCodes.INVALID_PAYLOAD, e.getMessage(), false, null);
        }
    }

    private void handleModuleEvent(Channel channel, Envelope envelope) {
        ModuleEventPayload payload;
        try {
            payload = envelope.payloadAs(ModuleEventPayload.class);
        } catch (Exception e) {
            ModuleEventResponder responder = new ModuleEventResponder(
                    channel, envelope.getRequestId(), mapper,
                    session.sessionId(), session.sessionToken());
            responder.ignore("invalid payload: " + e.getMessage());
            return;
        }
        ModuleEventResponder responder = new ModuleEventResponder(
                channel, envelope.getRequestId(), mapper,
                session.sessionId(), session.sessionToken());
        if (payload == null || payload.module == null) {
            responder.ignore("invalid module event");
            return;
        }
        notifier.onModuleEvent(payload, responder);
    }

    private void handleA2UIActionCall(Channel channel, Envelope envelope) {
        A2UIActionCallResponder responder = new A2UIActionCallResponder(
                channel, envelope.getRequestId(), mapper,
                session.sessionId(), session.sessionToken());
        try {
            A2UIActionCallPayload payload = envelope.payloadAs(A2UIActionCallPayload.class);
            notifier.onA2UIActionCall(payload, responder);
        } catch (Exception e) {
            responder.sendError(ErrorCodes.INVALID_PAYLOAD, e.getMessage(), false);
        }
    }

    private void handleA2UIDataRequest(Channel channel, Envelope envelope) {
        A2UIDataRequestResultPayload result = new A2UIDataRequestResultPayload();
        try {
            result.items = notifier.onA2UIDataRequest();
            result.success = true;
        } catch (Exception e) {
            result.success = false;
            result.error = ErrorInfo.of(ErrorCodes.HANDLER_EXCEPTION, e.getMessage(), false, null);
        }
        EnvelopeWriter.write(channel, mapper,
                Envelope.of(MessageType.A2UI_DATA_REQUEST_RESULT, envelope.getRequestId(), result)
                        .withSession(session.sessionId(), session.sessionToken()));
    }

    private void handleA2UIArgsSchemaRequest(Channel channel, Envelope envelope) {
        A2UIArgsSchemaResultPayload result = new A2UIArgsSchemaResultPayload();
        try {
            A2UIArgsSchemaRequestPayload payload =
                    envelope.payloadAs(A2UIArgsSchemaRequestPayload.class);
            result.args = notifier.onA2UIArgsSchemaRequest(
                    payload == null ? null : payload.schemaGetter);
            result.success = true;
        } catch (Exception e) {
            result.success = false;
            result.error = ErrorInfo.of(ErrorCodes.HANDLER_EXCEPTION, e.getMessage(), false, null);
        }
        EnvelopeWriter.write(channel, mapper,
                Envelope.of(MessageType.A2UI_ARGS_SCHEMA_RESULT, envelope.getRequestId(), result)
                        .withSession(session.sessionId(), session.sessionToken()));
    }

    private void handleMessage(Channel channel, Envelope envelope) {
        MessageResponder responder = new MessageResponder(
                channel, envelope.getRequestId(), mapper,
                session.sessionId(), session.sessionToken());
        try {
            notifier.onMessage(envelope.payloadAs(MessagePayload.class), responder);
        } catch (Exception e) {
            responder.sendError(ErrorCodes.INVALID_PAYLOAD, e.getMessage(), false);
        }
    }
}
