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
import android.support.v4.content.ContextCompat
import android.util.Log
import android.widget.*
import mozilla.components.service.fxa.Config
import mozilla.components.service.fxa.FirefoxAccount
import mozilla.components.service.fxa.FxaResult
import mozilla.components.service.fxa.OAuthInfo
import mozilla.components.service.fxa.Profile
import org.mozilla.sync15.logins.SyncResult
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*

open class MainActivity : AppCompatActivity(), LoginFragment.OnLoginCompleteListener {

    private var account: FirefoxAccount? = null
    private var scopes: Array<String> = arrayOf("profile https://identity.mozilla.com/apps/oldsync")
    private var wantsKeys: Boolean = true

    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var activityContext: MainActivity

    companion object {
        //const val CLIENT_ID = "12cc4070a481bc73"
        const val CLIENT_ID = "98adfa37698f255b"
        //const val REDIRECT_URL = "fxaclient://android.redirect"
        const val REDIRECT_URL = "https://lockbox.firefox.com/fxa/ios-redirect.html"
        //const val CONFIG_URL = "https://latest.dev.lcip.org"
        const val CONFIG_URL = "https://accounts.firefox.com"
        const val FXA_STATE_PREFS_KEY = "fxaAppState"
        const val FXA_STATE_KEY = "fxaState"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById<ListView>(R.id.logins_list_view)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        listView.adapter = adapter
        activityContext = this;

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
                    txtView.text = getString(R.string.signed_in, "${it.displayName
                            ?: ""} ${it.email}")
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
        val handleAuth = { oauthInfo: OAuthInfo ->
            val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)

            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                Log.d("LoginsSampleApp", "Got write Permission!")
            } else {
                Log.d("LoginsSampleApp", "No write Permission!")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
                }
            }

            val appFiles = this.applicationContext.getExternalFilesDir(null)
            val dbPath = appFiles.absolutePath + "/logins.mentatdb"
            Log.i("Setting DB Path", dbPath)
            val logins = MinimalLoginsExample(dbPath)
            val tokenServer = account!!.getTokenServerEndpointURL()!!
            Log.i("tokenServer", tokenServer)
            logins.syncAndGetPasswords(oauthInfo, tokenServer).thenCatch { err ->
                Log.i("Logins fail", "Start")
                SyncResult.fromException(err)
            }.whenComplete { logins ->
                runOnUiThread {
                    Toast.makeText(this, "Logins success", Toast.LENGTH_SHORT).show()
                    for (i in 0..logins.size - 1) {
                        adapter.addAll("L:" + logins[i].hostname);
                        adapter.notifyDataSetChanged();
                    }

                    Log.i("Logins", "End");
                }
            }

            FxaResult.fromValue(true)
        }

        account?.completeOAuthFlow(code, state)?.then(handleAuth)
    }
}
