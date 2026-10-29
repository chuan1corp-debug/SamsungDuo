package com.jsj.samsungduo;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;

/**
 * 三星原生状态图标的提取与绘制（飞行模式飞机 / 移动数据上下箭头）。
 *
 * <p><b>为什么不直接 {@code Drawable.draw}</b>：三星的 {@code stat_sys_*} 是矢量图，
 * ① 自带留白（bounds 比墨迹大），按 bounds 画出来比同格的 5G 文字小一圈；
 * ② 自带 alpha（半透明），画出来偏灰，跟同格的 5G 文字对不上。
 * 这里改成：<b>先栅格化一次量出墨迹范围、把 alpha 归一</b>，再按<b>墨迹高</b>缩放、
 * 用与文字同一个墨色着色 —— 于是图标与 5G 文字同高同色。
 *
 * <p>栅格化结果按资源 id 缓存（每个图标只做一次），之后每帧只是一次 drawBitmap。
 */
final class TrioSystemIcon {

    /** 栅格化分辨率：够量清墨迹边界即可（128 已远超状态栏实际像素）。 */
    private static final int RASTER = 128;

    /** 资源 id → 墨迹。 */
    private static final SparseArray<Shape> sShapes = new SparseArray<>();

    /** 一个图标栅格化后的墨迹。 */
    private static final class Shape {
        /** alpha = 形状覆盖（已按最大值拉满），RGB 无意义（绘制时统一着色）。 */
        Bitmap mask;
        /** 墨迹宽高，单位为 mask 像素。 */
        float w;
        float h;
    }

    private static final Paint PAINT =
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private TrioSystemIcon() {
    }

    /**
     * 取图标、量出墨迹并把 alpha 拉满；失败（资源不存在 / 画不出）返回 null。
     *
     * <p>alpha 归一的理由：图标的浓淡不该由图标自带透明度决定 —— 颜色只由墨色定，
     * 否则同一格里 5G 文字是不透明的、图标却半透明，看着"发灰"。
     */
    private static Shape shape(int resId) {
        if (resId == 0) {
            return null;
        }
        final Shape cached = sShapes.get(resId);
        if (cached != null) {
            return cached;
        }
        try {
            final Resources res = TrioState.getSystemUiResources();
            if (res == null) {
                return null;
            }
            // ★ 带主题取：三星的 stat_sys_* 的 fillColor 走主题属性，
            //   不给主题会解析成默认色（灰）。
            final Drawable d = res.getDrawable(resId, TrioState.getSystemUiTheme());
            if (d == null) {
                return null;
            }
            d.mutate();
            // 保持长宽比：非方形的图标不能拉成正方形。
            int iw = d.getIntrinsicWidth();
            int ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) {
                iw = 24;
                ih = 24;
            }
            final int bw;
            final int bh;
            if (iw >= ih) {
                bw = RASTER;
                bh = Math.max(1, Math.round(RASTER * (float) ih / iw));
            } else {
                bh = RASTER;
                bw = Math.max(1, Math.round(RASTER * (float) iw / ih));
            }
            final Bitmap full = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            final Canvas cv = new Canvas(full);
            d.setBounds(0, 0, bw, bh);
            d.draw(cv);
            final int[] px = new int[bw * bh];
            full.getPixels(px, 0, bw, 0, 0, bw, bh);
            full.recycle();
            int maxA = 0;
            int minX = bw;
            int minY = bh;
            int maxX = -1;
            int maxY = -1;
            for (int i = 0; i < px.length; i++) {
                final int a = (px[i] >>> 24);
                if (a > maxA) {
                    maxA = a;
                }
                if (a > 8) {
                    final int x = i % bw;
                    final int y = i / bw;
                    if (x < minX) {
                        minX = x;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
            if (maxX < 0 || maxA <= 0) {
                return null;
            }
            final int w = maxX - minX + 1;
            final int h = maxY - minY + 1;
            final int[] out = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    final int a = (px[(y + minY) * bw + (x + minX)] >>> 24);
                    final int na = Math.min(255, Math.round(a * 255f / maxA));
                    out[y * w + x] = (na << 24) | 0x00FFFFFF;
                }
            }
            final Bitmap mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            mask.setPixels(out, 0, w, 0, 0, w, h);
            final Shape sh = new Shape();
            sh.mask = mask;
            sh.w = w;
            sh.h = h;
            sShapes.put(resId, sh);
            return sh;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 把图标画到 {@code (cx, cy)}：<b>画面上的墨迹高</b> = {@code inkH}（设计单位），
     * 颜色与 5G 文字完全同色，绕中心旋转 {@code rotateDeg} 度（逆时针为正）。
     *
     * @return 画成功返回 true；资源缺失等返回 false。
     */
    static boolean draw(Canvas c, int resId, float cx, float cy, float inkH, int fg,
                        float rotateDeg) {
        final Shape sh = shape(resId);
        if (sh == null || inkH <= 0f) {
            return false;
        }
        // 旋转 90° 时画面上的高来自图形的宽 —— 按"画面高"定尺要先换过来。
        final boolean swap = Math.abs(rotateDeg % 180f) == 90f;
        final float scale = inkH / (swap ? sh.w : sh.h);
        final float w = sh.w * scale;
        final float h = sh.h * scale;
        final int save = c.save();
        c.translate(cx, cy);
        if (rotateDeg != 0f) {
            c.rotate(rotateDeg);
        }
        PAINT.setColorFilter(new PorterDuffColorFilter(fg, PorterDuff.Mode.SRC_IN));
        c.drawBitmap(sh.mask, null, new RectF(-w * 0.5f, -h * 0.5f, w * 0.5f, h * 0.5f), PAINT);
        PAINT.setColorFilter(null);
        c.restoreToCount(save);
        return true;
    }

    /** 该图标在给定画面墨迹高下的**画面宽**（排位用）；取不到返回 0。 */
    static float onScreenWidth(int resId, float inkH, float rotateDeg) {
        final Shape sh = shape(resId);
        if (sh == null || inkH <= 0f || sh.w <= 0f || sh.h <= 0f) {
            return 0f;
        }
        final boolean swap = Math.abs(rotateDeg % 180f) == 90f;
        final float scale = inkH / (swap ? sh.w : sh.h);
        return (swap ? sh.h : sh.w) * scale;
    }
}
