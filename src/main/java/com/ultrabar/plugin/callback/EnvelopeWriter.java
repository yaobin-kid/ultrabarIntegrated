package com.ultrabar.plugin.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultrabar.plugin.model.Envelope;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class EnvelopeWriter {
    private static final Logger log = LoggerFactory.getLogger(EnvelopeWriter.class);

    private EnvelopeWriter() {}

    static void write(Channel channel, ObjectMapper mapper, Envelope envelope) {
        try {
            channel.writeAndFlush(mapper.writeValueAsString(envelope) + "\n");
        } catch (Exception e) {
            log.error("failed to write envelope type={}", envelope.getType(), e);
        }
    }
}
