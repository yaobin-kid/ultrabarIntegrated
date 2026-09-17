package com.ultrabar.plugin.model;

import java.util.Map;

public class EventPayload implements Payload {
    public String eventId;
    public long timestamp;
    public Map<String, Object> params;

}
