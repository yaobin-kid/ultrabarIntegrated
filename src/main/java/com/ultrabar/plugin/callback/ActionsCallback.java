package com.ultrabar.plugin.callback;

import com.fasterxml.jackson.databind.JsonNode;

public interface ActionsCallback {
  void onSuccess(JsonNode ackPayload);
  void onError(Throwable t);
}
