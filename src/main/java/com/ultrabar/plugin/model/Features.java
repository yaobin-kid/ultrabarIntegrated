package com.ultrabar.plugin.model;

public enum Features {
    CALL(1), // 服务端 CALL
    OPEN_ACTIVITY(2), //服务端调用 open activity 动作
    REPORT_DATA(4), //客户端上报数据
    EVENT(8), //客户端上报的事件

    ;
    private final int code;

    Features(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
