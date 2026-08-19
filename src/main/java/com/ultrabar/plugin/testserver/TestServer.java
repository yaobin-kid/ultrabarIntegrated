package com.ultrabar.plugin.testserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple Netty test server that implements a minimal "main App" to interact with PluginClient.
 *
 * Behavior:
 * - Listens for newline-delimited JSON envelopes.
 * - Responds to `register` with `register_result` (includes sessionId/sessionToken/heartbeat interval).
 * - Responds to `actions` with `actions_ack`.
 * - After successful register, sends a `call` to the plugin to exercise incoming call handling.
 * - Prints `result` messages received from plugin.
 * - Optionally closes the active connection after N seconds (useful to test client's reconnect/re-register flow).
 */
public class TestServer {
  public static void main(String[] args) throws Exception {
    int port = 39001;
    int closeAfterSeconds = 0; // 0 = never
    if (args.length >= 1) port = Integer.parseInt(args[0]);
    if (args.length >= 2) closeAfterSeconds = Integer.parseInt(args[1]);

    ObjectMapper mapper = new ObjectMapper();
    NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
    NioEventLoopGroup workerGroup = new NioEventLoopGroup(1);
    AtomicReference<Channel> activeChannel = new AtomicReference<>();
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    try {
      ServerBootstrap b = new ServerBootstrap();
      b.group(bossGroup, workerGroup)
       .channel(NioServerSocketChannel.class)
       .childHandler(new ChannelInitializer<SocketChannel>() {
         @Override
         protected void initChannel(SocketChannel ch) {
           ChannelPipeline p = ch.pipeline();
           p.addLast(new LineBasedFrameDecoder(10 * 1024 * 1024));
           p.addLast(new StringDecoder(StandardCharsets.UTF_8));
           p.addLast(new StringEncoder(StandardCharsets.UTF_8));
           p.addLast(new SimpleChannelInboundHandler<String>() {
             @Override
             public void channelActive(ChannelHandlerContext ctx) throws Exception {
               System.out.println("Client connected: " + ctx.channel().remoteAddress());
               activeChannel.set(ctx.channel());
               super.channelActive(ctx);
             }

             @Override
             public void channelInactive(ChannelHandlerContext ctx) throws Exception {
               System.out.println("Client disconnected: " + ctx.channel().remoteAddress());
               activeChannel.set(null);
               super.channelInactive(ctx);
             }

             @Override
             protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
               try {
                 JsonNode node = mapper.readTree(msg);
                 String type = node.has("type") ? node.get("type").asText() : null;
                 String requestId = node.has("requestId") ? node.get("requestId").asText() : null;
                 JsonNode payload = node.has("payload") ? node.get("payload") : node;
                 System.out.println("[server] recv type=" + type + " reqId=" + requestId + " payload=" + payload.toString());

                 if ("register".equals(type)) {
                   // reply register_result
                   ObjectNode regResult = mapper.createObjectNode();
                   regResult.put("success", true);
                   regResult.put("sessionId", "sess-1");
                   regResult.put("sessionToken", "token-1");
                   ObjectNode hb = mapper.createObjectNode();
                   hb.put("interval", 5000);
                   regResult.set("heartbeat", hb);

                   ObjectNode env = mapper.createObjectNode();
                   env.put("type", "register_result");
                   env.put("requestId", requestId);
                   env.put("timestamp", Instant.now().toString());
                   env.set("payload", regResult);
                   String json = mapper.writeValueAsString(env);
                   ctx.channel().writeAndFlush(json + "\n");

                   // after short delay, send a call to plugin to test incoming call handling
                   scheduler.schedule(() -> {
                     try {
                       ObjectNode cp = mapper.createObjectNode();
                       cp.put("action", "music.play");
                       ObjectNode params = mapper.createObjectNode();
                       params.put("device", "speaker-001");
                       params.put("keyword", "晴天");
                       cp.set("params", params);
                       cp.put("idempotencyKey", "srv-call-1");

                       ObjectNode callEnv = mapper.createObjectNode();
                       callEnv.put("type", "call");
                       callEnv.put("requestId", "srv-call-1");
                       callEnv.put("timestamp", Instant.now().toString());
                       callEnv.set("payload", cp);
                       String callJson = mapper.writeValueAsString(callEnv);
                       System.out.println("[server] sending call -> plugin");
                       ctx.channel().writeAndFlush(callJson + "\n");
                     } catch (Exception ex) {
                       ex.printStackTrace();
                     }
                   }, 1, TimeUnit.SECONDS);

                 } else if ("actions".equals(type)) {
                   ObjectNode ack = mapper.createObjectNode();
                   ack.put("success", true);
                   ack.put("receivedCount", payload.has("actions") ? payload.get("actions").size() : 0);
                   ObjectNode env = mapper.createObjectNode();
                   env.put("type", "actions_ack");
                   env.put("requestId", requestId);
                   env.set("payload", ack);
                   ctx.channel().writeAndFlush(mapper.writeValueAsString(env) + "\n");
                 } else if ("heartbeat".equals(type)) {
                   // log and optionally reply with heartbeat_ack
                   System.out.println("[server] heartbeat from session=" + (payload.has("sessionId") ? payload.get("sessionId").asText() : "?"));
                   ObjectNode ack = mapper.createObjectNode();
                   ack.put("ok", true);
                   ObjectNode env = mapper.createObjectNode();
                   env.put("type", "heartbeat_ack");
                   env.put("requestId", requestId);
                   env.set("payload", ack);
                   ctx.channel().writeAndFlush(mapper.writeValueAsString(env) + "\n");
                 } else if ("result".equals(type)) {
                   System.out.println("[server] plugin returned result payload=" + payload.toString());
                 } else {
                   System.out.println("[server] unknown message type=" + type);
                 }

               } catch (Exception e) {
                 e.printStackTrace();
               }
             }

             @Override
             public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
               cause.printStackTrace();
               ctx.close();
             }
           });
         }
       });

      ChannelFuture f = b.bind(port).sync();
      System.out.println("TestServer listening on port " + port);

      if (closeAfterSeconds > 0) {
        // schedule closing the active connection after closeAfterSeconds to simulate server crash
        scheduler.schedule(() -> {
          Channel ch = activeChannel.get();
          if (ch != null && ch.isActive()) {
            System.out.println("[server] closing active connection to simulate crash");
            ch.close();
          }
        }, closeAfterSeconds, TimeUnit.SECONDS);
      }

      f.channel().closeFuture().sync();
    } finally {
      scheduler.shutdownNow();
      workerGroup.shutdownGracefully();
      bossGroup.shutdownGracefully();
    }
  }
}
