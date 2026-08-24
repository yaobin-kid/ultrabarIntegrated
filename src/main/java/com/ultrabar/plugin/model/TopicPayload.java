package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class TopicPayload implements Payload {
    public Topic topic;
    public Object data;



}
