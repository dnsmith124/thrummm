package com.dnsmith.thrummm

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout

/**
 * Preset list + custom on/off/pulses fields + pattern readout + test button.
 * With [defaultSpec] set, a "Use default" option is offered and [spec] may be null.
 */
class PatternEditor(
    private val ui: Ui,
    initial: PatternSpec?,
    private val defaultSpec: PatternSpec? = null,
    private val onChange: (PatternSpec?) -> Unit = {},
) : LinearLayout(ui.context) {

    private var preset: Preset? = initial?.preset
    private var onMs = initial?.onMs ?: 600
    private var offMs = initial?.offMs ?: 250
    private var pulses = initial?.pulses ?: 2

    private val customRow: LinearLayout
    private val readout = ui.explainer("")

    /** Current selection; null means "use default". */
    val spec: PatternSpec?
        get() = preset?.let { PatternSpec(it, onMs, offMs, pulses) }

    init {
        orientation = VERTICAL

        val options = buildList {
            if (defaultSpec != null) add("Use default · ${defaultSpec.label()}" to null)
            Preset.values().forEach { add(it.label to it) }
        }
        addView(ui.Choice(options, preset, vertical = true) { preset = it; changed() }, matchWidth())

        customRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, ui.dp(12), 0, 0)
            addView(field("On ms", onMs) { onMs = it }, weighted())
            addView(field("Off ms", offMs) { offMs = it }, weighted(ui.dp(8)))
            addView(field("Pulses", pulses.toLong()) { pulses = it.toInt() }, weighted(ui.dp(8)))
        }
        addView(customRow, matchWidth())

        addView(readout.apply { setPadding(0, ui.dp(10), 0, ui.dp(12)) })
        addView(ui.pill("Test vibration") { Buzzer.vibrate(context, effective().timings()) }, matchWidth())
        refresh()
    }

    private fun effective(): PatternSpec = spec ?: defaultSpec!!

    private fun changed() {
        refresh()
        onChange(spec)
    }

    private fun refresh() {
        customRow.visibility = if (preset == Preset.CUSTOM) View.VISIBLE else View.GONE
        readout.text = "Pattern: " + effective().timings().joinToString(" · ") + " ms"
    }

    private fun field(caption: String, initial: Long, set: (Long) -> Unit) = LinearLayout(context).apply {
        orientation = VERTICAL
        addView(ui.explainer(caption).apply { setPadding(ui.dp(2), 0, 0, ui.dp(4)) })
        addView(ui.numberField(initial).apply { onNumber { set(it); changed() } }, matchWidth())
    }

    private fun EditText.onNumber(block: (Long) -> Unit) = addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) {
            s?.toString()?.toLongOrNull()?.let(block)
        }
    })

    private fun matchWidth() = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun weighted(startMargin: Int = 0) =
        LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = startMargin }
}
