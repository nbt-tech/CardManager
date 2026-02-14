# インストールガイド (Installation Guide)

本プロジェクト `CardManager` を利用・ビルドするための手順です。

---

## 1. 一般ユーザー向け（アプリをすぐに使いたい場合）
Google Playを介さずに、ビルド済みのAPKファイルを直接インストールする方法です。

1. **ダウンロード**: [Releases](https://github.com/nbt-tech/CardManager/releases) ページから、最新バージョンの `app-release.apk` をダウンロードします。
2. **不明なアプリの許可**: Android端末でAPKファイルを開きます。「このソースからのアプリを許可する」という警告が出た場合は、一時的に許可設定を行ってください。
3. **インストール**: インストールボタンを押し、完了後にアプリを起動します。
    - ※Playプロテクトの警告が出る場合がありますが、「詳細」から「インストールする」を選択してください。

---

## 2. 開発者向け（ソースコードからビルドする場合）
自分でコードを改変したり、デバッグ実行したりするための手順です。

### 動作要件
- **Android Studio**: Ladybug (2024.2.1) 以降を推奨
- **JDK**: Java 17 または 21
- **Target SDK**: 35 (Android 15)

### ビルド手順
1. **リポジトリをクローン**:
   ```bash
   git clone [https://github.com/nbt-tech/CardManager.git](https://github.com/nbt-tech/CardManager.git)
プロジェクトのインポート: Android Studio を起動し、クローンしたフォルダを選択して開きます。

署名情報の設定: 本プロジェクトはセキュリティのため、署名情報を local.properties から読み込む設定になっています。プロジェクト直下の local.properties に以下の項目を追記してください。

Properties
signing.keyAlias=your_key_alias
signing.keyPassword=your_key_password
signing.storeFilePath=path/to/your/keystore.jks
signing.storePassword=your_store_password
※自身で作成したキーストア（JKSファイル）を使用してください。

ビルドと実行: Build > Make Project を実行し、エラーがなければ実機またはエミュレーターで実行（Run）します。

3. アップデート時の注意
v1.0.x 以前から v1.1.0 以降へアップデートする場合、データの互換性維持のため、以下の手順を推奨します。

エクスポート: 以前のバージョンでデータを「エクスポート」し、JSONファイルを保存します。

アップデート: アプリを最新版（v1.1.0以降）に更新します。

インポート: 保存したJSONファイルを「インポート」します。

これにより、既存のカードデータに新しいBIN照会（発行体情報）が正しく反映されます。
