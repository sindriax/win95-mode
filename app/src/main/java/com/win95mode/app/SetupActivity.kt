package com.win95mode.app

import android.app.WallpaperManager
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** First-run wizard in the style of Windows 95 Setup: Welcome, launcher check, Apply, Finish. */
class SetupActivity : AppCompatActivity() {

    private var step = 0
    private var launcherChecked = false
    private var wantWallpaper = true
    private var appliedMessage: String? = null

    private val title by lazy { findViewById<TextView>(R.id.setup_title) }
    private val body by lazy { findViewById<TextView>(R.id.setup_body) }
    private val stepLabel by lazy { findViewById<TextView>(R.id.setup_step) }
    private val progress by lazy { findViewById<BlockProgressBar>(R.id.setup_progress) }
    private val actions by lazy { findViewById<LinearLayout>(R.id.setup_actions) }
    private val wallpaperBox by lazy { findViewById<CheckBox>(R.id.setup_wallpaper) }
    private val back by lazy { findViewById<TextView>(R.id.btn_setup_back) }
    private val next by lazy { findViewById<TextView>(R.id.btn_setup_next) }
    private val cancel by lazy { findViewById<TextView>(R.id.btn_setup_cancel) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Win95.edgeToEdge(this)
        setContentView(R.layout.activity_setup)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.setup_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<ImageView>(R.id.setup_background).setImageBitmap(
            Wallpapers.thumbnail(this, R.drawable.wall_setup, resources.displayMetrics.widthPixels / 2)
        )
        findViewById<ImageView>(R.id.setup_title_icon).setImageBitmap(IconArt.icon(resources, R.drawable.ic_disk, 96))

        step = savedInstanceState?.getInt("step") ?: intent.getIntExtra(EXTRA_STEP, 0)
        savedInstanceState?.let {
            launcherChecked = it.getBoolean("checked")
            wantWallpaper = it.getBoolean("wallpaper", true)
            appliedMessage = it.getString("applied")
        }
        wallpaperBox.setOnCheckedChangeListener { _, checked -> wantWallpaper = checked }
        back.setOnClickListener { go(step - 1) }
        next.setOnClickListener { onNext() }
        cancel.setOnClickListener { close() }
        findViewById<View>(R.id.btn_setup_close).setOnClickListener { close() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = if (step > 0 && step < LAST) go(step - 1) else close()
        })
        go(step)
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the Play Store or the home-screen setting: look again.
        if (step == 1 && launcherChecked) showLauncherResult()
        if (step == 2) go(2)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("step", step)
        outState.putBoolean("checked", launcherChecked)
        outState.putBoolean("wallpaper", wantWallpaper)
        outState.putString("applied", appliedMessage)
    }

    private fun go(target: Int) {
        step = target.coerceIn(0, LAST)
        actions.removeAllViews()
        progress.visibility = View.GONE
        wallpaperBox.visibility = View.GONE
        stepLabel.visibility = if (step < LAST) View.VISIBLE else View.GONE
        stepLabel.text = getString(R.string.setup_step, step + 1, LAST)
        setEnabled(back, step in 1 until LAST)
        cancel.visibility = if (step < LAST) View.VISIBLE else View.GONE
        next.setText(if (step == LAST) R.string.setup_finish else R.string.setup_next)
        setEnabled(next, true)
        picture(R.drawable.ic_my_computer)

        when (step) {
            0 -> {
                title.setText(R.string.setup_welcome_title)
                body.setText(R.string.setup_welcome_body)
            }
            1 -> {
                title.setText(R.string.setup_launcher_title)
                if (launcherChecked) {
                    showLauncherResult()
                } else {
                    body.setText(R.string.setup_checking)
                    progress.visibility = View.VISIBLE
                    setEnabled(next, false)
                    progress.animateTo(1f, 900) {
                        launcherChecked = true
                        if (step == 1) showLauncherResult()
                    }
                }
            }
            2 -> {
                title.setText(R.string.setup_apply_title)
                wallpaperBox.visibility = View.VISIBLE
                wallpaperBox.isChecked = wantWallpaper
                when (val s = Launchers.state(this)) {
                    is LauncherState.Unsupported -> body.setText(R.string.setup_apply_later)
                    else -> {
                        val installed = if (s is LauncherState.Ready) listOf(s.target) else (s as LauncherState.NotHome).installed
                        body.text = getString(R.string.setup_apply_body, installed.first().label)
                        appliedMessage?.let { body.append("\n\n" + getString(R.string.setup_applied)) }
                        actions.addView(Win95.button(this, getString(R.string.apply_icons), primary = true) {
                            ApplyFlow.choose(this, installed) { msg ->
                                appliedMessage = msg
                                Win95.confirmHaptic(next)
                            }
                        }.full())
                    }
                }
            }
            LAST -> {
                title.setText(R.string.setup_finish_title)
                body.setText(R.string.setup_finish_body)
                picture(R.drawable.ic_win95mode)
            }
        }
    }

    private fun showLauncherResult() {
        if (step != 1) return
        progress.visibility = View.VISIBLE
        progress.progress = 1f
        actions.removeAllViews()
        when (val s = Launchers.state(this)) {
            is LauncherState.Ready -> {
                body.text = getString(R.string.setup_ready_body, s.target.label)
                setEnabled(next, true)
            }
            is LauncherState.NotHome -> {
                body.text = getString(R.string.setup_not_home_body, s.installed.first().label)
                actions.addView(Win95.button(this, getString(R.string.set_as_home_screen)) {
                    Launchers.openHomeSettings(this)
                }.full())
                setEnabled(next, true)
            }
            is LauncherState.Unsupported -> {
                body.text = getString(R.string.setup_unsupported_body, s.homeLabel ?: getString(R.string.your_home_screen))
                actions.addView(Win95.button(this, getString(R.string.get_lawnchair), primary = true) {
                    Launchers.openStore(this, "app.lawnchair")
                }.full())
                actions.addView(Win95.button(this, getString(R.string.other_launchers)) {
                    ApplyFlow.showLaunchers(this)
                }.full())
                setEnabled(next, false)
            }
        }
    }

    private fun onNext() {
        if (step < LAST) {
            go(step + 1)
            return
        }
        // Set last: a wallpaper change makes Android re-color and recreate running screens.
        if (wantWallpaper) {
            Wallpapers.apply(this, Wallpapers.all.first(), WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) {}
        }
        close()
    }

    private fun close() {
        Prefs.markSetupDone(this)
        finish()
    }

    private fun picture(resId: Int) {
        findViewById<ImageView>(R.id.setup_picture).setImageBitmap(
            IconArt.icon(resources, resId, IconArt.crispPx(this, 96f, maxPx = Win95.dp(this, 120)))
        )
    }

    /** Win95 disabled look: gray text with a white emboss. */
    private fun setEnabled(view: TextView, enabled: Boolean) {
        view.isEnabled = enabled
        view.setTextColor(getColor(if (enabled) R.color.black else R.color.win95_dark_gray))
        view.setShadowLayer(if (enabled) 0f else 0.01f, 1f, 1f, getColor(R.color.white))
    }

    private fun TextView.full() = apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = Win95.dp(context, 8) }
    }

    companion object {
        const val EXTRA_STEP = "step"
        private const val LAST = 3
    }
}
