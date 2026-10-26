package com.ultrabar.plugin.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultrabar.plugin.model.Envelope;
import com.ultrabar.plugin.model.MessageType;
import com.ultrabar.plugin.model.ModuleEventResultPayload;
import io.netty.channel.Channel;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 插件回应对模块事件的消费结果。{@link #consume()} 与 {@link #ignore(String)} 只能调用一次。
 */
public class ModuleEventResponder {
    private final Channel channel;
    private final String requestId;
    private final ObjectMapper mapper;
    private final String sessionId;
    private final String auth;
    private final AtomicBoolean done = new AtomicBoolean();

    public ModuleEventResponder(Channel channel,
                                String requestId,
                                ObjectMapper mapper,
                                String sessionId,
                                String auth) {
        this.channel = channel;
        this.requestId = requestId;
        this.mapper = mapper;
        this.sessionId = sessionId;
        this.auth = auth;
    }

    /**
     * 声明已消费该事件，服务端不再继续分发给其他应用。
     */
    public void consume() {
        send(true, null);
    }

    /**
     * 声明不消费该事件，服务端会继续尝试下一个应用。
     */
    public void ignore(String reason) {
        send(false, reason);
    }

    private void send(boolean consumed, String reason) {
        if (!done.compareAndSet(false, true)) {
            return;
        }
        ModuleEventResultPayload payload = new ModuleEventResultPayload();
        payload.consumed = consumed;
        payload.success = consumed;
        payload.reason = reason;
        EnvelopeWriter.write(channel, mapper,
                Envelope.of(MessageType.MODULE_EVENT_RESULT, requestId, payload)
                        .withSession(sessionId, auth));
    }
}
