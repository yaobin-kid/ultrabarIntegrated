package com.ultrabar.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultrabar.plugin.model.Envelope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ServerChannelHandler extends SimpleChannelInboundHandler<String> {
    private static final Logger log = LoggerFactory.getLogger(ServerChannelHandler.class);

    private final PluginServer server;
    private final ObjectMapper mapper;

    ServerChannelHandler(PluginServer server, ObjectMapper mapper) {
        this.server = server;
        this.mapper = mapper;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("plugin connected {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("plugin disconnected {}", ctx.channel().remoteAddress());
        server.onInactive(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        try {
            Envelope envelope = mapper.readValue(msg, Envelope.class);
            server.onMessage(ctx.channel(), envelope);
        } catch (Exception e) {
            log.error("invalid envelope from {}", ctx.channel().remoteAddress(), e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("channel error {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
