package com.firewall.app

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var vpnButton: Button
    private lateinit var prefs: SharedPreferences

    private val appPackage = "com.firewall.app"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("firewall_rules_v2", MODE_PRIVATE)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(16))
            setBackgroundColor(Color.rgb(20, 23, 28))
        }

        val title = TextView(this).apply {
            text = "FIREWALL"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(
            title,
            LinearLayout.LayoutParams(-1, dp(55))
        )

        statusText = TextView(this).apply {
            text = "FIREWALL OFF\nRules are saved only, not enforced."
            textSize = 15f
            setTextColor(Color.rgb(255, 190, 70))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(12))
        }
        root.addView(
            statusText,
            LinearLayout.LayoutParams(-1, -2)
        )

        vpnButton = Button(this).apply {
            text = "REQUEST VPN PERMISSION"
            setOnClickListener {
                requestVpnPermission()
            }
        }
        root.addView(
            vpnButton,
            LinearLayout.LayoutParams(-1, -2)
        )

        val legend = TextView(this).apply {
            text = "□ No rule    × Block    ✓ Allow"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(8))
        }
        root.addView(
            legend,
            LinearLayout.LayoutParams(-1, -2)
        )

        val heading = TextView(this).apply {
            text = "INSTALLED APPS"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(
            heading,
            LinearLayout.LayoutParams(-1, -2)
        )

        val scrollView = ScrollView(this)

        val appList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scrollView.addView(appList)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams( -1, 0, 1f)
        )

        setContentView(root)

        loadInstalledApps(appList)
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)

        if (intent != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, 100)
        } else {
            statusText.text =
                "VPN permission already granted.\nFiltering is not active."
        }
    }

    @Deprecated("Uses the compatible VPN permission result callback")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100) {
            if (resultCode == RESULT_OK) {
                statusText.text =
                    "VPN permission granted.\nFiltering is not active."
            } else {
                statusText.text =
                    "VPN permission declined.\nFiltering is not active."
            }
        }
    }

    private fun loadInstalledApps(appList: LinearLayout) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = packageManager
            .queryIntentActivities(launcherIntent, 0)
            .filter { it.activityInfo.packageName != appPackage }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy {
                it.loadLabel(packageManager).toString()
                    .lowercase()
            }

        appList.removeAllViews()

        if (apps.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No installed apps found."
                setTextColor(Color.LTGRAY)
                textSize = 16f
            }
            appList.addView(emptyText)
            return
        }

        for (info in apps) {
            val packageName = info.activityInfo.packageName
            val label = info.loadLabel(packageManager).toString()

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setBackground(
                    roundedBackground(
                        Color.rgb(34, 39, 47),
                        Color.rgb(60, 67, 77)
                    )
                )
            }

            val appIcon = info.loadIcon(packageManager)
            val iconView = android.widget.ImageView(this).apply {
                setImageDrawable(appIcon)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }

            row.addView(
                iconView,
                LinearLayout.LayoutParams(dp(42), dp(42))
            )

            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, dp(6), 0)
            }

            val nameView = TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(Color.WHITE)
                maxLines = 2
            }

            val packageView = TextView(this).apply {
                text = packageName
                textSize = 10f
                setTextColor(Color.LTGRAY)
                maxLines = 2
            }

            details.addView(nameView)
            details.addView(packageView)

            row.addView(
                details,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            val stateButton = TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 25f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, 0)
                isClickable = true
                isFocusable = true
            }

            fun updateButton() {
                when (prefs.getString(packageName, "none")) {
                    "block" -> {
                        stateButton.text = "×"
                        stateButton.setTextColor(
                            Color.rgb(255, 65, 65)
                        )
                        stateButton.background = roundedBackground(
                            Color.rgb(65, 27, 30),
                            Color.rgb(255, 65, 65)
                        )
                        stateButton.contentDescription =
                            "Marked to block; not enforced"
                    }

                    "allow" -> {
                        stateButton.text = "✓"
                        stateButton.setTextColor(
                            Color.rgb(50, 230, 130)
                        )
                        stateButton.background = roundedBackground(
                            Color.rgb(24, 59, 43),
                            Color.rgb(50, 230, 130)
                        )
                        stateButton.contentDescription =
                            "Marked to allow; not enforced"
                    }

                    else -> {
                        stateButton.text = ""
                        stateButton.setTextColor(Color.LTGRAY)
                        stateButton.background = roundedBackground(
                            Color.TRANSPARENT,
                            Color.rgb(155, 160, 170)
                        )
                        stateButton.contentDescription =
                            "No rule selected"
                    }
                }
            }

            stateButton.setOnClickListener {
                val current = prefs.getString(packageName, "none")

                val next = when (current) {
                    "none" -> "block"
                    "block" -> "allow"
                    else -> "none"
                }

                prefs.edit()
                    .putString(packageName, next)
                    .apply()

                updateButton()
            }

            row.addView(
                stateButton,
                LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                    leftMargin = dp(8)
                }
            )

            updateButton()

            val rowParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            }

            appList.addView(row, rowParams)
        }
    }

    private fun roundedBackground(
        fillColor: Int,
        strokeColor: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), strokeColor)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
