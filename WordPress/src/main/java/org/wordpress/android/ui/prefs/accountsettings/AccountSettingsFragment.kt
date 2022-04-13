package org.wordpress.android.ui.prefs.accountsettings

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.os.AsyncTask
import android.os.Bundle
import android.preference.Preference
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.PreferenceFragment
import android.text.InputType
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.MAIN
import org.wordpress.android.R
import org.wordpress.android.R.layout
import org.wordpress.android.R.string
import org.wordpress.android.R.xml
import org.wordpress.android.WordPress
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.AccountActionBuilder
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.AccountStore
import org.wordpress.android.fluxc.store.AccountStore.AccountErrorType.SETTINGS_FETCH_GENERIC_ERROR
import org.wordpress.android.fluxc.store.AccountStore.AccountErrorType.SETTINGS_FETCH_REAUTHORIZATION_REQUIRED_ERROR
import org.wordpress.android.fluxc.store.AccountStore.AccountErrorType.SETTINGS_POST_ERROR
import org.wordpress.android.fluxc.store.AccountStore.OnAccountChanged
import org.wordpress.android.fluxc.store.AccountStore.PushAccountSettingsPayload
import org.wordpress.android.fluxc.store.SiteStore
import org.wordpress.android.ui.FullScreenDialogFragment
import org.wordpress.android.ui.FullScreenDialogFragment.Builder
import org.wordpress.android.ui.FullScreenDialogFragment.OnConfirmListener
import org.wordpress.android.ui.accounts.signup.BaseUsernameChangerFullScreenDialogFragment
import org.wordpress.android.ui.accounts.signup.SettingsUsernameChangerFragment
import org.wordpress.android.ui.prefs.DetailListPreference
import org.wordpress.android.ui.prefs.EditTextPreferenceWithValidation
import org.wordpress.android.ui.prefs.EditTextPreferenceWithValidation.ValidationType.EMAIL
import org.wordpress.android.ui.prefs.EditTextPreferenceWithValidation.ValidationType.PASSWORD
import org.wordpress.android.ui.prefs.EditTextPreferenceWithValidation.ValidationType.URL
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T.SETTINGS
import org.wordpress.android.util.NetworkUtils
import org.wordpress.android.util.SiteUtils
import org.wordpress.android.util.ToastUtils
import org.wordpress.android.util.ToastUtils.Duration.LONG
import org.wordpress.android.widgets.WPSnackbar.Companion.make
import javax.inject.Inject

class AccountSettingsFragment : PreferenceFragment(),
        OnPreferenceChangeListener,
        OnConfirmListener {
    private var mUsernamePreference: Preference? = null
    private var mEmailPreference: EditTextPreferenceWithValidation? = null
    private var mPrimarySitePreference: DetailListPreference? = null
    private var mWebAddressPreference: EditTextPreferenceWithValidation? = null
    private var mChangePasswordPreference: EditTextPreferenceWithValidation? = null
    private var mChangePasswordProgressDialog: ProgressDialog? = null
    private var mEmailSnackbar: Snackbar? = null

    @Inject
    var mDispatcher: Dispatcher? = null

    @Inject
    var mAccountStore: AccountStore? = null

    @Inject
    var mSiteStore: SiteStore? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity.application as WordPress).component().inject(this)
        retainInstance = true
        addPreferencesFromResource(xml.account_settings)
        mUsernamePreference = findPreference(getString(string.pref_key_username))
        mEmailPreference = findPreference(getString(string.pref_key_email)) as EditTextPreferenceWithValidation
        mPrimarySitePreference = findPreference(getString(string.pref_key_primary_site)) as DetailListPreference
        mWebAddressPreference = findPreference(getString(string.pref_key_web_address)) as EditTextPreferenceWithValidation
        mChangePasswordPreference = findPreference(getString(string.pref_key_change_password)) as EditTextPreferenceWithValidation
        mEmailPreference!!.editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        mEmailPreference!!.setValidationType(EMAIL)
        mWebAddressPreference!!.editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        mWebAddressPreference!!.setValidationType(URL)
        mWebAddressPreference!!.setDialogMessage(string.web_address_dialog_hint)
        mChangePasswordPreference!!.editText.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
        mChangePasswordPreference!!.setValidationType(PASSWORD)
        mChangePasswordPreference!!.setDialogMessage(string.change_password_dialog_hint)
        mEmailPreference!!.onPreferenceChangeListener = this
        mPrimarySitePreference!!.onPreferenceChangeListener = this
        mWebAddressPreference!!.onPreferenceChangeListener = this
        mChangePasswordPreference!!.onPreferenceChangeListener = this
        setTextAlignment(mEmailPreference!!.editText)
        setTextAlignment(mWebAddressPreference!!.editText)
        setTextAlignment(mChangePasswordPreference!!.editText)

        // load site list asynchronously
        LoadSitesTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle
    ): View? {
        val coordinatorView = inflater.inflate(layout.preference_coordinator, container, false)
        val coordinator: CoordinatorLayout = coordinatorView.findViewById(R.id.coordinator)
        val preferenceView = super.onCreateView(inflater, coordinator, savedInstanceState)
        val listOfPreferences = preferenceView!!.findViewById<ListView>(android.R.id.list)
        if (listOfPreferences != null) {
            ViewCompat.setNestedScrollingEnabled(listOfPreferences, true)
        }
        coordinator.addView(preferenceView)
        return coordinatorView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        refreshAccountDetails()
    }

    override fun onResume() {
        super.onResume()
        if (NetworkUtils.isNetworkAvailable(activity)) {
            mDispatcher!!.dispatch(AccountActionBuilder.newFetchSettingsAction())
        }
    }

    override fun onStart() {
        super.onStart()
        mDispatcher!!.register(this)
    }

    override fun onStop() {
        mDispatcher!!.unregister(this)
        super.onStop()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        if (newValue == null) {
            return false
        }
        if (preference === mEmailPreference) {
            updateEmail(newValue.toString())
            showPendingEmailChangeSnackbar(newValue.toString())
            mEmailPreference!!.isEnabled = false
            return false
        } else if (preference === mPrimarySitePreference) {
            changePrimaryBlogPreference(newValue.toString().toLong())
            updatePrimaryBlog(newValue.toString())
            return false
        } else if (preference === mWebAddressPreference) {
            mWebAddressPreference!!.summary = newValue.toString()
            updateWebAddress(newValue.toString())
            return false
        } else if (preference === mChangePasswordPreference) {
            showChangePasswordProgressDialog(true)
            updatePassword(newValue.toString())
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> activity.finish()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setTextAlignment(editText: EditText) {
        editText.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    }

    private fun showChangePasswordProgressDialog(show: Boolean) {
        if (show && mChangePasswordProgressDialog == null) {
            mChangePasswordProgressDialog = ProgressDialog(activity)
            mChangePasswordProgressDialog!!.setCancelable(false)
            mChangePasswordProgressDialog!!.isIndeterminate = true
            mChangePasswordProgressDialog!!.setMessage(getString(string.change_password_dialog_message))
            mChangePasswordProgressDialog!!.show()
        } else if (!show && mChangePasswordProgressDialog != null) {
            mChangePasswordProgressDialog!!.dismiss()
            mChangePasswordProgressDialog = null
        }
    }

    private fun refreshAccountDetails() {
        val account = mAccountStore!!.account
        mUsernamePreference!!.summary = account.userName
        mEmailPreference!!.summary = account.email
        mWebAddressPreference!!.summary = account.webAddress
        changePrimaryBlogPreference(account.primarySiteId)
        checkIfEmailChangeIsPending()
        checkIfUsernameCanBeChanged()
    }

    private fun checkIfEmailChangeIsPending() {
        val account = mAccountStore!!.account
        if (account.pendingEmailChange) {
            showPendingEmailChangeSnackbar(account.newEmail)
        } else if (mEmailSnackbar != null && mEmailSnackbar!!.isShown) {
            mEmailSnackbar!!.dismiss()
        }
        mEmailPreference!!.isEnabled = !account.pendingEmailChange
    }

    // BaseTransientBottomBar.LENGTH_LONG is pointing to Snackabr.LENGTH_LONG which confuses checkstyle
    @SuppressLint("WrongConstant") private fun showPendingEmailChangeSnackbar(newEmail: String) {
        if (view != null) {
            if (mEmailSnackbar == null || !mEmailSnackbar!!.isShown) {
                val clickListener = View.OnClickListener { cancelPendingEmailChange() }
                mEmailSnackbar = Snackbar
                        .make(view!!, "", BaseTransientBottomBar.LENGTH_INDEFINITE)
                        .setAction(getString(string.button_discard), clickListener)
                val textView = mEmailSnackbar!!.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                textView.maxLines = 4
            }
            // instead of creating a new snackbar, update the current one to avoid the jumping animation
            mEmailSnackbar!!.setText(getString(string.pending_email_change_snackbar, newEmail))
            if (!mEmailSnackbar!!.isShown) {
                mEmailSnackbar!!.show()
            }
        }
    }

    private fun changePrimaryBlogPreference(siteRemoteId: Long) {
        mPrimarySitePreference!!.value = siteRemoteId.toString()
        val site = mSiteStore!!.getSiteBySiteId(siteRemoteId)
        if (site != null) {
            mPrimarySitePreference!!.summary = SiteUtils.getSiteNameOrHomeURL(site)
            mPrimarySitePreference!!.refreshAdapter()
        }
    }

    private fun cancelPendingEmailChange() {
        val payload = PushAccountSettingsPayload()
        payload.params = HashMap()
        payload.params["user_email_change_pending"] = "false"
        mDispatcher!!.dispatch(AccountActionBuilder.newPushSettingsAction(payload))
        if (mEmailSnackbar != null && mEmailSnackbar!!.isShown) {
            mEmailSnackbar!!.dismiss()
        }
    }

    private fun updateEmail(newEmail: String) {
        val payload = PushAccountSettingsPayload()
        payload.params = HashMap()
        payload.params["user_email"] = newEmail
        mDispatcher!!.dispatch(AccountActionBuilder.newPushSettingsAction(payload))
    }

    private fun updatePrimaryBlog(blogId: String) {
        val payload = PushAccountSettingsPayload()
        payload.params = HashMap()
        payload.params["primary_site_ID"] = blogId
        mDispatcher!!.dispatch(AccountActionBuilder.newPushSettingsAction(payload))
    }

    fun updateWebAddress(newWebAddress: String?) {
        val payload = PushAccountSettingsPayload()
        payload.params = HashMap()
        payload.params["user_URL"] = newWebAddress
        mDispatcher!!.dispatch(AccountActionBuilder.newPushSettingsAction(payload))
    }

    fun updatePassword(newPassword: String?) {
        val payload = PushAccountSettingsPayload()
        payload.params = HashMap()
        payload.params["password"] = newPassword
        mDispatcher!!.dispatch(AccountActionBuilder.newPushSettingsAction(payload))
    }

    @Subscribe(threadMode = MAIN) fun onAccountChanged(event: OnAccountChanged) {
        if (!isAdded) {
            return
        }

        // When account change is caused by password change, progress dialog will be shown (i.e. not null).
        if (mChangePasswordProgressDialog != null) {
            showChangePasswordProgressDialog(false)
            if (event.isError) {
                // We usually rely on event.error.type and provide our own localized message.
                // This case is exceptional because:
                // 1. The server-side error type is generic, but patching this server-side is quite involved
                // 2. We know the error string return from the server has decent localization
                val errorMessage = if (!TextUtils.isEmpty(event.error.message)) event.error.message else getString(
                        string.error_post_account_settings
                )
                ToastUtils.showToast(activity, errorMessage, LONG)
                AppLog.e(SETTINGS, event.error.message)
            } else {
                ToastUtils.showToast(activity, string.change_password_confirmation, LONG)
                refreshAccountDetails()
            }
        } else {
            if (event.isError) {
                when (event.error.type) {
                    SETTINGS_FETCH_GENERIC_ERROR -> ToastUtils.showToast(
                            activity, string.error_fetch_account_settings,
                            LONG
                    )
                    SETTINGS_FETCH_REAUTHORIZATION_REQUIRED_ERROR -> ToastUtils.showToast(
                            activity, string.error_disabled_apis,
                            LONG
                    )
                    SETTINGS_POST_ERROR -> {
                        // We usually rely on event.error.type and provide our own localized message.
                        // This case is exceptional because:
                        // 1. The server-side error type is generic, but patching this server-side is quite involved
                        // 2. We know the error string return from the server has decent localization
                        val errorMessage = if (!TextUtils.isEmpty(event.error.message)) event.error.message else getString(
                                string.error_post_account_settings
                        )
                        ToastUtils.showToast(activity, errorMessage, LONG)
                        // we optimistically show the email change snackbar, if that request fails, we should
                        // remove the snackbar
                        checkIfEmailChangeIsPending()
                    }
                }
            } else {
                refreshAccountDetails()
            }
        }
    }

    /**
     * If the username can be changed then the control can be clicked to open to the
     * Username Changer screen.
     */
    private fun checkIfUsernameCanBeChanged() {
        val account = mAccountStore!!.account
        mUsernamePreference!!.isEnabled = account.usernameCanBeChanged
        mUsernamePreference!!.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference: Preference? ->
            showUsernameChangerFragment()
            true
        }
    }

    private fun showUsernameChangerFragment() {
        val account = mAccountStore!!.account
        val bundle: Bundle = BaseUsernameChangerFullScreenDialogFragment.newBundle(
                account.displayName,
                account.userName
        )
        Builder(activity)
                .setTitle(string.username_changer_title)
                .setAction(string.username_changer_action)
                .setOnConfirmListener(this)
                .setHideActivityBar(true)
                .setIsLifOnScroll(false)
                .setOnDismissListener(null)
                .setContent(SettingsUsernameChangerFragment::class.java, bundle)
                .build()
                .show(
                        (activity as AppCompatActivity).supportFragmentManager,
                        FullScreenDialogFragment.TAG
                )
    }

    override fun onConfirm(result: Bundle?) {
        if (result != null) {
            val username = result.getString(BaseUsernameChangerFullScreenDialogFragment.RESULT_USERNAME)
            if (username != null) {
                make(
                        view!!,
                        String.format(
                                getString(string.settings_username_changer_toast_content),
                                username
                        ),
                        Snackbar.LENGTH_LONG
                ).show()
                mUsernamePreference!!.summary = username
            }
        }
    }

    /*
     * AsyncTask which loads sites from database for primary site preference
     */
    @SuppressLint("StaticFieldLeak")
    private inner class LoadSitesTask : AsyncTask<Void?, Void?, Void?>() {
        override fun onPreExecute() {
            super.onPreExecute()
        }

        override fun onCancelled() {
            super.onCancelled()
        }

        protected override fun doInBackground(vararg params: Void?): Void? {
            val sites = mSiteStore!!.sitesAccessedViaWPComRest
            mPrimarySitePreference!!.entries = getSiteNamesFromSites(
                    sites
            )
            mPrimarySitePreference!!.entryValues = getSiteIdsFromSites(
                    sites
            )
            mPrimarySitePreference!!.setDetails(getHomeURLOrHostNamesFromSites(sites))
            return null
        }

        override fun onPostExecute(results: Void?) {
            super.onPostExecute(results)
            mPrimarySitePreference!!.refreshAdapter()
        }
    }

    companion object {
        fun getSiteNamesFromSites(sites: List<SiteModel>): Array<String> {
            val blogNames: MutableList<String> = ArrayList()
            for (site in sites) {
                blogNames.add(SiteUtils.getSiteNameOrHomeURL(site))
            }
            return blogNames.toTypedArray()
        }

        fun getHomeURLOrHostNamesFromSites(sites: List<SiteModel>): Array<String> {
            val urls: MutableList<String> = ArrayList()
            for (site in sites) {
                urls.add(SiteUtils.getHomeURLOrHostName(site))
            }
            return urls.toTypedArray()
        }

        fun getSiteIdsFromSites(sites: List<SiteModel>): Array<String> {
            val ids: MutableList<String> = ArrayList()
            for (site in sites) {
                ids.add(site.siteId.toString())
            }
            return ids.toTypedArray()
        }
    }
}
