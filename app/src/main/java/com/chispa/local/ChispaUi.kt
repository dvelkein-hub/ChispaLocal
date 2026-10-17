package com.chispa.local

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.widget.Button
import android.widget.TextView

/** Sistema visual ligero, Android nativo: no necesita Compose ni librerías gráficas. */
object ChispaUi {
    val BACKGROUND: Int = Color.rgb(8, 18, 32)
    val SURFACE: Int = Color.rgb(18, 35, 53)
    val SURFACE_ALT: Int = Color.rgb(26, 47, 67)
    val OUTLINE: Int = Color.rgb(45, 71, 93)
    val ACCENT: Int = Color.rgb(61, 222, 205)
    val TEXT: Int = Color.rgb(243, 250, 252)
    val MUTED: Int = Color.rgb(169, 192, 208)
    val USER: Int = Color.rgb(23, 84, 99)

    fun dp(ctx: Context, size: Int) = (size * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun background(ctx: Context, color: Int, radius: Int, stroke: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(ctx, radius).toFloat()
            if (stroke) setStroke(dp(ctx, 1), OUTLINE)
        }

    fun clickable(ctx: Context, color: Int, radius: Int): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(Color.argb(64, 237, 255, 255)),
            background(ctx, color, radius), null)

    fun label(ctx: Context, value: String, size: Float, color: Int = TEXT, heavy: Boolean = false): TextView =
        TextView(ctx).apply {
            text = value
            textSize = size
            setTextColor(color)
            includeFontPadding = false
            if (heavy) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    fun button(ctx: Context, label: String, primary: Boolean = false): Button = Button(ctx).apply {
        text = label
        textSize = 13f
        isAllCaps = false
        setTextColor(if (primary) BACKGROUND else TEXT)
        background = clickable(ctx, if (primary) ACCENT else SURFACE_ALT, 14)
        minHeight = dp(ctx, 45)
        minimumHeight = dp(ctx, 45)
        setPadding(dp(ctx, 12), dp(ctx, 5), dp(ctx, 12), dp(ctx, 5))
    }

    fun markdown(input: String): CharSequence {
        val text = input
            .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")
            .replace(Regex("(?m)^\\s*#{1,4}\\s+"), "")
        val target = SpannableStringBuilder()
        val pattern = Regex("\\*\\*([^*\\n]+)\\*\\*|\\x60([^\\x60\\n]+)\\x60")
        var pos = 0
        for (match in pattern.findAll(text)) {
            target.append(text.substring(pos, match.range.first))
            val value = (match.groups[1] ?: match.groups[2])!!.value
            val start = target.length
            target.append(value)
            if (match.groups[1] != null) {
                target.setSpan(StyleSpan(Typeface.BOLD), start, target.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                target.setSpan(TypefaceSpan("monospace"), start, target.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            pos = match.range.last + 1
        }
        target.append(text.substring(pos))
        return target
    }

    /** Avatar original para evitar usar sprites con propiedad intelectual ajena. */
    fun avatar(ctx: Context): View = object : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private fun rounded(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) {
            paint.color = color
            c.drawRoundRect(x, y, x + w, y + h, r, r, paint)
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val size = minOf(width, height).toFloat()
            canvas.save()
            canvas.translate((width - size) / 2, (height - size) / 2)
            canvas.scale(size / 72f, size / 72f)
            rounded(canvas, 3f, 4f, 66f, 64f, 17f, SURFACE)
            rounded(canvas, 10f, 12f, 52f, 48f, 12f, ACCENT)
            rounded(canvas, 13f, 25f, 46f, 20f, 5f, Color.rgb(18, 64, 111))
            rounded(canvas, 20f, 29f, 8f, 9f, 1f, Color.WHITE)
            rounded(canvas, 44f, 29f, 8f, 9f, 1f, Color.WHITE)
            rounded(canvas, 33f, 41f, 7f, 3f, 1f, Color.rgb(245, 137, 168))
            rounded(canvas, 29f, 7f, 14f, 7f, 3f, Color.rgb(117, 242, 226))
            canvas.restore()
        }
    }
}
