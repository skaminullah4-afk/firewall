package com.firewall.app

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ImageView

class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var vpnButton: Button
    private lateinit var appList: LinearLayout
    private lateinit var prefs: SharedPreferences

    private val appPackage = "com.firewall.app"
    private val vpnRequestCode = 100

    private var startRequested = false
    
    private val statusHandler = Handler(android.os.Looper.getMainLooper())

    private val statusRefresh = object : Runnable {
        override fun run() {
            if (::statusText.isInitialized) {
                updateStatus()
            }
            statusHandler.postDelayed(this, 1000)
        }
    }
 
    override fun onStart() {
        super.onStart()
        statusHandler.removeCallbacks(statusRefresh)
        statusHandler.post(statusRefresh)
    }

    override fun onStop() {
        statusHandler.removeCallbacks(statusRefresh)
        super.onStop()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)      
if (intent?.action == "com.firewall.app.START_FROM_NOTIFICATION") {
    intent.action = null
    window.decorView.post {
        if (!FirewallVpnService.isRunning && !startRequested) {
            startFirewallFromNotification()
        }
    }
}

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
        root.addView(title, LinearLayout.LayoutParams(-1, dp(55)))

        statusText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(255, 190, 70))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(12))
        }
        root.addView(statusText, LinearLayout.LayoutParams(-1, -2))

        vpnButton = Button(this).apply {
            text = "START FIREWALL"
            setOnClickListener { toggleFirewall() }
        }
        root.addView(vpnButton, LinearLayout.LayoutParams(-1, -2))

        val legend = TextView(this).apply {
            text = "□ No rule    × Block    ✓ Allow"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(8))
        }
        root.addView(legend, LinearLayout.LayoutParams(-1, -2))

        val heading = TextView(this).apply {
            text = "INSTALLED APPS"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(heading, LinearLayout.LayoutParams(-1, -2))

        val scrollView = ScrollView(this)
        appList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(appList)
        root.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        loadInstalledApps()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) {
            updateStatus()
        }
    }

    private fun toggleFirewall() {
        if (FirewallVpnService.isRunning || startRequested) {
            stopFirewall()
            return
        }

        val hasBlockedApps = prefs.all.any {
            it.value == "block" && it.key != appPackage
        }

        if (!hasBlockedApps) {
            statusText.text = "Choose an app with a red × first."
            return
        }

        val permissionIntent = VpnService.prepare(this)

        if (permissionIntent != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(permissionIntent, vpnRequestCode)
        } else {
            startFirewallService()
        }
    }

    @Deprecated("Uses compatible VPN permission callback")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == vpnRequestCode) {
            if (resultCode == RESULT_OK) {
                startFirewallService()
            } else {
                startRequested = false
                statusText.text = "VPN permission declined. Firewall is off."
                updateStatus()
            }
        }
    }

    private fun startFirewallService() {
        try {
            val intent = Intent(this, FirewallVpnService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            startRequested = true
            updateStatus()
        } catch (_: Exception) {
            startRequested = false
            statusText.text = "Could not start VPN. Firewall is off."
            updateStatus()
        }
    }

    private fun stopFirewall() {
        val intent = Intent(this, FirewallVpnService::class.java).apply {
            action = FirewallVpnService.ACTION_STOP
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (_: Exception) {
            stopService(Intent(this, FirewallVpnService::class.java))
        }

        startRequested = false
        updateStatus()
    }

    private fun updateStatus() {
        if (!::statusText.isInitialized) return

        val running = FirewallVpnService.isRunning
        val permissionGranted = VpnService.prepare(this) == null

        statusText.text = when {
            running ->
                "VPN SERVICE RUNNING\nWarning: routed app traffic is currently discarded."
            startRequested ->
                "START REQUESTED\nWaiting for the service status."
            permissionGranted ->
                "FIREWALL OFF\nVPN permission granted; filtering is not running."
            else ->
                "FIREWALL OFF\nRules are saved only, not enforced."
        }

        vpnButton.text = if (running || startRequested) {
            "STOP FIREWALL"
        } else {
            "START FIREWALL"
        }

        val canEditRules = !running && !startRequested

        for (i in 0 until appList.childCount) {
            val row = appList.getChildAt(i) as? LinearLayout ?: continue
            if (row.childCount > 0) {
                val control = row.getChildAt(row.childCount - 1)
                control.isEnabled = canEditRules
                control.alpha = if (canEditRules) 1f else 0.5f
            }
        }
    }

    private fun loadInstalledApps() {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = packageManager
            .queryIntentActivities(launcherIntent, 0)
            .filter { it.activityInfo.packageName != appPackage }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy {
                it.loadLabel(packageManager).toString().lowercase()
            }

        appList.removeAllViews()

        for (info in apps) {
            val packageName = info.activityInfo.packageName
            val label = info.loadLabel(packageManager).toString()

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = roundedBackground(
                    Color.rgb(34, 39, 47),
                    Color.rgb(60, 67, 77)
                )
            }

            val iconView = ImageView(this).apply {
                setImageDrawable(info.loadIcon(packageManager))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            row.addView(iconView, LinearLayout.LayoutParams(dp(42), dp(42)))

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
            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))

            val stateButton = TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 25f
                typeface = Typeface.DEFAULT_BOLD
                isClickable = true
                isFocusable = true
            }

            fun updateButton() {
                when (prefs.getString(packageName, "none")) {
                    "block" -> {
                        stateButton.text = "×"
                        stateButton.setTextColor(Color.rgb(255, 65, 65))
                        stateButton.background = roundedBackground(
                            Color.rgb(65, 27, 30),
                            Color.rgb(255, 65, 65)
                        )
                        stateButton.contentDescription = "Marked to block"
                    }
                    "allow" -> {
                        stateButton.text = "✓"
                        stateButton.setTextColor(Color.rgb(50, 230, 130))
                        stateButton.background = roundedBackground(
                            Color.rgb(24, 59, 43),
                            Color.rgb(50, 230, 130)
                        )
                        stateButton.contentDescription = "Marked to allow"
                    }
                    else -> {
                        stateButton.text = ""
                        stateButton.setTextColor(Color.LTGRAY)
                        stateButton.background = roundedBackground(
                            Color.TRANSPARENT,
                            Color.rgb(155, 160, 170)
                        )
                        stateButton.contentDescription = "No rule selected"
                    }
                }
            }

            stateButton.setOnClickListener {
                if (FirewallVpnService.isRunning || startRequested) {
                    statusText.text = "Stop the firewall before changing rules."
                    return@setOnClickListener
                }

                val current = prefs.getString(packageName, "none")
                val next = when (current) {
                    "none" -> "block"
                    "block" -> "allow"
                    else -> "none"
                }

                prefs.edit().putString(packageName, next).apply()
                updateButton()
            }

            row.addView(
                stateButton,
                LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                    leftMargin = dp(8)
                }
            )

            updateButton()

            appList.addView(
                row,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(8)
                }
            )
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
