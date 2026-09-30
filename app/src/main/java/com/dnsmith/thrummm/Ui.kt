package com.dnsmith.thrummm

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView

/**
 * View builders for the Side-suite look: Inter type, sky-derived ink and accent,
 * segmented pills on a faint track, accent "cushions" for the selected choice,
 * and no ripples.
 */
class Ui(val context: Context, val amb: Ambient) {

    private val light: Typeface = context.resources.getFont(R.font.inter_light)
    val regular: Typeface = context.resources.getFont(R.font.inter_regular)
    val semibold: Typeface = context.resources.getFont(R.font.inter_semibold)

    fun dp(v: Number): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

    fun rounded(color: Int, radiusDp: Int = 10) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    // ---- type ----

    fun text(s: CharSequence, sizeSp: Float, face: Typeface = regular, color: Int = amb.fg) =
        TextView(context).apply {
            text = s
            textSize = sizeSp
            typeface = face
            setTextColor(color)
            includeFontPadding = false
        }

    fun pageTitle(s: String) = text(s, 30f, light)

    fun sectionTitle(s: String) = text(s, 22f, light)

    fun label(s: String) = text(s, 17f)

    fun explainer(s: String) = text(s, 13f, color = amb.fg(0.85f)).apply {
        setLineSpacing(0f, 1.2f)
    }

    fun spacer(heightDp: Int) = Space(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
    }

    // ---- controls ----

    /** Label on the left, control on the right. */
    fun settingRow(label: String, control: View) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(label(label), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(control)
    }

    /**
     * Pill selector on a faint track; the chosen option sits on an accent cushion.
     * Horizontal for short labels, vertical for choices too long to sit side by side;
     * [fill] stretches a horizontal one edge to edge with equal cells (a tab bar).
     */
    inner class Choice<T>(
        options: List<Pair<String, T>>,
        selected: T,
        vertical: Boolean = false,
        fill: Boolean = false,
        private val onSelect: (T) -> Unit,
    ) : LinearLayout(context) {
        private val radius = if (vertical) 12 else 10
        private val cells: List<Pair<TextView, T>> = options.map { (label, value) ->
            text(label, 15f).apply {
                // Tab cells share the width equally; three pills must still leave room for a label.
                val side = if (fill) 4 else if (options.size > 2) 12 else 16
                if (fill) isSingleLine = true
                if (vertical) setPadding(dp(14), dp(11), dp(14), dp(11)) else setPadding(dp(side), dp(8), dp(side), dp(8))
                setOnClickListener { select(value); onSelect(value) }
            } to value
        }

        init {
            orientation = if (vertical) VERTICAL else HORIZONTAL
            background = rounded(amb.fg(if (vertical) 0.06f else 0.08f), radius)
            cells.forEach { (cell, _) ->
                when {
                    vertical -> addView(cell, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    fill -> addView(cell.apply { gravity = Gravity.CENTER }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    else -> addView(cell)
                }
            }
            select(selected)
        }

        /** Moves the cushion without calling onSelect. */
        fun select(value: T) = cells.forEach { (cell, v) ->
            val on = v == value
            cell.background = if (on) rounded(amb.accent(0.22f), radius) else null
            cell.typeface = if (on) semibold else regular
        }
    }

    fun onOff(selected: Boolean, onSelect: (Boolean) -> Unit) =
        Choice(listOf("Off" to false, "On" to true), selected, onSelect = onSelect)

    fun pill(label: String, onClick: () -> Unit) = text(label, 15f).apply {
        gravity = Gravity.CENTER
        background = rounded(amb.fg(0.08f))
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener { onClick() }
    }

    /** Right-hand value that opens something: accent, semibold, with a chevron. */
    fun link(label: String, onClick: () -> Unit) = text("$label  ›", 15f, semibold, amb.accent).apply {
        setPadding(dp(8), dp(6), 0, dp(6))
        setOnClickListener { onClick() }
    }

    fun numberField(initial: Long) = EditText(context).apply {
        setText(initial.toString())
        inputType = InputType.TYPE_CLASS_NUMBER
        textSize = 15f
        typeface = regular
        gravity = Gravity.CENTER
        setTextColor(amb.fg)
        background = rounded(amb.fg(0.08f))
        setPadding(dp(8), dp(9), dp(8), dp(9))
        textCursorDrawable = GradientDrawable().apply { setColor(amb.accent); setSize(dp(2), 1) }
        highlightColor = amb.accent(0.35f)
    }

    /**
     * A small centred panel a shade lighter than the sky, with Cancel in plain ink and
     * the action in bold accent.
     */
    fun dialog(title: String, body: View, actionLabel: String, onAction: () -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(amb.sheet, 14)
            setPadding(dp(18), dp(16), dp(10), dp(8))
            addView(text(title, 20f, light).apply { setPadding(0, 0, dp(8), dp(12)) })
            addView(
                ScrollView(context).apply {
                    isVerticalScrollBarEnabled = false
                    addView(body.apply { setPadding(0, 0, dp(8), 0) })
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(8), 0, 0)
                addView(text("Cancel", 15f).apply {
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    setOnClickListener { dialog.dismiss() }
                })
                addView(text(actionLabel, 15f, semibold, amb.accent).apply {
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    setOnClickListener { onAction(); dialog.dismiss() }
                })
            })
        }
        dialog.setContentView(panel)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val maxHeight = (context.resources.displayMetrics.heightPixels * 0.85).toInt()
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight)
            decorView.setPadding(dp(20), 0, dp(20), 0)
            setDimAmount(0.35f)
        }
        dialog.show()
    }
}
