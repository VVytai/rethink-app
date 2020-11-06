/*
Copyright 2020 RethinkDNS and its authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.celzero.bravedns.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.Html
import android.text.Html.FROM_HTML_MODE_LEGACY
import android.text.Spanned
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import com.celzero.bravedns.R
import com.celzero.bravedns.automaton.FirewallManager
import com.celzero.bravedns.automaton.FirewallRules
import com.celzero.bravedns.data.ConnectionRules
import com.celzero.bravedns.database.*
import com.celzero.bravedns.service.BraveVPNService
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.HomeScreenActivity.GlobalVariable.DEBUG
import com.celzero.bravedns.util.Constants.Companion.LOG_TAG
import com.celzero.bravedns.util.Protocol
import com.celzero.bravedns.util.Utilities
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ConnectionTrackerBottomSheetFragment
 * Displays the details about the network logs. Renders in NetworkMonitor UI as bottom sheet.
 * Fetches the details of the network logs, users can apply the rules based on the request or
 * based on the app.
 *
 * TODO : Need to move the strings to strings.xml file.
 */
class ConnTrackerBottomSheetFragment(private var contextVal: Context, private var ipDetails: ConnectionTracker) : BottomSheetDialogFragment() {

    private var fragmentView: View? = null

    private lateinit var txtRule1: TextView
    //private lateinit var txtRule2: TextView
    private lateinit var txtRule3: TextView

    private lateinit var txtAppName: TextView
    private lateinit var txtAppBlockDesc: TextView
    private lateinit var txtAppBlock: TextView
    private lateinit var txtConnDetails: TextView
    private lateinit var txtConnectionIP: TextView
    private lateinit var txtFlag: TextView
    private lateinit var imgAppIcon: ImageView
    private lateinit var rulesInfoImg : ImageView

    private lateinit var rule1HeaderLL : LinearLayout
    private lateinit var rule1HeaderTxt : TextView

    private lateinit var switchBlockApp: SwitchCompat
    //private lateinit var switchBlockConnApp: SwitchCompat
    private lateinit var switchBlockConnAll: SwitchCompat

    private lateinit var chipKillApp: Chip
    private lateinit var chipClearRules: Chip

    private lateinit var firewallRules: FirewallRules

    private var isAppBlocked: Boolean = false
    private var isRuleBlocked: Boolean = false
    private var isRuleUniversal: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firewallRules = FirewallRules.getInstance()
    }

    companion object {
        const val UNIVERSAL_RULES_UID = -1000
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme
    lateinit var mDb: AppDatabase
    lateinit var appInfoRepository: AppInfoRepository
    lateinit var blockedConnectionsRepository: BlockedConnectionsRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentView = inflater.inflate(R.layout.bottom_sheet_conn_track, container, false)
        mDb = AppDatabase.invoke(contextVal.applicationContext)
        appInfoRepository = mDb.appInfoRepository()
        blockedConnectionsRepository = mDb.blockedConnectionRepository()
        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView(view)
    }


    private fun initView(view: View) {
        txtAppName = view.findViewById(R.id.bs_conn_track_app_name)
        imgAppIcon = view.findViewById(R.id.bs_conn_track_app_icon)

        rule1HeaderLL = view.findViewById(R.id.bs_conn_blocked_rule1_header_ll)
        rule1HeaderTxt = view.findViewById(R.id.bs_conn_blocked_rule1_txt)

        txtRule1 = view.findViewById(R.id.bs_conn_block_app_txt)
        //txtRule2 = view.findViewById(R.id.bs_conn_block_conn_app_txt)
        txtRule3 = view.findViewById(R.id.bs_conn_block_conn_all_txt)

        txtAppBlock = view.findViewById(R.id.bs_conn_blocked_desc)
        txtAppBlockDesc = view.findViewById(R.id.bs_conn_blocked_desc_2)
        txtConnDetails = view.findViewById(R.id.bs_conn_connection_details)

        txtConnectionIP = view.findViewById(R.id.bs_conn_connection_type_heading)
        txtFlag = view.findViewById(R.id.bs_conn_connection_flag)

        chipKillApp = view.findViewById(R.id.bs_conn_track_app_kill)
        chipClearRules = view.findViewById(R.id.bs_conn_track_app_clear_rules)

        switchBlockApp = view.findViewById(R.id.bs_conn_block_app_check)
        switchBlockConnAll = view.findViewById(R.id.bs_conn_block_conn_all_switch)
        //switchBlockConnApp = view.findViewById(R.id.bs_conn_block_conn_app_switch)

        rulesInfoImg = view.findViewById(R.id.bs_conn_info_image)


        val protocol = Protocol.getProtocolName(ipDetails.protocol).name


        //var time = Utilities.convertLongToTime(ipDetails!!.timeStamp)

        txtConnectionIP.text = ipDetails.ipAddress!!
        txtFlag.text = ipDetails.flag.toString()

        var _text = getString(R.string.bsct_block)
        var _styledText: Spanned = Html.fromHtml(_text, FROM_HTML_MODE_LEGACY)
        txtRule1.text = _styledText

        _text = getString(R.string.bsct_block_app)
        _styledText = Html.fromHtml(_text, FROM_HTML_MODE_LEGACY)
        //txtRule2.text = _styledText

        _text = getString(R.string.bsct_block_all)
        _styledText = Html.fromHtml(_text, FROM_HTML_MODE_LEGACY)
        txtRule3.text = _styledText


        val time = DateUtils.getRelativeTimeSpanString(ipDetails.timeStamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)

        if (ipDetails.appName != "Unknown") {
            try {
                val appArray = contextVal.packageManager.getPackagesForUid(ipDetails.uid)
                val appCount = (appArray?.size)?.minus(1)
                txtAppName.text = ipDetails.appName
                if (appArray?.size!! > 2) {
                    txtAppName.text = "${ipDetails.appName} + $appCount other apps"
                    //chipKillApp.text = "Kill all ${appCount?.plus(1)} apps"
                    chipKillApp.visibility = View.GONE
                } else if (appArray.size == 2) {
                    txtAppName.text = "${ipDetails.appName} + $appCount other app"
                    //chipKillApp.text = "Kill ${appCount?.plus(1)} apps"
                    chipKillApp.visibility = View.GONE
                }

                imgAppIcon.setImageDrawable(contextVal.packageManager.getApplicationIcon(appArray?.get(0)!!))
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Package Not Found - " + e.message, e)
            }
        }else{
            txtAppName.text = "Unknown"
            chipKillApp.visibility = View.GONE
            rule1HeaderTxt.text = "Rule #5"
            txtRule1.text = contextVal.resources.getString(R.string.univ_block_unknown_connections)
        }

        val listBlocked = blockedConnectionsRepository.getAllBlockedConnectionsForUID(ipDetails.uid)
        listBlocked.forEach{
            /*if(it.ruleType == (BraveVPNService.BlockedRuleNames.RULE2.ruleName) && ipDetails.ipAddress.equals(it.ipAddress) && ipDetails.uid == it.uid){
                switchBlockConnAll.isChecked = true
            }else*/
            if(it.ruleType == (BraveVPNService.BlockedRuleNames.RULE2.ruleName) && ipDetails.ipAddress.equals(it.ipAddress) && it.uid == UNIVERSAL_RULES_UID){
                switchBlockConnAll.isChecked = true
            }
        }

        isAppBlocked = FirewallManager.checkInternetPermission(ipDetails.uid)
        val connRules = ConnectionRules(ipDetails.ipAddress!!, ipDetails.port, protocol)
        isRuleBlocked = firewallRules.checkRules(ipDetails.uid, connRules)
        isRuleUniversal = firewallRules.checkRules(UNIVERSAL_RULES_UID, connRules)

        switchBlockApp.setOnCheckedChangeListener(null)
        switchBlockApp.setOnClickListener {
            if(ipDetails.uid == 0){
                switchBlockApp.isChecked = false
                Utilities.showToastInMidLayout(contextVal, "Android cannot be firewalled", Toast.LENGTH_SHORT)
            } else if (ipDetails.appName != "Unknown") {
                firewallApp(FirewallManager.checkInternetPermission(ipDetails.uid))
            }else{
                if(DEBUG) Log.d(LOG_TAG,"setBlockUnknownConnections - ${switchBlockApp.isChecked} ")
                PersistentState.setBlockUnknownConnections(contextVal, switchBlockApp.isChecked)
            }
        }

        if(ipDetails.uid == 0){
            //switchBlockApp.isEnabled = false
            chipKillApp.visibility = View.GONE
        }

        if (ipDetails.isBlocked) {
            txtAppBlockDesc.visibility = View.VISIBLE
            //val text = getString(R.string.bsct_conn_block_desc, time)
           /* val styledText: Spanned = Html.fromHtml(text, FROM_HTML_MODE_LEGACY)
            txtAppBlock.text = styledText*/
            txtAppBlockDesc.text = ipDetails.blockedByRule
            _text = getString(R.string.bsct_conn_conn_desc_blocked, protocol, ipDetails.port.toString(), time)
            _styledText = Html.fromHtml(_text, FROM_HTML_MODE_LEGACY)
            txtConnDetails.text = _styledText
            rulesInfoImg.visibility = View.VISIBLE
            //txtConnDetails.visibility = View.GONE
        } else {
            _text = getString(R.string.bsct_conn_conn_desc_allowed,  protocol, ipDetails.port.toString(), time)
            txtAppBlockDesc.visibility = View.GONE
            if(ipDetails.blockedByRule.equals(BraveVPNService.BlockedRuleNames.RULE7.ruleName)){
                txtAppBlockDesc.visibility = View.VISIBLE
                txtAppBlockDesc.text = "Whitelisted"
            }
            _styledText = Html.fromHtml(_text, FROM_HTML_MODE_LEGACY)
            txtConnDetails.text = _styledText
            //val text = getString(R.string.bsct_conn_unblock_desc, time)
           /* txtAppBlock.text = text*/
            rulesInfoImg.visibility = View.GONE
        }

        if (ipDetails.appName != "Unknown") {
            switchBlockApp.isChecked = isAppBlocked
        }else{
            switchBlockApp.isChecked = PersistentState.getBlockUnknownConnections(contextVal)
        }

        rulesInfoImg.setOnClickListener{
            showDialogForInfo()
        }


        switchBlockConnAll.setOnCheckedChangeListener(null)
        switchBlockConnAll.setOnClickListener {
            if (isRuleUniversal) {
                if (DEBUG) Log.d(LOG_TAG, "Universal Remove - ${connRules.ipAddress}, ${BraveVPNService.BlockedRuleNames.RULE2.ruleName}")
                firewallRules.removeFirewallRules(UNIVERSAL_RULES_UID, connRules.ipAddress, BraveVPNService.BlockedRuleNames.RULE2.ruleName, contextVal)
                isRuleUniversal = false
                Toast.makeText(contextVal, "Unblocked ${connRules.ipAddress}", Toast.LENGTH_SHORT).show()
            } else {
                if (DEBUG) Log.d(LOG_TAG, "Universal Add - ${connRules.ipAddress}, ${BraveVPNService.BlockedRuleNames.RULE2.ruleName}")
                firewallRules.addFirewallRules(UNIVERSAL_RULES_UID, connRules.ipAddress, BraveVPNService.BlockedRuleNames.RULE2.ruleName, contextVal)
                isRuleUniversal = true
                Toast.makeText(contextVal, "Blocking all connections to ${connRules.ipAddress}", Toast.LENGTH_SHORT).show()
            }
            switchBlockConnAll.isChecked = isRuleUniversal
        }

        /*switchBlockConnApp.setOnCheckedChangeListener(null)
        switchBlockConnApp.setOnClickListener {
            var allApps = false
            val appUIDList = appInfoRepository.getAppListForUID(ipDetails.uid)
            if (appUIDList.size > 1) {
                var title = "Adding rules for \"${ipDetails.appName}\" will also include these ${appUIDList.size} other apps"
                var positiveText = "Apply rule"
                if (isRuleBlocked) {
                    title = "Removing rules for \"${ipDetails.appName}\" will also remove rules for these ${appUIDList.size} other apps"
                }
                allApps = showDialog(appUIDList, ipDetails.appName!!, title, positiveText)
                if (allApps) {
                    if (isRuleBlocked) {
                        firewallRules.removeFirewallRules(ipDetails.uid, connRules.ipAddress, BraveVPNService.BlockedRuleNames.RULE2.ruleName, contextVal)
                        isRuleBlocked = false
                    } else {
                        firewallRules.addFirewallRules(ipDetails.uid, connRules.ipAddress, BraveVPNService.BlockedRuleNames.RULE2.ruleName, contextVal)
                        isRuleBlocked = true
                    }
                }
                switchBlockConnApp.isChecked = isRuleBlocked
            } else {
                if (isRuleBlocked) {
                    firewallRules.removeFirewallRules(ipDetails.uid, connRules.ipAddress, BraveVPNService.BlockedRuleNames.RULE2.ruleName, contextVal)
                    isRuleBlocked = false
                } else {
                    firewallRules.addFirewallRules(ipDetails.uid, connRules.ipAddress, BraveVPNService.BlockedRuleNames.RULE2.ruleName, contextVal)
                    isRuleBlocked = true
                }
                switchBlockConnApp.isChecked = isRuleBlocked
            }
        }*/

        chipKillApp.setOnClickListener {
            try {
                val appUIDList = appInfoRepository.getAppListForUID(ipDetails.uid)
                if (appUIDList.size == 1) {
                    val activityManager: ActivityManager = contextVal.getSystemService(Activity.ACTIVITY_SERVICE) as ActivityManager
                    val mDb = AppDatabase.invoke(contextVal.applicationContext)
                    val appInfoRepository = mDb.appInfoRepository()
                    if (ipDetails.appName != null || ipDetails.appName!!.equals("Unknown")) {
                        val packageName = appInfoRepository.getPackageNameForAppName(ipDetails.appName!!)
                        activityManager.killBackgroundProcesses(packageName)
                        Toast.makeText(contextVal, "${ipDetails.appName} app killed.", Toast.LENGTH_SHORT).show()
                        if (DEBUG) Log.d(LOG_TAG, "App kill - $packageName")
                    } else {
                        Toast.makeText(contextVal, "Can't able to kill the app", Toast.LENGTH_SHORT).show()
                    }
                }/*else if(appUIDList.size == 2){
                    appUIDList.forEach{
                        val activityManager: ActivityManager = contextVal.getSystemService(Activity.ACTIVITY_SERVICE) as ActivityManager
                        val mDb = AppDatabase.invoke(contextVal.applicationContext)
                        val appInfoRepository = mDb.appInfoRepository()
                        if (it.appName != null || it.appName == "Unknown") {
                            activityManager.killBackgroundProcesses(it.packageInfo)
                            Toast.makeText(contextVal, "${it.appName} app killed.", Toast.LENGTH_SHORT).show()
                            if (DEBUG) Log.d(LOG_TAG, "App kill - $it.packageInfo")
                        } else {
                            Toast.makeText(contextVal, "Can't able to kill the app", Toast.LENGTH_SHORT).show()
                        }
                    }
                }*/

                else {
                    Toast.makeText(contextVal, "System app - kill denied", Toast.LENGTH_SHORT).show()
                }
                //mDb.close()
            } catch (e: java.lang.Exception) {
                Toast.makeText(contextVal, "Can't able to kill the app", Toast.LENGTH_SHORT).show()
            }
        }

        chipClearRules.setOnClickListener {
            clearAppRules()
        }
    }

    private fun firewallApp(isBlocked: Boolean) {
        var blockAllApps = false
        val appUIDList = appInfoRepository.getAppListForUID(ipDetails.uid)
        if(appUIDList[0].whiteListUniv1){
            Toast.makeText(contextVal, getString(R.string.bsct_firewall_not_available_whitelist), Toast.LENGTH_SHORT).show()
            switchBlockApp.isChecked = false
            return
        }else if(appUIDList[0].isExcluded){
            Toast.makeText(contextVal, getString(R.string.bsct_firewall_not_available_excluded), Toast.LENGTH_SHORT).show()
            switchBlockApp.isChecked = false
            return
        }
        if (appUIDList.size > 1) {
            var title = "Blocking \"${ipDetails.appName}\" will also block these ${appUIDList.size} other apps"
            var positiveText = "Block ${appUIDList.size} apps"
            if (isBlocked) {
                title = "Unblocking \"${ipDetails.appName}\" will also unblock these ${appUIDList.size} other apps"
                positiveText = "Unblock ${appUIDList.size} apps"
            }
            blockAllApps = showDialog(appUIDList, ipDetails.appName!!, title, positiveText)
        }
        if (appUIDList.size <= 1 || blockAllApps) {
            val uid = ipDetails.uid
            CoroutineScope(Dispatchers.IO).launch {
                appUIDList.forEach {
                    PersistentState.setExcludedPackagesWifi(it.packageInfo, isBlocked, contextVal)
                    FirewallManager.updateAppInternetPermission(it.packageInfo, isBlocked)
                    FirewallManager.updateAppInternetPermissionByUID(it.uid, isBlocked)
                    val categoryInfoRepository = mDb.categoryInfoRepository()
                    categoryInfoRepository.updateNumberOfBlocked(it.appCategory, !isBlocked)
                    if(DEBUG) Log.d(LOG_TAG,"Category block executed with blocked as $isBlocked")
                }
                appInfoRepository.updateInternetForuid(uid, isBlocked)
            }
        } else {
            switchBlockApp.isChecked = isBlocked
        }
    }

    private fun clearAppRules() {
        val blockAllApps: Boolean
        val appUIDList = appInfoRepository.getAppListForUID(ipDetails.uid)
        if (appUIDList.size > 1) {
            val title = "Clearing rules for \"${ipDetails.appName}\" will also clear rules for these ${appUIDList.size} other apps"
            val positiveText = "Clear rules"
            blockAllApps = showDialog(appUIDList, ipDetails.appName!!, title, positiveText)
            if (blockAllApps) {
                firewallRules.clearFirewallRules(ipDetails.uid, contextVal)
                Toast.makeText(contextVal, getString(R.string.bsct_rules_cleared_toast), Toast.LENGTH_SHORT).show()
            }
        } else {
            showAlertForClearRules()
        }
    }

    private fun showAlertForClearRules() {
        val builder = AlertDialog.Builder(contextVal)
        //set title for alert dialog
        builder.setTitle(R.string.bsct_alert_message_clear_rules_heading)
        //set message for alert dialog
        builder.setMessage(R.string.bsct_alert_message_clear_rules)
        builder.setIcon(android.R.drawable.ic_dialog_alert)
        builder.setCancelable(true)
        //performing positive action
        builder.setPositiveButton("Clear") { dialogInterface, which ->
            firewallRules.clearFirewallRules(ipDetails.uid, contextVal)
            //switchBlockConnApp.isChecked = false
            Toast.makeText(contextVal, getString(R.string.bsct_rules_cleared_toast), Toast.LENGTH_SHORT).show()
        }

        //performing negative action
        builder.setNeutralButton("Cancel") { dialogInterface, which ->
        }
        // Create the AlertDialog
        val alertDialog: AlertDialog = builder.create()
        // Set other dialog properties
        alertDialog.setCancelable(false)
        alertDialog.show()
    }

    private fun showDialogForInfo() {

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setContentView(R.layout.dialog_info_rules_layout)
        val okBtn = dialog.findViewById(R.id.info_rules_dialog_cancel_img) as ImageView
        val descText = dialog.findViewById(R.id.info_rules_dialog_rules_desc) as TextView

        var text = getString(R.string.bsct_conn_rule_explanation)
        text = text.replace("\n","<br /><br />")
        val styledText = Html.fromHtml(text)
        descText.text = styledText

        okBtn.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()

    }

    /**
     *TODO : Come up with better way to handle the dialog instead of using the handlers.
     */
    private fun showDialog(packageList: List<AppInfo>, appName: String, title: String, positiveText: String): Boolean {
        //Change the handler logic into some other
        val handler: Handler = @SuppressLint("HandlerLeak")
        object : Handler() {
            override fun handleMessage(mesg: Message?) {
                throw RuntimeException()
            }
        }
        var positiveTxt = ""
        val packageNameList: List<String> = packageList.map { it.appName }
        var proceedBlocking = false

        val builderSingle: AlertDialog.Builder = AlertDialog.Builder(contextVal)

        builderSingle.setIcon(R.drawable.spinner_firewall)
        builderSingle.setTitle(title)
        positiveTxt = positiveText

        val arrayAdapter = ArrayAdapter<String>(contextVal, android.R.layout.simple_list_item_activated_1)
        arrayAdapter.addAll(packageNameList)
        builderSingle.setCancelable(false)
        builderSingle.setItems(packageNameList.toTypedArray(), null)

        builderSingle.setPositiveButton(
            positiveTxt,
            DialogInterface.OnClickListener { _: DialogInterface, _: Int ->
                proceedBlocking = true
                handler.sendMessage(handler.obtainMessage())
            }).setNeutralButton(
            "Go Back",
            DialogInterface.OnClickListener { _: DialogInterface, _: Int ->
                handler.sendMessage(handler.obtainMessage());
                proceedBlocking = false
            })

        val alertDialog: AlertDialog = builderSingle.show()
        alertDialog.listView.setOnItemClickListener { _, _, _, _ -> }
        alertDialog.setCancelable(false)
        try {
            Looper.loop()
        } catch (e2: java.lang.RuntimeException) {
        }

        return proceedBlocking
    }

}