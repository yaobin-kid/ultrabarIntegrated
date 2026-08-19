package com.example.plugin.callback;

import com.fasterxml.jackson.databind.JsonNode;

public interface RegisterCallback {
  void onSuccess(JsonNode registerResultPayload);
  void onError(Throwable t);
}
