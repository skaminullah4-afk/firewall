package com.firewall.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private fun makeText(
        value: String,
        size: Float
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(18, 18, 18)
        window.navigationBarColor = Color.BLACK

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 32, 24, 32)
            setBackgroundColor(Color.rgb(18, 18, 18))
        }

        layout.addView(
            makeText("🛡️ FIREWALL", 30f)
        )

        val status = makeText(
            "Your phone, your network rules",
            16f
        )

        val statusParams = LinearLayout.LayoutParams(
            -1, -2
        ).apply {
            topMargin = 20
            bottomMargin = 32
        }
        layout.addView(status, statusParams)

        val startButton = Button(this).apply {
            text = "SET UP FIREWALL"
            isAllCaps = false
        }

        layout.addView(
            startButton,
            LinearLayout.LayoutParams(-1, -2)
        )

        startButton.setOnClickListener {
            status.text =
                "Firewall service setup is coming next."
        }

        val note = makeText(
            "Local VPN • No root required",
            14f
        )

        val noteParams = LinearLayout.LayoutParams(
            -1, -2
        ).apply {
            topMargin = 28
        }
        layout.addView(note, noteParams)

        setContentView(layout)
    }
}
