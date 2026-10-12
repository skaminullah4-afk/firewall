package com.firewall.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    companion object {
        private const val VPN_REQUEST_CODE = 1001
    }

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

        layout.addView(makeText("🛡️ FIREWALL", 30f))

        status = makeText(
            "Firewall is OFF\nYour internet is unchanged.",
            16f
        )

        layout.addView(
            status,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = 20
                bottomMargin = 32
            }
        )

        val setupButton = Button(this).apply {
            text = "REQUEST VPN PERMISSION"
            isAllCaps = false
        }

        layout.addView(
            setupButton,
            LinearLayout.LayoutParams(-1, -2)
        )

        setupButton.setOnClickListener {
            val intent = VpnService.prepare(this)

            if (intent != null) {
                @Suppress("DEPRECATION")
                startActivityForResult(
                    intent,
                    VPN_REQUEST_CODE
                )
            } else {
                status.text =
                    "VPN permission already granted.\n" +
                    "Firewall is still OFF."
            }
        }

        layout.addView(
            makeText("Local VPN • No root required", 14f),
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = 28
            }
        )

        setContentView(layout)
    }

    @Deprecated("Uses the classic activity result API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == VPN_REQUEST_CODE) {
            status.text = if (resultCode == RESULT_OK) {
                "VPN permission granted.\nFirewall is still OFF."
            } else {
                "VPN permission declined.\nFirewall is OFF."
            }
        }
    }
}
