# Ultrabar Plugin SDK

### [English](README.md) | [简体中文](README_zh.md) | [日本語](README_ja.md)


An Ultrabar plugin protocol SDK based on Netty (protocol version = 2). Communication is UTF-8 JSON per line (framed by `\n`).

| Role | Class | Purpose |
|------|-------|---------|
| Plugin side | `com.ultrabar.plugin.PluginClient` | Register, report actions, handle describe / get_options / call |
| Host side (LineOS) | `com.ultrabar.server.PluginServer` | Manage sessions by `packageName`, store actions, initiate calls to plugins |

Third-party developers act as the plugin side and only need to integrate PluginClient and follow the steps. LineOS will periodically scan AndroidManifest.xml to discover services and start them (already started services will be skipped).


## 1. Gradle setup
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```
 
Dependencies required:

```groovy
implementation 'com.github.yaobin-kid:ultrabarIntegrated:.1.0.12' // sdk ver

implementation "io.netty:netty-all:4.1.94.Final"
implementation "com.fasterxml.jackson.core:jackson-databind:2.15.2"
implementation "org.slf4j:slf4j-simple:2.0.7"
```
```groovy
  packagingOptions {
        exclude 'META-INF/INDEX.LIST'
        exclude 'META-INF/io.netty.versions.properties'
    }
```

> Do NOT use `netty-all`


## 2. Service
`AndroidManifest.xml`:
```xml
   <service
            android:name=".service.BackgroundService"
            android:enabled="true"
            android:exported="true"
            android:permission="com.ultrabar.plugin.SERVER_REGISER_PERMISSION">
            <meta-data  android:name="ultrabar.plugin"   android:value="com.test.music" />
```
### Required settings

> The `meta-data` value must be the `applicationId`.
> `android:exported="true"` is required.
> `android:permission="com.ultrabar.plugin.SERVER_REGISER_PERMISSION"` is required.

## 3. Permissions
`AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## 4. BackgroundService example
> The service should perform the PluginClient startup in the `start()` section.
>
> Declared BackgroundService will be automatically discovered and started by the system (skipped if already started). During development you may start it manually to test the flow.
```java

public class BackgroundService extends Service {
    public static final String TAG = "BackgroundService";
    private static final String CHANNEL_ID = "BackgroundServiceChannel";

    public BackgroundService() {
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        AudioPlayerManager.getInstance().init(this);
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Persistent Background Service")
                .setContentText("Running...")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build();

        // Important: after starting, you must call this method within a few seconds to "report" to the system
        startForeground(1001, notification);


        start();
    }

    public void start() {
        PluginClient c = new PluginClient();
        RegisterPayload rp = new RegisterPayload();
        rp.name = "TestMusic";
        rp.packageName = "com.test.music";


        ActionsPayload ap = new ActionsPayload();

        ActionSummary play = new ActionSummary();
        play.actionId = "music.play";
        play.name = "Play";
        play.description = "Execute device play";


        ActionSummary pause = new ActionSummary();
        pause.actionId = "music.pause";
        pause.name = "Pause";
        pause.description = "Execute device pause";


        ap.actions = Arrays.asList(play, pause);
        c.setRegisterConfig(rp);
        c.setActionsConfig(ap);

        c.setPluginListener(new PluginListener() {
            @Override
            public void onRegisterSuccess(RegisterResultPayload result) {
                Log.d(TAG, "Registered port:" + result.configServer.port);
                // start http server
                RawHttpServer server = new RawHttpServer(result.configServer.port);
                server.startServer();
            }

            @Override
            public void onRegisterFailed(Throwable throwable) {

            }

            @Override
            public void onActionsFailed(Throwable throwable) {

            }

            @Override
            public void onActionsAck(ActionsResultPayload result) {
                Log.d(TAG, "Actions registered successfully");
            }

            @Override

            public void onActionsUpdate(ActionsPayload actionsPayload) {

            }


            @Override
            public void onDescribe(DescribePayload describe, DescribeResponder describeResponder) {
                Log.d(TAG, "Received describe request:" + describe.actionId);
                DescribeResultPayload resultPayload = new DescribeResultPayload();
                resultPayload.actionId = describe.actionId;
                resultPayload.success = true;
                resultPayload.parameters = new ArrayList<>();


                DescribeResultPayload.ParameterSpec spec1 = new DescribeResultPayload.ParameterSpec();
                spec1.id = "deviceId";
                spec1.name = "Device";
                spec1.placeholder = "Please select a device";
                spec1.required = true;
                spec1.type = ParameterType.SELECT;
                spec1.options = new DescribeResultPayload.OptionSpec();
                spec1.options.searchable = false;
                spec1.options.provider = OptionProvider.STATIC;
                spec1.options.items = new ArrayList<>();
                spec1.options.items.add(new Label("Living Room Player", "test01"));
                spec1.options.items.add(new Label("Bedroom Player", "test02"));


                DescribeResultPayload.ParameterSpec spec2 = new DescribeResultPayload.ParameterSpec();
                spec2.id = "title";
                spec2.name = "Song Title";
                spec2.placeholder = "Enter song title";
                spec2.required = true;
                spec2.type = ParameterType.TEXT;
                spec2.options = new DescribeResultPayload.OptionSpec();
                spec2.options.searchable = false;
                spec2.options.provider = OptionProvider.STATIC;


                DescribeResultPayload.ParameterSpec spec3 = new DescribeResultPayload.ParameterSpec();
                spec3.id = "in";
                spec3.name = "Input Source";
                spec3.placeholder = "Input source";
                spec3.required = true;
                spec3.type = ParameterType.SELECT;
                spec3.options = new DescribeResultPayload.OptionSpec();
                spec3.options.searchable = false;
                spec3.options.provider = OptionProvider.REMOTE;
                spec3.dependsOn = new ArrayList<>();
                spec3.dependsOn.add("deviceId");

                if ("music.play".equals(describe.actionId)) {
                    resultPayload.parameters.add(spec1);
                    resultPayload.parameters.add(spec2);
                    resultPayload.parameters.add(spec3);
                }

                describeResponder.sendSuccess(resultPayload);
            }

            @Override
            public void onCall(CallPayload call, CallResponder callResponder) {
                if ("music.play".equals(call.actionId)) {
                    AudioPlayerManager.getInstance().play();
                    Intent intent = new Intent(MyApp.context, TestActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // required
                    startActivity(intent);
                } else if ("music.pause".equals(call.actionId)) {
                    AudioPlayerManager.getInstance().pause();
                }
                callResponder.sendSuccess(null);
            }

            @Override
            public void onOptions(GetOptionsPayload request, OptionsResponder optionsResponder) {
                if ("music.play".equals(request.actionId)) {
                    if ("in".equals(request.describeId)) {
                        GetOptionsResultPayload result = new GetOptionsResultPayload();
                        result.hasMore = false;
                        result.nextCursor = "0";
                        result.items = new ArrayList<>();
                        result.items.add(new Label("qqMusic", "qq"));
                        result.items.add(new Label("NetEase Music", "163"));
                        optionsResponder.sendSuccess(result);
                    }
                }
            }
        });

        c.startAsync();

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {


        return null;
    }

}
```



## PluginClient details

```java
    PluginClient client = new PluginClient();
    // configure registration
    RegisterPayload rp = new RegisterPayload();
    rp.name = "Music"; // application name
    rp.packageName = "com.ultrabar.music"; // recommended package name


    // supported actions
    ActionSummary play = new ActionSummary();
    play.actionId = "music.play";
    play.name = "play music";
    play.description = "play on a device";
    
    ActionSummary pause = new ActionSummary();
    pause.actionId = "music.pause";
    pause.name = "pause music";
    pause.description = "pause a device";
    
    
    ActionSummary stop = new ActionSummary();
    stop.actionId = "music.stop";
    stop.name = "stop all play";
    stop.description = "stop all device  play";


    client.setActionsConfig(new ActionsPayload(Arrays.asList(play, pause, stop)));
    client.setRegisterConfig(rp);

    ExecutorService exec = Executors.newFixedThreadPool(4);
    
    client.setPluginListener(new PluginListener() {
        @Override
        public void onRegisterSuccess(RegisterResultPayload payload) {
            // ============================================================
            // Registration success
            //
            // After the SDK and host app complete registration, the payload
            // will return session information.
            //
            // configServer.port:
            //     The HTTP debug server port assigned by the host app.
            //
            // Third-party apps should start an HTTP server on this port
            // to provide a plugin debug page.
            //
            // When the host app opens the plugin debug page, it will include
            // a sessionToken.
            //
            // sessionToken:
            //     The credential for the current plugin debug session.
            //
            // Note:
            //     The sessionToken is provided to the third-party app by the host.
            //     The third-party app must validate the token itself — do not assume
            //     the token is trustworthy by default.
            //
            // It's recommended to start the HTTP server and bind to payload.configServer.port here.
            // ============================================================

            int httpPort = payload.configServer.port;
            String sessionToken = payload.sessionToken;

            System.out.println("Register success: session=" + payload.sessionId+",port="+httpPort+",sessionToken=+sessionToken");
        }
    
        @Override
        public void onRegisterFailed(Throwable t) {
            // Registration failed.
            //
            // If registration fails, the plugin cannot communicate with the host app normally.
            // Do not start HTTP or Action services that depend on the host app.
            //
            // You can:
            // 1. Log the error
            // 2. Notify the user
            // 3. Wait for the SDK to reconnect automatically
            // 4. Reinitialize the PluginClient if needed
            
            System.err.println("Register failed: " + t.getMessage());
        }
    
        @Override
        public void onActionsFailed(Throwable t) {
            System.err.println("Actions failed: " + t.getMessage());
        }
    
        @Override
        public void onActionsAck(ActionsResultPayload ack) {
            // ============================================================
            // Action configuration result
            //
            // After a third-party app sends Action configuration to the host,
            // the host will return the processing result.
            //
            // success:
            //     Whether the host successfully received/processed the actions.
            //
            // receivedCount:
            //     The number of actions actually received by the host.
            //
            // Note:
            //     This is an ACK for action configuration synchronization,
            //     not the result of executing onCall().
            // ============================================================
    
            System.out.println("Actions result: success=" + ack.success + " received=" + ack.receivedCount);
        }
    
        @Override
        public void onActionsUpdate(ActionsPayload update) {
            int count = (update != null && update.actions != null) ? update.actions.size() : 0;
            System.out.println("Actions update pushed: count=" + count);
        }
    
    
        @Override
        public void onDescribe(DescribePayload payload, DescribeResponder responder) {
    
            // ============================================================
            // Action parameter description
            //
            // Before the host app calls an action, it will request the action's
            // parameter definition via actionId.
            //
            // Here you should tell the host app:
            //   1. Which parameters the action requires
            //   2. Each parameter's id / name / type
            //   3. Whether a parameter is required
            //   4. Where the parameter's options come from if it is a selectable parameter
            //
            // The parameter id in the definition is important:
            // The payload.params received later in onCall() will use these ids as keys.
            // ============================================================
    
            DescribeResultPayload result = new DescribeResultPayload();
            result.actionId = payload.actionId;
            result.success = true;
            if ("music.play".equals(payload.actionId)) { // describe parameters for play
                DescribeResultPayload.ParameterSpec device = new DescribeResultPayload.ParameterSpec();
                device.id = "deviceId";
                device.name = "device";
                device.required = true;
                device.type = ParameterType.SELECT;
                device.placeholder = "select a device";
                DescribeResultPayload.OptionSpec options = new DescribeResultPayload.OptionSpec();
                options.provider = OptionProvider.REMOTE;
                options.searchable = true;
                device.options = options;
                result.parameters = Arrays.asList(device);
            } else if ("music.pause".equals(payload.actionId)) { // pause static parameter example
                DescribeResultPayload.ParameterSpec device = new DescribeResultPayload.ParameterSpec();
                device.id = "deviceId2";
                device.name = "device";
                device.required = true;
                device.type = ParameterType.SELECT;
                device.placeholder = "select a device";
                DescribeResultPayload.OptionSpec options = new DescribeResultPayload.OptionSpec();
                options.provider = OptionProvider.STATIC;
                options.searchable = true;
                options.items = new ArrayList<>();
                options.items.add(new Label("pause test 01", "pause_test_01"));
                options.items.add(new Label("pause test 02", "pause_test_02"));
                device.options = options;
                result.parameters = Arrays.asList(device);
            }
    
            responder.sendSuccess(result);
        }
    
    
        @Override
        public void onOptions(GetOptionsPayload payload, OptionsResponder responder) {
            // ============================================================
            // Dynamic parameter options
            //
            // When a parameter's OptionProvider is set to REMOTE in onDescribe(),
            // the host app will call this method when it needs to show options for that parameter.
            //
            // payload.actionId  -> current action
            // payload.describeId -> the parameter id
            //
            // For example:
            //   actionId  = music.play
            //   describeId = deviceId
            //
            // Means the host is requesting:
            //   "Please tell me what options are available for music.play's deviceId parameter"
            //
            // If there are many options, you can use hasMore / nextCursor for pagination.
            // ============================================================
    
            GetOptionsResultPayload result = new GetOptionsResultPayload();
            result.success = true;
            result.hasMore = false;
            result.nextCursor = null;
    
            if ("music.play".equals(payload.actionId)) { // action id
    
                if ("deviceId".equals(payload.describeId)) { // parameter id
                    Label item = new Label();
                    item.value = "sp00-1";
                    item.label = "sony tv";
                    Label item2 = new Label();
                    item2.value = "sp00-2";
                    item2.label = "sony amp";
                    result.items = Arrays.asList(item, item2);
                }
            }
    
    
            responder.sendSuccess(result);
        }
    
    
        @Override
        public void onCall(CallPayload payload, CallResponder responder) {
    
            // ============================================================
            // Execute Action and return final result
            //
            // When the user configures and runs an Action in the host app,
            // the host app will call this method.
            //
            // payload.actionId
            //     -> the action to execute
            //
            // payload.params
            //     -> the parameters defined in onDescribe()
            //
            // Note:
            // The keys in params must match the ParameterSpec.id values defined in onDescribe().
            //
            // For example in onDescribe():
            //     device.id = "deviceId";
            // then here you must use:
            //     params.get("deviceId");
            // to retrieve the user's selected device.
            // ============================================================
    
            String actionId = payload.actionId;
            Map<String, Object> params = payload.params;
            if ("music.play".equals(actionId)) {
                // onDescribe DescribeResultPayload.ParameterSpec.id
                String deviceId = (String) params.get("deviceId");
                // todo: perform play on the specified device
    
            } else if ("music.pause".equals(actionId)) {
                // onDescribe DescribeResultPayload.ParameterSpec.id
                String deviceId2 = (String) params.get("deviceId2");
                // todo: perform pause on the specified device
    
            } else if ("music.stop".equals(actionId)) {
                // todo: stop all devices
            }
    
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("actionId", payload.actionId);
            data.put("status", "ok");
    
    
            responder.sendSuccess(data);
        }
    });

    client.startAsync().thenRun(new Runnable() {
        @Override
        public void run() {
            System.out.println("Client started and will auto-register/send actions");
        }
    });

    Thread.sleep(30_000);
    client.stop();
    exec.shutdownNow();
}

```

Call `client.updateActions(new ActionsPayload(...))` when the action list changes.



## PluginServer (Host App)

Sessions are unique by the registered `packageName`; re-registering with the same `packageName` will replace the old connection. A `PluginSession` is only created when the registration callback returns `success == true`.

```java
final PluginServer server = new PluginServer(39001);

server.setRegisterHandler(new PluginRegisterHandler() {
    @Override
    public RegisterResultPayload handleRegister(RegisterPayload request) {
        RegisterResultPayload result = new RegisterResultPayload();
        if (request.packageName == null) {
            result.success = false;
            result.error = ErrorInfo.of("REJECTED", "packageName required", false, null);
            return result;
        }
        result.success = true;
        result.configServer = new ConfigServer();
        result.configServer.host = "127.0.0.1";
        result.configServer.port = 8123;
        // If sessionId / sessionToken / heartbeat are not provided, the server will fill defaults
        return result;
    }
});

server.setListener(new PluginServerListener() {
    @Override
    public void onRegistered(PluginSession session) { }

    @Override
    public void onActionsUpdated(PluginSession session) { }

    @Override
    public void onUnregistered(PluginSession session) { }
});

server.start();

// Query session
PluginSession session = server.getSession("com.ultrabar.music");

// Call plugin action (the session must have reported the corresponding actionId)
Map<String, Object> params = new HashMap<String, Object>();
params.put("deviceId", "sp00-1");
server.call("music.play", params)
        .thenAccept(result -> { /* CallResultPayload */ })
        .exceptionally(err -> { err.printStackTrace(); return null; });

// If multiple plugins have the same actionId include packageName
server.call("com.ultrabar.music", "music.play", params);

// server.stop();
```

## Others
 
### R8 / ProGuard

Models use public fields + Jackson. For release builds you must keep these classes:

```
-keep class com.ultrabar.plugin.model.** { *; }
-keep class com.ultrabar.plugin.callback.** { *; }
-keep class com.ultrabar.plugin.PluginClient { *; }
```
 
 

## Protocol highlights

- Envelope: `type`, `requestId`, `timestamp`, `protocol`, `payload`; after successful registration the client will include `sessionId` and `auth`.
- `requestId` is a UUID, used once per RPC.
- Action ID field is consistently `actionId`.
- Paired messages: `register` / `register_result`, `actions` / `actions_result`, `describe` / `describe_result`, `get_options` / `get_options_result`, `call` / `call_result`, `heartbeat` / `heartbe[...]`
