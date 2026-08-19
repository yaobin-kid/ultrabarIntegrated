package com.ultrabar.plugin.callback;

import com.ultrabar.plugin.model.CallPayload;

/**
 * Handler invoked when the main App sends a `call` message to this plugin (client).
 * Implement this interface to process incoming calls.
 */
public interface CallHandler {
  /**
   * Called when a `call` request is received.
   * @param payload the CallPayload sent by server
   * @param responder responder to send result or error back to server
   */
  void onCall(CallPayload payload, CallResponder responder);
}
