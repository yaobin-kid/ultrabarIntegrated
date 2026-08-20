package com.ultrabar.plugin.model;

import com.ultrabar.server.PluginRegisterHandler;
import com.ultrabar.server.PluginServer;
import com.ultrabar.server.PluginServerListener;
import com.ultrabar.server.PluginSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        int port = 39001;
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }
        final PluginServer server = new PluginServer(port);
        server.setRegisterHandler(new PluginRegisterHandler() {
            @Override
            public RegisterResultPayload handleRegister(RegisterPayload request) {
                log.info("register request packageName={} name={} version={}",
                        request.packageName, request.name, request.version);
                RegisterResultPayload result = new RegisterResultPayload();
                result.success = true;
                return result;
            }
        });
        server.setListener(new PluginServerListener() {
            @Override
            public void onRegistered(PluginSession session) {
                log.info("plugin online packageName={} name={}",  session.packageName(), session.plugin().name);
            }

            @Override
            public void onActionsUpdated(PluginSession session) {
                log.info("plugin actions packageName={} count={}",
                        session.packageName(), session.actions().size());
            }

            @Override
            public void onUnregistered(PluginSession session) {
                log.info("plugin offline packageName={}", session.packageName());
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
