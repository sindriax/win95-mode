package com.win95mode.app

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Win95 dialog pieces shared by every screen. */
object Win95 {

    fun dialog(activity: Activity, layoutId: Int): Dialog = Dialog(activity).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(layoutId)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    class Choice(val label: CharSequence, val primary: Boolean = false, val onClick: () -> Unit = {})

    /** A Win95 message box: glyph, text, and buttons on the right. The last choice is the default. */
    fun messageBox(
        activity: Activity,
        title: CharSequence,
        message: CharSequence,
        warning: Boolean = false,
        choices: List<Choice> = listOf(Choice(activity.getString(R.string.ok), primary = true))
    ): Dialog {
        val dialog = dialog(activity, R.layout.dialog_message)
        dialog.findViewById<TextView>(R.id.message_title).text = title
        dialog.findViewById<TextView>(R.id.message_text).text = message
        dialog.findViewById<ImageView>(R.id.message_glyph)
            .setImageResource(if (warning) R.drawable.win95_glyph_warning else R.drawable.win95_glyph_info)
        dialog.findViewById<View>(R.id.message_close).setOnClickListener { dialog.dismiss() }
        val row = dialog.findViewById<LinearLayout>(R.id.message_buttons)
        choices.forEach { choice ->
            row.addView(button(activity, choice.label, choice.primary) {
                dialog.dismiss()
                choice.onClick()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(activity, 8) }
            })
        }
        dialog.show()
        return dialog
    }

    fun button(context: Context, label: CharSequence, primary: Boolean = false, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextColor(context.getColor(R.color.black))
            textSize = 14f
            if (primary) setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            minHeight = dp(context, if (primary) 48 else 44)
            minWidth = dp(context, 88)
            setPadding(dp(context, 16), 0, dp(context, 16), 0)
            setBackgroundResource(R.drawable.win95_button_selector)
            setOnClickListener { onClick() }
        }

    fun confirmHaptic(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        )
    }

    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
