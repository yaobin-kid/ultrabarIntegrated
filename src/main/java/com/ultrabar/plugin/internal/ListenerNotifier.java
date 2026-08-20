package com.ultrabar.plugin.internal;

import com.ultrabar.plugin.callback.PluginListener;
import com.ultrabar.plugin.model.ActionsAckPayload;
import com.ultrabar.plugin.model.ActionsPayload;
import com.ultrabar.plugin.model.CallPayload;
import com.ultrabar.plugin.model.DescribePayload;
import com.ultrabar.plugin.model.GetOptionsPayload;
import com.ultrabar.plugin.model.RegisterResultPayload;
import com.ultrabar.plugin.callback.CallResponder;
import com.ultrabar.plugin.callback.DescribeResponder;
import com.ultrabar.plugin.callback.OptionsResponder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class ListenerNotifier {
    private static final Logger log = LoggerFactory.getLogger(ListenerNotifier.class);

    private final Executor executor;
    private volatile PluginListener listener;

    public ListenerNotifier(Executor executor) {
        this.executor = executor;
    }

    public void setListener(PluginListener listener) {
        this.listener = listener;
    }

    void onRegisterSuccess(final RegisterResultPayload payload) {
        emit(new ListenerAction() {
            @Override
            public void run(PluginListener l) {
                l.onRegisterSuccess(payload);
            }
        });
    }

    void onRegisterFailed(final Throwable error) {
        emit(new ListenerAction() {
            @Override
            public void run(PluginListener l) {
                l.onRegisterFailed(error);
            }
        });
    }

    void onActionsAck(final ActionsAckPayload ack) {
        emit(new ListenerAction() {
            @Override
            public void run(PluginListener l) {
                l.onActionsAck(ack);
            }
        });
    }

    void onActionsFailed(final Throwable error) {
        emit(new ListenerAction() {
            @Override
            public void run(PluginListener l) {
                l.onActionsFailed(error);
            }
        });
    }

    void onActionsUpdate(final ActionsPayload update) {
        emit(new ListenerAction() {
            @Override
            public void run(PluginListener l) {
                l.onActionsUpdate(update);
            }
        });
    }

    void onDescribe(final DescribePayload payload, final DescribeResponder responder) {
        PluginListener current = listener;
        if (current == null) {
            responder.sendError("NO_HANDLER", "No PluginListener registered", false, null);
            return;
        }
        emitHandled(current, responderGuard(responder), new ListenerAction() {
            @Override
            public void run(PluginListener l) {
                l.onDescribe(payload, responder);
            }
        });
    }

    void onCall(final CallPayload payload, final CallResponder responder) {
        PluginListener current = listener;
        if (current == null) {
            responder.sendError("NO_HANDLER", "No PluginListener registered", false, null);
            return;
        }
        emitHandled(current, new ErrorSink() {
            @Override
            public void sendError(String code, String message) {
                responder.sendError(code, message, false, null);
            }
        }, new ListenerAction() {
            @Override
            public void run(PluginListener l) {
                l.onCall(payload, responder);
            }
        });
    }

    void onOptions(final GetOptionsPayload payload, final OptionsResponder responder) {
        PluginListener current = listener;
        if (current == null) {
            responder.sendError("NO_HANDLER", "No PluginListener registered", false, null);
            return;
        }
        emitHandled(current, new ErrorSink() {
            @Override
            public void sendError(String code, String message) {
                responder.sendError(code, message, false, null);
            }
        }, new ListenerAction() {
            @Override
            public void run(PluginListener l) {
                l.onOptions(payload, responder);
            }
        });
    }

    private ErrorSink responderGuard(final DescribeResponder responder) {
        return new ErrorSink() {
            @Override
            public void sendError(String code, String message) {
                responder.sendError(code, message, false, null);
            }
        };
    }

    private void emitHandled(final PluginListener current, final ErrorSink errors, final ListenerAction action) {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    action.run(current);
                } catch (Exception e) {
                    log.error("plugin listener failed", e);
                    errors.sendError("HANDLER_EXCEPTION", e.getMessage());
                }
            }
        };
        submit(task);
    }

    private void emit(final ListenerAction action) {
        final PluginListener current = listener;
        if (current == null) {
            return;
        }
        submit(new Runnable() {
            @Override
            public void run() {
                try {
                    action.run(current);
                } catch (Exception e) {
                    log.error("plugin listener failed", e);
                }
            }
        });
    }

    private void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            task.run();
        }
    }

    private interface ListenerAction {
        void run(PluginListener listener) throws Exception;
    }

    private interface ErrorSink {
        void sendError(String code, String message);
    }
}
