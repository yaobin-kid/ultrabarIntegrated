package com.ultrabar.plugin.callback;

import com.ultrabar.plugin.callback.CallResponder;
import com.ultrabar.plugin.model.ActionsAckPayload;
import com.ultrabar.plugin.model.ActionsPayload;
import com.ultrabar.plugin.model.CallPayload;
import com.ultrabar.plugin.model.DescribeResultPayload;
import com.ultrabar.plugin.model.RegisterResultPayload;
import com.ultrabar.plugin.model.DescribePayload;

/**
 * Single global listener interface for plugin events.
 * Implement this to receive register/actions/describe failures and incoming calls.
 */
public interface PluginListener {
  void onRegisterSuccess(RegisterResultPayload payload);
  void onRegisterFailed(Throwable t);
  void onActionsFailed(Throwable t);
  void onActionsAck(ActionsAckPayload ack);
  void onActionsUpdate(ActionsPayload update);

  // outbound describe completion (SDK-initiated describe) - typed result
  void onDescribeSuccess(DescribeResultPayload result);
  void onDescribeError(Throwable t);

  // incoming describe from server -> plugin should respond via responder (similar to call)
  void onDescribe(DescribePayload payload, DescribeResponder responder);

  // incoming call from server -> plugin should respond via responder
  void onCall(CallPayload payload, CallResponder responder);
}
