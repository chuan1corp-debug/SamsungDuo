package com.jsj.samsungduo;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

final class TrioRenderer {

    // =========================================================
    // 🌟 自定义微调区：以后如果还想微调，只改这三个数字即可！
    // =========================================================
    // 1. 电量字体大小缩放：1.0f 代表跟时间一样大，0.8f 代表稍小
    private static final float CUSTOM_LEVEL_SIZE_SCALE = 0.9f; 
    
    // 2. 电量上下位置微调：负数往屏幕上方移，正数往胶囊内部移（默认 0f 刚好在缺口正中间）
    private static final float CUSTOM_LEVEL_Y_OFFSET = 0f; 
    
    // 3. 胶囊顶部开口宽度：数字越大，中间断开的缺口越宽（默认 45f 足够放下 100%）
    private static final float CUSTOM_TOP_GAP_WIDTH = 45f; 
    // =========================================================

    private TrioRenderer() {
    }

    private static Boolean sIs24Hour;

    static void set24Hour(boolean is24Hour) {
        sIs24Hour = Boolean.valueOf(is24Hour);
    }

    private static final Paint STROKE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint FILL_STROKE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final float STATE_ICON_SCALE = 1.12f;
    private static final float DATA_ARROW_H = 0.86f;
    private static final float DATA_ARROW_GAP = 0.10f;
    private static final float SLASH_SPAN = 1.15f;
    private static final int SLASH_COLOR = 0xFFFF3B30;
    private static final Path BOLT = buildBolt();
    private static final Path WIFI_DOT = buildWifiDot();
    private static final RectF ARC = new RectF();
    private static final Path RECT_BAR = new Path();
    private static final RectF RECT_RECT = new RectF();
    private static final float[] RECT_RADII = new float[8];

    private static int sTextWeight = -1;
    private static final Typeface TEXT_BASE = Typeface.create("sans-serif", Typeface.NORMAL);

    static {
        STROKE.setStyle(Paint.Style.STROKE);
        STROKE.setStrokeCap(Paint.Cap.ROUND);
        STROKE.setStrokeJoin(Paint.Join.ROUND);
        FILL_STROKE.setStyle(Paint.Style.STROKE);
        FILL_STROKE.setStrokeCap(Paint.Cap.ROUND);
        FILL_STROKE.setStrokeJoin(Paint.Join.ROUND);
        FILL.setStyle(Paint.Style.FILL);
        TEXT.setStyle(Paint.Style.FILL);
        TEXT.setTextAlign(Paint.Align.CENTER);
        TEXT.setLetterSpacing(TrioGeometry.VALUE_LETTER_SPACING);
    }

    static void draw(Canvas canvas, View host, TrioState s) {
        draw(canvas, host, s, TrioConfig.get());
    }

    static void draw(Canvas canvas, View host, TrioState s, TrioSettings cfg) {
        drawState(canvas, host.getWidth(), host.getHeight(), s, cfg);
    }

    static void drawState(Canvas canvas, int w, int h, TrioState s, TrioSettings cfg) {
        if (w <= 0 || h <= 0 || s == null || cfg == null) {
            return;
        }
        drawInto(canvas, w, h, s.level, s.charging, s.quickCharging, s.powerSave, s.low,
                s.wifiPresent ? s.wifiLevel : -1, s.mobileLevel, s.slotLevels, s.mobileType,
                s.foreground(), cfg, true);
    }

    static void drawInto(Canvas canvas, int w, int h, int level, boolean charging,
                         boolean powerSave, boolean low, int wifiLevel, int mobileLevel,
                         int fg, TrioSettings cfg) {
        drawInto(canvas, w, h, level, charging, false, powerSave, low, wifiLevel, mobileLevel,
                null, null, fg, cfg, true);
    }

    static void drawInto(Canvas canvas, int w, int h, int level, boolean charging,
                         boolean quickCharging, boolean powerSave, boolean low,
                         int wifiLevel, int mobileLevel, int[] slotLevels, String mobileType,
                         int fg, TrioSettings cfg, boolean clearWhenDone) {
        if (w <= 0 || h <= 0 || cfg == null) {
            return;
        }
        final TrioAppearance appearance = TrioAppearance.of(cfg);
        TrioGeometry.setCapsuleLength(cfg.capsuleLength);
        TrioGeometry.setCapsuleTimeSide(cfg.capsuleTimeSide);
        final boolean rect = appearance.rect;
        final boolean capsule = appearance.capsule;
        final float scale = inkScale(w, h, capsule, rect)
                * ((cfg.glyphScale > 100) ? 1f : appearance.glyphFit());
        if (scale <= 0f) {
            return;
        }

        if (clearWhenDone) {
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        }

        final int role = roleColor(appearance, level, charging, powerSave, low, fg);

        final int[] slots = (slotLevels != null && slotLevels.length >= TrioState.SIM_SLOTS)
                ? slotLevels : null;
        final int sims = (slots == null) ? 0
                : (slots[0] >= 0 ? 1 : 0) + (slots[1] >= 0 ? 1 : 0);

        final int save = canvas.save();
        if (capsule) {
            canvas.translate(
                    (w - TrioGeometry.capInkW() * scale) / 2f - TrioGeometry.CAP_INK_X * scale,
                    (h - TrioGeometry.CAP_INK_H * scale) / 2f - TrioGeometry.CAP_INK_Y * scale);
        } else if (rect) {
            canvas.translate(
                    (w - TrioGeometry.RECT_INK_W * scale) / 2f - TrioGeometry.RECT_INK_X * scale,
                    (h - TrioGeometry.RECT_INK_H * scale) / 2f - TrioGeometry.RECT_INK_Y * scale);
        } else {
            canvas.translate(
                    (w - TrioGeometry.INK_W * scale) / 2f - TrioGeometry.INK_X * scale,
                    (h - TrioGeometry.INK_H * scale) / 2f - TrioGeometry.INK_Y * scale);
        }
        canvas.scale(scale, scale);

        if (capsule) {
            drawCapsuleLayout(canvas, level, charging, quickCharging, wifiLevel, mobileLevel,
                    slots, sims, mobileType, role, fg, appearance);
        } else if (rect) {
            drawRectLayout(canvas, level, charging, quickCharging, wifiLevel, mobileLevel,
                    slots, sims, mobileType, role, fg, appearance);
        } else {
            drawRingLayout(canvas, level, charging, quickCharging, wifiLevel, mobileLevel,
                    slots, sims, mobileType, role, fg, appearance);
        }

        canvas.restoreToCount(save);
    }

    private static void drawRingLayout(Canvas canvas, int level, boolean charging,
                                       boolean quickCharging, int wifiLevel, int mobileLevel,
                                       int[] slotLevels, int sims, String mobileType, int role,
                                       int fg, TrioAppearance a) {
        final TrioAppearance.Ring r = a.ring(level, charging, wifiLevel, mobileType, sims);
        final boolean dualGap = r.dual;
        final boolean boltInGap = r.bolt && !r.pairInCentre;
        final boolean wideGap = r.pairInGap && (r.pairValue || r.pairWifi || r.pairType);
        final float gapStart = !r.gapUsed
                ? TrioGeometry.GAP_NONE
                : dualGap ? TrioGeometry.GAP_START_DUAL
                : wideGap ? TrioGeometry.GAP_START_CHARGE_PAIR
                : boltInGap ? TrioGeometry.GAP_START_CHARGE
                : TrioGeometry.GAP_START_IDLE;
        final float gapEnd = !r.gapUsed
                ? TrioGeometry.GAP_NONE
                : dualGap ? TrioGeometry.GAP_END_DUAL
                : wideGap ? TrioGeometry.GAP_END_CHARGE_PAIR
                : boltInGap ? TrioGeometry.GAP_END_CHARGE
                : TrioGeometry.GAP_END_IDLE;

        drawBattery(canvas, level, role, fg, a, gapStart, gapEnd);
        
        if (r.drawWifi && !r.pairWifi) {
            drawWifi(canvas, wifiLevel, fg, a, r.wifiInGap);
        }
        if (a.signalDots()) {
            drawLevelDots(canvas, mobileLevel, slotLevels, r.dual, fg, a);
        }
        if (r.typeInCentre && !r.pairType) {
            drawCentreType(canvas, mobileType, fg, a);
        }
        if (r.typeInGap && !r.pairType) {
            drawGapType(canvas, mobileType, fg, a);
        }
        if (r.pairInGap || r.pairInCentre) {
            drawPair(canvas, level, mobileType, wifiLevel, fg, a, quickCharging, r);
        } else if (r.bolt) {
            drawBolt(canvas, quickCharging ? TrioGeometry.QUICK_CHARGE : fg);
        }
        if (r.drawValue && !r.pairValue) {
            drawValue(canvas, level, fg, a, r.valueInCentre);
        }
    }

    private static void drawPair(Canvas c, int level, String mobileType, int wifiLevel,
                                 int fg, TrioAppearance a, boolean quickCharging,
                                 TrioAppearance.Ring r) {
        final boolean inGap = r.pairInGap;
        final float stroke = a.ringStroke;
        final float arcStroke = a.arcStroke;
        final float vs = r.pairValue ? a.chargeValueSize : 0f;
        final float ss = (r.pairWifi || r.pairType) ? a.chargeSignalSize : 0f;
        final float base = Math.max(vs, ss);
        if (base <= 0f) {
            drawBolt(c, quickCharging ? TrioGeometry.QUICK_CHARGE : fg);
            return;
        }
        applyWeight(vs >= ss ? a.valueWeight : a.typeWeight);

        float k = 1f;
        for (int i = 0; i < 3; i++) {
            final float h = base * k * TrioGeometry.CAP_HEIGHT_RATIO;
            final float clear = inGap
                    ? TrioGeometry.gapClearWidthAt(TrioGeometry.GAP_TOP + h,
                            TrioGeometry.GAP_START_CHARGE_PAIR,
                            TrioGeometry.GAP_END_CHARGE_PAIR, stroke)
                    : TrioGeometry.centreClearWidth(base * k, stroke);
            final float total = pairWidth(level, mobileType, arcStroke, k, vs, ss, a, r);
            if (total <= clear || total <= 0f) {
                break;
            }
            k = k * clear / total;
        }

        final float h = base * k * TrioGeometry.CAP_HEIGHT_RATIO;
        final float top = inGap ? TrioGeometry.GAP_TOP
                                : TrioGeometry.CENTER_CY - h * 0.5f;
        final float bw = TrioGeometry.BOLT_REF_W * (h / TrioGeometry.BOLT_REF_H);
        final float totalW = pairWidth(level, mobileType, arcStroke, k, vs, ss, a, r);
        float px = TrioGeometry.B_CX - totalW * 0.5f;

        final int save = c.save();
        final float pairScale = h / TrioGeometry.BOLT_REF_H;
        c.translate(px, top);
        c.scale(pairScale, pairScale);
        c.translate(-51.3f, -2.2f);
        FILL.setColor(quickCharging ? TrioGeometry.QUICK_CHARGE : fg);
        c.drawPath(BOLT, FILL);
        c.restoreToCount(save);
        px += bw + TrioGeometry.CHARGE_PAIR_GAP;

        if (vs > 0f) {
            final float hv = vs * k * TrioGeometry.CAP_HEIGHT_RATIO;
            final float ty = top + (h - hv) * 0.5f;
            TEXT.setTextAlign(Paint.Align.LEFT);
            TEXT.setTextSize(vs * k);
            TEXT.setColor(fg);
            c.drawText(String.valueOf(level), px, ty + hv, TEXT);
            TEXT.setTextAlign(Paint.Align.CENTER);
            px += TEXT.measureText(String.valueOf(level)) + TrioGeometry.CHARGE_PAIR_GAP;
        }
        if (ss > 0f) {
            final float hs = ss * k * TrioGeometry.CAP_HEIGHT_RATIO;
            final float ty = top + (h - hs) * 0.5f;
            if (r.pairType) {
                final float w = measureType(mobileType, ss * k, a.chargeSuffixAScale);
                drawType(c, mobileType, px + w * 0.5f, ty + hs, ss * k, fg, a,
                        a.chargeSuffixAScale);
            } else {
                final int s2 = c.save();
                final float ws = hs / TrioGeometry.wifiInkH(arcStroke);
                c.translate(px + TrioGeometry.wifiInkW(arcStroke) * ws * 0.5f
                                - TrioGeometry.W_CX * ws,
                        ty - (TrioGeometry.W_TOP - arcStroke * 0.5f) * ws);
                c.scale(ws, ws);
                drawWifiArcs(c, wifiLevel, fg, a);
                c.restoreToCount(s2);
            }
        }
    }

    private static float pairWidth(int level, String mobileType, float arcStroke,
                                   float k, float vs, float ss,
                                   TrioAppearance a, TrioAppearance.Ring r) {
        float total = TrioGeometry.BOLT_REF_W
                * (Math.max(vs, ss) * k * TrioGeometry.CAP_HEIGHT_RATIO
                   / TrioGeometry.BOLT_REF_H);
        if (vs > 0f) {
            TEXT.setTextSize(vs * k);
            total += TrioGeometry.CHARGE_PAIR_GAP + TEXT.measureText(String.valueOf(level));
        }
        if (ss > 0f) {
            if (r.pairType) {
                total += TrioGeometry.CHARGE_PAIR_GAP
                        + measureType(mobileType, ss * k, a.chargeSuffixAScale);
            } else {
                final float hs = ss * k * TrioGeometry.CAP_HEIGHT_RATIO;
                total += TrioGeometry.CHARGE_PAIR_GAP
                        + TrioGeometry.wifiInkW(arcStroke)
                        * (hs / TrioGeometry.wifiInkH(arcStroke));
            }
        }
        return total;
    }

    private static void drawRectLayout(Canvas canvas, int level, boolean charging,
                                       boolean quickCharging, int wifiLevel, int mobileLevel,
                                       int[] slotLevels, int sims, String mobileType, int role,
                                       int fg, TrioAppearance a) {
        final TrioAppearance.Rect r = a.rect(level, charging, wifiLevel, mobileType, sims);
        if (r.dots) {
            drawRectDots(canvas, mobileLevel, slotLevels, r.dual, fg, a);
        }
        drawRectBatteryBar(canvas, level, role, fg, a);

        if (r.bolt) {
            drawRectBolt(canvas, quickCharging ? TrioGeometry.QUICK_CHARGE : fg);
        } else if (r.wifi) {
            drawRectWifi(canvas, wifiLevel, fg, a);
        } else if (r.type) {
            drawRectType(canvas, mobileType, fg, a);
        }

        if (r.value) {
            drawRectValue(canvas, level, fg, a);
        }
    }

    private static void drawRectDots(Canvas c, int level, int[] slotLevels, boolean dual,
                                     int fg, TrioAppearance a) {
        final int left = (dual && slotLevels != null) ? slotLevels[0] : level;
        final int right = (dual && slotLevels != null) ? slotLevels[1] : level;
        final int inactive = withAlpha(fg, a.trackAlpha);
        final int leftActive = left < 0 ? 0 : Math.min(TrioGeometry.RECT_DOT_ROWS, left);
        final int rightActive = right < 0 ? 0 : Math.min(TrioGeometry.RECT_DOT_ROWS, right);
        for (int i = 0; i < TrioGeometry.RECT_DOT_ROWS; i++) {
            final float y = TrioGeometry.RECT_DOT_TOP + i * TrioGeometry.RECT_DOT_STEP;
            FILL.setColor(i < leftActive ? fg : inactive);
            c.drawCircle(TrioGeometry.RECT_DOT_LX, y, TrioGeometry.RECT_DOT_R, FILL);
            FILL.setColor(i < rightActive ? fg : inactive);
            c.drawCircle(TrioGeometry.RECT_DOT_RX, y, TrioGeometry.RECT_DOT_R, FILL);
        }
    }

    private static void drawRectBatteryBar(Canvas c, int level, int role, int fg,
                                          TrioAppearance a) {
        final float left = TrioGeometry.RECT_BAR_LEFT;
        final float right = TrioGeometry.RECT_BAR_RIGHT;
        final float r = a.ringStroke * 0.5f;
        final float top = TrioGeometry.RECT_BAR_CY - r;
        final float bottom = TrioGeometry.RECT_BAR_CY + r;

        FILL.setColor(withAlpha(fg, a.trackAlpha));
        RECT_RECT.set(left, top, right, bottom);
        c.drawRoundRect(RECT_RECT, r, r, FILL);

        if (level <= 0) {
            return;
        }
        final float cut = left + (right - left) * clamp01(level / 100f);
        RECT_RECT.set(left, top, cut, bottom);
        RECT_RADII[0] = r;
        RECT_RADII[1] = r;
        RECT_RADII[2] = 0f;
        RECT_RADII[3] = 0f;
        RECT_RADII[4] = 0f;
        RECT_RADII[5] = 0f;
        RECT_RADII[6] = r;
        RECT_RADII[7] = r;
        RECT_BAR.reset();
        RECT_BAR.addRoundRect(RECT_RECT, RECT_RADII, Path.Direction.CW);
        FILL.setColor(role);
        c.drawPath(RECT_BAR, FILL);
    }

    private static void drawRectWifi(Canvas c, int level, int fg, TrioAppearance a) {
        final int save = c.save();
        c.translate(TrioGeometry.rectWifiOffsetX(), TrioGeometry.rectWifiOffsetY(a.arcStroke));
        c.scale(TrioGeometry.RECT_WIFI_SCALE, TrioGeometry.RECT_WIFI_SCALE);
        drawWifiArcs(c, level, fg, a);
        c.restoreToCount(save);
    }

    private static void drawRectBolt(Canvas c, int color) {
        final int save = c.save();
        c.translate(TrioGeometry.rectBoltOffsetX(), TrioGeometry.rectBoltOffsetY());
        c.scale(TrioGeometry.BOLT_SCALE, TrioGeometry.BOLT_SCALE);
        FILL.setColor(color);
        c.drawPath(BOLT, FILL);
        c.restoreToCount(save);
    }

    private static void drawRectType(Canvas c, String type, int fg, TrioAppearance a) {
        final float requested = a.typeSize * TrioGeometry.RECT_TYPE_SCALE;
        applyWeight(a.typeWeight);
        final float size = fitSize(type, requested, TrioGeometry.RECT_TEXT_CLEAR, a.typeSuffixScale);
        drawType(c, type, TrioGeometry.RECT_VALUE_X, TrioGeometry.RECT_TYPE_BASELINE,
                size, fg, a, a.typeSuffixScale);
    }

    private static void drawRectValue(Canvas c, int level, int fg, TrioAppearance a) {
        final String text = String.valueOf(level);
        final float requested = a.valueSize * TrioGeometry.RECT_VALUE_SCALE;
        applyWeight(a.valueWeight);
        final float size = fitSize(text, requested, TrioGeometry.RECT_TEXT_CLEAR);
        TEXT.setTextSize(size);
        TEXT.setColor(fg);
        c.drawText(text, TrioGeometry.RECT_VALUE_X, TrioGeometry.RECT_VALUE_BASELINE, TEXT);
    }

    static int roleColor(TrioAppearance a, int level, boolean charging,
                         boolean powerSave, boolean low, int fg) {
        if (a == null || !a.roleColors) {
            return fg;
        }
        final boolean dark = isDarkBackground(fg);
        if (level >= 0 && level < TrioGeometry.CRITICAL_LEVEL) {
            return dark ? a.criticalOnDark : a.criticalOnLight;
        }
        if ((powerSave && a.powerSaveTint) || low || (level >= 0 && level <= a.lowThreshold)) {
            return dark ? a.lowOnDark : a.lowOnLight;
        }
        if (charging) {
            return dark ? a.chargingOnDark : a.chargingOnLight;
        }
        return fg;
    }

    private static void drawBattery(Canvas c, int level, int role, int fg,
                                    TrioAppearance a, float gapStart, float gapEnd) {
        STROKE.setStrokeWidth(a.ringStroke);

        STROKE.setColor(withAlpha(fg, a.trackAlpha));
        batteryRing(c, 0f, 1f, gapStart, gapEnd);

        if (level > 0) {
            STROKE.setColor(role);
            batteryRing(c, 0f, Math.min(1f, level / 100f), gapStart, gapEnd);
        }
    }

    private static void batteryRing(Canvas c, float from, float to, float gapStart, float gapEnd) {
        from = clamp01(from);
        to = clamp01(to);
        if (to <= from) {
            return;
        }
        final float mouthStart = clamp01(gapStart);
        final float mouthEnd = clamp01(gapEnd);
        final float drawn = mouthStart + (1f - mouthEnd);
        if (drawn <= 0f) {
            return;
        }
        final float visibleFrom = from * drawn;
        final float visibleTo = to * drawn;
        final float firstEnd = Math.min(visibleTo, mouthStart);
        if (firstEnd > visibleFrom) {
            arcByProgress(c, visibleFrom, firstEnd, TrioGeometry.B_CX, TrioGeometry.B_CY,
                    TrioGeometry.B_R);
        }
        final float secondFrom = Math.max(visibleFrom, mouthStart);
        if (visibleTo > secondFrom) {
            arcByProgress(c, mouthEnd + secondFrom - mouthStart, mouthEnd + visibleTo - mouthStart,
                    TrioGeometry.B_CX, TrioGeometry.B_CY, TrioGeometry.B_R);
        }
    }

    private static void arcByProgress(Canvas c, float from, float to, float cx, float cy, float r) {
        arc(c, cx, cy, r,
                TrioGeometry.B_START + from * TrioGeometry.B_SWEEP,
                (to - from) * TrioGeometry.B_SWEEP);
    }

    private static void arc(Canvas c, float cx, float cy, float r, float startDeg, float sweepDeg) {
        ARC.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(ARC, startDeg, sweepDeg, false, STROKE);
    }

    private static void drawWifi(Canvas c, int level, int fg, TrioAppearance a, boolean gap) {
        final int save = gap ? c.save() : 0;
        if (gap) {
            c.translate(TrioGeometry.gapWifiOffsetX(), TrioGeometry.gapWifiOffsetY(a.arcStroke));
            c.scale(TrioGeometry.GAP_WIFI_SCALE, TrioGeometry.GAP_WIFI_SCALE);
        }
        drawWifiArcs(c, level, fg, a);
        if (gap) {
            c.restoreToCount(save);
        }
    }

    private static void drawWifiArcs(Canvas c, int level, int fg, TrioAppearance a) {
        STROKE.setStrokeWidth(a.arcStroke);
        STROKE.setColor(fg);
        if (level >= 3) {
            arc(c, TrioGeometry.W_CX, TrioGeometry.W1_CY, TrioGeometry.W1_R,
                    TrioGeometry.W1_START, TrioGeometry.W1_SWEEP);
            arc(c, TrioGeometry.W_CX, TrioGeometry.W2_CY, TrioGeometry.W2_R,
                    TrioGeometry.W2_START, TrioGeometry.W2_SWEEP);
        } else if (level == 2) {
            arc(c, TrioGeometry.W_CX, TrioGeometry.W2_CY, TrioGeometry.W2_R,
                    TrioGeometry.W2_START, TrioGeometry.W2_SWEEP);
        }
        if (level >= 1) {
            FILL.setColor(fg);
            c.drawPath(WIFI_DOT, FILL);
        }
    }

    private static void drawLevelDots(Canvas c, int level, int[] slotLevels, boolean dual,
                                     int fg, TrioAppearance a) {
        final int inactive = withAlpha(fg, a.trackAlpha);
        if (!dual || slotLevels == null) {
            final int active = level < 0 ? 0 : Math.min(TrioGeometry.DOTS.length, level);
            for (int i = 0; i < TrioGeometry.DOTS.length; i++) {
                FILL.setColor(i < active ? fg : inactive);
                c.drawCircle(TrioGeometry.DOTS[i][0], TrioGeometry.DOTS[i][1],
                        TrioGeometry.DOT_R, FILL);
            }
            return;
        }
        final int row = TrioGeometry.DOTS.length;
        for (int i = 0; i < TrioGeometry.DOTS_DUAL.length; i++) {
            final int step = i % row;
            final int slotLevel = slotLevels[i / row];
            final int active = slotLevel < 0 ? 0 : Math.min(row, slotLevel);
            FILL.setColor(step < active ? fg : inactive);
            c.drawCircle(TrioGeometry.DOTS_DUAL[i][0], TrioGeometry.DOTS_DUAL[i][1],
                    TrioGeometry.DUAL_DOT_R, FILL);
        }
    }

    private static final RectF OUT_BAR = new RectF();

    static void outSignalLevels(boolean dataSimOnly, int mobileLevel, int[] slotLevels,
                               int dataSlot, int[] out) {
        int slot0 = -1;
        int slot1 = -1;
        if (slotLevels != null && slotLevels.length >= TrioState.SIM_SLOTS) {
            slot0 = slotLevels[0];
            slot1 = slotLevels[1];
        }
        final int firstAnswered = slot0 >= 0 ? slot0 : slot1;
        int bars;
        int dots = -1;
        if (dataSimOnly) {
            bars = firstAnswered;
            if (dataSlot == 0 && slot0 >= 0) {
                bars = slot0;
            } else if (dataSlot == 1 && slot1 >= 0) {
                bars = slot1;
            }
        } else if (slot0 >= 0 && slot1 >= 0) {
            bars = slot0;
            dots = slot1;
        } else {
            bars = firstAnswered;
        }
        if (bars < 0) {
            bars = mobileLevel;
        }
        out[0] = bars;
        out[1] = dots;
    }

    static float outSignalInkH(boolean dots) {
        return dots ? TrioGeometry.STACK_INK_H
                : TrioGeometry.STACK_BAR_H[TrioGeometry.STACK_COLUMNS - 1];
    }

    static int outSignalHeight(int sizeDp, float density) {
        if (sizeDp <= 0 || density <= 0f) {
            return 0;
        }
        return Math.max(1, Math.round(sizeDp * density));
    }

    static int outSignalWidth(int height) {
        if (height <= 0) {
            return 0;
        }
        return Math.max(1,
                Math.round(height * TrioGeometry.STACK_INK_W / TrioGeometry.STACK_INK_H));
    }

    static boolean drawOutSignal(Canvas c, int width, int height, int bars, int dots,
                                 int fg, TrioAppearance a) {
        if (bars < 0 || width <= 0 || height <= 0) {
            return false;
        }
        final boolean dotRow = dots >= 0;
        final float inkW = TrioGeometry.STACK_INK_W;
        final float inkH = outSignalInkH(dotRow);
        final float scale = Math.min(width / inkW, height / TrioGeometry.STACK_INK_H);
        if (scale <= 0f) {
            return false;
        }
        final int columns = TrioGeometry.STACK_COLUMNS;
        final float x0 = (width - inkW * scale) / 2f;
        final float baseline = (height - inkH * scale) / 2f
                + TrioGeometry.STACK_BAR_H[columns - 1] * scale;
        final float barW = TrioGeometry.STACK_BAR_W * scale;
        final float radius = barW / 2f;
        final float dotR = TrioGeometry.STACK_DOT_D * scale / 2f;
        final float dotCy = baseline
                + (TrioGeometry.STACK_DOT_GAP + TrioGeometry.STACK_DOT_D / 2f) * scale;
        final int inactive = withAlpha(fg, a.trackAlpha);
        for (int i = 0; i < columns; i++) {
            final float left = x0 + i * TrioGeometry.STACK_PITCH * scale;
            FILL.setColor(i < bars ? fg : inactive);
            OUT_BAR.set(left, baseline - TrioGeometry.STACK_BAR_H[i] * scale,
                    left + barW, baseline);
            c.drawRoundRect(OUT_BAR, radius, radius, FILL);
            if (dotRow) {
                FILL.setColor(i < dots ? fg : inactive);
                c.drawCircle(left + radius, dotCy, dotR, FILL);
            }
        }
        return true;
    }

    static float outTypePx(int sizeDp, float density) {
        if (sizeDp <= 0 || density <= 0f) {
            return 0f;
        }
        return sizeDp * density;
    }

    static int outTypeWidth(String type, float density, TrioAppearance a) {
        final float size = (a == null) ? 0f : outTypePx(a.outTypeSize, density);
        if (type == null || type.isEmpty() || size <= 0f) {
            return 0;
        }
        final int scale = (a == null) ? 100 : a.typeSuffixScale;
        final boolean shrunk = TrioGeometry.hasShrunkSuffix(type, scale);
        final String base = shrunk ? TrioGeometry.typeBase(type) : type;
        final String suffix = shrunk ? String.valueOf(TrioGeometry.TYPE_SUFFIX) : "";
        TEXT.setTypeface(typefaceFor(a.typeWeight));
        TEXT.setTextScaleX(1f);
        TEXT.setTextSize(size);
        float w = TEXT.measureText(base);
        TEXT.setTextSize(size * scale / 100f);
        w += TEXT.measureText(suffix);
        return Math.max(1, (int) Math.ceil(w));
    }

    static int outTypeHeight(float density, TrioAppearance a) {
        final float size = (a == null) ? 0f : outTypePx(a.outTypeSize, density);
        if (size <= 0f) {
            return 0;
        }
        TEXT.setTypeface(typefaceFor(a.typeWeight));
        TEXT.setTextSize(size);
        final Paint.FontMetrics fm = TEXT.getFontMetrics();
        return Math.max(1, (int) Math.ceil(fm.descent - fm.ascent));
    }

    static void drawOutType(Canvas c, int w, int h, String type, int fg,
                            float density, TrioAppearance a, float bottomY) {
        final float size = (a == null) ? 0f : outTypePx(a.outTypeSize, density);
        if (type == null || type.isEmpty() || size <= 0f || w <= 0 || h <= 0) {
            return;
        }
        final int scale = (a == null) ? 100 : a.typeSuffixScale;
        final boolean shrunk = TrioGeometry.hasShrunkSuffix(type, scale);
        final String base = shrunk ? TrioGeometry.typeBase(type) : type;
        final String suffix = shrunk ? String.valueOf(TrioGeometry.TYPE_SUFFIX) : "";
        final float suffixSize = size * scale / 100f;

        TEXT.setTypeface(typefaceFor(a.typeWeight));
        TEXT.setTextAlign(Paint.Align.LEFT);
        TEXT.setTextScaleX(1f);
        TEXT.setColor(fg);

        TEXT.setTextSize(size);
        final float baseWidth = TEXT.measureText(base);
        TEXT.setTextSize(suffixSize);
        final float suffixWidth = TEXT.measureText(suffix);

        final float total = baseWidth + suffixWidth;
        final float scaleX = (total > w && total > 0f) ? w / total : 1f;
        TEXT.setTextScaleX(scaleX);

        TEXT.setTextSize(size);
        final Paint.FontMetrics fm = TEXT.getFontMetrics();
        final float baseline = bottomY;
        c.drawText(base, 0f, baseline, TEXT);
        if (shrunk) {
            TEXT.setTextSize(suffixSize);
            c.drawText(suffix, baseWidth * scaleX, baseline, TEXT);
        }
        TEXT.setTextAlign(Paint.Align.CENTER);
        TEXT.setTextScaleX(1f);
    }

    private static final char PERF_BREAK = '\n';
    private static final float PERF_MIN_SIZE_RATIO = 0.6f;

    static String perfText(boolean tempOn, int tempC) {
        if (tempOn && tempC != TrioState.TEMP_UNKNOWN) {
            return tempC + "\u00B0C";
        }
        return "";
    }

    static int perfLines(String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == PERF_BREAK) {
                lines++;
            }
        }
        return lines;
    }

    static float perfTextSize(String text, int hostW, int hostH,
                              float baseSize, TrioAppearance a) {
        final float factor = (a == null) ? 1f : a.perfFit();
        final int lines = perfLines(text);
        if (lines < 2 || hostW <= 0 || hostH <= 0) {
            return baseSize * factor;
        }
        final float fit = inkScale(hostW, hostH, a.rect)
                * (((a == null) ? 100 : a.glyphScale) > 100 ? 1f : a.glyphFit());
        if (fit <= 0f) {
            return baseSize * factor;
        }
        final float inkH = a.rect ? TrioGeometry.RECT_INK_H : TrioGeometry.INK_H;
        final float icon = inkH * fit;
        final float lineHeight = perfLineHeight(baseSize, a.perfWeight);
        if (lineHeight <= 0f) {
            return baseSize * factor;
        }
        final float size = baseSize * (icon / lines) / lineHeight;
        return Math.max(baseSize * PERF_MIN_SIZE_RATIO, Math.min(baseSize, size)) * factor;
    }

    static float perfLineHeight(float size, int weight) {
        TEXT.setTypeface(typefaceFor(weight));
        TEXT.setTextSize(size);
        final Paint.FontMetrics fm = TEXT.getFontMetrics();
        return fm.descent - fm.ascent;
    }

    static int perfWidth(String text, float size, TrioAppearance a) {
        if (text == null || text.isEmpty() || size <= 0f) {
            return 0;
        }
        TEXT.setTypeface(typefaceFor(a == null ? 700 : a.perfWeight));
        TEXT.setTextScaleX(1f);
        TEXT.setTextSize(size);
        char widest = '0';
        float best = -1f;
        for (char c = '0'; c <= '9'; c++) {
            final float w = TEXT.measureText(String.valueOf(c));
            if (w > best) {
                best = w;
                widest = c;
            }
        }
        final StringBuilder probe = new StringBuilder(text.length());
        float width = 0f;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == PERF_BREAK) {
                width = Math.max(width, TEXT.measureText(probe.toString()));
                probe.setLength(0);
                continue;
            }
            final char c = text.charAt(i);
            probe.append(c >= '0' && c <= '9' ? widest : c);
        }
        return (int) Math.ceil(width);
    }

    static int perfHeight(String text, float size, TrioAppearance a) {
        if (text == null || text.isEmpty() || size <= 0f) {
            return 0;
        }
        final int lines = perfLines(text);
        return Math.max(1, (int) Math.ceil(perfLineHeight(size, a == null ? 700 : a.perfWeight)
                * lines));
    }

    static void drawPerf(Canvas c, int w, int h, String text, float size, int fg,
                         TrioAppearance a, float bottomY) {
        if (text == null || text.isEmpty() || size <= 0f || w <= 0 || h <= 0) {
            return;
        }
        final int weight = (a == null) ? 700 : a.perfWeight;
        TEXT.setTypeface(typefaceFor(weight));
        TEXT.setTextAlign(Paint.Align.LEFT);
        TEXT.setTextScaleX(1f);
        TEXT.setColor(fg);
        TEXT.setTextSize(size);
        final Paint.FontMetrics fm = TEXT.getFontMetrics();
        final float lineHeight = fm.descent - fm.ascent;
        final int lines = perfLines(text);
        float baseline = bottomY - (lines - 1) * lineHeight;
        int start = 0;
        for (int i = 0; i < lines; i++) {
            final String line;
            if (i == lines - 1) {
                line = text.substring(start);
            } else {
                final int br = text.indexOf(PERF_BREAK, start);
                line = text.substring(start, br);
                start = br + 1;
            }
            c.drawText(line, 0f, baseline, TEXT);
            baseline += lineHeight;
        }
        TEXT.setTextAlign(Paint.Align.CENTER);
        TEXT.setTextScaleX(1f);
    }

    private static void applyWeight(int weight) {
        if (weight == sTextWeight) {
            return;
        }
        TEXT.setTypeface(typefaceFor(weight));
        sTextWeight = weight;
    }

    private static void drawValue(Canvas c, int level, int fg, TrioAppearance a, boolean centre) {
        final String text = String.valueOf(level);
        final float requested = centre ? TrioGeometry.centreSize(a.valueSize) : a.valueSize;
        final float clear = centre ? TrioGeometry.centreClearWidth(requested, a.ringStroke)
                                   : TrioGeometry.gapClearWidth(a.ringStroke);
        applyWeight(a.valueWeight);
        final float size = fitSize(text, requested, clear);
        TEXT.setTextSize(size);
        TEXT.setColor(fg);
        c.drawText(text, TrioGeometry.VALUE_X,
                centre ? TrioGeometry.centerBaseline(size)
                       : TrioGeometry.gapBaseline(size),
                TEXT);
    }

    private static void drawCentreType(Canvas c, String type, int fg, TrioAppearance a) {
        final float requested = a.typeSize;
        final float clear = TrioGeometry.centreClearWidth(requested, a.ringStroke);
        applyWeight(a.typeWeight);
        if (type == null || type.isEmpty()) {
            final float size = Math.min(requested, clear);
            final float capH = size * TrioGeometry.CAP_HEIGHT_RATIO;
            final float cy = TrioGeometry.centerBaseline(size) - capH * 0.5f;
            drawSlotIcon(c, TrioGeometry.VALUE_X, cy, capH, fg);
            return;
        }
        final float size = fitSize(type, requested, clear, a.typeSuffixScale);
        drawType(c, type, TrioGeometry.VALUE_X, TrioGeometry.centerBaseline(size),
                size, fg, a, a.typeSuffixScale);
    }

    private static void drawGapType(Canvas c, String type, int fg, TrioAppearance a) {
        final float requested = a.typeSize;
        final float clear = TrioGeometry.gapClearWidth(a.ringStroke);
        applyWeight(a.typeWeight);
        if (type == null || type.isEmpty()) {
            final float size = Math.min(requested, clear);
            final float capH = size * TrioGeometry.CAP_HEIGHT_RATIO;
            final float cy = TrioGeometry.gapBaseline(size) - capH * 0.5f;
            drawSlotIcon(c, TrioGeometry.VALUE_X, cy, capH, fg);
            return;
        }
        final float size = fitSize(type, requested, clear, a.typeSuffixScale);
        drawType(c, type, TrioGeometry.VALUE_X, TrioGeometry.gapBaseline(size),
                size, fg, a, a.typeSuffixScale);
    }

    private static void drawSlotIcon(Canvas c, float cx, float cy, float capH, int fg) {
        final float inkH = capH * STATE_ICON_SCALE;
        if (TrioState.sAirplaneMode) {
            TrioSystemIcon.draw(c, TrioState.sAirplaneResId, cx, cy, inkH, fg, -90f);
            return;
        }
        if (!TrioState.sMobileDataOn) {
            drawDataArrows(c, cx, cy, inkH, fg);
        }
    }

    private static void drawType(Canvas c, String type, float x, float baseline,
                                 float size, int fg, TrioAppearance a, int suffixPercent) {
        if (!TrioGeometry.hasShrunkSuffix(type, suffixPercent)) {
            TEXT.setTextAlign(Paint.Align.CENTER);
            TEXT.setTextSize(size);
            TEXT.setColor(fg);
            c.drawText(type, x, baseline, TEXT);
            return;
        }
        final String base = TrioGeometry.typeBase(type);
        final String suffix = String.valueOf(TrioGeometry.TYPE_SUFFIX);
        final float suffixSize = size * suffixPercent / 100f;
        TEXT.setTextAlign(Paint.Align.LEFT);
        TEXT.setColor(fg);
        TEXT.setTextSize(size);
        final float baseWidth = TEXT.measureText(base);
        TEXT.setTextSize(suffixSize);
        final float suffixWidth = TEXT.measureText(suffix);
        float left = x - (baseWidth + suffixWidth) * 0.5f;
        TEXT.setTextSize(size);
        c.drawText(base, left, baseline, TEXT);
        left += baseWidth;
        TEXT.setTextSize(suffixSize);
        c.drawText(suffix, left, baseline, TEXT);
        TEXT.setTextAlign(Paint.Align.CENTER);
    }

    private static void drawBolt(Canvas c, int color) {
        final int save = c.save();
        c.translate(TrioGeometry.boltOffsetX(), TrioGeometry.boltOffsetY());
        c.scale(TrioGeometry.BOLT_SCALE, TrioGeometry.BOLT_SCALE);
        FILL.setColor(color);
        c.drawPath(BOLT, FILL);
        c.restoreToCount(save);
    }

    private static float fitSize(String text, float requested, float clearWidth) {
        return fitSize(text, requested, clearWidth, 100);
    }

    private static float fitSize(String text, float requested, float clearWidth,
                                 int scalePercent) {
        if (clearWidth <= 0f) {
            return requested;
        }
        TEXT.setTextSize(requested);
        final float width = measureType(text, requested, scalePercent);
        if (width <= clearWidth || width <= 0f) {
            return requested;
        }
        return requested * (clearWidth / width);
    }

    private static float measureType(String text, float size, int scalePercent) {
        if (!TrioGeometry.hasShrunkSuffix(text, scalePercent)) {
            TEXT.setTextSize(size);
            return TEXT.measureText(text);
        }
        TEXT.setTextSize(size);
        final float base = TEXT.measureText(TrioGeometry.typeBase(text));
        TEXT.setTextSize(size * scalePercent / 100f);
        final float suffix = TEXT.measureText(String.valueOf(TrioGeometry.TYPE_SUFFIX));
        TEXT.setTextSize(size);
        return base + suffix;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    static float inkScale(int w, int h) {
        return inkScale(w, h, TrioGeometry.INK_W, TrioGeometry.INK_H);
    }

    static float inkScale(int w, int h, boolean rect) {
        return rect
                ? inkScale(w, h, TrioGeometry.RECT_INK_W, TrioGeometry.RECT_INK_H)
                : inkScale(w, h);
    }

    static float inkScale(int w, int h, boolean capsule, boolean rect) {
        if (capsule) {
            return inkScale(w, h, TrioGeometry.capInkW(), TrioGeometry.CAP_INK_H);
        }
        return inkScale(w, h, rect);
    }

    static final float INK_INSET = 0.94f;

    static int viewEdgeForInk(float inkPx) {
        if (inkPx <= 0f) {
            return 0;
        }
        return (int) Math.ceil(inkPx / INK_INSET);
    }

    static float inkScale(int w, int h, float inkW, float inkH) {
        if (w <= 0 || h <= 0) {
            return 0f;
        }
        return Math.min(w / inkW, h / inkH) * INK_INSET;
    }

    static Typeface typefaceFor(int weight) {
        return Typeface.create(TEXT_BASE, weight, false);
    }

    private static Path buildBolt() {
        final Path p = new Path();
        p.moveTo(62.1f, 2.2f);
        p.quadTo(62.8f, 2.5f, 62.6f, 3.3f);
        p.lineTo(61.2f, 7.8f);
        p.lineTo(65.9f, 7.8f);
        p.quadTo(66.9f, 7.8f, 67.3f, 8.6f);
        p.quadTo(67.6f, 9.3f, 67.0f, 10.0f);
        p.lineTo(57.0f, 21.3f);
        p.quadTo(56.4f, 22.0f, 55.6f, 21.6f);
        p.quadTo(55.0f, 21.3f, 55.3f, 20.5f);
        p.lineTo(57.4f, 14.1f);
        p.lineTo(52.9f, 14.1f);
        p.quadTo(52.0f, 14.1f, 51.6f, 13.3f);
        p.quadTo(51.3f, 12.6f, 51.9f, 12.0f);
        p.lineTo(61.1f, 2.7f);
        p.quadTo(61.6f, 2.1f, 62.1f, 2.2f);
        p.close();
        return p;
    }

    private static Path buildWifiDot() {
        final Path p = new Path();
        p.moveTo(59.5f, 69.9f);
        p.cubicTo(61.0f, 69.9f, 65.2f, 70.8f, 66.5f, 73.0f);
        p.cubicTo(66.7f, 73.8f, 66.7f, 74.3f, 66.5f, 75.0f);
        p.cubicTo(63.8f, 78.8f, 61.15f, 80.95f, 59.5f, 80.95f);
        p.cubicTo(57.85f, 80.95f, 55.2f, 78.8f, 52.5f, 75.0f);
        p.cubicTo(52.3f, 74.3f, 52.3f, 73.8f, 52.5f, 73.0f);
        p.cubicTo(53.8f, 70.8f, 58.0f, 69.9f, 59.5f, 69.9f);
        p.close();
        return p;
    }

    static boolean isDarkBackground(int color) {
        final float lum = (0.2126f * Color.red(color)
                + 0.7152f * Color.green(color)
                + 0.0722f * Color.blue(color)) / 255f;
        return lum > 0.5f;
    }

    static int capsuleViewWidth(int edge) {
        if (edge <= 0) {
            return 0;
        }
        TrioGeometry.setCapsuleLength(TrioConfig.get().capsuleLength);
        TrioGeometry.setCapsuleTimeSide(TrioConfig.get().capsuleTimeSide);
        return Math.round(edge * TrioGeometry.capInkW() / TrioGeometry.CAP_INK_H);
    }

    static int capsuleViewHeight(int width) {
        if (width <= 0) {
            return 0;
        }
        TrioGeometry.setCapsuleLength(TrioConfig.get().capsuleLength);
        TrioGeometry.setCapsuleTimeSide(TrioConfig.get().capsuleTimeSide);
        return Math.round(width * (TrioGeometry.CAP_INK_BOTTOM - TrioGeometry.CAP_INK_TOP)
                / TrioGeometry.capInkW());
    }

    // =========================================================
    // ★ 核心修改：顶部中间断开，电量精准填入缺口中心
    // =========================================================
    private static void drawCapsuleLayout(Canvas canvas, int level, boolean charging,
                                          boolean quickCharging, int wifiLevel, int mobileLevel,
                                          int[] slotLevels, int sims, String mobileType,
                                          int role, int fg, TrioAppearance a) {
        final float stroke = a.ringStroke * TrioGeometry.CAP_STROKE_RATIO;
        final TrioAppearance.Capsule cap =
                a.capsule(level, charging, wifiLevel, mobileType, sims);

        // 1. 计算电量文字，为了给顶部开口留出空间
        String levelText = String.valueOf(level);
        applyWeight(a.valueWeight);
        float levelSize = capsuleTextSize(levelText, a.valueSize, stroke,
                TrioGeometry.capOpeningWidth(), 100,
                TrioGeometry.capTextMaxSize(stroke)) * CUSTOM_LEVEL_SIZE_SCALE;
        
        // 强制顶部开口！使用自定义的宽度
        final float topReach = CUSTOM_TOP_GAP_WIDTH;

        final float bottomReach =
                openingReach(cap, TrioAppearance.Zone.BOTTOM, stroke, level, mobileType, a);

        STROKE.setStrokeWidth(stroke);
        STROKE.setStyle(Paint.Style.STROKE);
        final Path outline = new Path();
        // buildCapsuleOutline 会根据 topReach 把顶部的线切断
        buildCapsuleOutline(outline, topReach, bottomReach, a.capsuleShape);
        STROKE.setColor(withAlpha(fg, a.trackAlpha));
        canvas.drawPath(outline, STROKE);

        if (level > 0) {
            FILL_STROKE.setColor(role);
            drawCapsuleFill(canvas, stroke, Math.min(1f, level / 100f), topReach, bottomReach,
                    a.capsuleShape);
        }

        if (cap.dots) {
            drawCapsuleDots(canvas, mobileLevel, slotLevels, cap.dual, stroke, fg, a, cap);
        }

        final float interiorCy = TrioGeometry.capInteriorCy(stroke);

        // 2. 时间固定在左侧槽位
        drawCapsuleTime(canvas, stroke, fg, a,
                TrioGeometry.capTimeCx(stroke, TrioGeometry.capsuleTimeSide()),
                interiorCy, -1f);

        // 3. 网络状态固定在右侧槽位
        String netText = "";
        if (wifiLevel >= 0) {
            netText = "WiFi";
        } else if (mobileType != null && !mobileType.isEmpty()) {
            netText = mobileType;
        }

        if (!netText.isEmpty()) {
            float netSize = capsuleTextSize(netText, a.valueSize, stroke,
                    TrioGeometry.capValueSlotWidth(stroke), 100,
                    TrioGeometry.capTextMaxSize(stroke));
            TEXT.setTextSize(netSize);
            TEXT.setColor(fg);
            TEXT.setTextAlign(Paint.Align.CENTER);
            float netCx = TrioGeometry.capValueCx(stroke, TrioGeometry.capsuleTimeSide());
            canvas.drawText(netText, netCx, interiorCy + (netSize * TrioGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT);
        }

        // 4. 电量精准填入中间的缺口
        if (level >= 0) {
            TEXT.setTextSize(levelSize);
            TEXT.setColor(fg);
            TEXT.setTextAlign(Paint.Align.CENTER);
            
            // X轴：绝对中心（直接使用胶囊左边和右边的物理中点）
            float capMidX = (TrioGeometry.CAP_LEFT + TrioGeometry.capRight()) * 0.5f;
            
            // Y轴：使用 FontMetrics 计算，让文字的视觉中心死死钉在 CAP_TOP (断口) 这条线上
            Paint.FontMetrics fm = TEXT.getFontMetrics();
            float centerY = TrioGeometry.CAP_TOP - (fm.ascent + fm.descent) / 2f;
            
            // 加上你自定义的上下偏移量
            float levelCy = centerY + CUSTOM_LEVEL_Y_OFFSET;
            
            canvas.drawText(levelText, capMidX, levelCy, TEXT);
        }

        // 5. 保留原有的顶部（如充电闪电）和底部（如信号点）绘制
        drawCapsuleCell(canvas, stroke, TrioAppearance.Zone.TOP, cap, level, quickCharging,
                wifiLevel, mobileType, fg, a);
        
        drawCapsuleCell(canvas, stroke, TrioAppearance.Zone.BOTTOM, cap, level, quickCharging,
                wifiLevel, mobileType, fg, a);
    }

    private static void drawCapsuleCell(Canvas c, float stroke, TrioAppearance.Zone band,
                                        TrioAppearance.Capsule cap, int level,
                                        boolean quickCharging, int wifiLevel, String type,
                                        int fg, TrioAppearance a) {
        final int part = cap.parts[band.ordinal()];
        final boolean pairHere = ((band == TrioAppearance.Zone.TOP && cap.pairTop)
                || (band == TrioAppearance.Zone.CENTRE && cap.pairCentre))
                && cap.pairMate != Prefs.PART_NONE;
        if (part == Prefs.PART_NONE && !pairHere) {
            return;
        }
        final float cx = (band == TrioAppearance.Zone.TOP || band == TrioAppearance.Zone.BOTTOM)
                ? (TrioGeometry.CAP_INK_X + TrioGeometry.capInkW() * 0.5f)
                : TrioGeometry.capValueCx(stroke, TrioGeometry.capsuleTimeSide());
        final float cy;
        switch (band) {
            case TOP:
                cy = TrioGeometry.capOpeningCy(true, stroke) + TrioGeometry.CAP_TOP_OPEN_DROP;
                break;
            case BOTTOM:
                cy = TrioGeometry.capOpeningCy(false, stroke);
                break;
            default:
                cy = TrioGeometry.capInteriorCy(stroke);
                break;
        }
        if (pairHere) {
            final int mate = cap.pairMate;
            drawCapsulePair(c, mate, cx, cy,
                    capsulePairDrawSize(band, mate, stroke, level, type, a),
                    level, type, wifiLevel, stroke,
                    quickCharging ? TrioGeometry.QUICK_CHARGE : fg, fg, a);
            return;
        }
        switch (part) {
            case Prefs.PART_BOLT:
                drawCapsuleBolt(c, cx, cy,
                        (band == TrioAppearance.Zone.TOP)
                                ? TrioGeometry.capOpeningTextRoomH(stroke)
                                : TrioGeometry.CAP_ICON_H,
                        quickCharging ? TrioGeometry.QUICK_CHARGE : fg);
                break;
            case Prefs.PART_WIFI:
                drawCapsuleWifi(c, cx, cy,
                        (band == TrioAppearance.Zone.CENTRE)
                                ? Math.min(TrioGeometry.CAP_ICON_H,
                                        TrioGeometry.capInteriorHeight(stroke))
                                : TrioGeometry.capOpeningTextRoomH(stroke),
                        fg, a, wifiLevel);
                break;
            case Prefs.PART_VALUE:
                drawCapsuleValue(c, stroke, level, fg, a, cx, cy, openingRoom(band, stroke));
                break;
            case Prefs.PART_TYPE:
                drawCapsuleType(c, stroke, type, fg, a, cx, cy, openingRoom(band, stroke));
                break;
            default:
                break;
        }
    }

    private static float effectiveHeightLimit(float hint, float stroke) {
        return hint < 0f ? TrioGeometry.capTextMaxSize(stroke) : hint;
    }

    private static float capsuleSlotWidth(TrioAppearance.Zone band, float heightHint,
                                          float stroke, boolean clock) {
        if (heightHint >= 0f) {
            return TrioGeometry.capOpeningWidth();
        }
        return clock ? TrioGeometry.capTimeSlotWidth(stroke)
                : TrioGeometry.capValueSlotWidth(stroke);
    }

    private static float openingRoom(TrioAppearance.Zone band, float stroke) {
        return band == TrioAppearance.Zone.CENTRE
                ? -1f
                : TrioGeometry.capOpeningTextEm(stroke);
    }

    private static float openingReach(TrioAppearance.Capsule cap, TrioAppearance.Zone band,
                                      float stroke, int level, String type, TrioAppearance a) {
        if (cap == null) {
            return 0f;
        }
        final float maxReach = TrioGeometry.capOpeningMaxReach();
        if (cap.dotsIn(band)) {
            return Math.min(TrioGeometry.capDotReach(stroke), maxReach);
        }
        final int part = cap.parts[band.ordinal()];
        if (part == Prefs.PART_NONE) {
            return 0f;
        }
        if ((band == TrioAppearance.Zone.TOP && cap.pairTop)
                || (band == TrioAppearance.Zone.CENTRE && cap.pairCentre)) {
            return Math.min(capPairHalf(cap.pairMate, stroke, level, type, a, band)
                    + TrioGeometry.capDotClearance() + stroke * 0.5f
                    + TrioGeometry.CAP_OPENING_EXTRA, maxReach);
        }
        return Math.min(TrioGeometry.capPieceReach(part, level, type, stroke,
                TrioGeometry.capOpeningTextRoomH(stroke))
                + TrioGeometry.capDotClearance() + stroke * 0.5f
                + TrioGeometry.CAP_OPENING_EXTRA, maxReach);
    }

    private static float capsulePairDrawSize(TrioAppearance.Zone band, int mate, float stroke,
                                             int level, String type, TrioAppearance a) {
        final float base = capsulePairBaseSize(mate, stroke, level, type, a);
        final float avail = (band == TrioAppearance.Zone.CENTRE)
                ? TrioGeometry.capValueSlotWidth(stroke)
                : TrioGeometry.capOpeningAvailWidth(stroke);
        final float size = fitPairSize(mate, base, avail, level, type, a);
        return (band == TrioAppearance.Zone.TOP)
                ? Math.min(size, TrioGeometry.capOpeningTextRoomH(stroke)
                        / TrioGeometry.CAP_HEIGHT_RATIO)
                : size;
    }

    private static float capPairHalf(int mate, float stroke, int level, String type,
                                     TrioAppearance a, TrioAppearance.Zone band) {
        if (mate == Prefs.PART_NONE) {
            return TrioGeometry.BOLT_INK_W * TrioGeometry.capBoltScale(stroke) * 0.5f;
        }
        final float size = capsulePairDrawSize(band, mate, stroke, level, type, a);
        final float capH = size * TrioGeometry.CAP_HEIGHT_RATIO;
        final float boltW = TrioGeometry.BOLT_INK_W * (capH / TrioGeometry.BOLT_INK_H);
        final float mateW = capsuleReadingWidth(mate, level, type, size, a);
        final float halfBase = (boltW + TrioGeometry.CAP_PAIR_GAP + mateW) * 0.5f;
        final float avail = (band == TrioAppearance.Zone.CENTRE)
                ? TrioGeometry.capValueSlotWidth(stroke)
                : TrioGeometry.capOpeningAvailWidth(stroke);
        return Math.min(halfBase, avail * 0.5f);
    }

    private static void buildCapsuleOutline(Path p, float topReach, float bottomReach, int shape) {
        final float left = TrioGeometry.CAP_LEFT;
        final float right = TrioGeometry.capRight();
        final float top = TrioGeometry.CAP_TOP;
        final float bottom = TrioGeometry.CAP_BOTTOM;
        final float r = TrioGeometry.CAP_RADIUS;
        final float cy = (top + bottom) * 0.5f;
        final float cx = (left + right) * 0.5f;
        p.reset();
        if (shape == Prefs.CAP_SHAPE_SAMSUNG) {
            final float n = TrioGeometry.CAP_SHAPE_SAMSUNG_N;
            p.moveTo(cx + topReach, top);
            p.lineTo(right - r, top);
            appendCap(p, right - r, cy, r, n, (float) -Math.PI / 2, 0f, false);
            appendCap(p, right - r, cy, r, n, 0f, (float) Math.PI / 2, false);
            p.lineTo(cx + bottomReach, bottom);
            p.moveTo(cx - bottomReach, bottom);
            p.lineTo(left + r, bottom);
            appendCap(p, left + r, cy, r, n, (float) Math.PI / 2, 0f, true);
            appendCap(p, left + r, cy, r, n, 0f, (float) -Math.PI / 2, true);
            p.lineTo(cx - topReach, top);
            return;
        }
        final float k = 0.5522847498307936f * r;
        p.reset();
        p.moveTo(cx + topReach, top);
        p.lineTo(right - r, top);
        p.cubicTo(right - r + k, top, right, cy - k, right, cy);
        p.cubicTo(right, cy + k, right - r + k, bottom, right - r, bottom);
        p.lineTo(cx + bottomReach, bottom);
        p.moveTo(cx - bottomReach, bottom);
        p.lineTo(left + r, bottom);
        p.cubicTo(left + r - k, bottom, left, cy + k, left, cy);
        p.cubicTo(left, cy - k, left + r - k, top, left + r, top);
        p.lineTo(cx - topReach, top);
    }

    private static void appendCap(Path p, float ccx, float ccy, float r, float n,
                                  float a0, float a1, boolean leftSide) {
        final int SEG = 20;
        final float xdir = leftSide ? -1f : 1f;
        for (int i = 1; i <= SEG; i++) {
            final float a = a0 + (a1 - a0) * i / SEG;
            final float ct = (float) Math.cos(a);
            final float st = (float) Math.sin(a);
            final float x = ccx + xdir * r * (float) Math.pow(Math.abs(ct), 2f / n) * Math.signum(ct);
            final float y = ccy + r * (float) Math.pow(Math.abs(st), 2f / n) * Math.signum(st);
            p.lineTo(x, y);
        }
    }

    private static void drawCapsuleFill(Canvas c, float stroke, float fraction,
                                        float topReach, float bottomReach, int shape) {
        final float left = TrioGeometry.CAP_LEFT;
        final float right = TrioGeometry.capRight();
        final float top = TrioGeometry.CAP_TOP;
        final float bottom = TrioGeometry.CAP_BOTTOM;
        final float r = TrioGeometry.CAP_RADIUS;
        final float cy = (top + bottom) * 0.5f;
        final float cx = (left + right) * 0.5f;
        final float topLeft = (cx - topReach) - (left + r);
        final float bottomLeft = (cx - bottomReach) - (left + r);
        final float bottomRight = (right - r) - (cx + bottomReach);
        final float topRight = (right - r) - (cx + topReach);
        final float capLen = (shape == Prefs.CAP_SHAPE_SAMSUNG)
                ? capArcLen(r, TrioGeometry.CAP_SHAPE_SAMSUNG_N)
                : (float) Math.PI * r;
        final float total = topLeft + capLen + bottomLeft + bottomRight + capLen + topRight;
        if (total <= 0f) {
            return;
        }
        final float budget = total * fraction;
        if (budget <= 0f) {
            return;
        }
        final Path p = new Path();
        float left0 = budget;
        boolean join;
        float prev = left0;
        left0 = run(p, left0, bottomLeft, cx - bottomReach, bottom, left + r, bottom, true);
        join = bottomLeft > 0f && prev >= bottomLeft;
        left0 = cap(p, left0, capLen, left + r, cy, r, 90f, 180f, shape, join);
        left0 = run(p, left0, topLeft, left + r, top, cx - topReach, top, false);
        prev = left0;
        left0 = run(p, left0, topRight, cx + topReach, top, right - r, top, true);
        join = topRight > 0f && prev >= topRight;
        left0 = cap(p, left0, capLen, right - r, cy, r, 270f, 180f, shape, join);
        run(p, left0, bottomRight, right - r, bottom, cx + bottomReach, bottom, false);
        FILL_STROKE.setStrokeWidth(stroke);
        c.drawPath(p, FILL_STROKE);
    }

    private static float run(Path p, float remaining, float length,
                             float x1, float y1, float x2, float y2, boolean fresh) {
        if (remaining <= 0f || length <= 0f) {
            return remaining;
        }
        if (fresh) {
            p.moveTo(x1, y1);
        }
        if (remaining >= length) {
            p.lineTo(x2, y2);
            return remaining - length;
        }
        final float t = remaining / length;
        p.lineTo(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t);
        return 0f;
    }

    private static float cap(Path p, float remaining, float length,
                             float cx, float cy, float r, float startDeg, float sweepDeg,
                             int shape, boolean join) {
        if (remaining <= 0f || length <= 0f) {
            return remaining;
        }
        if (shape == Prefs.CAP_SHAPE_SAMSUNG) {
            return capSquircle(p, remaining, cx, cy, r,
                    TrioGeometry.CAP_SHAPE_SAMSUNG_N, startDeg, sweepDeg, join);
        }
        ARC.set(cx - r, cy - r, cx + r, cy + r);
        if (remaining >= length) {
            p.arcTo(ARC, startDeg, sweepDeg, !join);
            return remaining - length;
        }
        p.arcTo(ARC, startDeg, sweepDeg * (remaining / length), !join);
        return 0f;
    }

    private static float capSquircle(Path p, float remaining,
                                     float cx, float cy, float r, float n,
                                     float startDeg, float sweepDeg, boolean join) {
        final int SEG = 48;
        final float a0 = (float) Math.toRadians(startDeg);
        final float a1 = (float) Math.toRadians(startDeg + sweepDeg);
        final float c0 = (float) Math.cos(a0);
        final float s0 = (float) Math.sin(a0);
        float px = cx + r * (float) Math.pow(Math.abs(c0), 2f / n) * Math.signum(c0);
        float py = cy + r * (float) Math.pow(Math.abs(s0), 2f / n) * Math.signum(s0);
        if (!join) {
            p.moveTo(px, py);
        }
        float consumed = 0f;
        for (int i = 1; i <= SEG; i++) {
            final float a = a0 + (a1 - a0) * i / SEG;
            final float ct = (float) Math.cos(a);
            final float st = (float) Math.sin(a);
            final float x = cx + r * (float) Math.pow(Math.abs(ct), 2f / n) * Math.signum(ct);
            final float y = cy + r * (float) Math.pow(Math.abs(st), 2f / n) * Math.signum(st);
            final float segLen = (float) Math.hypot(x - px, y - py);
            if (remaining - consumed >= segLen) {
                p.lineTo(x, y);
                consumed += segLen;
            } else {
                final float t = Math.max(0f, (remaining - consumed) / segLen);
                p.lineTo(px + (x - px) * t, py + (y - py) * t);
                consumed = remaining;
                break;
            }
            px = x;
            py = y;
        }
        return remaining - consumed;
    }

    private static float capArcLen(float r, float n) {
        final int SEG = 96;
        float len = 0f;
        float px = r, py = 0f;
        for (int i = 1; i <= SEG; i++) {
            final float a = (float) Math.PI * i / SEG;
            final float ct = (float) Math.cos(a);
            final float st = (float) Math.sin(a);
            final float x = r * (float) Math.pow(Math.abs(ct), 2f / n) * Math.signum(ct);
            final float y = r * (float) Math.pow(Math.abs(st), 2f / n) * Math.signum(st);
            len += (float) Math.hypot(x - px, y - py);
            px = x;
            py = y;
        }
        return len;
    }

    private static void drawCapsuleDots(Canvas c, int level, int[] slotLevels, boolean dual,
                                        float stroke, int fg, TrioAppearance a,
                                        TrioAppearance.Capsule cap) {
        final boolean topRow = cap == null || cap.dotsIn(TrioAppearance.Zone.TOP);
        final boolean bottomRow = cap == null || cap.dotsIn(TrioAppearance.Zone.BOTTOM);
        final int top = (dual && slotLevels != null) ? slotLevels[0] : level;
        final int bottom = (dual && slotLevels != null) ? slotLevels[1] : level;
        final int inactive = withAlpha(fg, a.trackAlpha);
        final int topActive = top < 0 ? 0 : Math.min(TrioGeometry.RECT_DOT_ROWS, top);
        final int bottomActive = bottom < 0 ? 0 : Math.min(TrioGeometry.RECT_DOT_ROWS, bottom);
        final float topY = TrioGeometry.capDotTopY();
        final float bottomY = TrioGeometry.capDotBottomY();
        final float first = TrioGeometry.capDotCx() - TrioGeometry.CAP_DOT_PITCH * 1.5f;
        FILL.setStyle(Paint.Style.FILL);
        for (int i = 0; i < TrioGeometry.RECT_DOT_ROWS; i++) {
            final float x = first + i * TrioGeometry.CAP_DOT_PITCH;
            if (topRow) {
                FILL.setColor(i < topActive ? fg : inactive);
                c.drawCircle(x, topY, TrioGeometry.CAP_DOT_R, FILL);
            }
            if (bottomRow) {
                FILL.setColor(i < bottomActive ? fg : inactive);
                c.drawCircle(x, bottomY, TrioGeometry.CAP_DOT_R, FILL);
            }
        }
    }

    private static void drawCapsuleTime(Canvas c, float stroke, int fg, TrioAppearance a,
                                        float cx, float cy, float heightHint) {
        final String text = clockText();
        if (text == null || text.isEmpty()) {
            return;
        }
        applyWeight(a.valueWeight);
        final float size = capsuleTextSize(text, a.valueSize, stroke,
                capsuleSlotWidth(TrioAppearance.Zone.CENTRE, heightHint, stroke, true), 100,
                effectiveHeightLimit(heightHint, stroke));
        TEXT.setTextSize(size);
        TEXT.setColor(fg);
        TEXT.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, cx, cy + (size * TrioGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT);
    }

    private static void drawCapsuleValue(Canvas c, float stroke, int level, int fg,
                                         TrioAppearance a, float cx, float cy,
                                         float heightHint) {
        final String text = String.valueOf(level);
        applyWeight(a.valueWeight);
        final float size = capsuleTextSize(text, a.valueSize, stroke,
                capsuleSlotWidth(TrioAppearance.Zone.CENTRE, heightHint, stroke, false), 100,
                effectiveHeightLimit(heightHint, stroke));
        TEXT.setTextSize(size);
        TEXT.setColor(fg);
        TEXT.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, cx, cy + (size * TrioGeometry.CAP_HEIGHT_RATIO) * 0.5f, TEXT);
    }

    private static void drawCapsuleType(Canvas c, float stroke, String type, int fg,
                                        TrioAppearance a, float cx, float cy,
                                        float heightHint) {
        final float size = capsuleTextSize(type, a.valueSize, stroke,
                TrioGeometry.capOpeningWidth(), a.typeSuffixScale,
                effectiveHeightLimit(heightHint, stroke));
        final float capH = size * TrioGeometry.CAP_HEIGHT_RATIO;
        if (type == null || type.isEmpty()) {
            drawSignalStateIcon(c, cx, cy, stroke, fg, capH);
            return;
        }
        applyWeight(a.typeWeight);
        drawType(c, type, cx, cy + capH * 0.5f, size, fg, a, a.typeSuffixScale);
    }

    private static float signalStateIconWidth(float capH) {
        final float inkH = capH * STATE_ICON_SCALE;
        if (TrioState.sAirplaneMode) {
            return TrioSystemIcon.onScreenWidth(TrioState.sAirplaneResId, inkH, -90f);
        }
        if (!TrioState.sMobileDataOn) {
            final float arrowH = inkH * DATA_ARROW_H;
            final float gap = inkH * DATA_ARROW_GAP;
            return TrioSystemIcon.onScreenWidth(TrioState.sDataInResId, arrowH, 0f)
                    + gap
                    + TrioSystemIcon.onScreenWidth(TrioState.sDataOutResId, arrowH, 0f);
        }
        return 0f;
    }

    private static void drawSignalStateIcon(Canvas c, float cx, float cy, float stroke,
                                             int fg, float capH) {
        drawSlotIcon(c, cx, cy, capH, fg);
    }

    private static void drawDataArrows(Canvas c, float cx, float cy, float inkH, int fg) {
        final int inId = TrioState.sDataInResId;
        final int outId = TrioState.sDataOutResId;
        if (inId == 0 && outId == 0) {
            return;
        }
        final float arrowH = inkH * DATA_ARROW_H;
        final float gap = inkH * DATA_ARROW_GAP;
        final float wIn = TrioSystemIcon.onScreenWidth(inId, arrowH, 0f);
        final float wOut = TrioSystemIcon.onScreenWidth(outId, arrowH, 0f);
        if (wIn <= 0f && wOut <= 0f) {
            return;
        }
        final float totalW = wIn + gap + wOut;
        float x = cx - totalW * 0.5f;
        if (wIn > 0f) {
            TrioSystemIcon.draw(c, inId, x + wIn * 0.5f, cy, arrowH, fg, 0f);
        }
        x += wIn + gap;
        if (wOut > 0f) {
            TrioSystemIcon.draw(c, outId, x + wOut * 0.5f, cy, arrowH, fg, 0f);
        }
        drawDiagonalSlash(c, cx, cy,
                TrioSystemIcon.strokeWidth(outId != 0 ? outId : inId, arrowH), totalW);
    }

    private static void drawDiagonalSlash(Canvas c, float cx, float cy, float w, float spanW) {
        if (spanW <= 0f || w <= 0f) {
            return;
        }
        final float halfW = spanW * 0.5f * SLASH_SPAN;
        final float halfH = halfW * 0.1763f;
        FILL.setStyle(Paint.Style.STROKE);
        FILL.setStrokeWidth(w);
        FILL.setStrokeCap(Paint.Cap.ROUND);
        FILL.setColor(SLASH_COLOR);
        c.drawLine(cx - halfW, cy - halfH, cx + halfW, cy + halfH, FILL);
        FILL.setStrokeCap(Paint.Cap.BUTT);
        FILL.setStyle(Paint.Style.FILL);
    }

    private static float capsuleTextSize(String text, int setting, float stroke,
                                         float slotWidth, int suffixPercent) {
        return capsuleTextSize(text, setting, stroke, slotWidth, suffixPercent,
                TrioGeometry.capTextMaxSize(stroke));
    }

    private static float capsuleTextSize(String text, int setting, float stroke,
                                         float slotWidth, int suffixPercent,
                                         float heightLimit) {
        final float requested = Math.min(setting * TrioGeometry.CAP_TEXT_SCALE, heightLimit);
        return fitSize(text, requested, slotWidth, suffixPercent);
    }

    private static void drawCapsuleWifi(Canvas c, float cx, float cy, float capH, int fg,
                                        TrioAppearance a, int level) {
        final float inkH = TrioGeometry.wifiInkH(a.arcStroke);
        if (inkH <= 0f) {
            return;
        }
        final float scale = capH / inkH;
        final float inkCy = (TrioGeometry.W_TOP - a.arcStroke * 0.5f
                + TrioGeometry.WIFI_INK_BOTTOM) * 0.5f;
        final int save = c.save();
        c.translate(cx - TrioGeometry.W_CX * scale, cy - inkCy * scale);
        c.scale(scale, scale);
        drawWifiArcs(c, level, fg, a);
        c.restoreToCount(save);
    }

    private static void drawCapsuleBolt(Canvas c, float cx, float cy, float capH, int color) {
        final float scale = capH / TrioGeometry.BOLT_INK_H;
        final int save = c.save();
        c.translate(cx - TrioGeometry.BOLT_CX_REF * scale,
                cy - TrioGeometry.BOLT_INK_CY * scale);
        c.scale(scale, scale);
        FILL.setStyle(Paint.Style.FILL);
        FILL.setColor(color);
        c.drawPath(BOLT, FILL);
        c.restoreToCount(save);
    }

    private static void drawCapsulePair(Canvas c, int mate, float cx, float cy, float size,
                                        int level, String mobileType, int wifiLevel,
                                        float stroke, int boltColor, int fg,
                                        TrioAppearance a) {
        final float capH = size * TrioGeometry.CAP_HEIGHT_RATIO;
        final float boltW = TrioGeometry.BOLT_INK_W * (capH / TrioGeometry.BOLT_INK_H);
        final float mateW = capsuleReadingWidth(mate, level, mobileType, size, a);
        final float left = cx - (boltW + TrioGeometry.CAP_PAIR_GAP + mateW) * 0.5f;
        drawCapsuleBolt(c, left + boltW * 0.5f, cy, capH, boltColor);
        drawCapsuleMate(c, mate, left + boltW + TrioGeometry.CAP_PAIR_GAP + mateW * 0.5f, cy,
                capH, level, mobileType, wifiLevel, stroke, fg, a);
    }

    private static void drawCapsuleMate(Canvas c, int mate, float cx, float cy, float capH,
                                        int level, String mobileType, int wifiLevel, float stroke,
                                        int fg, TrioAppearance a) {
        if (mate == Prefs.PART_WIFI) {
            drawCapsuleWifi(c, cx, cy, capH, fg, a, wifiLevel);
            return;
        }
        if (mate == Prefs.PART_TYPE) {
            final String type = (mobileType == null) ? "" : mobileType;
            if (type.isEmpty()) {
                drawSignalStateIcon(c, cx, cy, stroke, fg, capH);
                return;
            }
            drawType(c, type, cx, cy + capH * 0.5f, capH / TrioGeometry.CAP_HEIGHT_RATIO,
                    fg, a, a.typeSuffixScale);
            return;
        }
        applyWeight(a.valueWeight);
        TEXT.setTextAlign(Paint.Align.CENTER);
        TEXT.setTextSize(capH / TrioGeometry.CAP_HEIGHT_RATIO);
        TEXT.setColor(fg);
        c.drawText(String.valueOf(level), cx, cy + capH * 0.5f, TEXT);
    }

    private static float capsulePairBaseSize(int mate, float stroke, int level,
                                            String mobileType, TrioAppearance a) {
        if (mate == Prefs.PART_WIFI) {
            return Math.min(TrioGeometry.CAP_ICON_H, TrioGeometry.capInteriorHeight(stroke))
                    / TrioGeometry.CAP_HEIGHT_RATIO;
        }
        final int base = (mate == Prefs.PART_VALUE) ? a.chargeValueSize : a.chargeSignalSize;
        return capsuleTextSize(capsuleReadingText(mate, level, mobileType),
                base, stroke,
                TrioGeometry.capValueSlotWidth(stroke), capsuleSuffixPercent(mate, a));
    }

    private static String capsuleReadingText(int mate, int level, String mobileType) {
        if (mate == Prefs.PART_TYPE) {
            return (mobileType == null) ? "" : mobileType;
        }
        if (mate == Prefs.PART_VALUE) {
            return String.valueOf(level);
        }
        return "";
    }

    private static int capsuleSuffixPercent(int mate, TrioAppearance a) {
        return (mate == Prefs.PART_TYPE) ? a.typeSuffixScale : 100;
    }

    private static float capsuleReadingWidth(int mate, int level, String mobileType, float size,
                                             TrioAppearance a) {
        if (mate == Prefs.PART_WIFI) {
            final float scale = size * TrioGeometry.CAP_HEIGHT_RATIO
                    / TrioGeometry.wifiInkH(a.arcStroke);
            return TrioGeometry.wifiInkW(a.arcStroke) * scale;
        }
        applyWeight(mate == Prefs.PART_TYPE ? a.typeWeight : a.valueWeight);
        TEXT.setTextSize(size);
        if (mate == Prefs.PART_TYPE) {
            final String type = (mobileType == null) ? "" : mobileType;
            if (type.isEmpty()) {
                return signalStateIconWidth(size * TrioGeometry.CAP_HEIGHT_RATIO);
            }
            if (!TrioGeometry.hasShrunkSuffix(type, a.typeSuffixScale)) {
                return TEXT.measureText(type);
            }
            final float base = TEXT.measureText(TrioGeometry.typeBase(type));
            TEXT.setTextSize(size * a.typeSuffixScale / 100f);
            final float suffix = TEXT.measureText(String.valueOf(TrioGeometry.TYPE_SUFFIX));
            TEXT.setTextSize(size);
            return base + suffix;
        }
        return TEXT.measureText(String.valueOf(level));
    }

    private static float fitPairSize(int mate, float size, float avail, int level,
                                     String mobileType, TrioAppearance a) {
        final float capH = size * TrioGeometry.CAP_HEIGHT_RATIO;
        final float boltW = TrioGeometry.BOLT_INK_W * (capH / TrioGeometry.BOLT_INK_H);
        final float mateW = capsuleReadingWidth(mate, level, mobileType, size, a);
        final float total = boltW + TrioGeometry.CAP_PAIR_GAP + mateW;
        if (total <= avail || total <= 0f) {
            return size;
        }
        return size * (avail / total);
    }

    private static String clockText() {
        final Calendar cal = Calendar.getInstance();
        final int hour = cal.get(Calendar.HOUR_OF_DAY);
        final int minute = cal.get(Calendar.MINUTE);
        if (sIs24Hour == null || sIs24Hour.booleanValue()) {
            return String.format(Locale.US, "%02d:%02d", Integer.valueOf(hour),
                    Integer.valueOf(minute));
        }
        final int h12 = hour % 12;
        return String.format(Locale.US, "%02d:%02d", Integer.valueOf(h12 == 0 ? 12 : h12),
                Integer.valueOf(minute));
    }

}
