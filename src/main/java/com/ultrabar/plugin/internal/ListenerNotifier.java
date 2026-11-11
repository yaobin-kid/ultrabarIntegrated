package com.ultrabar.plugin.internal;

import com.ultrabar.plugin.callback.PluginListener;
import com.ultrabar.plugin.model.*;
import com.ultrabar.plugin.callback.A2UIActionCallResponder;
import com.ultrabar.plugin.callback.A2UIRenderResponder;
import com.ultrabar.plugin.callback.CallResponder;
import com.ultrabar.plugin.callback.DescribeResponder;
import com.ultrabar.plugin.callback.MessageResponder;
import com.ultrabar.plugin.callback.ModuleEventResponder;
import com.ultrabar.plugin.callback.OptionsResponder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
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

    void onActionsAck(final ActionsResultPayload ack) {
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
            responder.sendError(ErrorCodes.NO_HANDLER, "No PluginListener registered", false, null);
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
            responder.sendError(ErrorCodes.NO_HANDLER, "No PluginListener registered", false, null);
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

    void onModuleEvent(final ModuleEventPayload payload, final ModuleEventResponder responder) {
        PluginListener current = listener;
        if (current == null) {
            responder.ignore("No PluginListener registered");
            return;
        }
        emitHandled(current, new ErrorSink() {
            @Override
            public void sendError(String code, String message) {
                responder.ignore(message);
            }
        }, new ListenerAction() {
            @Override
            public void run(PluginListener l) {
                l.onModuleEvent(payload, responder);
            }
        });
    }

    void onOptions(final GetOptionsPayload payload, final OptionsResponder responder) {
        PluginListener current = listener;
        if (current == null) {
            responder.sendError(ErrorCodes.NO_HANDLER, "No PluginListener registered", false, null);
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

    void onA2UIActionCall(final A2UIActionCallPayload payload, final A2UIActionCallResponder responder) {
        PluginListener current = listener;
        if (current == null) {
            responder.sendError(ErrorCodes.NO_HANDLER, "No PluginListener registered", false);
            return;
        }
        emitHandled(current, new ErrorSink() {
            @Override
            public void sendError(String code, String message) {
                responder.sendError(code, message, false);
            }
        }, new ListenerAction() {
            @Override
            public void run(PluginListener l) {
                l.onA2UIActionCall(payload, responder);
            }
        });
    }

    void onA2UIRender(final A2UIRenderPayload payload, final A2UIRenderResponder responder) {
        PluginListener current = listener;
        if (current == null) {
            responder.sendError(ErrorCodes.NO_HANDLER, "No PluginListener registered", false);
            return;
        }
        emitHandled(current, new ErrorSink() {
            @Override
            public void sendError(String code, String message) {
                responder.sendError(code, message, false);
            }
        }, new ListenerAction() {
            @Override
            public void run(PluginListener l) {
                l.onA2UIRender(payload, responder);
            }
        });
    }

    void onMessage(final MessagePayload payload, final MessageResponder responder) {
        PluginListener current = listener;
        if (current == null) {
            responder.sendError(ErrorCodes.NO_HANDLER, "No PluginListener registered", false);
            return;
        }
        emitHandled(current, new ErrorSink() {
            @Override
            public void sendError(String code, String message) {
                responder.sendError(code, message, false);
            }
        }, new ListenerAction() {
            @Override
            public void run(PluginListener l) {
                l.onMessage(payload, responder);
            }
        });
    }

    /**
     * 同步调用 {@link PluginListener#onA2UIDataRequest()} 并返回结果。
     * 必须在调用线程同步执行，以便 InboundDispatcher 立即回包。
     */
    List<A2UIItem> onA2UIDataRequest() {
        PluginListener current = listener;
        if (current == null) {
            return Collections.emptyList();
        }
        try {
            List<A2UIItem> result = current.onA2UIDataRequest();
            return result == null ? Collections.<A2UIItem>emptyList() : result;
        } catch (Exception e) {
            log.error("onA2UIDataRequest listener failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * 同步调用 {@link PluginListener#onA2UIArgsSchemaRequest(String)} 并返回结果。
     * 必须在调用线程同步执行，以便 InboundDispatcher 立即回包。
     * 插件未处理时返回 {@code null}，由服务端归一化为空 {@link A2UIArgs}。
     */
    A2UIArgs onA2UIArgsSchemaRequest(String schemaGetter) {
        PluginListener current = listener;
        if (current == null) {
            return null;
        }
        try {
            return current.onA2UIArgsSchemaRequest(schemaGetter);
        } catch (Exception e) {
            log.error("onA2UIArgsSchemaRequest listener failed", e);
            return null;
        }
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
                    errors.sendError(ErrorCodes.HANDLER_EXCEPTION, e.getMessage());
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
