package com.ultrabar.plugin.model;

public class TopicResultPayload extends ReplyPayload  {
    public Topic topic;

    public TopicResultPayload(Topic topic) {
        this.topic = topic;
        this.success = true;
    }

    public TopicResultPayload() {
    }
}
