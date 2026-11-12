# Ultrabar プラグイン SDK 利用ガイド

> 言語: [中文](./README_zh.md) | [English](./README.md) | **日本語**

> 対応バージョン: ultrabarIntegrated 1.0.12+（プロトコル version = 2）
> 対象読者: LineOS（Ultrabar）に接続するサードパーティプラグイン開発者
> 前提条件: Android Service と Gradle 依存関係の設定に慣れていること

---

## 目次

1. [SDK 概要](#1-sdk-概要)
2. [クイックスタート](#2-クイックスタート)
3. [コアコンセプトと通信モデル](#3-コアコンセプトと通信モデル)
4. [シナリオアプリケーション機能（アクション登録と実行）](#4-シナリオアプリケーション機能アクション登録と実行)
5. [A2UI 生成的 UI](#5-a2ui-生成的-ui)
6. [モジュールイベント（DOT_KEY / KNOB_KEY）](#6-モジュールイベントdot_key--knob_key)
7. [ホットキーとメッセージ](#7-ホットキーとメッセージ)
8. [コールバック早見表](#8-コールバック早見表)
9. [注意事項とよくある質問](#9-注意事項とよくある質問)

---

## 1. SDK 概要

Ultrabar プラグイン SDK は Netty をベースに、プラグインアプリと LineOS の間の双方向通信を実現します。転送プロトコルは**1行1 UTF-8 JSON**（`\n` でフレーム分割）で、すべてのリクエスト/レスポンスは非同期モデル（`CompletableFuture`）です。

| ロール | クラス | 責務 |
| ------ | ------ | ---- |
| プラグイン側（あなたのアプリ） | `com.ultrabar.plugin.PluginClient` | 登録、アクションの上报、describe / get_options / call の処理、A2UI データの上报 |
| LineOS 側 | `com.ultrabar.server.PluginServer` | プラグインセッションの管理、プラグインへの呼び出し、A2UI データのキャッシュ |

**サードパーティ開発者としては `PluginClient` を統合するだけで済みます。** LineOS は `AndroidManifest.xml` に宣言されたプラグインサービスを定期的にスキャンして自動起動します（既に起動している場合はスキップ）。

プラグイン SDK は4つの主要機能を提供します：

1. **シナリオアプリケーション機能**: プラグインが LineOS に「自分に何ができるか」（再生、一時停止など）を宣言し、ユーザー操作時に LineOS がプラグインに実行をコールバックします。
2. **A2UI 生成的 UI**: プラグインが JSON で UI 構造とデータを記述し、LineOS がネイティブ画面として描画、ユーザー操作がプラグインに返されます。
3. **モジュールイベント**: LineOS ハードウェアモジュール（DOT_KEY キー、KNOB_KEY ノブなど）のリアルタイムイベントを受信します。
4. **ホットキーとメッセージ**: PC 側にキーストロークを注入（単キー/コンビネーション/長押し）したり、LineOS と双方向メッセージをやり取り（文字列と構造化オブジェクトをサポート）します。

---

## 2. クイックスタート

### 2.1 依存関係の追加

`build.gradle`:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.yaobin-kid:ultrabarIntegrated:1.1.0'
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

> ⚠️ `packagingOptions` の2つの `exclude` は**必須設定**です。設定しないと、パッケージング時に Netty リソースファイルが競合してビルドが失敗します。

### 2.2 AndroidManifest の設定

LineOS がプラグインを発見して起動するためのバックグラウンドサービスを宣言します：

```xml
<manifest ...>
    <!-- ネットワーク権限（ローカルループバック通信でも必要） -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application ...>
        <service
            android:name=".service.BackgroundService"
            android:enabled="true"
            android:exported="true"
            android:permission="com.ultrabar.plugin.SERVER_REGISER_PERMISSION">
            <!-- value は applicationId と一致させる必要がある -->
            <meta-data android:name="ultrabar.plugin" android:value="com.example.music" />
        </service>
    </application>
</manifest>
```

**3つの必須要件：**

| 設定 | 説明 |
| ---- | ---- |
| `meta-data android:value` | `applicationId` と一致させる必要がある。LineOS はこれをプラグインの一意識別子（packageName）として使用 |
| `android:exported="true"` | エクスポート必須。でないと LineOS がサービスを起動できない |
| `android:permission` | `com.ultrabar.plugin.SERVER_REGISER_PERMISSION` 固定。他のアプリによるなりすましを防止 |

### 2.3 最小実行可能デモ

サービスの `onCreate()` で `PluginClient` を初期化して起動します：

```java
public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    private static final String CHANNEL_ID = "plugin_bg_channel";

    private PluginClient client;

    @Override
    public void onCreate() {
        super.onCreate();

        // Android 8.0+ フォアグラウンドサービス要件: 通知チャネル + startForeground
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("プラグインサービス実行中")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build();
        startForeground(1001, notification);

        startPluginClient();
    }

    private void startPluginClient() {
        client = new PluginClient(); // デフォルト 127.0.0.1:39001

        // 1. 登録情報: name は表示名、packageName は applicationId と一致させる
        RegisterPayload rp = new RegisterPayload();
        rp.name = "サンプル音楽プラグイン";
        rp.packageName = "com.example.music";
        client.setRegisterConfig(rp);

        // 2. サポートするアクションリストを宣言
        ActionSummary play = new ActionSummary();
        play.actionId = "music.play";
        play.name = "再生";
        play.description = "指定デバイスで音楽を再生";

        ActionSummary pause = new ActionSummary();
        pause.actionId = "music.pause";
        pause.name = "一時停止";
        pause.description = "指定デバイスの再生を一時停止";

        client.setActionsConfig(new ActionsPayload(Arrays.asList(play, pause)));

        // 3. リスナーを設定（すべてのコールバックはオプションの override）
        client.setPluginListener(new PluginListener() {
            @Override
            public void onRegisterSuccess(RegisterResultPayload result) {
                Log.d(TAG, "登録成功, デバッグポート: " + result.configServer.port);
            }

            @Override
            public void onRegisterFailed(Throwable t) {
                Log.e(TAG, "登録失敗: " + t.getMessage());
            }

            @Override
            public void onCall(CallPayload call, CallResponder responder) {
                if ("music.play".equals(call.actionId)) {
                    // 再生を実行……
                }
                responder.sendSuccess(null);
            }
        });

        // 4. 非同期起動: 接続 → 登録 → アクション上报、全自動で完了
        client.startAsync();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "プラグインバックグラウンドサービス", NotificationManager.IMPORTANCE_LOW);
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

以上3ステップで、プラグインは LineOS に発見され通信可能になります。

---

## 3. コアコンセプトと通信モデル

### 3.1 接続ライフサイクル

```
startAsync()
   │
   ▼
TCP 接続（127.0.0.1:39001）
   │
   ▼
REGISTER ─────────► LineOS が packageName を検証
   ◄───────── REGISTER_RESULT（sessionId / sessionToken / configServer.port）
   │
   ▼
ACTIONS ──────────► アクションリストを上报
   ◄───────── ACTIONS_RESULT（success / receivedCount）
   │
   ▼
セッション確立 ◄──── onRegisterSuccess が発火
   │
   ▼
ハートビート（自動）＋ 切断自動再接続（再接続後に自動で再登録＋アクション再上报）
```

**重要ポイント：**

- `startAsync()` は `CompletableFuture<Void>` を返し、接続確立で完了。登録結果は `onRegisterSuccess` / `onRegisterFailed` コールバックで通知されます。
- SDK にはハートビートと自動再接続が組み込まれています。**再接続成功後は自動的に再登録しアクションを再上报するため、手動処理は不要です。**
- `onRegisterSuccess` の `payload.configServer.port` は LineOS が割り当てた HTTP デバッグポートです。プラグインデバッグページを提供する場合は、このポートで HTTP サーバーを起動してください。

### 3.2 sessionToken セキュリティ

`onRegisterSuccess` で `sessionToken` が配布されます——現在のデバッグセッションの身分証明です。LineOS がデバッグページを開く際にこれを携帯します。

> ⚠️ **Token の妥当性は自分で検証する必要があります**。Token を携帯しているリクエストを無条件に信用しないでください。

### 3.3 スレッドモデル

すべての `PluginListener` コールバックは SDK のコールバックスレッドプール（Android メインスレッド以外）で実行されます。**UI 操作が必要な場合は自分でメインスレッドに切り替えてください：**

```java
new Handler(Looper.getMainLooper()).post(() -> {
    // UI 操作
});
```

### 3.4 セッションの一意性

LineOS は `packageName` でプラグインセッションを一意に識別します。同じ `packageName` で再登録すると、古い接続は上書きされます。

---

## 4. シナリオアプリケーション機能（アクション登録と実行）

Action はプラグインが LineOS に公開する「能力ポイント」です。完全なフローは4ステップ：

```
プラグインが actionId リストを上报 ──► LineOS がパラメータ記述を問い合わせ(onDescribe)
   ──► （オプション）LineOS が動的オプションを取得(onOptions) ──► ユーザー操作後に LineOS が実行をコールバック(onCall)
```

### 4.1 アクションの登録

```java
ActionSummary play = new ActionSummary();
play.actionId = "music.play";        // グローバルで一意の ID、「ドメイン.動詞」形式推奨
play.name = "再生";                   // LineOS 表示名
play.description = "指定デバイスで音楽を再生";

client.setActionsConfig(new ActionsPayload(Arrays.asList(play, pause, stop)));
```

実行中にアクションリストが変わった場合は呼び出し：

```java
client.updateActions(new ActionsPayload(newList));
```

上报結果は `onActionsAck(ActionsResultPayload ack)` で通知されます：

```java
@Override
public void onActionsAck(ActionsResultPayload ack) {
    // ack.success        — LineOS が正常に受信したか
    // ack.receivedCount  — 実際に受信したアクション数
    // 注意: これは「設定同期」の ACK であり、onCall の実行結果ではない
}
```

### 4.2 パラメータ記述 onDescribe

LineOS は Action を呼び出す前に、まず `onDescribe` でその Action に必要なパラメータを取得します（設定画面の描画用）：

```java
@Override
public void onDescribe(DescribePayload payload, DescribeResponder responder) {
    DescribeResultPayload result = new DescribeResultPayload();
    result.actionId = payload.actionId;
    result.success = true;
    result.parameters = new ArrayList<>();

    if ("music.play".equals(payload.actionId)) {
        // パラメータ 1: デバイス選択（静的オプション）
        DescribeResultPayload.ParameterSpec device = new DescribeResultPayload.ParameterSpec();
        device.id = "deviceId";            // ★ 重要: onCall でこの id を使って値を取得
        device.name = "デバイス";
        device.placeholder = "デバイスを選択";
        device.required = true;
        device.type = ParameterType.SELECT;

        DescribeResultPayload.OptionSpec deviceOpts = new DescribeResultPayload.OptionSpec();
        deviceOpts.provider = OptionProvider.STATIC;   // 静的オプション、記述と一緒に配信
        deviceOpts.searchable = false;
        deviceOpts.items = new ArrayList<>();
        deviceOpts.items.add(new Label("リビングプレーヤー", "test01"));
        deviceOpts.items.add(new Label("寝室プレーヤー", "test02"));
        device.options = deviceOpts;

        // パラメータ 2: 曲名（テキスト入力）
        DescribeResultPayload.ParameterSpec title = new DescribeResultPayload.ParameterSpec();
        title.id = "title";
        title.name = "曲名";
        title.placeholder = "曲名を入力";
        title.required = true;
        title.type = ParameterType.TEXT;

        // パラメータ 3: 入力ソース（動的オプション、deviceId に依存）
        DescribeResultPayload.ParameterSpec source = new DescribeResultPayload.ParameterSpec();
        source.id = "source";
        source.name = "入力ソース";
        source.required = true;
        source.type = ParameterType.SELECT;
        DescribeResultPayload.OptionSpec sourceOpts = new DescribeResultPayload.OptionSpec();
        sourceOpts.provider = OptionProvider.REMOTE;   // 動的オプション、onOptions コールバックをトリガー
        sourceOpts.searchable = false;
        source.options = sourceOpts;
        source.dependsOn = new ArrayList<>();
        source.dependsOn.add("deviceId");              // 依存パラメータ: deviceId が変わると再取得

        result.parameters.add(device);
        result.parameters.add(title);
        result.parameters.add(source);
    }

    responder.sendSuccess(result);
}
```

### 4.3 動的オプション onOptions

パラメータに `OptionProvider.REMOTE` が宣言されている場合のみ、LineOS がこのメソッドをコールバックしてオプションを取得します：

```java
@Override
public void onOptions(GetOptionsPayload payload, OptionsResponder responder) {
    GetOptionsResultPayload result = new GetOptionsResultPayload();
    result.success = true;
    result.hasMore = false;
    result.nextCursor = null;

    if ("music.play".equals(payload.actionId) && "source".equals(payload.describeId)) {
        result.items = new ArrayList<>();
        result.items.add(new Label("QQ 音楽", "qq"));
        result.items.add(new Label("NetEase 音楽", "163"));
    }

    responder.sendSuccess(result);
}
```

- `payload.actionId`: 現在の Action。`payload.describeId`: 現在のパラメータの id。
- オプションが多い場合は `hasMore = true` + `nextCursor` でページングを実現。LineOS がページをめくる際はリクエストに `cursor` を付与します。

### 4.4 実行コールバック onCall

ユーザーが LineOS で Action をトリガーした後にここにコールバックされます：

```java
@Override
public void onCall(CallPayload payload, CallResponder responder) {
    Map<String, Object> params = payload.params;

    if ("music.play".equals(payload.actionId)) {
        String deviceId = (String) params.get("deviceId"); // key は ParameterSpec.id と一致させる
        String title = (String) params.get("title");
        String source = (String) params.get("source");
        // TODO: 実際の再生ロジックを実行
    }

    // 戻り値（オプション）、LineOS はこの Map を受け取れる
    Map<String, Object> data = new HashMap<>();
    data.put("status", "ok");
    responder.sendSuccess(data);

    // 失敗時:
    // responder.sendError("PLAY_FAILED", "デバイスがオフライン", false);
}
```

> ⚠️ **`params` の key は `onDescribe` で定義した `ParameterSpec.id` と完全に一致させる必要があります**。これは両者の暗黙の契約です。

---

## 5. A2UI 生成的 UI

A2UI はプラグインが JSON で UI を記述し、LineOS がネイティブ画面として描画します：

- **プラグイン → LineOS**: UI 構造の上报（surface 作成 / コンポーネントツリー）、データモデルの更新
- **LineOS → プラグイン**: ユーザー操作（ボタンクリックなど）を action として返却

> 📖 **重要: プロトコルと描画の詳細は元のライブラリドキュメントに従う**
>
> 本 SDK（ultrabarIntegrated）は**プラグイン ↔ LineOS 間の A2UI メッセージ転送チャネル**を担当するだけです。A2UI JSON のフォーマット仕様、コンポーネントカタログ、データバインディング構文、Action セマンティクスは、LineOS に統合された描画ライブラリ **[a2ui-android](https://github.com/yaobin-kid/a2ui-android)** によって実装・定義されています。
>
> **A2UI JSON を書く前に、必ず元のライブラリドキュメントをお読みください: [https://github.com/yaobin-kid/a2ui-android](https://github.com/yaobin-kid/a2ui-android)**
>
> - プロトコルバージョン: **A2UI v0.9.1**
> - 公式仕様: [A2UI v0.9.1 Specification](https://a2ui.org/specification/v0.9-a2ui/)
> - Basic Catalog には **18 種類のコンポーネント**があります: レイアウト（Row、Column、List、Card、Tabs、Modal）、表示（Text、Image、Icon、Video、AudioPlayer、Divider）、入力（Button、TextField、CheckBox、ChoicePicker、Slider、DateTimeInput）

> ⚠️ **プロトコルバージョンの必須要件**: すべての A2UI JSON は **v0.9.1** 形式である必要があり、各 envelope には `"version": "v0.9.1"` フィールドが必要です。v0.8 の `beginRendering` / `surfaceUpdate` / `dataModelUpdate` というメッセージ名は使用しないでください。

### 5.1 A2UI を有効にする

登録時に `supportsA2UI = true` を宣言します：

```java
RegisterPayload rp = new RegisterPayload();
rp.name = "サンプルプラグイン";
rp.packageName = "com.example.music";
rp.supportsA2UI = true;   // ★ キーとなるスイッチ
client.setRegisterConfig(rp);
```

**宣言しない場合の影響**: LineOS からの `callA2UIAction` / `queryA2UIData` / `notifyA2UIRender` 呼び出しはサーバー側で拒否されます。ただしプラグインから LineOS への能動的なデータ上报は影響を受けません。

### 5.2 A2UI メッセージタイプ対照

| Envelope キー | 方向 | SDK API | 用途 |
| ------------- | ---- | ------- | ---- |
| `createSurface` | プラグイン→LineOS | `refreshA2UI` | UI surface を作成 |
| `updateComponents` | プラグイン→LineOS | `refreshA2UI` | コンポーネントツリーを定義/更新 |
| `updateDataModel` | プラグイン→LineOS | `sendA2UIUpdateDataModel` | データモデルを書き込み/更新/削除 |
| `deleteSurface` | プラグイン→LineOS | `refreshA2UI` | surface を削除 |
| `action`（event） | LineOS→プラグイン | コールバック `onA2UIActionCall` | ユーザー操作の返却 |
| 描画通知 | LineOS→プラグイン | コールバック `onA2UIRender` | カード初回描画/更新通知 |

### 5.3 既存ビューの更新 refreshA2UI

`refreshA2UI` は**既に存在する** A2UI ビューを更新するためのものです：`instanceId + layoutId` で既に描画されたビューインスタンスを特定し、新しい `ui` データで内容を置き換えます。
（カードの初回定義はサーバー側の `queryA2UIData` で取得され、プラグインは `onA2UIDataRequest` で完全な `A2UIItem` を返します。§5.4 を参照。）

```java
// A2UIRefreshItem: instanceId（インスタンスID）+ layoutId（レイアウトID）+ ui（A2UI メッセージリスト）
// 3 つのパラメータは作成時にすべて必須。ui は JSON 配列（List<Map<String, Object>>）：
//   [{createSurface...}, {updateComponents...}, {updateDataModel}...]
// ui はオーバーロードをサポート: 構造化配列を直接渡すか、JSON 文字列を渡して SDK が内部で解析
// （単一の JSON オブジェクト文字列も自動的に単一要素配列にラップされる）
String ui = "["
    + "{\"version\":\"v0.9.1\",\"updateComponents\":{"
    + "   \"surfaceId\":\"example-focus\","
    + "   \"components\":["
    + "     {\"id\":\"root\",\"component\":\"Card\",\"child\":\"content\"},"
    + "     {\"id\":\"content\",\"component\":\"Text\","
    + "      \"text\":{\"path\":\"/headline\"},\"variant\":\"h3\"}"
    + "   ]}}"
    + "]";

// 方法1: 文字列（内部で JSON 配列に自動解析）
client.refreshA2UI(new A2UIRefreshItem("inst-example", "example-focus", ui))
      .thenAccept(ack -> Log.d(TAG, "更新結果: " + ack.success));

// 方法2: 構造化配列を直接構築（推奨、文字列連結のミスを回避）
Map<String, Object> updateComponents = new HashMap<>();
// ... A2UI メッセージ構造を設定
client.refreshA2UI(new A2UIRefreshItem("inst-example", "example-focus", uiList));

// 複数ビューを一括更新:
// client.refreshA2UI(Arrays.asList(item1, item2));
```

サーバーは受信後に `PluginServerListener.onA2UIRefresh(session, payload)` コールバックを発火します。更新はローカルな内容置き換えであり、`PluginSession` にキャッシュされた完全なカード定義（`a2uiData`）を**上書きしません**。

**記述上のポイント（詳細は元のライブラリドキュメントを参照）：**

- ルートコンポーネントの ID は**必ず `root`** にしてください。コンポーネント同士は ID で参照し合います（例: `Card.child = "content"`）
- `catalogId` には v0.9.1 Basic Catalog を使用: `https://a2ui.org/specification/v0_9_1/catalogs/basic/catalog.json`
- `createSurface.theme.primaryColor` は v0.9.1 のテーマフィールド（`#RRGGBB` / `#AARRGGBB`）で、Icon、Button、Tabs および入力コントロールの選択/フォーカス状態に適用されます。デフォルトは `#3F51B5`
- `createSurface.sendDataModel = true` は、操作返却時に dataModel スナップショット全体を携帯することを意味します

**推奨タイミング**: `onRegisterSuccess` コールバック内で現在の UI スナップショットを上报し、LineOS が最新状態を取得できるようにします。

### 5.4 データバインディング（概要）

各 Surface は内部に JSON オブジェクト `dataModel` を保持しています。コンポーネントの動的プロパティは `{ "path": "/xxx" }` で dataModel フィールドにバインドされ、`updateDataModel` で更新するとバインドされたコンポーネントが自動的に再描画されます。

**path は [RFC 6901 JSON Pointer](https://tools.ietf.org/html/rfc6901) に従います：**

| 表記 | 意味 |
| ---- | ---- |
| `"/message"` | ルートオブジェクトの `message` フィールド |
| `"/user/name"` | `user` オブジェクト配下の `name` フィールド |
| `"/items/0/title"` | 配列 `items` の 0 番目の `title` |
| `"/items/-"` | 配列末尾の追加位置（書き込み時のみ） |

エスケープルール: `~` は `~0`、`/` は `~1` と記述します。

**2 つのバインディング記法（使用場面の区別に注意）：**

| 記法 | 形式 | 適用場所 |
| ---- | ---- | -------- |
| オブジェクト形式 | `{ "path": "/form/email" }` | コンポーネントプロパティ（`Text.text`、`Image.url`、`Slider.value` など） |
| 文字列短縮形 | `"#/form/email"` | **`action.event.context` と `action.functionCall.args` のみ** |

**リスト動的描画:** `Row`/`Column`/`List` の `children` は `{ "path": "/配列パス", "componentId": "テンプレートコンポーネントID" }` をサポートし、エンジンが配列を走査して各要素にテンプレートインスタンスを生成します。テンプレートコンポーネント内では**相対パス**（`/` なし）を記述でき、現在の配列要素パスの前に自動的に付与されます。

> 📖 詳細は元のライブラリドキュメント「[データバインディング](https://github.com/yaobin-kid/a2ui-android#4-数据绑定data-model-binding)」の章をご覧ください。

### 5.5 データ更新 sendA2UIUpdateDataModel

UI 構造は変わらず、バインドされたデータだけを更新する場合は、envelope 全体を再送する必要はありません。`updateDataModel` メッセージ本体には `surfaceId`（必須）、`path`（オプション）、`value`（オプション）の3つのフィールドがあり、3 つの使い方に対応します。

メソッドシグネチャ: `sendA2UIUpdateDataModel(instanceId, layoutId, dataModel)`、`incremental` 付きのオーバーロードもあります（デフォルト `true` で増分マージ、`false` で全体置換）：

```java
// 使い方1: path なし、value がオブジェクト → dataModel を全体置換
String full = "{\"version\":\"v0.9.1\",\"updateDataModel\":{"
    + "\"surfaceId\":\"example-focus\","
    + "\"value\":{\"headline\":\"新しいタイトル\",\"progress\":\"2 / 3\"}"
    + "}}";
client.sendA2UIUpdateDataModel("inst-example", "example-focus", full);

// 使い方2: path + value → 指定パスだけ更新（途中のパスがなければ自動作成）
String partial = "{\"version\":\"v0.9.1\",\"updateDataModel\":{"
    + "\"surfaceId\":\"example-focus\","
    + "\"path\":\"/progress\",\"value\":\"3 / 3\"}}";
client.sendA2UIUpdateDataModel("inst-example", "example-focus", partial);

// 配列に要素を追加（path は /items/-）:
// "{\"path\":\"/items/-\",\"value\":{\"title\":\"新しいタスク\"}}"

// 使い方3: path あり value なし → そのパスを削除
String del = "{\"version\":\"v0.9.1\",\"updateDataModel\":{"
    + "\"surfaceId\":\"example-focus\",\"path\":\"/progress\"}}";
client.sendA2UIUpdateDataModel("inst-example", "example-focus", del);

// 全体置換（旧データをマージしない）:
// client.sendA2UIUpdateDataModel("inst-example", "example-focus", full, false);
```

**処理結果の確認:** 戻り値 `A2UIUpdateDataModelResultPayload` の `success=false` は LineOS が処理に失敗したことを意味します。プラグイン側は盲目的に成功とせず ack を確認してください：

```java
client.sendA2UIUpdateDataModel("inst-example", "example-focus", update).thenAccept(ack -> {
    if (!ack.success) {
        Log.e(TAG, "データ更新が LineOS に拒否されました: " + (ack.error != null ? ack.error.message : "unknown"));
    }
});
```

### 5.6 イベント返却 onA2UIActionCall

A2UI コンポーネントの `action` には**3 つの形式**があり、`event` 形式のみがプラグインに返却されます：

| 形式 | トリガーコンポーネント | 実行者 | プラグインに返却されるか |
| ---- | --------------------- | ------ | ------------------------ |
| `event` | Button / Modal trigger | LineOS → **プラグインに転送** | ✅ `onA2UIActionCall` を通る |
| `functionCall` | Button / 任意のコンポーネント | Surface 内蔵/登録関数（`formatString`、`formatCurrency` など） | ❌ 描画層内部で実行 |
| `input` | TextField / CheckBox / Slider などの入力コントロール | Surface 内部（dataModel に書き戻し） | ❌ dataModel に自動書き戻し |

**ユーザー操作を受け取るには `event` 形式を使用する必要があります：**

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

**フィールドマッピング（event → A2UIActionCallPayload）：**

| A2UI event フィールド | プラグイン側 payload フィールド | 説明 |
| --------------------- | ------------------------------- | ---- |
| `event.name` | `payload.actionName` | イベント名、例: `"submitForm"` |
| `event.context` | `payload.context` | `Map<String, Object>`。`#/path` 参照は** dataModel の実際の値に置換済み**のため、再解析は不要 |
| `surfaceId` | `payload.layoutId` | イベントソースのレイアウト id |
| —— | `payload.instanceId` | イベントソースのカードインスタンス id |
| —— | `payload.cardId` | イベントソースのカード id |

プラグイン側の受信：

```java
@Override
public void onA2UIActionCall(A2UIActionCallPayload payload, A2UIActionCallResponder responder) {
    String actionName = payload.actionName;          // event.name、例: "submitForm"
    Map<String, Object> context = payload.context;   // event.context（#/path は実際の値に解決済み）
    String instanceId = payload.instanceId;          // ソースカードインスタンス
    String layoutId = payload.layoutId;              // ソース surfaceId

    if ("submitForm".equals(actionName)) {
        String email = context == null ? null : (String) context.get("email");
        // TODO: 操作ロジックを処理

        Map<String, Object> reply = new HashMap<>();
        reply.put("handled", true);
        responder.sendSuccess(reply);

        // 処理完了後、通常は UI を更新する:
        // client.sendA2UIUpdateDataModel(instanceId, layoutId, updateJson);
        return;
    }

    // 未知の action: NO_HANDLER を返し、LineOS はフォールバックロジックに進める
    responder.sendError("NO_HANDLER", "no handler for " + actionName, false);
}
```

> 📖 3 つの action 形式の完全なセマンティクス、内蔵関数リスト、カスタム関数の登録については、元のライブラリドキュメント「[Action の使い方](https://github.com/yaobin-kid/a2ui-android#34-action-用法button-交互)」の章をご覧ください。

### 5.7 LineOS からの能動的取得 onA2UIDataRequest

プラグインが能動的にプッシュするほか、LineOS もプラグインの現在の A2UI データを能動的に取得します（例: 登録成功後に自動で1回取得）。このコールバックを実装して現在のスナップショットを返してください：

```java
@Override
public List<A2UIItem> onA2UIDataRequest() {
    List<A2UIItem> items = new ArrayList<>();
    // A2UIItem は4パラメータ必須: layoutId / cardId / name / ui
    items.add(new A2UIItem("example-focus", "card-example", "サンプル", currentUiJson));
    return items;   // null または空リストを返すとデータなし
}
```

> 📌 取得成功後、LineOS はデータをプラグインセッションに自動キャッシュします。このコールバックを実装しない場合は、デフォルトで空リストが返されます。

### 5.8 カードパラメータ schema（args）

`A2UIItem.args` は、ユーザーがどのフィールドを設定可能か、どのコントロールで描画するかを宣言します。6 種類の型それぞれに**専用 Builder** があり、その型自身のフィールドしか設定できません（コンパイル時に分離、クロス設定不可）：

| 型 | Builder | 設定可能 |
| -- | ------- | -------- |
| `int` | `intArg()` | `label` `required` `defaultValue(int)` `min(int)` `max(int)` |
| `float` | `floatArg()` | `label` `required` `defaultValue(double)` `min(double)` `max(double)` |
| `bool` | `boolArg()` | `label` `required` `defaultValue(boolean)` |
| `string` | `stringArg()` | `label` `required` `defaultValue(String)` |
| `select` | `selectArg()` | `label` `required` `defaultValue(String)` `option(value)` / `option(value, label)` |
| `object` | `objectArg()` | `label` `required` `property(name, A2UIArgSpec)`（再帰的ネスト） |

```java
A2UIArgs args = A2UIArgs.create()
        .arg("title", A2UIArgSpec.stringArg().label("タイトル").defaultValue("パラメータデモ").build())
        .arg("count", A2UIArgSpec.intArg().label("個数").defaultValue(3).min(1).max(99).build())
        .arg("level", A2UIArgSpec.selectArg().label("レベル").defaultValue("normal")
                .option("low", "低").option("normal", "中").build())
        .arg("headers", A2UIArgSpec.objectArg().label("ネストパラメータグループ")
                .property("retries", A2UIArgSpec.intArg().defaultValue(3).max(10).build())
                .build());

// A2UIItem は onA2UIDataRequest の照会返却に使用（args パラメータ schema 付き）
new A2UIItem("example-focus", "card-example", "サンプル", ui, args);
```

**動的 schema**: パラメータをプラグインがリアルタイムで決定する場合は `dynamic + schemaGetter` を宣言します。LineOS はフォーム描画前にパラメータを取得し、プラグインは `PluginListener.onA2UIArgsSchemaRequest(schemaGetter)` で `A2UIArgs` を直接返します：

```java
// プラグイン宣言: 動的パラメータ
A2UIArgs args = A2UIArgs.dynamic("schema.argsdemo");

// プラグイン側: 取得に応答し、A2UIArgs をリアルタイムで生成
@Override
public A2UIArgs onA2UIArgsSchemaRequest(String schemaGetter) {
    return A2UIArgs.create()
            .arg("fieldA", A2UIArgSpec.stringArg().label("フィールドA").defaultValue("v1").build())
            .arg("fieldB", A2UIArgSpec.intArg().label("フィールドB").min(1).max(100).build());
    // null を返すと設定可能パラメータなしとみなされ、サーバーは空の A2UIArgs に正規化
}
```

静的フィールドと動的宣言は共存できます：静的フィールドが先に描画され、動的フィールドは getter が返した schema とマージされて追加されます。

### 5.9 描画通知 onA2UIRender

LineOS はカードの**初回描画時**と**更新描画時**に、プラグインに能動的に通知します（`PluginServer.notifyA2UIRender`）。プラグインは `PluginListener.onA2UIRender` で受信します：

```java
@Override
public void onA2UIRender(A2UIRenderPayload payload, A2UIRenderResponder responder) {
    // payload.instanceId  カードインスタンス id
    // payload.layoutId    レイアウト id
    // payload.cardId      カード id
    // payload.args        今回の描画で有効となった args パラメータ値（Map、null の場合あり）
    // payload.stage       FIRST_RENDER 初回描画 / REFRESH 更新描画

    if (payload.stage == A2UIRenderStage.FIRST_RENDER) {
        // 初回描画: 必要に応じて初期データを取得、内容を更新など
    }

    responder.sendSuccess();
}
```

サーバー側の呼び出し：

```java
server.notifyA2UIRender(packageName, instanceId, layoutId, cardId, argsMap,
        A2UIRenderStage.FIRST_RENDER);
```

> 📌 このコールバックを実装しない場合はデフォルトで `NO_HANDLER` を返します。通知を受け取るにはプラグインが `supportsA2UI=true` を宣言している必要があります。

### 5.10 参考資料

| 資料 | リンク | 説明 |
| ---- | ------ | ---- |
| a2ui-android 描画ライブラリ | [github.com/yaobin-kid/a2ui-android](https://github.com/yaobin-kid/a2ui-android) | **A2UI JSON 記述の権威あるドキュメント**: コンポーネントカタログ、スタイル、データバインディング、Action、関数 |
| A2UI 公式仕様 | [a2ui.org/specification/v0.9-a2ui](https://a2ui.org/specification/v0.9-a2ui/) | プロトコル仕様原文 |
| RFC 6901 JSON Pointer | [tools.ietf.org/html/rfc6901](https://tools.ietf.org/html/rfc6901) | データバインディング path 構文 |

---

## 6. モジュールイベント（DOT_KEY / KNOB_KEY）

LineOS のハードウェアモジュール（キー、ノブなど）が発生させたイベントをリアルタイムでプラグインに配信できます。

### 6.1 モジュールイベントの購読

```java
// 可変長引数、必要なモジュールタイプを渡す
client.subscribeModuleEvents(ModuleType.DOT_KEY, ModuleType.KNOB_KEY);

// 購読解除
// client.unsubscribeModuleEvents(ModuleType.DOT_KEY);
```

> 📌 購読関係は LineOS 側のセッションに保存されます。切断/再接続後も SDK が自動的に再購読するため、手動処理は不要です。

### 6.2 配信ルール（重要）

モジュールイベントは**ブロードキャストではなく**、LineOS が優先度に従って**シリアルユニキャスト**します：候補プラグインリストから順に問い合わせ、いずれかのプラグインがイベントを「消費」するまで続けます。誰も消費しない場合はフォールバックフローに進みます。

完全な配信フロー：

```
モジュールイベント発生（キー押下 / ノブ回転）
   │
   ▼
① 候補プラグインをフィルタリング
   · subscribeModuleEvents で当該 ModuleType を購読済み
   · かつ現在の接続が生存している
   │
   ▼
② 候補をソート（優先度 高→低）
   · フォアグラウンドプラグインが優先、複数ある場合は「最近フォアグラウンドになった時刻」降順
   · バックグラウンドプラグインは後、複数ある場合は「最近アクティブだった時刻」降順
   │
   ▼
③ シリアルユニキャスト: ソート順に候補プラグインへ送信
   · 各プラグインが受け取るイベントの appState フィールドは「自分自身」の
     フォア/バックグラウンド状態（foreground / background）
   · 各候補には独立した返信タイムアウト（デフォルト 1000ms）がある
   │
   ├── プラグインが responder.consume() を呼ぶ ──► ④a 配信終了
   │                                          結果: handled、消費したパッケージ名を記録
   │
   ├── プラグインが responder.ignore() を呼ぶ ──► 次の候補を試行
   │
   └── タイムアウトで未返信（ignore とみなす）──────► 次の候補を試行
   │
   ▼（全候補が消費しなかった場合）
⑤ フォールバックフロー: イベントの appState を none に設定
   ├── 当該 ModuleType に registerFallback コールバックが登録されている場合
   │   ──► 登録されたすべてのフォールバックコールバックを順に実行
   └── それ以外 ──► PluginServerListener.onModuleEventFallback(event)
```

**ルールの要点：**

| ルール | 説明 |
| ------ | ---- |
| ブロードキャストではなくユニキャスト | 1 つのイベントは最大 1 つのプラグインにしか消費されない。上位プラグインが `consume()` した後、後続のプラグインはそのイベントを**受信しない** |
| 優先度 | フォアグラウンド > バックグラウンド。同階層内は最近フォアグラウンド/最近アクティブが優先。 |
| appState の意味 | イベント内の `appState` は**受信者自身の状態**（foreground/background）であり、イベントソースの状態ではない。フォールバック時は `none` |
| 未返信 = ignore | `onModuleEvent` 内で `consume()` も `ignore()` も呼ばないと、タイムアウト（デフォルト 1s）後に自動的に放棄とみなされ、イベントは次の候補に流れる |

### 6.3 イベント受信 onModuleEvent

```java
@Override
public void onModuleEvent(ModuleEventPayload payload, ModuleEventResponder responder) {
    // payload.appState は現在のプラグイン自身のフォア/バックグラウンド状態（6.2 配信ルール参照）
    if (ModuleEventPayload.APP_STATE_FOREGROUND.equals(payload.appState)) {
        // フォアグラウンド: イベントを消費、配信は即座に終了し、後続プラグインとフォールバックは発火しない
        responder.consume();
        // TODO: キー/ノブイベント payload.event を処理
    } else {
        // バックグラウンド: 明示的に放棄、イベントは即座に次の候補へ（1s のタイムアウト待ちを回避）
        responder.ignore(null);
    }
}
```

**イベント処理ルール：**

- `responder.consume()` — プラグインがイベントを消費、配信終了
- `responder.ignore(null)` — 明示的に放棄、イベントは**即座に**次の候補へ
- どちらも呼ばない — タイムアウト（デフォルト 1s）後に受動的に放棄され、同様に次の候補へ。**配信チェーン全体の遅延を避けるため、処理しない場合は明示的に `ignore()` を呼ぶことを推奨**

### 6.4 イベントデータフォーマット

**DOT_KEY（キー）：**

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

- `event.action`: 1=押下、0=解放
- `event.keyCode`: キーコード、値は 0 / 1 / 2
- `event.repeatCount`: 長押しでインクリメント

**KNOB_KEY（ノブ + キー）：**

```json
// ノブイベント（回転が速いほど報告頻度が上がる）
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

- `action`: `button`=キー（フォーマットは DOT_KEY と同じ）、`rotate`=回転
- `event.value`: 1=時計回り、-1=反時計回り

**SENSE_CUBE（多機能センシングキューブ）：**

照度（`action = "illuminance"`）：

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

温度（`action = "temperature"`）：

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

存在検知（`action = "presence"`）：

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

湿度（`action = "humidity"`）：

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

- `action`: `illuminance`=照度、`temperature`=温度、`presence`=存在検知、`humidity`=湿度
- `event.value`: 現在のセンサー値（`presence` は `boolean`、true=人を検知）
- `event.unit`: 計測単位（Lux / °F / %）
- `event.level`: 体感/照度レベル（`BRIGHT`、`MILD` など）、一部のイベントに存在

### 6.5 モジュール一覧の照会 queryModules

```java
// 全件照会
client.queryModules().thenAccept(result -> {
    for (ModuleInfo m : result.modules) {
        Log.d(TAG, m.module + " / " + m.moduleId + " / " + m.name
                + " / online=" + m.online + " / sn=" + m.sn);
    }
});

// オンライン / オフラインのみ
// client.queryModules(ModuleOnlineFilter.ONLINE);
// client.queryModules(ModuleOnlineFilter.OFFLINE);
```

戻り構造：

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

> 現在サポートしているモジュールは `DOT_KEY`、`KNOB_KEY`、`SENSE_CUBE` の3種類のみです。

---

## 7. ホットキーとメッセージ

本章ではプラグイン ↔ PC の2つの直通機能を紹介します：

- **ホットキー**: PC 側にキーボードキーストロークを注入。単キー、コンビネーション、長押しをサポート（Windows の Ctrl+C コピーのシミュレートなど）。
- **メッセージチャネル**: プラグインと LineOS の間で双方向メッセージをやり取り（インスタンス id でアドレッシング、内容は文字列と構造化オブジェクトをサポート）。

### 7.1 キーコードの約束

ホットキー API のパラメータは**生のキーコード（int）**で、ホストプラットフォームと一致します——PC 側では Windows VK 仮想キーコードです。キーコードの完全な対照表は [hidKey.md](./hidKey.md) をご覧ください。

### 7.2 短押し sendHotkey

`sendHotkey(int... keys)` は1回の完全な「押して離す」を表し、**SDK が内部で down + up を自動的に補完**するため、ペアで呼び出す必要はありません：

```java
// 単キー: F1 を1回押す
client.sendHotkey(0x70);

// コンビネーション: Windows の貼り付け Ctrl+V をシミュレート（修飾キーを先に）
client.sendHotkey(0xE0, 0x19).thenAccept(ack -> {
    if (!ack.success) {
        Log.e(TAG, "ホットキー実行失敗: " + ack.error.message);
    }
});
```

セマンティックルール：

- コンビネーションは全体として**一度に**送信され（keys 配列が1つのメッセージにパッケージングされる）、down はパラメータ順、up は**逆順**（修飾キーは先に押して後に離す、実際のキーボードと一致）；
- 各呼び出しはサーバー側で2回のコールバック（down 1回 + up 1回）を発生させ、メッセージの `pressType = short`；
- 返される `CompletableFuture` は up の ack です（チャネル書き込みは順序付きなので、先行する down は既に到達済み）。`success=false` はホストの実行失敗を意味します。

### 7.3 長押し sendHotkeyDown / sendHotkeyUp

「押しっぱなし」のセマンティクスが必要な場合は、`sendHotkeyDown` / `sendHotkeyUp` で明示的にペアリングします。メッセージの `pressType = long`：

```java
client.sendHotkeyDown(0x5E);   // 押下（離さない）
// ...押下中...
client.sendHotkeyUp(0x5E);     // 解放
```

**自動リリース保護**: `sendHotkeyDown` 後 3000ms（`client.setHotkeyAutoReleaseMs(...)` で調整可、0 で無効化）以内に `sendHotkeyUp` が呼ばれないと、SDK が自動的に up を1回補完し、キーがシステム側でスタックするのを防ぎます。明示的な `sendHotkeyUp` はこのタスクをキャンセルします。

**連続効果のシナリオ**（例: Photoshop で Ctrl+] を押しっぱなしにするとブラシが線形に拡大し続ける）：実際のキーボードの「連続効果」はハードウェアの自動リピートに由来します——押しっぱなしにすると down イベントが繰り返し発生します。SDK の注入は down を1回送信するだけで自動リピートしないため、一定間隔で繰り返し送信する必要があります：

```java
client.setHotkeyAutoReleaseMs(0);          // 押下中は自動リリースを無効化
client.sendHotkeyDown(0x11);                   // 1. Ctrl 押下、1回だけ送信

while (holding) {                          // 2. 押下中: 80ms ごとに ] の down を繰り返し送信
    client.sendHotkeyDown(0xDD);               //    キーボードの自動リピートをシミュレート → ブラシが拡大し続ける
    Thread.sleep(80);
}

client.sendHotkeyUp(0x11, 0xDD);               // 3. 終了: down と同じ順序で渡すと、SDK が内部で逆順に解放（] を先に、Ctrl を後に）
```

> 同じキーで `sendHotkeyDown` を繰り返すと自動リリースタイマーが**リセット**されます——繰り返し間隔がリリース閾値より短ければ、デフォルト設定でも誤リリースは発生しません。

### 7.4 メッセージ送信 sendMessage

指定したプラグインインスタンスにメッセージを送信します（`message` は `Object` で、文字列、Map、List、POJO など任意のシリアライズ可能オブジェクトをサポート）。デフォルトで Ultra WorkSpace PLUGIN 側にパススルーされます：

```java
// デフォルト送信先 ULTRA_WORKSPACE_PLUGIN —— 文字列を送信
client.sendMessage("plugin-001", "再生開始")
      .thenAccept(ack -> {
          if (!ack.success) {
              Log.e(TAG, "メッセージ配信失敗: " + ack.error.message);
          }
      });

// 構造化オブジェクトを送信（受信側は Map として受け取る）
Map<String, Object> body = new HashMap<>();
body.put("action", "play");
body.put("trackId", 42);
client.sendMessage("plugin-001", body);

// パススルー先を明示的に指定（MessageDestination 列挙型）
client.sendMessage(MessageDestination.ULTRA_WORKSPACE_PLUGIN, "plugin-002", "チャンネル切替");
```

- `destination` は `MessageDestination` 列挙型（現在は `ULTRA_WORKSPACE_PLUGIN` を含む）。将来的に送信先を追加する場合は列挙型を拡張するだけで、API は変わりません；
- `pluginId` が空、または `message` / `destination` が null の場合はクライアント側でローカルに `IllegalArgumentException` をスローし、リクエストは送信されません。空文字列 `""` は正当なメッセージ内容です；
- サーバーが受信すると `PluginServerListener.onMessage` がコールバックされ、その戻り値が ack として返されます——`success=false` はホストの処理失敗を意味します。

### 7.5 LineOS から配信されたメッセージの受信 onMessage

メッセージチャネルは双方向です。LineOS 側が `PluginServer.sendMessage(packageName, pluginId, message)` を呼び出すと、プラグインは `PluginListener.onMessage` で受信します：

```java
client.setPluginListener(new PluginListener() {
    @Override
    public void onMessage(MessagePayload payload, MessageResponder responder) {
        String pluginId = payload.pluginId;       // 対象プラグインインスタンス id
        Object message = payload.message;         // メッセージ内容（String / Map / List などの可能性あり）
        // payload.destination はこの方向では null（プラグイン→LineOS 方向でのみ使用）

        if (message instanceof String) {
            String text = (String) message;
        } else if (message instanceof Map) {
            Map<String, Object> body = (Map<String, Object>) message;
        }

        // 処理後は必ず応答、どちらかを選択:
        responder.sendSuccess();
        // responder.sendError("NOT_SUPPORTED", "このインスタンスはメッセージをサポートしていません", false);
    }
});
```

> デフォルト実装は `NO_HANDLER` エラーを返します。この機能が不要な場合は override しなくて構いません。

### 7.6 ホストへのアクション実行要求 sendHostAction

ファイルを開く、アプリを起動する、ブラウザを開く、テキストを入力する、コマンドを実行する——この5種類の操作は**1つのメソッド**に統一されています。各アクションには**専用 Builder** があり、そのアクション自身のパラメータしか設定できません（コンパイル時に分離、クロス設定不可）。パラメータキーは SDK 内にカプセル化され、呼び出し側はプロトコルのフィールド名を気にする必要はありません：

```java
// 1. ファイルまたはフォルダを開く（path のみ設定可）
client.sendHostAction(HostActionRequest.openFile()
        .path("C:/Users/kid/Desktop/ap")
        .build());

// 2. アプリを起動（path と launchMode のみ設定可）
client.sendHostAction(HostActionRequest.startApp()
        .path("C:\\Users\\kid\\AppData\\Local\\SourceTree\\app-3.4.30\\SourceTree.exe")
        .launchMode(HostActionRequest.HostLaunchMode.ACTIVATE_OR_START)  // オプション
        .build());

// 3. ブラウザを開く（url のみ設定可）
client.sendHostAction(HostActionRequest.openBrowser()
        .url("google.com")
        .build());

// 4. テキストを入力（text のみ設定可）
client.sendHostAction(HostActionRequest.inputText()
        .text("hello")
        .build());

// 5. コマンドを実行（cmd のみ設定可）
client.sendHostAction(HostActionRequest.execCmd()
        .cmd("notepad")
        .build())
      .thenAccept(ack -> {
          if (!ack.success) {
              Log.e(TAG, "実行失敗: " + ack.error.message);
          }
      });
```

**launchMode 起動方式**（`HostActionRequest.HostLaunchMode` 列挙型）：

| 列挙値 | ワイヤ値 | 意味 |
| ------ | -------- | ---- |
| `NEW_PROCESS` | 0 | 常に新しいプロセスを起動 |
| `ACTIVATE_ONLY` | 1 | アクティブ化のみ（新規起動せず、実行中のプロセスをアクティブ化） |
| `ACTIVATE_OR_START` | 2 | 起動していなければ起動、起動していればアクティブ化 |

- アクションタイプは `HostAction` 列挙型で区別され、サーバーは `PluginServerListener.onHostAction` をコールバックし、`request.action` でアクションを正確に区別、`request.params` でパラメータを取得します；
- 各専用 Builder の `build()` は必須パラメータを検証し（例: `openFile()` には `path` が必須）、欠落している場合は `IllegalArgumentException` をスローします。新しいアクションを追加する場合は列挙型と Builder を拡張するだけで、**新しい送信メソッドは不要**です；
- 戻り値の `success=false` はホストの実行失敗を意味します。

---

## 8. コールバック早見表

`PluginListener` のすべてのメソッドは `default` の空実装であり、必要に応じて override してください：

| コールバック | 発火タイミング | 必須実装 |
| ------------ | -------------- | -------- |
| `onRegisterSuccess(RegisterResultPayload)` | 登録成功（configServer.port / sessionToken を取得可能） | 推奨 |
| `onRegisterFailed(Throwable)` | 登録失敗 | 推奨 |
| `onActionsAck(ActionsResultPayload)` | アクション設定同期 ACK | オプション |
| `onActionsFailed(Throwable)` | アクション上报失敗 | オプション |
| `onActionsUpdate(ActionsPayload)` | LineOS がアクションリスト変更をプッシュ | オプション |
| `onDescribe(DescribePayload, DescribeResponder)` | LineOS がアクションのパラメータ定義を問い合わせ | パラメータ付きアクションは必須 |
| `onOptions(GetOptionsPayload, OptionsResponder)` | REMOTE 型パラメータのオプションを取得 | REMOTE パラメータがある場合は必須 |
| `onCall(CallPayload, CallResponder)` | アクションを実行 | **必須** |
| `onModuleEvent(ModuleEventPayload, ModuleEventResponder)` | モジュールハードウェアイベント | モジュールを購読している場合は必須 |
| `onA2UIActionCall(A2UIActionCallPayload, A2UIActionCallResponder)` | A2UI ユーザー操作の返却（デフォルトは NO_HANDLER） | インタラクティブコンポーネントがある場合は必須 |
| `onA2UIDataRequest()` | LineOS が能動的に A2UI データを取得（デフォルトは空リスト） | A2UI プラグインは推奨 |
| `onA2UIRender(A2UIRenderPayload, A2UIRenderResponder)` | カード初回描画/更新描画の通知（デフォルトは NO_HANDLER） | A2UI プラグインはオプション |
| `onMessage(MessagePayload, MessageResponder)` | LineOS がメッセージを配信（デフォルトは NO_HANDLER） | オプション |

**Responder 共通の約束：**

- `responder.sendSuccess(data)` — 成功、`data` は null 可
- `responder.sendError(code, message, retryable)` — 失敗。`retryable=true` は LineOS に再試行可能を提示

---

## 9. 注意事項とよくある質問

### 9.1 ビルドと統合

1. **`packagingOptions` で Netty メタファイルを除外必須**（2.1 参照）。でないとパッケージングが競合します。
2. `meta-data` の `android:value` は**必ず `applicationId` と一致**させてください。これは LineOS がプラグインを識別する一意の ID であり、不一致だと登録後にセッションが一致しません。
3. サービスは `android:exported="true"` かつ固定の permission が必要で、どちらも欠かせません。

### 9.2 ランタイム振る舞い

4. **フォアグラウンドサービス要件**: `onCreate()` 内で数秒以内に `startForeground()` を呼ぶ必要があります。呼ばないとシステムが ANR/プロセス kill します。
5. **コールバックスレッドはメインスレッドではない**: `PluginListener` のすべてのコールバックは SDK のスレッドプールで実行されます。UI 操作が必要な場合は自分で `Looper.getMainLooper()` で切り替えてください。
6. **切断時の自動再接続**: SDK は自動的に再接続し再登録＋アクション再上报を行いますが、**A2UI の UI データは自動再送されません**——LineOS は登録成功後に能動的に1回取得します（`onA2UIDataRequest` 経由）。このコールバックを適切に実装しておけば、再接続後の UI データ復旧が保証されます。
7. **sessionToken は自分で検証**: これはデバッグセッションの証明書であり、HTTP インターフェースで無条件に信用しないでください。
8. **ホットキーの down/up はペアリング必須**: `sendHotkeyDown` 後に `sendHotkeyUp` を呼び忘れると、SDK がタイムアウト後に up を自動補完します（デフォルト 3000ms）が、補完のタイミングは制御できません。正確な長押しセマンティクスが必要な場合は自分でペアリングを保証してください。[7.3](#73-長押し-sendhotkeydown--sendhotkeyup) を参照。

### 9.3 プロトコル契約

8. **パラメータ id の一致性**: `onDescribe` の `ParameterSpec.id` は `onCall` の `params` の key であり、両者は一致していなければなりません。
9. **A2UI バージョンは v0.9.1 必須**: 各 envelope に `"version": "v0.9.1"` を付与してください。
10. **`supportsA2UI=true` は LineOS からの逆方向呼び出しの前提**: 宣言しなくてもプラグインは能動的にデータを上报できますが、LineOS の `callA2UIAction` / `queryA2UIData` は拒否されます。
11. **上报結果の ack を確認**: `refreshA2UI` / `sendA2UIUpdateDataModel` の戻り値の `success=false` は LineOS が処理に失敗したことを意味します。プラグイン側はログ出力またはリトライ戦略を持つべきです。
12. **モジュールイベントはシリアルユニキャスト、未返信は放棄**: `onModuleEvent` 内で `consume()` も `ignore()` も呼ばないと、1秒のタイムアウトを無駄に消費してから次のプラグインに渡されます——処理しない場合は**明示的に `ignore()`** を呼んでください。詳細な配信ルールは [6.2](#62-配信ルール重要) を参照。

### 9.4 よくある質問（FAQ）

**Q: `onRegisterSuccess` の後、なぜ LineOS に自分の Action が見えないの？**
A: `setActionsConfig` が `startAsync()` の前に呼ばれているか確認してください。また `onActionsAck` の `receivedCount` が期待通りか確認してください。

**Q: 開発段階でセルフテストするには？**
A: 開発期は自分のアプリ内で手動で `startService()` して `BackgroundService` を起動し、フローを検証できます。本番環境では LineOS が Manifest をスキャンして自動起動します。

---
