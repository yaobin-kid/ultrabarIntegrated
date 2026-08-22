# Ultrabar Plugin SDK

### [English](README.md) | [简体中文](README_zh.md) | [日本語](README_ja.md)


Nettyを基盤としたUltrabarプラグインプロトコルSDK（プロトコル version = 2）。通信はUTF-8の1行1件のJSON（`\n`でフレーム）です。

| 役割 | クラス | 用途 |
|------|-------|------|
| プラグイン側 | `com.ultrabar.plugin.PluginClient` | 登録、アクションの報告、describe / get_options / call の処理 |
| ホスト側（LineOS） | `com.ultrabar.server.PluginServer` | `packageName`ごとにセッション管理、アクションの保存、プラグインへのcallの発行 |

サードパーティ開発者はプラグイン側の役割となり、**PluginClient** を組み込んで手順に従えば動作します。LineOSは定期的に AndroidManifest.xml をスキャンしてサービスを検出し起動します（既に起動済みならスキップ）。


## 1. Gradle の導入
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```
 
必要な依存：

```groovy
implementation 'com.github.yaobin-kid:ultrabarIntegrated:1.0.12' // sdk ver

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

> `netty-all` は使用しないでください


## 2. サービス設定
`AndroidManifest.xml`：
```xml
   <service
            android:name=".service.BackgroundService"
            android:enabled="true"
            android:exported="true"
            android:permission="com.ultrabar.plugin.SERVER_REGISER_PERMISSION">
            <meta-data  android:name="ultrabar.plugin"   android:value="com.test.music" />
```
### 必須設定

> `meta-data` の値は `applicationId` を指定してください。<br>
> `android:exported="true"` を設定してください。<br>
> `android:permission="com.ultrabar.plugin.SERVER_REGISER_PERMISSION"` を設定してください。

## 3. パーミッション
`AndroidManifest.xml`：
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## 4. BackgroundService の参考実装
> サービス内で `PluginClient` を起動する処理（`start()` 部分）を実装してください。
>
> 宣言した `BackgroundService` はシステムによって自動的に検出・起動されます（既に起動済みならスキップ）。開発中は動作確認のために手動で起動して構いません。
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
                .setContentTitle("常駐バックグラウンドサービス")
                .setContentText("実行中...")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build();

        // 重要: 起動後数秒以内にこのメソッドを呼び出してシステムに報告する必要があります
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
        play.name = "再生";
        play.description = "デバイスで再生を実行します";


        ActionSummary pause = new ActionSummary();
        pause.actionId = "music.pause";
        pause.name = "一時停止";
        pause.description = "デバイスの一時停止を実行します";


        ap.actions = Arrays.asList(play, pause);
        c.setRegisterConfig(rp);
        c.setActionsConfig(ap);

        c.setPluginListener(new PluginListener() {
            @Override
            public void onRegisterSuccess(RegisterResultPayload result) {
                Log.d(TAG, "登録ポート番号:" + result.configServer.port);
                // HTTP サーバーを起動
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
                Log.d(TAG, "アクションの登録に成功しました");
            }

            @Override

            public void onActionsUpdate(ActionsPayload actionsPayload) {

            }


            @Override
            public void onDescribe(DescribePayload describe, DescribeResponder describeResponder) {
                Log.d(TAG, "Describe リクエストを受信: " + describe.actionId);
                DescribeResultPayload resultPayload = new DescribeResultPayload();
                resultPayload.actionId = describe.actionId;
                resultPayload.success = true;
                resultPayload.parameters = new ArrayList<>();


                DescribeResultPayload.ParameterSpec spec1 = new DescribeResultPayload.ParameterSpec();
                spec1.id = "deviceId";
                spec1.name = "デバイス";
                spec1.placeholder = "デバイスを選択してください";
                spec1.required = true;
                spec1.type = ParameterType.SELECT;
                spec1.options = new DescribeResultPayload.OptionSpec();
                spec1.options.searchable = false;
                spec1.options.provider = OptionProvider.STATIC;
                spec1.options.items = new ArrayList<>();
                spec1.options.items.add(new Label("リビングプレーヤー", "test01"));
                spec1.options.items.add(new Label("ベッドルームプレーヤー", "test02"));


                DescribeResultPayload.ParameterSpec spec2 = new DescribeResultPayload.ParameterSpec();
                spec2.id = "title";
                spec2.name = "曲名";
                spec2.placeholder = "曲名を入力してください";
                spec2.required = true;
                spec2.type = ParameterType.TEXT;
                spec2.options = new DescribeResultPayload.OptionSpec();
                spec2.options.searchable = false;
                spec2.options.provider = OptionProvider.STATIC;


                DescribeResultPayload.ParameterSpec spec3 = new DescribeResultPayload.ParameterSpec();
                spec3.id = "in";
                spec3.name = "入力ソース";
                spec3.placeholder = "入力ソースを入力してください";
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
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // 必須！
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
                        result.items.add(new Label("QQ Music", "qq"));
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



## PluginClient の詳細

```java
    PluginClient client = new PluginClient();
    // 登録情報を設定
    RegisterPayload rp = new RegisterPayload();
    rp.name = "Music"; // アプリ名
    rp.packageName = "com.ultrabar.music"; // 推奨パッケージ名


    // サポートするアクション
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
            // 登録成功
            //
            // SDK とホストアプリが登録を完了すると、payload にセッション情報が返されます。
            //
            // configServer.port:
            //     ホストアプリがこのプラグインに割り当てた HTTP デバッグサーバーのポートです。
            //
            // サードパーティのアプリはこのポートで HTTP サーバーを起動し、プラグインデバッグページを提供する必要があります。
            //
            // ホストアプリがプラグインデバッグページを開くと、sessionToken が付与されます。
            //
            // sessionToken:
            //     現在のプラグインデバッグセッションの認証トークンです。
            //
            // 注意:
            //     sessionToken はホストからサードパーティアプリへ渡されますが、
            //     サードパーティ側で有効性を検証する必要があります。受け取ったトークンをそのまま信用しないでください。
            //
            // HTTP サーバーはここで起動し、payload.configServer.port にバインドすることを推奨します。
            // ============================================================

            int httpPort = payload.configServer.port;
            String sessionToken = payload.sessionToken;

            System.out.println("Register success: session=" + payload.sessionId+",port="+httpPort+",sessionToken=+sessionToken");
        }
    
        @Override
        public void onRegisterFailed(Throwable t) {
            // 登録失敗。
            //
            // 登録に失敗した場合、プラグインはホストアプリと正常に通信できません。
            // この場合、ホストに依存する HTTP / Action サービスは起動しないでください。
            //
            // 実施可能な対処:
            // 1. エラーログを記録
            // 2. ユーザーへ通知
            // 3. SDK の自動再接続を待機
            // 4. 必要に応じて PluginClient を再初期化

            System.err.println("Register failed: " + t.getMessage());
        }
    
        @Override
        public void onActionsFailed(Throwable t) {
            System.err.println("Actions failed: " + t.getMessage());
        }
    
        @Override
        public void onActionsAck(ActionsResultPayload ack) {
            // ============================================================
            // アクション設定の処理結果
            //
            // サードパーティアプリがアクション設定をホストに送信すると、ホストは処理結果を返します。
            //
            // success:
            //     ホストが正常に受信/処理したかどうか
            //
            // receivedCount:
            //     ホストが実際に受領したアクション数
            //
            // 注意:
            //     これはアクション設定同期のACKであり、onCall() の実行結果ではありません。
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
            // アクションのパラメータ記述
            //
            // ホストアプリはアクションを呼び出す前に、actionId を使ってそのアクションのパラメータ定義を取得します。
            //
            // ここではホストに対して以下を伝える必要があります:
            //   1. このアクションが必要とするパラメータ
            //   2. 各パラメータの id / 名称 / 型
            //   3. パラメータが必須かどうか
            //   4. 選択式パラメータの場合、オプションがどこから来るか
            //
            // 定義内のパラメータ id は重要です：
            // 後続の onCall() で受け取る payload.params はこれらの id をキーにします。
            // ============================================================
    
            DescribeResultPayload result = new DescribeResultPayload();
            result.actionId = payload.actionId;
            result.success = true;
            if ("music.play".equals(payload.actionId)) { // 再生アクションのパラメータ定義
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
            } else if ("music.pause".equals(payload.actionId)) { // pause の静的パラメータ例
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
            // 動的パラメータオプション
            //
            // onDescribe() でパラメータの OptionProvider を REMOTE に設定した場合、
            // ホストアプリはそのパラメータのオプションを表示する際にここを呼び出します。
            //
            // payload.actionId  -> 現在のアクション
            // payload.describeId -> 現在のパラメータ id
            //
            // 例:
            //   actionId  = music.play
            //   describeId = deviceId
            //
            // ホストが要求しているのは:
            //   "music.play の deviceId パラメータの選択肢を教えてください"
            //
            // オプション数が多い場合は hasMore / nextCursor でページネーションできます。
            // ============================================================
    
            GetOptionsResultPayload result = new GetOptionsResultPayload();
            result.success = true;
            result.hasMore = false;
            result.nextCursor = null;
    
            if ("music.play".equals(payload.actionId)) { // アクション id
    
                if ("deviceId".equals(payload.describeId)) { // パラメータ id
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
            // アクションの実行と最終結果の返却
            //
            // ユーザーがホストアプリ上でアクションを実行すると、ホストがここを呼び出します。
            //
            // payload.actionId
            //     -> 実行するアクション
            //
            // payload.params
            //     -> onDescribe() で定義したパラメータ
            //
            // 注意:
            // params のキーは onDescribe() 内で定義した ParameterSpec.id と一致する必要があります。
            //
            // 例えば onDescribe() で:
            //     device.id = "deviceId";
            // としている場合、ここでは:
            //     params.get("deviceId");
            // でユーザーが選択したデバイスを取得します。
            // ============================================================
    
            String actionId = payload.actionId;
            Map<String, Object> params = payload.params;
            if ("music.play".equals(actionId)) {
                // onDescribe で定義した ParameterSpec.id
                String deviceId = (String) params.get("deviceId");
                // TODO: 指定されたデバイスで再生を実行
    
            } else if ("music.pause".equals(actionId)) {
                // onDescribe で定義した ParameterSpec.id
                String deviceId2 = (String) params.get("deviceId2");
                // TODO: 指定されたデバイスで一時停止を実行
    
            } else if ("music.stop".equals(actionId)) {
                // TODO: 全デバイスを停止
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

アクションリストが変わった場合は `client.updateActions(new ActionsPayload(...))` を呼び出してください。



## PluginServer（ホストアプリ）

セッションは登録時の `packageName` によって一意です。同一 `packageName` で再登録すると旧接続は切断されます。登録コールバックが `success == true` を返した場合にのみ `PluginSession` が作成されます。

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
        // sessionId / sessionToken / heartbeat が未設定の場合、サーバー側でデフォルト値を補います
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

// セッションの取得
PluginSession session = server.getSession("com.ultrabar.music");

// プラグインアクションの呼び出し（対象セッションが該当 actionId を報告している必要があります）
Map<String, Object> params = new HashMap<String, Object>();
params.put("deviceId", "sp00-1");
server.call("music.play", params)
        .thenAccept(result -> { /* CallResultPayload */ })
        .exceptionally(err -> { err.printStackTrace(); return null; });

// 同名の actionId を持つプラグインが複数ある場合は packageName を指定してください
server.call("com.ultrabar.music", "music.play", params);

// server.stop();
```

## その他
 
### R8 / ProGuard

モデルは public フィールド + Jackson を使用しています。リリースビルドでは以下を keep してください：

```
-keep class com.ultrabar.plugin.model.** { *; }
-keep class com.ultrabar.plugin.callback.** { *; }
-keep class com.ultrabar.plugin.PluginClient { *; }
```
 
 

## プロトコルの要点

- エンベロープ：`type`、`requestId`、`timestamp`、`protocol`、`payload`；登録成功後、クライアントは `sessionId`、`auth` を送ります。
- `requestId` は UUID で、RPC ごとに一度だけ使用します。
- アクション ID フィールドは `actionId` に統一されています。
- 対応するメッセージペア：`register` / `register_result`，`actions` / `actions_result`，`describe` / `describe_result`，`get_options` / `get_options_result`，`call` / `call_result`，`heartbeat` / `heartbe[...]`
