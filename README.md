# Ultrabar Plugin SDK — Usage Guide

> Language: [中文](./README_zh.md) | **English** | [日本語](./README_ja.md)

> Applicable version: ultrabarIntegrated 1.0.12+ (protocol version = 2)
> Audience: Third-party plugin developers integrating with LineOS (Ultrabar)
> Prerequisites: Familiarity with Android Service and Gradle dependency configuration

---

## Table of Contents

1. [SDK Overview](#1-sdk-overview)
2. [Quick Start](#2-quick-start)
3. [Core Concepts & Communication Model](#3-core-concepts--communication-model)
4. [Scenario Application Ability (Action Registration & Execution)](#4-scenario-application-ability-action-registration--execution)
5. [A2UI Generative UI](#5-a2ui-generative-ui)
6. [Module Events (DOT_KEY / KNOB_KEY)](#6-module-events-dot_key--knob_key)
7. [Hotkeys & Messages](#7-hotkeys--messages)
8. [Callback Quick Reference](#8-callback-quick-reference)
9. [Notes & FAQ](#9-notes--faq)

---

## 1. SDK Overview

The Ultrabar Plugin SDK is built on Netty for bidirectional communication between a plugin app and LineOS. The transport protocol is **one UTF-8 JSON per line** (framed by `\n`); all requests/responses are asynchronous (`CompletableFuture`).

| Role | Class | Responsibility |
| ---- | ----- | -------------- |
| Plugin side (your app) | `com.ultrabar.plugin.PluginClient` | Register, report Actions, handle describe / get_options / call, report A2UI data |
| LineOS side | `com.ultrabar.server.PluginServer` | Manage plugin sessions, invoke plugins, cache A2UI data |

**As a third-party developer you only need to integrate `PluginClient`.** LineOS periodically scans the plugin services declared in `AndroidManifest.xml` and starts them automatically (skips if already running).

The plugin SDK provides four major capabilities:

1. **Scenario application ability**: The plugin declares to LineOS "what I can do" (e.g., play, pause), and LineOS calls the plugin back to execute when a user triggers it.
2. **A2UI generative UI**: The plugin describes the UI structure and data in JSON, LineOS renders it as a native interface, and user interactions are passed back to the plugin.
3. **Module events**: Receive real-time events from LineOS hardware modules (DOT_KEY buttons, KNOB_KEY knobs, etc.).
4. **Hotkeys & messages**: Inject keystrokes into the PC (single key / combos / long-press), or pass messages bi-directionally with LineOS (supports strings and structured objects).

---

## 2. Quick Start

### 2.1 Add Dependencies

`build.gradle`:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.yaobin-kid:ultrabarIntegrated:1.0.12'
    implementation "io.netty:netty-all:4.1.94.Final"
    implementation "com.fasterxml.jackson.core:jackson-databind:2.15.2"
    implementation "org.slf4j:slf4j-simple:2.0.7"
}

android {
    packagingOptions {
        exclude 'META-INF/INDEX.LIST'
        exclude 'META-INF/io.netty.versions.properties'
    }
}
```

> ⚠️ The two `exclude` entries in `packagingOptions` are **mandatory** — otherwise Netty resource conflicts will break the build.

### 2.2 Configure AndroidManifest

Declare a background service through which LineOS discovers and launches your plugin:

```xml
<manifest ...>
    <!-- Network permission (also needed for loopback communication) -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application ...>
        <service
            android:name=".service.BackgroundService"
            android:enabled="true"
            android:exported="true"
            android:permission="com.ultrabar.plugin.SERVER_REGISER_PERMISSION">
            <!-- value must equal your applicationId -->
            <meta-data android:name="ultrabar.plugin" android:value="com.example.music" />
        </service>
    </application>
</manifest>
```

**Three mandatory requirements:**

| Configuration | Description |
| ------------- | ----------- |
| `meta-data android:value` | Must equal `applicationId`; LineOS uses it as the plugin's unique identifier (packageName) |
| `android:exported="true"` | Must be exported, otherwise LineOS cannot launch the service |
| `android:permission` | Must be `com.ultrabar.plugin.SERVER_REGISER_PERMISSION` to prevent other apps from impersonating it |

### 2.3 Minimal Runnable Demo

Initialise and start `PluginClient` inside the service's `onCreate()`:

```java
public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    private static final String CHANNEL_ID = "plugin_bg_channel";

    private PluginClient client;

    @Override
    public void onCreate() {
        super.onCreate();

        // Android 8.0+ foreground service requirement: notification channel + startForeground
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Plugin service running")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build();
        startForeground(1001, notification);

        startPluginClient();
    }

    private void startPluginClient() {
        client = new PluginClient(); // default 127.0.0.1:39001

        // 1. Registration info: name is the display name, packageName must equal applicationId
        RegisterPayload rp = new RegisterPayload();
        rp.name = "Example Music Plugin";
        rp.packageName = "com.example.music";
        client.setRegisterConfig(rp);

        // 2. Declare the supported Action list
        ActionSummary play = new ActionSummary();
        play.actionId = "music.play";
        play.name = "Play";
        play.description = "Play music on the specified device";

        ActionSummary pause = new ActionSummary();
        pause.actionId = "music.pause";
        pause.name = "Pause";
        pause.description = "Pause playback on the specified device";

        client.setActionsConfig(new ActionsPayload(Arrays.asList(play, pause)));

        // 3. Set the listener (all callbacks are optional overrides)
        client.setPluginListener(new PluginListener() {
            @Override
            public void onRegisterSuccess(RegisterResultPayload result) {
                Log.d(TAG, "Registered, debug port: " + result.configServer.port);
            }

            @Override
            public void onRegisterFailed(Throwable t) {
                Log.e(TAG, "Registration failed: " + t.getMessage());
            }

            @Override
            public void onCall(CallPayload call, CallResponder responder) {
                if ("music.play".equals(call.actionId)) {
                    // perform playback...
                }
                responder.sendSuccess(null);
            }
        });

        // 4. Async start: connect → register → report Actions, fully automatic
        client.startAsync();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Plugin Background Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        if (client != null) client.stop();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
```

With these three steps, your plugin can be discovered by LineOS and communicate with it.

---

## 3. Core Concepts & Communication Model

### 3.1 Connection Lifecycle

```
startAsync()
   │
   ▼
TCP connect (127.0.0.1:39001)
   │
   ▼
REGISTER ─────────► LineOS validates packageName
   ◄───────── REGISTER_RESULT (sessionId / sessionToken / configServer.port)
   │
   ▼
ACTIONS ──────────► report action list
   ◄───────── ACTIONS_RESULT (success / receivedCount)
   │
   ▼
session ready ◄──── onRegisterSuccess fired
   │
   ▼
heartbeat keep-alive (auto) + auto reconnect (re-registers and re-reports Actions on reconnect)
```

**Key points:**

- `startAsync()` returns `CompletableFuture<Void>`, completing as soon as the connection is established; registration results are notified through the `onRegisterSuccess` / `onRegisterFailed` callbacks.
- The SDK has built-in heartbeat and auto-reconnect. **After a successful reconnection, it automatically re-registers and re-reports Actions — no manual handling needed.**
- `payload.configServer.port` in `onRegisterSuccess` is the HTTP debug port assigned by LineOS. If you want to provide a plugin debug page, start your HTTP server on this port.

### 3.2 sessionToken Security

`onRegisterSuccess` delivers a `sessionToken` — the identity credential for the current debug session. LineOS carries it when opening your debug page.

> ⚠️ **You must validate the token's validity yourself**; do not blindly trust any request that carries a token.

### 3.3 Threading Model

All `PluginListener` callbacks run on the SDK's callback thread pool (not the Android main thread). **When you need to touch the UI, switch to the main thread yourself:**

```java
new Handler(Looper.getMainLooper()).post(() -> {
    // UI operations
});
```

### 3.4 Session Uniqueness

LineOS identifies a plugin session uniquely by `packageName`. Re-registering with the same `packageName` replaces the old connection.

---

## 4. Scenario Application Ability (Action Registration & Execution)

An Action is a "capability point" the plugin exposes to LineOS. The full chain has four steps:

```
plugin reports actionId list ──► LineOS asks for parameter descriptions (onDescribe)
   ──► (optional) LineOS pulls dynamic options (onOptions) ──► after user trigger LineOS calls back to execute (onCall)
```

### 4.1 Register Actions

```java
ActionSummary play = new ActionSummary();
play.actionId = "music.play";        // globally unique ID, recommended "domain.verb" format
play.name = "Play";                  // LineOS display name
play.description = "Play music on the specified device";

client.setActionsConfig(new ActionsPayload(Arrays.asList(play, pause, stop)));
```

When the action list changes at runtime, call:

```java
client.updateActions(new ActionsPayload(newList));
```

The report result is notified via `onActionsAck(ActionsResultPayload ack)`:

```java
@Override
public void onActionsAck(ActionsResultPayload ack) {
    // ack.success        — whether LineOS received it successfully
    // ack.receivedCount  — actual number of actions received
    // note: this is the "config sync" ACK, not the onCall execution result
}
```

### 4.2 Parameter Description onDescribe

Before invoking an Action, LineOS first obtains the parameters the action needs via `onDescribe` (used to render the configuration UI):

```java
@Override
public void onDescribe(DescribePayload payload, DescribeResponder responder) {
    DescribeResultPayload result = new DescribeResultPayload();
    result.actionId = payload.actionId;
    result.success = true;
    result.parameters = new ArrayList<>();

    if ("music.play".equals(payload.actionId)) {
        // parameter 1: device selection (static options)
        DescribeResultPayload.ParameterSpec device = new DescribeResultPayload.ParameterSpec();
        device.id = "deviceId";            // ★ key: use this id to get the value in onCall
        device.name = "Device";
        device.placeholder = "Select a device";
        device.required = true;
        device.type = ParameterType.SELECT;

        DescribeResultPayload.OptionSpec deviceOpts = new DescribeResultPayload.OptionSpec();
        deviceOpts.provider = OptionProvider.STATIC;   // static options, delivered with the description
        deviceOpts.searchable = false;
        deviceOpts.items = new ArrayList<>();
        deviceOpts.items.add(new Label("Living room player", "test01"));
        deviceOpts.items.add(new Label("Bedroom player", "test02"));
        device.options = deviceOpts;

        // parameter 2: song title (text input)
        DescribeResultPayload.ParameterSpec title = new DescribeResultPayload.ParameterSpec();
        title.id = "title";
        title.name = "Song title";
        title.placeholder = "Enter song title";
        title.required = true;
        title.type = ParameterType.TEXT;

        // parameter 3: input source (dynamic options, depends on deviceId)
        DescribeResultPayload.ParameterSpec source = new DescribeResultPayload.ParameterSpec();
        source.id = "source";
        source.name = "Source";
        source.required = true;
        source.type = ParameterType.SELECT;
        DescribeResultPayload.OptionSpec sourceOpts = new DescribeResultPayload.OptionSpec();
        sourceOpts.provider = OptionProvider.REMOTE;   // dynamic options, triggers onOptions callback
        sourceOpts.searchable = false;
        source.options = sourceOpts;
        source.dependsOn = new ArrayList<>();
        source.dependsOn.add("deviceId");              // dependency: re-pull when deviceId changes

        result.parameters.add(device);
        result.parameters.add(title);
        result.parameters.add(source);
    }

    responder.sendSuccess(result);
}
```

### 4.3 Dynamic Options onOptions

LineOS only calls back this method to fetch options when a parameter declares `OptionProvider.REMOTE`:

```java
@Override
public void onOptions(GetOptionsPayload payload, OptionsResponder responder) {
    GetOptionsResultPayload result = new GetOptionsResultPayload();
    result.success = true;
    result.hasMore = false;
    result.nextCursor = null;

    if ("music.play".equals(payload.actionId) && "source".equals(payload.describeId)) {
        result.items = new ArrayList<>();
        result.items.add(new Label("QQ Music", "qq"));
        result.items.add(new Label("NetEase Music", "163"));
    }

    responder.sendSuccess(result);
}
```

- `payload.actionId`: the current Action; `payload.describeId`: the current parameter id.
- For large option lists, use `hasMore = true` + `nextCursor` for pagination; LineOS passes `cursor` in the request when paging.

### 4.4 Execution Callback onCall

Called back here after a user triggers the Action in LineOS:

```java
@Override
public void onCall(CallPayload payload, CallResponder responder) {
    Map<String, Object> params = payload.params;

    if ("music.play".equals(payload.actionId)) {
        String deviceId = (String) params.get("deviceId"); // key must match ParameterSpec.id
        String title = (String) params.get("title");
        String source = (String) params.get("source");
        // TODO: execute the real playback logic
    }

    // return value (optional), LineOS receives this Map
    Map<String, Object> data = new HashMap<>();
    data.put("status", "ok");
    responder.sendSuccess(data);

    // on failure:
    // responder.sendError("PLAY_FAILED", "Device offline", false);
}
```

> ⚠️ **The keys in `params` must exactly match the `ParameterSpec.id` defined in `onDescribe`** — this is an implicit contract between the two sides.

---

## 5. A2UI Generative UI

A2UI lets the plugin describe the UI in JSON, rendered by LineOS as a native interface:

- **Plugin → LineOS**: report UI structure (surface creation / component tree), update data model
- **LineOS → Plugin**: user interactions (button clicks, etc.) passed back as actions

> 📖 **Important: protocol and rendering details follow the original library documentation**
>
> This SDK (ultrabarIntegrated) only handles the **A2UI message transport channel between the plugin and LineOS**. The A2UI JSON format specification, component catalog, data binding syntax, and Action semantics are implemented and defined by the rendering library **[a2ui-android](https://github.com/yaobin-kid/a2ui-android)** integrated into LineOS.
>
> **Before writing A2UI JSON, be sure to read the original library docs: [https://github.com/yaobin-kid/a2ui-android](https://github.com/yaobin-kid/a2ui-android)**
>
> - Protocol version: **A2UI v0.9.1**
> - Official spec: [A2UI v0.9.1 Specification](https://a2ui.org/specification/v0.9-a2ui/)
> - Basic Catalog contains **18 component types**: layout (Row, Column, List, Card, Tabs, Modal), display (Text, Image, Icon, Video, AudioPlayer, Divider), input (Button, TextField, CheckBox, ChoicePicker, Slider, DateTimeInput)

> ⚠️ **Protocol version requirement**: All A2UI JSON must be in **v0.9.1** format, and every envelope must carry the `"version": "v0.9.1"` field. Do not use v0.8 message names such as `beginRendering` / `surfaceUpdate` / `dataModelUpdate`.

### 5.1 Enable A2UI

Declare `supportsA2UI = true` during registration:

```java
RegisterPayload rp = new RegisterPayload();
rp.name = "Example Plugin";
rp.packageName = "com.example.music";
rp.supportsA2UI = true;   // ★ the key switch
client.setRegisterConfig(rp);
```

**Consequence of not declaring:** LineOS calls to `callA2UIAction` / `queryA2UIData` / `notifyA2UIRender` will be rejected by the server; but the plugin's own active data reporting to LineOS is unaffected.

### 5.2 A2UI Message Type Reference

| Envelope key | Direction | SDK API | Purpose |
| ------------ | --------- | ------- | ------- |
| `createSurface` | Plugin→LineOS | `refreshA2UI` | Create a UI surface |
| `updateComponents` | Plugin→LineOS | `refreshA2UI` | Define / update component tree |
| `updateDataModel` | Plugin→LineOS | `sendA2UIUpdateDataModel` | Write / update / delete data model |
| `deleteSurface` | Plugin→LineOS | `refreshA2UI` | Delete a surface |
| `action` (event) | LineOS→Plugin | callback `onA2UIActionCall` | User interaction callback |
| Render notification | LineOS→Plugin | callback `onA2UIRender` | Card first-render / refresh notification |

### 5.3 Refresh an Existing View refreshA2UI

`refreshA2UI` is used to refresh an **already existing** A2UI view: locate the rendered view instance by `instanceId + layoutId`, and replace its content with new `ui` data.
(The card's initial definition is pulled by the server-side `queryA2UIData`, and the plugin returns the full `A2UIItem` in `onA2UIDataRequest` — see §5.4.)

```java
// A2UIRefreshItem: instanceId (instance ID) + layoutId (layout ID) + ui (A2UI message list)
// All 3 parameters must be provided at construction; ui is a JSON array (List<Map<String, Object>>):
//   [{createSurface...}, {updateComponents...}, {updateDataModel}...]
// ui supports overloads: pass a structured array directly, or a JSON string parsed by the SDK
// (a single JSON object string is also auto-wrapped into a single-element array)
String ui = "["
    + "{\"version\":\"v0.9.1\",\"updateComponents\":{"
    + "   \"surfaceId\":\"example-focus\","
    + "   \"components\":["
    + "     {\"id\":\"root\",\"component\":\"Card\",\"child\":\"content\"},"
    + "     {\"id\":\"content\",\"component\":\"Text\","
    + "      \"text\":{\"path\":\"/headline\"},\"variant\":\"h3\"}"
    + "   ]}}"
    + "]";

// Approach 1: string (auto-parsed to JSON array internally)
client.refreshA2UI(new A2UIRefreshItem("inst-example", "example-focus", ui))
      .thenAccept(ack -> Log.d(TAG, "Refresh result: " + ack.success));

// Approach 2: build a structured array directly (recommended, avoids string concatenation errors)
Map<String, Object> updateComponents = new HashMap<>();
// ... fill in the A2UI message structure
client.refreshA2UI(new A2UIRefreshItem("inst-example", "example-focus", uiList));

// batch-refresh multiple views:
// client.refreshA2UI(Arrays.asList(item1, item2));
```

After receiving it, the server triggers the `PluginServerListener.onA2UIRefresh(session, payload)` callback; refresh is a local content replacement and **does not** overwrite the full card definition cached in `PluginSession` (`a2uiData`).

**Writing notes (see the original library docs for details):**

- The root component ID **must be `root`**; components reference each other by ID (e.g. `Card.child = "content"`)
- `catalogId` uses the v0.9.1 Basic Catalog: `https://a2ui.org/specification/v0_9_1/catalogs/basic/catalog.json`
- `createSurface.theme.primaryColor` is the v0.9.1 theme field (`#RRGGBB` / `#AARRGGBB`), applied to Icon, Button, Tabs, and the selected/focused state of input controls; defaults to `#3F51B5`
- `createSurface.sendDataModel = true` means the whole dataModel snapshot is carried back on interaction

**Recommended timing:** Report the current UI snapshot in the `onRegisterSuccess` callback to ensure LineOS gets the latest state.

### 5.4 Data Binding (Overview)

Each Surface internally maintains a JSON object `dataModel`. A component's dynamic properties are bound to dataModel fields via `{ "path": "/xxx" }`, and bound components automatically re-render after `updateDataModel`.

**path follows [RFC 6901 JSON Pointer](https://tools.ietf.org/html/rfc6901):**

| Expression | Meaning |
| ---------- | ------- |
| `"/message"` | `message` field of the root object |
| `"/user/name"` | `name` field under the `user` object |
| `"/items/0/title"` | `title` of item 0 in the `items` array |
| `"/items/-"` | Append position at the end of the array (write only) |

Escaping rules: write `~` as `~0`, `/` as `~1`.

**Two binding notations (note the usage distinction):**

| Notation | Form | Applicable location |
| -------- | ---- | ------------------- |
| Object form | `{ "path": "/form/email" }` | Component properties (`Text.text`, `Image.url`, `Slider.value`, etc.) |
| String shorthand | `"#/form/email"` | **Only** in `action.event.context` and `action.functionCall.args` |

**List dynamic rendering:** `Row`/`Column`/`List`'s `children` supports `{ "path": "/arrayPath", "componentId": "templateComponentId" }`; the engine iterates the array and generates a template instance for each item; inside the template component you can write **relative paths** (without `/`), which are automatically prepended to the current array item path.

> 📖 See the original library docs "[Data Binding](https://github.com/yaobin-kid/a2ui-android#4-数据绑定data-model-binding)" chapter for full details.

### 5.5 Update Data sendA2UIUpdateDataModel

When the UI structure is unchanged and only the bound data needs updating, you don't need to resend the whole envelope. The `updateDataModel` message body has three fields: `surfaceId` (required), `path` (optional), `value` (optional), corresponding to three usages.

Method signature: `sendA2UIUpdateDataModel(instanceId, layoutId, dataModel)`, with an overload taking `incremental` (defaults to `true` for incremental merge, `false` for full replace):

```java
// Usage 1: no path, value is an object → fully replace dataModel
String full = "{\"version\":\"v0.9.1\",\"updateDataModel\":{"
    + "\"surfaceId\":\"example-focus\","
    + "\"value\":{\"headline\":\"New title\",\"progress\":\"2 / 3\"}"
    + "}}";
client.sendA2UIUpdateDataModel("inst-example", "example-focus", full);

// Usage 2: path + value → update only the specified path (intermediate paths are created if missing)
String partial = "{\"version\":\"v0.9.1\",\"updateDataModel\":{"
    + "\"surfaceId\":\"example-focus\","
    + "\"path\":\"/progress\",\"value\":\"3 / 3\"}}";
client.sendA2UIUpdateDataModel("inst-example", "example-focus", partial);

// append an element to an array (path uses /items/-):
// "{\"path\":\"/items/-\",\"value\":{\"title\":\"New task\"}}"

// Usage 3: path present but no value → delete that path
String del = "{\"version\":\"v0.9.1\",\"updateDataModel\":{"
    + "\"surfaceId\":\"example-focus\",\"path\":\"/progress\"}}";
client.sendA2UIUpdateDataModel("inst-example", "example-focus", del);

// full replace (do not merge old data):
// client.sendA2UIUpdateDataModel("inst-example", "example-focus", full, false);
```

**Check the result:** In the return value `A2UIUpdateDataModelResultPayload`, `success=false` means LineOS rejected the update — the plugin side should inspect the ack rather than blindly assume success:

```java
client.sendA2UIUpdateDataModel("inst-example", "example-focus", update).thenAccept(ack -> {
    if (!ack.success) {
        Log.e(TAG, "Data update rejected by LineOS: " + (ack.error != null ? ack.error.message : "unknown"));
    }
});
```

### 5.6 Event Callback onA2UIActionCall

A2UI component `action` has **three forms**, and only the `event` form is passed back to the plugin:

| Form | Trigger component | Executor | Passed to plugin |
| ---- | ----------------- | -------- | ---------------- |
| `event` | Button / Modal trigger | LineOS → **forwarded to the plugin** | ✅ goes through `onA2UIActionCall` |
| `functionCall` | Button / any component | Surface built-in / registered functions (e.g. `formatString`, `formatCurrency`) | ❌ executed internally in the renderer |
| `input` | TextField / CheckBox / Slider and other input controls | Inside the Surface (written back to dataModel) | ❌ auto-written back to dataModel |

**To receive user interactions, the plugin must use the `event` form:**

```json
{
  "id": "submit",
  "component": "Button",
  "variant": "filled",
  "child": "submitText",
  "action": {
    "event": {
      "name": "submitForm",
      "context": {
        "email": "#/form/email",
        "agreed": "#/form/agreed"
      }
    }
  }
}
```

**Field mapping (event → A2UIActionCallPayload):**

| A2UI event field | Plugin-side payload field | Description |
| ---------------- | ------------------------- | ----------- |
| `event.name` | `payload.actionName` | event name, e.g. `"submitForm"` |
| `event.context` | `payload.context` | `Map<String, Object>`; the `#/path` references have already been **replaced with actual dataModel values**, no need to parse again |
| `surfaceId` | `payload.layoutId` | the layout id of the event source |
| — | `payload.instanceId` | the card instance id of the event source |
| — | `payload.cardId` | the card id of the event source |

Plugin-side reception:

```java
@Override
public void onA2UIActionCall(A2UIActionCallPayload payload, A2UIActionCallResponder responder) {
    String actionName = payload.actionName;          // event.name, e.g. "submitForm"
    Map<String, Object> context = payload.context;   // event.context (#/path already resolved to actual values)
    String instanceId = payload.instanceId;          // source card instance
    String layoutId = payload.layoutId;              // source surfaceId

    if ("submitForm".equals(actionName)) {
        String email = context == null ? null : (String) context.get("email");
        // TODO: handle the interaction

        Map<String, Object> reply = new HashMap<>();
        reply.put("handled", true);
        responder.sendSuccess(reply);

        // after handling you typically want to refresh the UI:
        // client.sendA2UIUpdateDataModel(instanceId, layoutId, updateJson);
        return;
    }

    // unrecognized action: return NO_HANDLER, LineOS can fall back
    responder.sendError("NO_HANDLER", "no handler for " + actionName, false);
}
```

> 📖 For the full semantics of the three action forms, the list of built-in functions, and custom function registration, see the original library docs "[Action Usage](https://github.com/yaobin-kid/a2ui-android#34-action-用法button-交互)" chapter.

### 5.7 LineOS Active Pull onA2UIDataRequest

Besides the plugin actively pushing, LineOS also actively pulls the plugin's current A2UI data (e.g. automatically once after a successful registration). Implement this callback to return the current snapshot:

```java
@Override
public List<A2UIItem> onA2UIDataRequest() {
    List<A2UIItem> items = new ArrayList<>();
    // A2UIItem 4 required params: layoutId / cardId / name / ui
    items.add(new A2UIItem("example-focus", "card-example", "Example", currentUiJson));
    return items;   // return null or empty list for no data
}
```

> 📌 After a successful pull, LineOS automatically caches the data into the plugin session. If this callback is not implemented, it defaults to returning an empty list.

### 5.8 Card Parameter Schema (args)

`A2UIItem.args` declares which fields the user can configure and what controls render them. Each of the 6 types has its **own dedicated Builder** — you can only set that type's own fields (compile-time isolation, no cross-setting):

| Type | Builder | Settable |
| ---- | ------- | -------- |
| `int` | `intArg()` | `label` `required` `defaultValue(int)` `min(int)` `max(int)` |
| `float` | `floatArg()` | `label` `required` `defaultValue(double)` `min(double)` `max(double)` |
| `bool` | `boolArg()` | `label` `required` `defaultValue(boolean)` |
| `string` | `stringArg()` | `label` `required` `defaultValue(String)` |
| `select` | `selectArg()` | `label` `required` `defaultValue(String)` `option(value)` / `option(value, label)` |
| `object` | `objectArg()` | `label` `required` `property(name, A2UIArgSpec)` (recursive nesting) |

```java
A2UIArgs args = A2UIArgs.create()
        .arg("title", A2UIArgSpec.stringArg().label("Title").defaultValue("Params demo").build())
        .arg("count", A2UIArgSpec.intArg().label("Count").defaultValue(3).min(1).max(99).build())
        .arg("level", A2UIArgSpec.selectArg().label("Level").defaultValue("normal")
                .option("low", "Low").option("normal", "Normal").build())
        .arg("headers", A2UIArgSpec.objectArg().label("Nested param group")
                .property("retries", A2UIArgSpec.intArg().defaultValue(3).max(10).build())
                .build());

// A2UIItem for onA2UIDataRequest query response (with args schema)
new A2UIItem("example-focus", "card-example", "Example", ui, args);
```

**Dynamic schema:** When parameters are decided in real time by the plugin, declare `dynamic + schemaGetter`; LineOS pulls the parameters before rendering the form, and the plugin returns `A2UIArgs` directly in `PluginListener.onA2UIArgsSchemaRequest(schemaGetter)`:

```java
// plugin declares: dynamic parameters
A2UIArgs args = A2UIArgs.dynamic("schema.argsdemo");

// plugin side: respond to the pull, generate A2UIArgs in real time
@Override
public A2UIArgs onA2UIArgsSchemaRequest(String schemaGetter) {
    return A2UIArgs.create()
            .arg("fieldA", A2UIArgSpec.stringArg().label("Field A").defaultValue("v1").build())
            .arg("fieldB", A2UIArgSpec.intArg().label("Field B").min(1).max(100).build());
    // returning null means no configurable parameters; the server normalizes it to an empty A2UIArgs
}
```

Static fields and dynamic declarations can coexist: static fields render first, and dynamic fields are merged and appended from the schema returned by the getter.

### 5.9 Render Notification onA2UIRender

LineOS actively notifies the plugin (`PluginServer.notifyA2UIRender`) when a card is **first rendered** and **refresh-rendered**; the plugin receives it in `PluginListener.onA2UIRender`:

```java
@Override
public void onA2UIRender(A2UIRenderPayload payload, A2UIRenderResponder responder) {
    // payload.instanceId  card instance id
    // payload.layoutId    layout id
    // payload.cardId      card id
    // payload.args        args parameter values effective for this render (Map, may be null)
    // payload.stage       FIRST_RENDER first render / REFRESH refresh render

    if (payload.stage == A2UIRenderStage.FIRST_RENDER) {
        // first render: pull initial data on demand, refresh content, etc.
    }

    responder.sendSuccess();
}
```

Server-side call:

```java
server.notifyA2UIRender(packageName, instanceId, layoutId, cardId, argsMap,
        A2UIRenderStage.FIRST_RENDER);
```

> 📌 If this callback is not implemented, it defaults to returning `NO_HANDLER`; the plugin must declare `supportsA2UI=true` to receive notifications.

### 5.10 References

| Reference | Link | Description |
| --------- | ---- | ----------- |
| a2ui-android rendering library | [github.com/yaobin-kid/a2ui-android](https://github.com/yaobin-kid/a2ui-android) | **Authoritative A2UI JSON writing doc**: component catalog, styles, data binding, actions, functions |
| A2UI official spec | [a2ui.org/specification/v0.9-a2ui](https://a2ui.org/specification/v0.9-a2ui/) | Protocol spec original text |
| RFC 6901 JSON Pointer | [tools.ietf.org/html/rfc6901](https://tools.ietf.org/html/rfc6901) | Data binding path syntax |

---

## 6. Module Events (DOT_KEY / KNOB_KEY)

Events generated by LineOS hardware modules (buttons, knobs, etc.) can be distributed to plugins in real time.

### 6.1 Subscribe to Module Events

```java
// varargs, pass in the module types you need
client.subscribeModuleEvents(ModuleType.DOT_KEY, ModuleType.KNOB_KEY);

// unsubscribe
// client.unsubscribeModuleEvents(ModuleType.DOT_KEY);
```

> 📌 Subscriptions are stored in the LineOS-side session; after a disconnect/reconnect, the SDK automatically re-subscribes, no manual handling needed.

### 6.2 Dispatch Rules (Key)

Module events are **not broadcasts** — LineOS does **serial unicast** by priority: it asks candidate plugins one by one until one plugin "consumes" the event; if no one consumes it, it falls through to the fallback flow.

Full dispatch flow:

```
module event produced (button press / knob rotate)
   │
   ▼
① filter candidate plugins
   · have called subscribeModuleEvents for this ModuleType
   · and the current connection is alive
   │
   ▼
② sort candidates (priority high → low)
   · foreground plugins first, multiple foreground sorted by "most recent foreground time" descending
   · background plugins next, multiple background sorted by "most recent active time" descending
   │
   ▼
③ serial unicast: sent to candidates in sorted order
   · the appState field in the event each plugin receives marks **its own**
     foreground/background state (foreground / background)
   · each candidate has an independent reply timeout (default 1000ms)
   │
   ├── plugin calls responder.consume() ──► ④a dispatch ends
   │                                    result: handled, record the consuming package name
   │
   ├── plugin calls responder.ignore() ──► try next candidate
   │
   └── no reply before timeout (treated as ignore) ──► try next candidate
   │
   ▼ (no candidate consumed)
⑤ fallback flow: event appState set to none
   ├── if a registerFallback callback is registered for this ModuleType
   │   ──► execute all registered fallback callbacks in order
   └── otherwise ──► PluginServerListener.onModuleEventFallback(event)
```

**Rule summary:**

| Rule | Description |
| ---- | ----------- |
| Unicast, not broadcast | An event is consumed by at most one plugin; once a higher-priority plugin calls `consume()`, subsequent plugins **do not receive** it |
| Priority | Foreground > background; among the same class, the most recently foreground / most recently active one wins. |
| appState semantics | The `appState` in the event is **the receiver's own state** (foreground/background), not the event source's state; it is `none` during fallback |
| No reply = ignore | If neither `consume()` nor `ignore()` is called in `onModuleEvent`, after the timeout (default 1s) it is automatically treated as abandoned and the event flows to the next candidate |

### 6.3 Receive Events onModuleEvent

```java
@Override
public void onModuleEvent(ModuleEventPayload payload, ModuleEventResponder responder) {
    // payload.appState is the current plugin's own foreground/background state (see 6.2 dispatch rules)
    if (ModuleEventPayload.APP_STATE_FOREGROUND.equals(payload.appState)) {
        // foreground: consume the event, dispatch stops immediately, subsequent plugins and fallback never trigger
        responder.consume();
        // TODO: handle the button/knob event payload.event
    } else {
        // background: explicitly give up, the event immediately flows to the next candidate (avoid the 1s timeout wait)
        responder.ignore(null);
    }
}
```

**Event handling rules:**

- `responder.consume()` — the plugin consumes the event, dispatch terminates
- `responder.ignore(null)` — explicitly give up, the event **immediately** flows to the next candidate
- Calling neither — after the timeout (default 1s) it is passively abandoned and also flows to the next candidate; **explicitly calling ignore is recommended to avoid slowing down the whole dispatch chain**

### 6.4 Event Data Format

**DOT_KEY (button):**

```json
{
    "appState": "foreground",
    "module": "DOT_KEY",
    "action": "button",
    "event": {
        "action": 1,
        "downTime": 169360339,
        "eventTime": 169360339,
        "keyCode": 2,
        "repeatCount": 0
    },
    "moduleId": "4",
    "sn": "xxxxx",
    "timestamp": 1793002523558
}
```

- `event.action`: 1 = down, 0 = up
- `event.keyCode`: key code, values 0 / 1 / 2
- `event.repeatCount`: increments on long press

**KNOB_KEY (knob + button):**

```json
// knob event (faster rotation → more frequent reports)
{
    "appState": "foreground",
    "module": "KNOB_KEY",
    "action": "rotate",
    "event": { "value": 1 },
    "moduleId": "6",
    "sn": "xxxx",
    "timestamp": 1793005179031
}
```

- `action`: `button` = button (same format as DOT_KEY), `rotate` = rotation
- `event.value`: 1 = clockwise, -1 = counter-clockwise

**SENSE_CUBE (multi-function sensing cube):**

Illuminance (`action = "illuminance"`):

```json
{
    "action": "illuminance",
    "appState": "background",
    "event": {
        "level": "BRIGHT",
        "unit": "Lux",
        "value": 122
    },
    "module": "SENSE_CUBE",
    "moduleId": "26",
    "sn": "0000019F20E45C31",
    "timestamp": 1794449768487
}
```

Temperature (`action = "temperature"`):

```json
{
    "action": "temperature",
    "appState": "background",
    "event": {
        "level": "MILD",
        "unit": "°F",
        "value": 74.66
    },
    "module": "SENSE_CUBE",
    "moduleId": "26",
    "sn": "0000019F20E45C31",
    "timestamp": 1794449766512
}
```

Presence (`action = "presence"`):

```json
{
    "action": "presence",
    "appState": "background",
    "event": true,
    "module": "SENSE_CUBE",
    "moduleId": "26",
    "sn": "0000019F20E45C31",
    "timestamp": 1794449766412
}
```

Humidity (`action = "humidity"`):

```json
{
    "action": "humidity",
    "appState": "background",
    "event": {
        "unit": "%",
        "value": 38.9
    },
    "module": "SENSE_CUBE",
    "moduleId": "26",
    "sn": "0000019F20E45C31",
    "timestamp": 1794450176184
}
```

- `action`: `illuminance`=illuminance, `temperature`=temperature, `presence`=presence detection, `humidity`=humidity
- `event.value`: current sensor value (`presence` is `boolean`, true=person present)
- `event.unit`: unit of measurement (Lux / °F / %)
- `event.level`: perceived/illuminance level (e.g. `BRIGHT`, `MILD`), present for some events

### 6.5 Query Module List queryModules

```java
// query all
client.queryModules().thenAccept(result -> {
    for (ModuleInfo m : result.modules) {
        Log.d(TAG, m.module + " / " + m.moduleId + " / " + m.name
                + " / online=" + m.online + " / sn=" + m.sn);
    }
});

// only online / offline
// client.queryModules(ModuleOnlineFilter.ONLINE);
// client.queryModules(ModuleOnlineFilter.OFFLINE);
```

Return structure:

```json
{
    "modules": [
        {
            "module": "DOT_KEY",
            "moduleId": "37",
            "name": "Dot Key",
            "online": true,
            "sn": "0000019E4F84ABEF"
        }
    ],
    "success": true
}
```

> Currently only three modules are supported: `DOT_KEY`, `KNOB_KEY`, `SENSE_CUBE`.

---

## 7. Hotkeys & Messages

This chapter introduces two direct plugin ↔ PC capabilities:

- **Hotkeys**: Inject keyboard keystrokes into the PC, supporting single keys, combos and long-press (e.g. simulate Windows Ctrl+C copy);
- **Message channel**: bi-directional message passing between the plugin and LineOS (addressed by instance id, content supports strings and structured objects).

### 7.1 Key Code Convention

The hotkey API's parameter is the **raw key code (int)**, consistent with the host platform — on the PC side it is the Windows VK virtual key code. For the full key code reference table see [hidKey.md](./hidKey.md).

### 7.2 Short Press sendHotkey

`sendHotkey(int... keys)` represents one complete "press and release" — the **SDK internally fills in the down + up automatically**, no need to pair the calls:

```java
// single key: press F1 once
client.sendHotkey(0x70);

// combo: simulate Windows paste Ctrl+V (modifier keys first)
client.sendHotkey(0xE0, 0x19).thenAccept(ack -> {
    if (!ack.success) {
        Log.e(TAG, "Hotkey execution failed: " + ack.error.message);
    }
});
```

Semantic rules:

- A combo is sent **as a whole in one go** (the keys array is packed into a single message), down follows the parameter order, up follows the **reverse order** (modifier keys pressed first and released last, consistent with a real keyboard);
- Each call produces 2 callbacks on the server (one down + one up), message `pressType = short`;
- The returned `CompletableFuture` is the ack of the up (channel writes are ordered, so the preceding down has already been delivered); `success=false` means the host failed to execute.

### 7.3 Long Press sendHotkeyDown / sendHotkeyUp

When "press and hold" semantics are needed, use `sendHotkeyDown` / `sendHotkeyUp` for explicit pairing, message `pressType = long`:

```java
client.sendHotkeyDown(0x5E);   // press down (no release)
// ...holding...
client.sendHotkeyUp(0x5E);     // release
```

**Auto-release protection:** If `sendHotkeyUp` is not called within 3000ms after `sendHotkeyDown` (adjustable via `client.setHotkeyAutoReleaseMs(...)`, set to 0 to disable), the SDK automatically sends an up once, preventing the key from getting stuck on the system side. An explicit `sendHotkeyUp` cancels this task.

**Continuous-effect scenarios** (e.g. in Photoshop holding Ctrl+] continuously scales the brush linearly): A real keyboard's "continuous effect" comes from hardware auto-repeat — holding a key down produces repeated down events. The SDK injection only sends one down and does not auto-repeat; you need to send repeatedly at a fixed interval:

```java
client.setHotkeyAutoReleaseMs(0);          // disable auto-release while holding
client.sendHotkeyDown(0x11);                   // 1. Ctrl down, send only once

while (holding) {                          // 2. while holding: repeat send ] down every 80ms
    client.sendHotkeyDown(0xDD);               //    simulate keyboard auto-repeat → brush keeps scaling
    Thread.sleep(80);
}

client.sendHotkeyUp(0x11, 0xDD);               // 3. end: pass keys in the same order as down, SDK internally releases in reverse order (] first, then Ctrl)
```

> Repeating `sendHotkeyDown` for the same key **resets** the auto-release timer — as long as the repeat interval is shorter than the auto-release threshold, the default configuration will not accidentally trigger a release.

### 7.4 Send Message sendMessage

Send a message to a specified plugin instance (`message` is `Object`, supporting strings, Map, List, POJO and any other serializable object); defaults to pass-through to the Ultra WorkSpace PLUGIN side:

```java
// default destination ULTRA_WORKSPACE_PLUGIN — send a string
client.sendMessage("plugin-001", "Start playback")
      .thenAccept(ack -> {
          if (!ack.success) {
              Log.e(TAG, "Message delivery failed: " + ack.error.message);
          }
      });

// send a structured object (the receiver gets a Map)
Map<String, Object> body = new HashMap<>();
body.put("action", "play");
body.put("trackId", 42);
client.sendMessage("plugin-001", body);

// explicitly specify the pass-through destination (MessageDestination enum)
client.sendMessage(MessageDestination.ULTRA_WORKSPACE_PLUGIN, "plugin-002", "Switch channel");
```

- `destination` is the `MessageDestination` enum (currently containing `ULTRA_WORKSPACE_PLUGIN`); adding destinations in the future only requires extending the enum, the API stays the same;
- Empty `pluginId`, or null `message` / `destination` throws `IllegalArgumentException` locally on the client and does not send a request; an empty string `""` is a legal message content;
- After the server receives it, it calls back `PluginServerListener.onMessage`, whose return value is used as the ack — `success=false` means the host failed to process it.

### 7.5 Receive LineOS-Delivered Message onMessage

The message channel is bidirectional. When LineOS calls `PluginServer.sendMessage(packageName, pluginId, message)`, the plugin receives it via `PluginListener.onMessage`:

```java
client.setPluginListener(new PluginListener() {
    @Override
    public void onMessage(MessagePayload payload, MessageResponder responder) {
        String pluginId = payload.pluginId;       // target plugin instance id
        Object message = payload.message;         // message content (may be String / Map / List etc.)
        // payload.destination is null in this direction (only used plugin→LineOS direction)

        if (message instanceof String) {
            String text = (String) message;
        } else if (message instanceof Map) {
            Map<String, Object> body = (Map<String, Object>) message;
        }

        // must respond after processing, pick one:
        responder.sendSuccess();
        // responder.sendError("NOT_SUPPORTED", "This instance does not support messages", false);
    }
});
```

> The default implementation returns a `NO_HANDLER` error; if you don't need this capability, you can skip overriding it.

### 7.6 Request Host to Execute Actions sendHostAction

Five operations — open file, start app, open browser, input text, execute command — are unified into **one method**. Each action has its **own dedicated Builder** — you can only set that action's own parameters (compile-time isolation, no cross-setting); the parameter keys are encapsulated inside the SDK, and the caller doesn't need to care about protocol field names:

```java
// 1. open a file or folder (can only set path)
client.sendHostAction(HostActionRequest.openFile()
        .path("C:/Users/kid/Desktop/ap")
        .build());

// 2. start an app (can only set path and launchMode)
client.sendHostAction(HostActionRequest.startApp()
        .path("C:\\Users\\kid\\AppData\\Local\\SourceTree\\app-3.4.30\\SourceTree.exe")
        .launchMode(HostActionRequest.HostLaunchMode.ACTIVATE_OR_START)  // optional
        .build());

// 3. open a browser (can only set url)
client.sendHostAction(HostActionRequest.openBrowser()
        .url("google.com")
        .build());

// 4. input text (can only set text)
client.sendHostAction(HostActionRequest.inputText()
        .text("hello")
        .build());

// 5. execute a command (can only set cmd)
client.sendHostAction(HostActionRequest.execCmd()
        .cmd("notepad")
        .build())
      .thenAccept(ack -> {
          if (!ack.success) {
              Log.e(TAG, "Execution failed: " + ack.error.message);
          }
      });
```

**launchMode start-up modes** (`HostActionRequest.HostLaunchMode` enum):

| Enum value | Wire value | Meaning |
| ---------- | ---------- | ------- |
| `NEW_PROCESS` | 0 | Always start a new process |
| `ACTIVATE_ONLY` | 1 | Only activate (do not start a new process, just activate an already-running process) |
| `ACTIVATE_OR_START` | 2 | Start if no process was started; activate if a process is already running |

- Action types are distinguished by the `HostAction` enum; the server calls back `PluginServerListener.onHostAction`, distinguishing actions accurately via `request.action` and getting parameters via `request.params`;
- Each dedicated Builder's `build()` validates required parameters (e.g. `openFile()` requires `path`), throwing `IllegalArgumentException` if missing; when adding new actions, only extend the enum and Builder — **no need to add a new send method**;
- `success=false` in the return value means the host failed to execute.

---

## 8. Callback Quick Reference

All `PluginListener` methods have `default` empty implementations — override as needed:

| Callback | Trigger timing | Must implement |
| -------- | -------------- | -------------- |
| `onRegisterSuccess(RegisterResultPayload)` | Registration successful (can get configServer.port / sessionToken) | Recommended |
| `onRegisterFailed(Throwable)` | Registration failed | Recommended |
| `onActionsAck(ActionsResultPayload)` | Action config sync ACK | Optional |
| `onActionsFailed(Throwable)` | Action report failed | Optional |
| `onActionsUpdate(ActionsPayload)` | LineOS pushes action list changes | Optional |
| `onDescribe(DescribePayload, DescribeResponder)` | LineOS asks for Action parameter definitions | Required for actions with parameters |
| `onOptions(GetOptionsPayload, OptionsResponder)` | Fetch REMOTE-type parameter options | Required if there are REMOTE parameters |
| `onCall(CallPayload, CallResponder)` | Execute the Action | **Required** |
| `onModuleEvent(ModuleEventPayload, ModuleEventResponder)` | Module hardware events | Required if you subscribed to modules |
| `onA2UIActionCall(A2UIActionCallPayload, A2UIActionCallResponder)` | A2UI user interaction callback (defaults to NO_HANDLER) | Required if there are interactive components |
| `onA2UIDataRequest()` | LineOS actively pulls A2UI data (defaults to empty list) | Recommended for A2UI plugins |
| `onA2UIRender(A2UIRenderPayload, A2UIRenderResponder)` | Card first-render / refresh-render notification (defaults to NO_HANDLER) | Optional for A2UI plugins |
| `onMessage(MessagePayload, MessageResponder)` | LineOS delivers a message (defaults to NO_HANDLER) | Optional |

**Responder common conventions:**

- `responder.sendSuccess(data)` — success, `data` can be null
- `responder.sendError(code, message, retryable)` — failure; `retryable=true` hints LineOS can retry

---

## 9. Notes & FAQ

### 9.1 Build & Integration

1. **`packagingOptions` must exclude Netty meta files** (see 2.1), otherwise packaging conflicts.
2. The `meta-data`'s `android:value` **must equal `applicationId`** — it is LineOS's unique identifier for the plugin; a mismatch will cause the session to not line up after registration.
3. The service must be `android:exported="true"` and carry the fixed permission — both are indispensable.

### 9.2 Runtime Behavior

4. **Foreground service requirement:** `onCreate()` must call `startForeground()` within a few seconds, otherwise the system ANR/kills the process.
5. **Callback threads are not the main thread:** All `PluginListener` callbacks run on the SDK's thread pool; for UI operations, switch via `Looper.getMainLooper()` yourself.
6. **Auto-reconnect on disconnect:** The SDK automatically reconnects and re-registers + re-reports Actions; but **A2UI UI data is NOT automatically re-sent** — LineOS will actively pull it once after a successful registration (via `onA2UIDataRequest`), so implementing this callback properly ensures UI data is restored after a reconnect.
7. **sessionToken must be validated yourself:** it is the debug session credential; do not trust it unconditionally in HTTP interfaces.
8. **Hotkey down/up must be paired:** Forgetting `sendHotkeyUp` after `sendHotkeyDown` will cause the SDK to auto-release an up after the timeout (default 3000ms), but the release timing is out of your control; when precise long-press semantics are needed, guarantee the pairing yourself — see [7.3](#73-long-press-sendhotkeydown--sendhotkeyup).

### 9.3 Protocol Contracts

8. **Parameter id consistency:** The `ParameterSpec.id` in `onDescribe` is the key of `params` in `onCall`; both sides must be consistent.
9. **A2UI version must be v0.9.1:** Every envelope carries `"version": "v0.9.1"`.
10. **`supportsA2UI=true` is the prerequisite for LineOS reverse calls:** Without it the plugin can still actively report data, but LineOS's `callA2UIAction` / `queryA2UIData` will be rejected.
11. **Check the report result's ack:** `success=false` in the return value of `refreshA2UI` / `sendA2UIUpdateDataModel` means LineOS failed to process it; the plugin side should log or retry.
12. **Module events are serial unicast; no reply = give up:** When neither `consume()` nor `ignore()` is called in `onModuleEvent`, the event wastes a 1-second timeout before reaching the next plugin — when you don't intend to handle it, **explicitly call `ignore()`**. See [6.2](#62-dispatch-rules-key) for the full dispatch rules.

### 9.4 Frequently Asked Questions (FAQ)

**Q: Why can't LineOS see my Action after `onRegisterSuccess`?**
A: Check whether `setActionsConfig` was called before `startAsync()`; check whether `onActionsAck`'s `receivedCount` matches your expectations.

**Q: How do I self-test during development?**
A: During development you can manually `startService()` in your own app to launch `BackgroundService` and verify the flow; in production LineOS scans the Manifest and launches it automatically.

---
