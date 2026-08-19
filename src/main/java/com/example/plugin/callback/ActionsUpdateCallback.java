package com.example.plugin.callback;

import com.fasterxml.jackson.databind.JsonNode;

public interface ActionsUpdateCallback {
  void onUpdate(JsonNode updatePayload);
}
