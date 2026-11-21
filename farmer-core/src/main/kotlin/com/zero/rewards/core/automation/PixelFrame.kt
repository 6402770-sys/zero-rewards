package com.zero.rewards.core.automation

import kotlin.math.abs

/**
 * A screen capture, possibly downscaled. [argb] is row-major, `width * height`
 * ints, 0xAARRGGBB; each frame pixel stands for [scale] x [scale] screen pixels.
 */
class PixelFrame(val width: Int, val height: Int, val argb: IntArray, val scale: Int = 1) {
    init {
        require(width > 0 && height > 0) { "empty frame ${width}x$height" }
        require(scale >= 1) { "scale $scale < 1" }
        require(argb.size == width * height) { "pixel count ${argb.size} != ${width}x$height" }
    }
}

/**
 * Pure frame comparison used to tell an auto-playing feed video from a still
 * image. Calibrated on 2026-11-21 captures of Bing's feed (1080x2460): between
 * frames ~0.6 s apart a playing video card changed 6–96 % of the sampled pixels
 * at [PIXEL_DELTA], while a still card and the static text row changed 0.00 %.
 */
object FrameMotion {
    /** Summed |ΔR|+|ΔG|+|ΔB| above which a sampled pixel counts as changed. */
    const val PIXEL_DELTA = 24

    /** Sampling stride in SCREEN pixels, both axes (3 frame pixels at scale 4). */
    const val STEP = 12

    /**
     * Fraction (0..1) of the pixels sampled inside the SCREEN-coordinate box
     * `[left, right) x [top, bottom)` whose colour differs between [a] and [b] by
     * more than [PIXEL_DELTA]. The box is clamped to the frame. Null when the
     * frames differ in size or scale, or the clamped box holds no sample.
     */
    fun changedFraction(a: PixelFrame, b: PixelFrame, left: Int, top: Int, right: Int, bottom: Int): Double? {
        if (a.width != b.width || a.height != b.height || a.scale != b.scale) return null
        val s = a.scale
        val x0 = (left.coerceAtLeast(0) / s).coerceIn(0, a.width)
        val x1 = (right.coerceAtLeast(0) / s).coerceIn(0, a.width)
        val y0 = (top.coerceAtLeast(0) / s).coerceIn(0, a.height)
        val y1 = (bottom.coerceAtLeast(0) / s).coerceIn(0, a.height)
        val step = maxOf(1, STEP / s)
        var total = 0
        var changed = 0
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                val i = y * a.width + x
                val p = a.argb[i]
                val q = b.argb[i]
                val d = abs(((p shr 16) and 0xFF) - ((q shr 16) and 0xFF)) +
                    abs(((p shr 8) and 0xFF) - ((q shr 8) and 0xFF)) +
                    abs((p and 0xFF) - (q and 0xFF))
                if (d > PIXEL_DELTA) changed++
                total++
                x += step
            }
            y += step
        }
        return if (total == 0) null else changed.toDouble() / total
    }
}
