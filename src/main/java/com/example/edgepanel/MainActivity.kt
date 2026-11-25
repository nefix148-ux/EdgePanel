package com.example.edgepanel

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(pad, pad, pad, pad)
        val t = TextView(this)
        t.text = "Потяни язычок слева вправо, нажми «+» и добавь приложения."
        t.textSize = 18f
        root.addView(t)
        val on = Button(this)
        on.text = "Включить"
        on.setOnClickListener { start() }
        root.addView(on)
        val off = Button(this)
        off.text = "Выключить"
        off.setOnClickListener { stopService(Intent(this, EdgeService::class.java)) }
        root.addView(off)
        setContentView(root)
    }

    private fun start() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return
        }
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        startForegroundService(Intent(this, EdgeService::class.java))
    }
}
