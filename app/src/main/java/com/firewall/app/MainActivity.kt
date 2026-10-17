package com.firewall.app

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.RadioButton
import android.content.Intent
import android.app.AlarmManager
import android.net.Uri
import android.provider.Settings
import android.content.SharedPreferences
import android.graphics.BitmapFactory
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
    private lateinit var nightButton: Button
    private lateinit var nightStatusText: TextView
    private lateinit var appList: LinearLayout
    private lateinit var prefs: SharedPreferences
    private lateinit var customFilterButton: Button
    private lateinit var customRulesList: LinearLayout
    private lateinit var customFilterStatus: TextView
    private lateinit var newCustomFilterButton: Button

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
    background = android.graphics.drawable.BitmapDrawable(
        resources,
        BitmapFactory.decodeResource(
            resources,
            R.drawable.firewall_background
        )
    )
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

        newCustomFilterButton = Button(this).apply {
    text = "NEW CUSTOM FILTER"
    setTextColor(Color.WHITE)
    setBackgroundColor(Color.TRANSPARENT)
    stateListAnimator = null
    setOnClickListener {
        showNewCustomFilterDialog()
    }
}
root.addView(
    newCustomFilterButton,
    LinearLayout.LayoutParams(-1, -2)
)

        customFilterStatus = TextView(this).apply {
    text = "CUSTOM FILTERS: OFF"
    textSize = 14f
    setTextColor(Color.rgb(255, 190, 70))
    gravity = Gravity.CENTER
    setPadding(dp(8), dp(8), dp(8), dp(4))
}
root.addView(
    customFilterStatus,
    LinearLayout.LayoutParams(-1, -2)
)

customFilterButton = Button(this).apply {
    text = "CUSTOM FILTERS OFF"
    setTextColor(Color.WHITE)
    setBackgroundColor(Color.TRANSPARENT)
    stateListAnimator = null
    setOnClickListener {
        toggleCustomFilters()
    }
}
root.addView(
    customFilterButton,
    LinearLayout.LayoutParams(-1, -2)
)

        nightStatusText = TextView(this).apply {
    textSize = 14f
    setTextColor(Color.rgb(255, 190, 70))
    gravity = Gravity.CENTER
    setPadding(dp(8), dp(12), dp(8), dp(4))
}
root.addView(nightStatusText, LinearLayout.LayoutParams(-1, -2))

nightButton = Button(this).apply {
    setTextColor(Color.WHITE)
    setBackgroundColor(Color.TRANSPARENT)
    stateListAnimator = null
    setOnClickListener {
        toggleNightSchedule()
    }
}
root.addView(nightButton, LinearLayout.LayoutParams(-1, -2))

        vpnButton = Button(this).apply {
    text = "START FIREWALL"
    setTextColor(Color.WHITE)
    setBackgroundColor(Color.TRANSPARENT)
    stateListAnimator = null
    setOnClickListener { toggleFirewall() }
        }
        root.addView(vpnButton, LinearLayout.LayoutParams(-1, -2))

        val legend = TextView(this).apply {
            text = "× Block    ✓ Allow"
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
updateNightStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) {
            updateStatus()
        }
    }

    private fun startFirewallFromNotification() {
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

    private fun toggleCustomFilters() {
    val filterPrefs = getSharedPreferences(
        "custom_filter_settings",
        MODE_PRIVATE
    )

    val enabled = filterPrefs.getBoolean(
        "enabled",
        false
    )

    filterPrefs.edit()
        .putBoolean("enabled", !enabled)
        .apply()

    updateCustomFilterStatus()
}

private fun updateCustomFilterStatus() {
    if (!::customFilterButton.isInitialized ||
        !::customFilterStatus.isInitialized
    ) return

    val enabled = getSharedPreferences(
        "custom_filter_settings",
        MODE_PRIVATE
    ).getBoolean("enabled", false)

    customFilterButton.text = if (enabled) {
        "CUSTOM FILTERS ON"
    } else {
        "CUSTOM FILTERS OFF"
    }

    customFilterStatus.text = if (enabled) {
        "CUSTOM FILTERS: ON"
    } else {
        "CUSTOM FILTERS: OFF"
    }
}


private fun showNewCustomFilterDialog() {
    val form = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), dp(4))
    }

    val wifi = CheckBox(this).apply {
        text = "Wi-Fi"
    }
    val mobile = CheckBox(this).apply {
        text = "Mobile data"
    }

    val address = EditText(this).apply {
        hint = "Address"
        setText("*")
        singleLine = true
    }

    val port = EditText(this).apply {
        hint = "Port number (1–65535)"
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        singleLine = true
    }

    val actionGroup = RadioGroup(this).apply {
        orientation = RadioGroup.HORIZONTAL
    }

    val allow = RadioButton(this).apply {
        text = "Allow"
        id = android.view.View.generateViewId()
    }

    val block = RadioButton(this).apply {
        text = "Block"
        id = android.view.View.generateViewId()
    }

    actionGroup.addView(allow)
    actionGroup.addView(block)

    form.addView(wifi)
    form.addView(mobile)
    form.addView(address)
    form.addView(port)
    form.addView(actionGroup)

    val dialog = AlertDialog.Builder(this)
        .setTitle("New Custom Filter")
        .setView(form)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("OK", null)
        .create()

    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener {
                val portText = port.text.toString().trim()
                val portNumber = portText.toIntOrNull()
                val addressText = address.text.toString().trim()

                if (!wifi.isChecked && !mobile.isChecked) {
                    android.widget.Toast.makeText(
                        this,
                        "Select Wi-Fi or Mobile data.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                if (addressText.isBlank()) {
                    address.error = "Enter an address or *"
                    return@setOnClickListener
                }

                if (portNumber == null ||
                    portNumber !in 1..65535
                ) {
                    port.error = "Enter a port from 1 to 65535"
                    return@setOnClickListener
                }

                if (actionGroup.checkedRadioButtonId == -1) {
                    android.widget.Toast.makeText(
                        this,
                        "Choose Allow or Block.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val rule = org.json.JSONObject().apply {
                    put("wifi", wifi.isChecked)
                    put("mobile", mobile.isChecked)
                    put("address", addressText)
                    put("port", portNumber)
                    put(
                        "action",
                        if (actionGroup.checkedRadioButtonId == allow.id)
                            "allow" else "block"
                    )
                }

                val filterPrefs = getSharedPreferences(
                    "custom_filter_settings",
                    MODE_PRIVATE
                )

                val rules = org.json.JSONArray(
                    filterPrefs.getString("rules", "[]")
                )
                rules.put(rule)

                filterPrefs.edit()
                    .putString("rules", rules.toString())
                    .apply()

                android.widget.Toast.makeText(
                    this,
                    "Rule saved. Enforcement is not implemented yet.",
                    android.widget.Toast.LENGTH_LONG
                ).show()

                dialog.dismiss()
            }
    }

    dialog.show()
}
    
private fun toggleNightSchedule() {
    if (NightSchedule.isEnabled(this)) {
        NightSchedule.setEnabled(this, false)
        updateNightStatus()
        statusText.text = "Night schedule disabled."
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val alarmManager = getSystemService(AlarmManager::class.java)

    if (!alarmManager.canScheduleExactAlarms()) {
        val alarmIntent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:$packageName")
        )
        statusText.text =
            "Allow Alarms & reminders, then return and enable the night schedule."
        startActivity(alarmIntent)
        return
    }
    }

    val permissionIntent = VpnService.prepare(this)

    if (permissionIntent != null) {
        statusText.text = "Allow the VPN permission to enable night blocking."
        @Suppress("DEPRECATION")
        startActivityForResult(permissionIntent, vpnRequestCode + 1)
    } else {
        NightSchedule.setEnabled(this, true)
        updateNightStatus()
        statusText.text = "Night schedule enabled: 10:30 PM–5:00 AM."
    }
}

private fun updateNightStatus() {
    if (!::nightButton.isInitialized ||
        !::nightStatusText.isInitialized
    ) return

    val enabled = NightSchedule.isEnabled(this)
    val active = NightSchedule.isNightActive(this)

    nightButton.text = if (enabled) {
        "TURN NIGHT SCHEDULE OFF"
    } else {
        "TURN NIGHT SCHEDULE ON"
    }

    nightStatusText.text = when {
        !enabled -> "NIGHT BLOCK: DISABLED"
        active -> "NIGHT BLOCK: ACTIVE (10:30 PM–5:00 AM)"
        else -> "NIGHT SCHEDULE: ON — BLOCKING STARTS AT 10:30 PM"
    }
}

    @Deprecated("Uses compatible VPN permission callback")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == vpnRequestCode + 1) {
    if (resultCode == RESULT_OK) {
        NightSchedule.setEnabled(this, true)
        updateNightStatus()
        statusText.text =
            "Night schedule enabled: 10:30 PM–5:00 AM."
    } else {
        statusText.text =
            "VPN permission declined. Night schedule is off."
        updateNightStatus()
    }
    return
        }
        
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
    setBackgroundColor(Color.TRANSPARENT)
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
                        stateButton.setBackgroundColor(Color.TRANSPARENT)
                        stateButton.contentDescription = "Marked to block"
                    }
                    "allow" -> {
                        stateButton.text = "✓"
                        stateButton.setTextColor(Color.rgb(50, 230, 130))
                        stateButton.setBackgroundColor(Color.TRANSPARENT)
                        stateButton.contentDescription = "Marked to allow"
                    }
                    else -> {
                        stateButton.text = ""
                        stateButton.setTextColor(Color.LTGRAY)
                        stateButton.setBackgroundColor(Color.TRANSPARENT)
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
