# Security Policy

## Supported Versions

現在サポートされているバージョンは以下の通りです。

| Version | Supported          |
| ------- | ------------------ |
| 1.1.x   | :white_check_mark: |
| 1.0.x   | :x:                |
| < 1.0   | :x:                |

## Reporting a Vulnerability

脆弱性を見つけた場合は、以下の手順で報告してください。

1. **報告先**: GitHub の [Issues](https://github.com/nbt-tech/CardManager/issues) に投稿するか、Xを通じて開発者に直接連絡してください。
2. **対応**: 報告を受けた後、可能な限り速やかに内容を確認し、修正版のリリースを検討します。
3. **プライバシーと通信**: v1.1.0よりBIN照会機能のため外部API（binlist.net）との通信が発生しますが、カード番号の先頭数桁以外の個人情報を送信することはありません。それ以外の機能は従来通りオフラインで動作します。
