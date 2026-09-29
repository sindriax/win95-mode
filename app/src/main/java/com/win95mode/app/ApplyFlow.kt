package com.win95mode.app

import android.app.Activity
import android.widget.LinearLayout
import android.widget.TextView

/** Applying the pack, shared by Home and Setup. [done] gets a status-bar line. */
object ApplyFlow {

    fun apply(activity: Activity, target: LauncherTarget, done: (String) -> Unit) {
        if (Launchers.applyDirect(activity, target)) {
            done(activity.getString(R.string.status_applied, target.label))
            return
        }
        Win95.messageBox(
            activity,
            activity.getString(R.string.open_launcher_title, target.label),
            activity.getString(R.string.open_launcher_text, target.label),
            choices = listOf(
                Win95.Choice(activity.getString(R.string.cancel)),
                Win95.Choice(activity.getString(R.string.open_launcher, target.label), primary = true) {
                    Launchers.open(activity, target)
                    done(activity.getString(R.string.status_opened, target.label))
                }
            )
        )
    }

    /** One installed launcher applies straight away; several get a Display Properties chooser. */
    fun choose(activity: Activity, installed: List<LauncherTarget>, done: (String) -> Unit) {
        if (installed.size == 1) {
            apply(activity, installed.first(), done)
            return
        }
        list(activity, null, null, installed.map { it.label to { apply(activity, it, done) } })
    }

    /** Launchers someone without one can install. */
    fun showLaunchers(activity: Activity) {
        list(
            activity,
            activity.getString(R.string.launchers_title),
            activity.getString(R.string.launchers_text),
            Launchers.recommended.map { (label, pkg) -> label to { Launchers.openStore(activity, pkg) } }
        )
    }

    private fun list(activity: Activity, title: String?, text: String?, items: List<Pair<String, () -> Unit>>) {
        val dialog = Win95.dialog(activity, R.layout.dialog_apply)
        title?.let { dialog.findViewById<TextView>(R.id.apply_title).text = it }
        text?.let { dialog.findViewById<TextView>(R.id.apply_text).text = it }
        val list = dialog.findViewById<LinearLayout>(R.id.launcher_list)
        items.forEach { (label, action) ->
            list.addView(Win95.button(activity, label) {
                dialog.dismiss()
                action()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = Win95.dp(activity, 6) }
            })
        }
        dialog.findViewById<TextView>(R.id.btn_cancel_apply).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<TextView>(R.id.btn_close_apply).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
