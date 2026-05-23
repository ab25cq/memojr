# MemoJR - リッチメモアプリ

数式・HTML・写真をコピペできるAndroidメモアプリ

## 機能
- **数式対応**: `$...$`（インライン）、`$$...$$`（ブロック）形式のLaTeX数式
- **HTML貼り付け**: GeminiなどのリッチテキストをHTMLのまま保持
- **写真挿入**: ギャラリーから画像を選択してメモに挿入（WebP変換で容量節約）
- **書式ツールバー**: 太字・斜体・下線・見出し・リスト・コードブロック
- **数式プレビュー**: KaTeX（CDN）で本格的に数式をレンダリング
- **バックアップ**: 全メモを1つのJSONファイルにエクスポート/インポート（機種変対応）

## 必要環境
- Android 15 (API Level 35) 以上
- Android Studio Ladybug 以降

## Android Studioでのビルド手順

1. Android Studioを起動
2. `File → Open` でこのフォルダ（`memojr/`）を開く
3. Gradleの同期を待つ（自動でgradlew.jarが生成される）
4. `Run → Run 'app'` でビルド・実行

## 使い方

### メモの作成
1. `+` ボタンでメモ作成画面を開く
2. タイトルと本文を入力
3. Gemini等からコピーした内容をそのまま貼り付け可能
4. 「保存」で保存、「プレビュー」で数式レンダリング確認

### 数式の挿入
- ツールバーの `∑fx` ボタン → LaTeXを入力 → インラインまたはブロックを選択
- または直接 `$E=mc^2$` のように入力（プレビュー時にレンダリング）

### バックアップ
- メインメニュー → 「バックアップをエクスポート」 → JSONファイルを保存
- 「バックアップから復元」 → 保存したJSONを選択してインポート

## ファイル構成
```
app/src/main/
├── java/com/ab25cq/memojr/
│   ├── MainActivity.kt          # メモ一覧
│   ├── MemoEditActivity.kt      # 編集画面
│   ├── MemoViewActivity.kt      # 表示/プレビュー（KaTeXレンダリング）
│   ├── adapter/MemoListAdapter.kt
│   ├── data/                    # Room DB (Memo, Dao, Database, Repository)
│   ├── viewmodel/MemoViewModel.kt
│   └── backup/BackupManager.kt  # JSON export/import
└── assets/
    └── editor.html              # WebViewベースのリッチエディタ
```

# build 

buildはcodexなどのcliのAIを使うのが早いです。サーバーでコンパイルしてローカルのスマホにscpすると楽です。

# join google play tester

join the keibajr group

https://groups.google.com/g/get12tester

become the tester and install the app
https://play.google.com/apps/testing/com.keibajr

