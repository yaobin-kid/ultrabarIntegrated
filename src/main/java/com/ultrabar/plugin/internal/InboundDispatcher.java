package com.ultrabar.plugin.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultrabar.plugin.callback.CallResponder;
import com.ultrabar.plugin.callback.DescribeResponder;
import com.ultrabar.plugin.callback.OptionsResponder;
import com.ultrabar.plugin.model.ActionsPayload;
import com.ultrabar.plugin.model.CallPayload;
import com.ultrabar.plugin.model.DescribePayload;
import com.ultrabar.plugin.model.Envelope;
import com.ultrabar.plugin.model.ErrorCodes;
import com.ultrabar.plugin.model.GetOptionsPayload;
import com.ultrabar.plugin.model.MessageType;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        OptionsResponder responder = new OptionsResponder(
                channel, envelope.getRequestId(), mapper, session.sessionId(), session.sessionToken());
        try {
            notifier.onOptions(envelope.payloadAs(GetOptionsPayload.class), responder);
        } catch (Exception e) {
            responder.sendError(ErrorCodes.INVALID_PAYLOAD, e.getMessage(), false, null);
        }
    }
}
