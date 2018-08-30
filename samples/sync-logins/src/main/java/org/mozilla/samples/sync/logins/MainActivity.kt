/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.samples.sync.logins

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.v4.content.ContextCompat
import android.util.Log
import android.widget.CheckBox
import android.widget.TextView
import mozilla.components.service.fxa.Config
import mozilla.components.service.fxa.FirefoxAccount
import mozilla.components.service.fxa.FxaResult
import mozilla.components.service.fxa.OAuthInfo
import mozilla.components.service.fxa.Profile
import org.mozilla.sync15.logins.ServerPassword
import org.mozilla.sync15.logins.SyncResult
import java.io.PrintWriter
import java.io.StringWriter

open class MainActivity : AppCompatActivity(), LoginFragment.OnLoginCompleteListener {

    private var account: FirefoxAccount? = null
    private var scopes: Array<String> = arrayOf("profile https://identity.mozilla.com/apps/oldsync")
    private var wantsKeys: Boolean = true

    companion object {
        const val CLIENT_ID = "12cc4070a481bc73"
        const val REDIRECT_URL = "fxaclient://android.redirect"
        const val CONFIG_URL = "https://latest.dev.lcip.org"
        const val FXA_STATE_PREFS_KEY = "fxaAppState"
        const val FXA_STATE_KEY = "fxaState"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getSharedPreferences(FXA_STATE_PREFS_KEY, Context.MODE_PRIVATE).getString(FXA_STATE_KEY, "").let {
            FirefoxAccount.fromJSONString(it).then({
                this.account = it
                account?.getProfile()
            }, {
                Config.custom(CONFIG_URL).whenComplete { value: Config ->
                    account = FirefoxAccount(value, CLIENT_ID, REDIRECT_URL)
                }
                FxaResult()
            }).whenComplete {
                val txtView: TextView = findViewById(R.id.txtView)
                runOnUiThread {
                    txtView.text = getString(R.string.signed_in, "${it.displayName ?: ""} ${it.email}")
                }
            }
        }

        findViewById<View>(R.id.buttonWebView).setOnClickListener {
            account?.beginOAuthFlow(scopes, wantsKeys)?.whenComplete { openWebView(it) }
        }

        findViewById<View>(R.id.buttonLogout).setOnClickListener {
            getSharedPreferences(FXA_STATE_PREFS_KEY, Context.MODE_PRIVATE).edit().putString(FXA_STATE_KEY, "").apply()
            val txtView: TextView = findViewById(R.id.txtView)
            txtView.text = getString(R.string.logged_out)
        }

        findViewById<CheckBox>(R.id.checkboxKeys).setOnCheckedChangeListener { _, isChecked ->
            wantsKeys = isChecked
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        account?.close()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val action = intent.action
        val data = intent.dataString

        if (Intent.ACTION_VIEW == action && data != null) {
            val url = Uri.parse(data)
            val code = url.getQueryParameter("code")
            val state = url.getQueryParameter("state")
            displayAndPersistProfile(code, state)
        }
    }

    private fun openWebView(url: String) {
        supportFragmentManager?.beginTransaction()?.apply {
            replace(R.id.container, LoginFragment.create(url, REDIRECT_URL))
            addToBackStack(null)
            commit()
        }
    }

    override fun onLoginComplete(code: String, state: String, fragment: LoginFragment) {
        displayAndPersistProfile(code, state)
        supportFragmentManager?.popBackStack()
    }


    fun dumpError(tag: String, e: Exception) {
        val sw = StringWriter();
        val pw = PrintWriter(sw);
        e.printStackTrace(pw);
        val stack = sw.toString();
        Log.e(tag, e.message);
        Log.e(tag, stack);
        // XXX - need to do something better on error.
        // this.editText.setText("rust error (${tag}): : ${e.message}\n\n${stack}\n");
    }

    private fun displayAndPersistProfile(code: String, state: String) {
        val txtView: TextView = findViewById(R.id.txtView)
        val handleAuth = { oauthInfo: OAuthInfo ->
//

        val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            Log.d("LoginsSampleApp", "Got write Permission!");
        } else {
            Log.d("LoginsSampleApp", "No write Permission!");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            };
        }
        val appFiles = this.applicationContext.getExternalFilesDir(null)
        val dbPath = appFiles.absolutePath + "/logins.mentatdb"
        Log.i("Setting DB Path", dbPath)
        val logins = MinimalLoginsExample(dbPath)
        val tokenServer = account!!.getTokenServerEndpointURL()!!
        Log.i("tokenServer", tokenServer)
        logins.syncAndGetPasswords(oauthInfo, tokenServer).whenComplete { logins ->
            Log.i("Logins", "Start")
        }

        account?.getProfile()

        }
        val handleProfile = { value: Profile ->
            runOnUiThread {
                txtView.text = getString(R.string.signed_in, "${value.displayName ?: ""} ${value.email}")
            }
            account?.toJSONString().let {
                getSharedPreferences(FXA_STATE_PREFS_KEY, Context.MODE_PRIVATE).edit().putString(FXA_STATE_KEY, it).apply()
            }
        }

        account?.completeOAuthFlow(code, state)?.then(handleAuth)?.whenComplete(handleProfile)
    }
}
