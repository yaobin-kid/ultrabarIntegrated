package com.ultrabar.plugin.model;

public enum Features {
    SERVER_CALL(1), // 服务端 CALL
    SERVER_OPEN_ACTIVITY(2), //服务端调用 open activity 动作

    CLIENT_REPORT(4), //客户端上报数据


    EVENT_REPORT(10), //客户端上报的事件

    ;
    private final int code;

    Features(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
