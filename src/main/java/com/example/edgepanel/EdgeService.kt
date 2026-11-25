package com.example.edgepanel

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class EdgeService : Service() {
    private lateinit var wm: WindowManager
    private var handle: View? = null
    private var panel: View? = null
    private val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, f: Int, id: Int) = START_STICKY

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("edge", "Язычок", NotificationManager.IMPORTANCE_MIN))
        startForeground(1, Notification.Builder(this, "edge")
            .setSmallIcon(android.R.drawable.ic_menu_more)
            .setContentTitle("Язычок работает").build())
        showHandle()
    }

    private fun overlay(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT)

    private fun showHandle() {
        val v = View(this)
        val r = dp(10).toFloat()
        v.background = GradientDrawable().apply {
            setColor(Color.argb(140, 120, 120, 120))
            cornerRadii = floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        }
        val lp = overlay(dp(18), dp(96))
        lp.gravity = Gravity.TOP or Gravity.START
        lp.y = (resources.displayMetrics.heightPixels * 0.42).toInt() - dp(48)
        var downX = 0f
        var opened = false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; opened = false }
                MotionEvent.ACTION_MOVE ->
                    if (!opened && e.rawX - downX > dp(30)) { opened = true; showPanel() }
                MotionEvent.ACTION_UP ->
                    if (!opened && e.rawX - downX < dp(10)) showPanel()
            }
            true
        }
        wm.addView(v, lp)
        handle = v
    }

    private fun showPanel() {
        if (panel != null) return
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.argb(70, 0, 0, 0))
        root.setOnClickListener { closePanel() }
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.isClickable = true
        card.setPadding(dp(12), dp(12), dp(12), dp(12))
        card.background = GradientDrawable().apply {
            setColor(Color.argb(235, 30, 30, 30))
            cornerRadius = dp(20).toFloat()
        }
        val lp = FrameLayout.LayoutParams(dp(270), ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.START or Gravity.CENTER_VERTICAL)
        lp.leftMargin = dp(8)
        root.addView(card, lp)
        wm.addView(root, overlay(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        panel = root
        showPinned(card)
    }

    private fun prefs() = getSharedPreferences("p", Context.MODE_PRIVATE)
    private fun pins() = prefs().getString("pins", "")!!.split(",").filter { it.isNotEmpty() }
    private fun savePins(l: List<String>) =
        prefs().edit().putString("pins", l.joinToString(",")).apply()

    private fun cell(icon: Drawable?, onClick: () -> Unit): View {
        val v: View = if (icon != null) ImageView(this).apply { setImageDrawable(icon) }
        else TextView(this).apply {
            text = "+"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(80, 255, 255, 255))
            }
        }
        v.layoutParams = GridLayout.LayoutParams().apply {
            width = dp(56); height = dp(56)
            setMargins(dp(10), dp(10), dp(10), dp(10))
        }
        v.setOnClickListener { onClick() }
        return v
    }

    private fun showPinned(card: LinearLayout) {
        card.removeAllViews()
        val grid = GridLayout(this)
        grid.columnCount = 3
        for (pkg in pins()) {
            val icon = try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null } ?: continue
            val c = cell(icon) { launch(pkg) }
            c.setOnLongClickListener { savePins(pins() - pkg); showPinned(card); true }
            grid.addView(c)
        }
        grid.addView(cell(null) { showAll(card) })
        card.addView(grid)
    }

    private fun showAll(card: LinearLayout) {
        card.removeAllViews()
        val pm = packageManager
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(q, 0)
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .forEach { ri ->
                val pkg = ri.activityInfo.packageName
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    addView(ImageView(context).apply {
                        setImageDrawable(ri.loadIcon(pm))
                        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                    })
                    addView(TextView(context).apply {
                        text = ri.loadLabel(pm)
                        textSize = 16f
                        setTextColor(Color.WHITE)
                        setPadding(dp(12), 0, 0, 0)
                    })
                    setOnClickListener { savePins((pins() + pkg).distinct()); showPinned(card) }
                }
                list.addView(row)
            }
        val sv = ScrollView(this)
        sv.addView(list)
        card.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(400)))
    }

    private fun launch(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val d = resources.displayMetrics
        val w = (d.widthPixels * 0.85).toInt()
        val h = (d.heightPixels * 0.5).toInt()
        val l = (d.widthPixels - w) / 2
        val t = (d.heightPixels * 0.1).toInt()
        val o = ActivityOptions.makeBasic().setLaunchBounds(Rect(l, t, l + w, t + h))
        startActivity(i, o.toBundle())
        closePanel()
    }

    private fun closePanel() {
        panel?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        panel = null
    }

    override fun onDestroy() {
        closePanel()
        handle?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        super.onDestroy()
    }
}
