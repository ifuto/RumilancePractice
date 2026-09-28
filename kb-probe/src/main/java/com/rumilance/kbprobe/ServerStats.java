package com.rumilance.kbprobe;

/**
 * サーバー単位のノックバック計測統計。全フィールド public（Gson でそのまま永続化する）。
 */
public final class ServerStats {

    /** 水平サンプル数 / 垂直サンプル数。 */
    public int hSamples;
    public int vSamples;
    /** 生速度の合計（水平 |dxz| / 垂直 dy）。 */
    public double sumHRaw;
    public double sumVRaw;
    /** 推定係数の合計（バニラとの比）。 */
    public double sumHF;
    public double sumVF;
    /** KB無効領域判定の回数 / 攻撃不成立の回数。 */
    public int noKbEvents;
    public int noDamageEvents;
    public long firstSeenEpochMs;
    public long lastSeenEpochMs;

    ServerStats() {
    }

    void addHorizontal(double raw, double factor) {
        hSamples++;
        sumHRaw += raw;
        sumHF += factor;
        touch();
    }

    void addVertical(double raw, double factor) {
        vSamples++;
        sumVRaw += raw;
        sumVF += factor;
        touch();
    }

    double avgHorizontalFactor() {
        return hSamples > 0 ? sumHF / hSamples : 1.0d;
    }

    double avgVerticalFactor() {
        return vSamples > 0 ? sumVF / vSamples : 1.0d;
    }

    private void touch() {
        long now = System.currentTimeMillis();
        if (firstSeenEpochMs == 0L) {
            firstSeenEpochMs = now;
        }
        lastSeenEpochMs = now;
    }
}
