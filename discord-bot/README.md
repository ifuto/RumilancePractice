# N Arena Discord Bot

Discord **サーバー本体は Bot から作成できません**（Discord の仕様）。  
あなたがサーバーを新規作成 → この Bot を招待 → `/setup` でチャンネルを一括作成、という流れです。

## チャンネル構成

| カテゴリ | チャンネル |
|---------|-----------|
| INFO | welcome, rules, announcements, updates, maintenance, faq |
| COMPETE | duel（キルログ流し）, leaderboards, stats |
| SUPPORT | report / bug-report / unban-appeal / other-ticket（ボタンでチケット） |
| COMMUNITY | chat, clips |

## 起動

```bash
cd discord-bot
cp .env.example .env
# DISCORD_TOKEN / CLIENT_ID / GUILD_ID を記入
npm install
npm start
```

`--max-old-space-size=96` でヒープを抑えめにしています。

任意: `HOOK_PORT=8787` を立てると `POST /duel` で `#duel` にキルログを送れます（プラグイン連携は後から）。
