package com.rumilance.kbprobe;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * KB Probe のクライアント GUI (mod 側の「ホーム画面」の実装 0.5.0)。
 *
 * <pre>
 * HomeScreen ──[右上の KB ボタン]──> ServersScreen(記録したサーバー一覧)
 *                                        └─ 各行クリック ─> ServerDetailScreen
 *                                             └─ [数値 JSONをコピー] → クリップボード
 * </pre>
 *
 * コピーされる JSON はサーバー側プラグインの kb/&lt;Name&gt;.json と互換。コピー後に
 * そのファイル名で保存すれば、Duel Request の KB 選択肢に filename(.json除く) で出る。
 */
public final class KbProbeScreens {

    private KbProbeScreens() {
    }

    // ============================================================== Home

    /** ホーム画面: 右上に KB ボタン。本体は記録サマリ + 使い方。 */
    public static final class HomeScreen extends Screen {

        private final Screen parent;

        public HomeScreen(Screen parent) {
            super(Text.literal("KB Probe"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            // 右上に KB ボタン (ユーザー指定のレイアウト)。
            addDrawableChild(ButtonWidget.builder(Text.literal("KB"),
                            button -> MinecraftClient.getInstance()
                                    .setScreen(new ServersScreen(this)))
                    .dimensions(this.width - 104, 8, 96, 20)
                    .build());
            addDrawableChild(ButtonWidget.builder(Text.literal("閉じる"),
                            button -> close())
                    .dimensions(this.width / 2 - 50, this.height - 34, 100, 20)
                    .build());
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("KB Probe — ノックバック計測"), this.width / 2, 32, 0xFFFFFF);

            java.util.Set<String> servers = StatsStore.serverKeys();
            int totalH = 0;
            int totalV = 0;
            for (String key : servers) {
                ServerStats s = StatsStore.statsFor(key);
                totalH += s.hSamples;
                totalV += s.vSamples;
            }
            int y = 64;
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("記録したサーバー: " + servers.size() + " 件"
                            + "  (H " + totalH + " / V " + totalV + " サンプル)"),
                    this.width / 2 - 150, y, 0xA0FFA0);
            y += 18;
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("右上の [KB] でサーバー一覧 — 各サーバーの推定KBを"),
                    this.width / 2 - 150, y, 0xCFCFCF);
            y += 12;
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("数値 JSON でコピーできます。"),
                    this.width / 2 - 150, y, 0xCFCFCF);
            y += 24;
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("コピーした JSON を <鯖名>.json としてサーバー管理者の"
                            + " kb/ フォルダに置くと、その鯖のKBを再現した Duel Request 用"),
                    this.width / 2 - 150, y, 0x9FD7FF);
            y += 12;
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("KB プロファイルの選択肢になります (ファイル名表示)。"),
                    this.width / 2 - 150, y, 0x9FD7FF);
        }

        @Override
        public void close() {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }

    // ============================================================== Servers list

    /** 記録したサーバーの一覧 (ページ送り)。行クリックで詳細画面へ。 */
    public static final class ServersScreen extends Screen {

        private static final int PER_PAGE = 6;
        private final Screen parent;
        private int page;
        private List<String> keys = List.of();

        public ServersScreen(Screen parent) {
            super(Text.literal("KB Probe — サーバー一覧"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            clearChildren();
            keys = new ArrayList<>(StatsStore.serverKeys());
            keys.sort(String::compareToIgnoreCase);
            int maxPage = Math.max(0, (keys.size() - 1) / PER_PAGE);
            page = Math.max(0, Math.min(page, maxPage));

            int y = 48;
            int from = page * PER_PAGE;
            int to = Math.min(keys.size(), from + PER_PAGE);
            for (int i = from; i < to; i++) {
                String key = keys.get(i);
                ServerStats s = StatsStore.statsFor(key);
                String label = key + "   (H " + s.hSamples + " / V " + s.vSamples + ")";
                addDrawableChild(ButtonWidget.builder(Text.literal(label),
                                button -> MinecraftClient.getInstance()
                                        .setScreen(new ServerDetailScreen(this, key)))
                        .dimensions(this.width / 2 - 160, y, 320, 20)
                        .build());
                y += 24;
            }
            int nav = this.height - 56;
            addDrawableChild(ButtonWidget.builder(Text.literal("< 前"),
                            button -> { page--; init(client, width, height); })
                    .dimensions(this.width / 2 - 160, nav, 76, 20)
                    .build());
            addDrawableChild(ButtonWidget.builder(Text.literal(
                                    (page + 1) + " / " + (Math.max(0, (keys.size() - 1) / PER_PAGE) + 1)),
                            button -> {})
                    .dimensions(this.width / 2 - 76, nav, 72, 20)
                    .build()).active = false;
            addDrawableChild(ButtonWidget.builder(Text.literal("次 >"),
                            button -> { page++; init(client, width, height); })
                    .dimensions(this.width / 2 + 4, nav, 76, 20)
                    .build());
            addDrawableChild(ButtonWidget.builder(Text.literal("戻る"),
                            button -> close())
                    .dimensions(this.width / 2 - 50, this.height - 30, 100, 20)
                    .build());
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("記録したサーバー一覧"), this.width / 2, 28, 0xFFFFFF);
            if (keys.isEmpty()) {
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("まだ計測記録がありません。相手を殴って採集を始めてください。"),
                        this.width / 2, this.height / 2 - 8, 0xA0A0A0);
            }
        }

        @Override
        public void close() {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }

    // ============================================================== Server detail

    /** 1 サーバーの詳細 + 数値 JSON コピーボタン。 */
    public static final class ServerDetailScreen extends Screen {

        private final Screen parent;
        private final String serverKey;
        private boolean copied;

        public ServerDetailScreen(Screen parent, String serverKey) {
            super(Text.literal("KB Probe — サーバー詳細"));
            this.parent = parent;
            this.serverKey = serverKey;
        }

        @Override
        protected void init() {
            clearChildren();
            ServerStats s = StatsStore.statsFor(serverKey);
            boolean measurable = s.hSamples > 0 && s.vSamples > 0;
            addDrawableChild(ButtonWidget.builder(Text.literal("この鯖のKB 数値 JSONをコピー"),
                            button -> {
                                MinecraftClient.getInstance().keyboard
                                        .setClipboard(KbExport.export(serverKey, s));
                                copied = true;
                            })
                    .dimensions(this.width / 2 - 130, this.height - 84, 260, 20)
                    .build()).active = measurable;
            addDrawableChild(ButtonWidget.builder(Text.literal("戻る"),
                            button -> close())
                    .dimensions(this.width / 2 - 50, this.height - 34, 100, 20)
                    .build());
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            ServerStats s = StatsStore.statsFor(serverKey);
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(serverKey), this.width / 2, 26, 0xFFFFFF);

            float y = 60;
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("水平係数 (推定): " + fmt(mean(s.sumHF, s.hSamples))
                            + "   (" + s.hSamples + " サンプル)"),
                    this.width / 2 - 150, (int) y, 0xA0FFA0);
            y += 16;
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("垂直係数 (推定): " + fmt(mean(s.sumVF, s.vSamples))
                            + "   (" + s.vSamples + " サンプル)"),
                    this.width / 2 - 150, (int) y, 0xA0FFA0);
            y += 20;
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("ガード棄却: KB無効/無敵 " + s.noKbEvents
                            + " / ダメージ不成立 " + s.noDamageEvents
                            + " / 移動混入 " + s.contaminatedEvents),
                    this.width / 2 - 150, (int) y, 0x9A9A9A);
            y += 20;

            if (s.hSamples <= 0 || s.vSamples <= 0) {
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("どちらかのサンプルが 0 のためコピーはまだ無効です。"),
                        this.width / 2, this.height - 108, 0xFF7070);
            } else if (copied) {
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("コピーしました — "
                                + KbExport.fileSafeName(serverKey) + ".json として kb/ に保存"),
                        this.width / 2, this.height - 108, 0xA0FFA0);
            } else {
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("下のボタンでコピーして <鯖名>.json として保存してください。"),
                        this.width / 2, this.height - 108, 0xCFCFCF);
            }
        }

        private static double mean(double sum, int n) {
            return n > 0 ? sum / n : Double.NaN;
        }

        private static String fmt(double v) {
            if (Double.isNaN(v)) {
                return "—";
            }
            return String.format(java.util.Locale.ROOT, "%.4f", v);
        }

        @Override
        public void close() {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }
}

