package com.firewall.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var appList: LinearLayout

    companion object {
        private const val VPN_REQUEST_CODE = 1001
        private const val PREFS = "firewall_rules"
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 18, 16, 10)
            setBackgroundColor(Color.rgb(18, 18, 18))
        }

        root.addView(makeText("🛡️ FIREWALL", 28f))

        status = makeText(
            "FIREWALL OFF\nRules are saved only, not enforced.",
            14f
        )

        root.addView(
            status,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = 12
                bottomMargin = 12
            }
        )

        val vpnButton = Button(this).apply {
            text = "REQUEST VPN PERMISSION"
            isAllCaps = false
        }

        root.addView(
            vpnButton,
            LinearLayout.LayoutParams(-1, -2)
        )

        vpnButton.setOnClickListener {
            val intent = VpnService.prepare(this)

            if (intent != null) {
                @Suppress("DEPRECATION")
                startActivityForResult(
                    intent, VPN_REQUEST_CODE
                )
            } else {
                status.text =
                    "Permission granted.\nFirewall is still OFF."
            }
        }

        root.addView(
            makeText("INSTALLED APPS", 20f),
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = 16
                bottomMargin = 6
            }
        )

        root.addView(
            makeText(
                "❌ = Allow | 🚫 = Mark to block",
                14f
            )
        )

        appList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this)
        scroll.addView(
            appList,
            ViewGroup.LayoutParams(-1, -2)
        )

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        setContentView(root)
        loadApps()
    }

    private fun loadApps() {
        appList.removeAllViews()

        val prefs = getSharedPreferences(
            PREFS, MODE_PRIVATE
        )

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        @Suppress("DEPRECATION")
        val apps = packageManager
            .queryIntentActivities(intent, 0)
            .distinctBy {
                it.activityInfo.packageName
            }
            .filter {
                it.activityInfo.packageName != packageName
            }
            .sortedBy {
                it.loadLabel(packageManager)
                    .toString().lowercase()
            }

        for (app in apps) {
            val appPackage =
                app.activityInfo.packageName

            val appName =
                app.loadLabel(packageManager).toString()

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4, 8, 4, 8)
            }

            val details = TextView(this).apply {
                text = "$appName\n$appPackage"
                textSize = 13f
                setTextColor(Color.WHITE)
            }

            row.addView(
                details,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            val blockButton = Button(this).apply {
                isAllCaps = false
                textSize = 18f
            }

            fun updateButton() {
                val marked = prefs.getBoolean(
                    appPackage, false
                )

                blockButton.text =
                    if (marked) "🚫" else "❌"

                blockButton.contentDescription =
                    if (marked) {
                        "Marked to block, not enforced"
                    } else {
                        "Allowed, not enforced"
                    }
            }

            updateButton()

            blockButton.setOnClickListener {
                val newValue = !prefs.getBoolean(
                    appPackage, false
                )

                prefs.edit()
                    .putBoolean(appPackage, newValue)
                    .apply()

                updateButton()
            }

            row.addView(
                blockButton,
                LinearLayout.LayoutParams(
                    64, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            appList.addView(
                row,
                LinearLayout.LayoutParams(-1, -2)
            )
        }

        if (apps.isEmpty()) {
            appList.addView(
                makeText("No launcher apps found.", 14f)
            )
        }
    }

    @Deprecated("Uses the classic activity result API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode, resultCode, data
        )

        if (requestCode == VPN_REQUEST_CODE) {
            status.text = if (resultCode == RESULT_OK) {
                "VPN permission granted.\nFirewall is still OFF."
            } else {
                "VPN permission declined.\nFirewall is OFF."
            }
        }
    }
}
