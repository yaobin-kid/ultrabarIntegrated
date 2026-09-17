package com.ultrabar.plugin.model;

import com.fasterxml.jackson.databind.util.JSONPObject;
import com.ultrabar.server.PluginRegisterHandler;
import com.ultrabar.server.PluginServer;
import com.ultrabar.server.PluginServerListener;
import com.ultrabar.server.PluginSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Starts the plugin protocol server (default 127.0.0.1:39001).
 * <p>
 * After a plugin registers and reports actions, invoke it with:
 * {@code server.call("music.play", params)} or
 * {@code server.call("com.ultrabar.music", "music.play", params)}.
 */
public final class ServerMain {
    private static final Logger log = LoggerFactory.getLogger(ServerMain.class);

    public static void main(String[] args) throws Exception {
        final PluginServer server = new PluginServer();
        server.setRegisterHandler(new PluginRegisterHandler() {
            @Override
            public RegisterResultPayload handleRegister(RegisterPayload request) {
                log.info("register request packageName={} name={}", request.packageName, request.name);
                RegisterResultPayload result = new RegisterResultPayload();
                result.success = true;
                result.configServer = new ConfigServer();
                result.configServer.port = 100;
                result.sessionToken = UUID.randomUUID().toString();

                return result;
            }


        });


        server.setListener(new PluginServerListener() {
            @Override
            public void onRegistered(PluginSession session) {
                log.info("plugin online packageName={} name={}", session.packageName(), session.plugin().name);
            }

            @Override
            public void onActionsUpdated(PluginSession session) {
                log.info("plugin actions packageName={} count={}", session.packageName(), session.actions().size());

                System.out.println("动作注册成功");

                server.publish(session.packageName(), Topic.CPU, "112")
                        .thenAccept(c -> {
                            System.out.println("发布状态:" + c.success);
                        });


              /*  server.getDescribe("com.ultrabar.music", "music.play")
                        .thenAccept(c -> {
                            try {
                                System.out.println("接受到订阅数据:" + Json.mapper().writeValueAsString(c.parameters));
                            } catch (Exception e) {
                                e.getMessage();
                            }

                        });
*/

               /* server.getOptions("com.ultrabar.music", "music.play",
                                "deviceId", null, 0, 100, null)
                        .thenAccept(c -> {
                            try {
                                System.out.println("getOptions:" + Json.mapper().writeValueAsString(c));
                            } catch (Exception e) {
                                e.getMessage();
                            }
                        });*/
            }

            @Override
            public void onReport(PluginSession session, ReportPayload payload) {
                try {
                    System.out.println("接受到上报数据了:" + Json.mapper().writeValueAsString(payload));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onUnregistered(PluginSession session) {
                log.info("plugin offline packageName={}", session.packageName());
            }

            @Override
            public void onEventReceived(PluginSession session, EventPayload event) {
                log.info("onEventReceived :{}", event.eventId);
            }
        });


        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                server.stop();
            }
        }, "ultrabar-server-shutdown"));
        Thread.currentThread().join();
    }
}
