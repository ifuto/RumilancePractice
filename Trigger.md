# Trigger.md — customize.yml の実行内容

このファイルを書き換えて Push すると、`customize.yml` が起動し、
**このファイル内の最初のコードフェンス（```〜```）の中身だけが bash で実行**されます。
（手動実行: Actions → customize → Run workflow）

```bash
# ここに実行したいコマンドを書く（複数行OK）
echo "hello from Trigger.md"
```
