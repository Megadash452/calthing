package me.marti.calprovexample.syncAdapter

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Service
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import me.marti.calprovexample.calendar.ACCOUNT_NAME


/** A bound Service that instantiates the authenticator
 * when started. */
class AuthenticatorService : Service() {
    /** Instance field that stores the authenticator object. */
    private lateinit var mAuthenticator: Authenticator

    /** Create a new authenticator object */
    override fun onCreate() {
        mAuthenticator = Authenticator(this.applicationContext)
    }

    /** When the system binds to this Service to make the RPC call
     * return the authenticator's IBinder. */
    override fun onBind(intent: Intent?): IBinder = mAuthenticator.iBinder
}


/** Create a stub Authenticator (a fake authenticator, which is required for sync adapter).
  * Taken from [here](https://developer.android.com/training/sync-adapters/creating-authenticator.html) */
class Authenticator(context: Context): AbstractAccountAuthenticator(context) {
    /** Editing properties is not supported */
    override fun editProperties(r: AccountAuthenticatorResponse, s: String): Bundle = throw UnsupportedOperationException()

    /** Can add account, but only for the first time. */
    override fun addAccount(
        response: AccountAuthenticatorResponse,
        accountType: String,
        authTokenType: String?,
        requiredFeatures: Array<String>?,
        options: Bundle
    ): Bundle {
        return Bundle().apply {
            this.putString(AccountManager.KEY_ACCOUNT_NAME, ACCOUNT_NAME)
            this.putString(AccountManager.KEY_ACCOUNT_TYPE, accountType)
        }
    }

    /** Ignore attempts to confirm credentials */
    override fun confirmCredentials(
        r: AccountAuthenticatorResponse,
        account: Account,
        bundle: Bundle
    ): Bundle?  = null

    /** Getting an authentication token is not supported */
    override fun getAuthToken(
        r: AccountAuthenticatorResponse,
        account: Account,
        s: String,
        bundle: Bundle
    ): Bundle = throw UnsupportedOperationException()

    /** Getting a label for the auth token is not supported */
    override fun getAuthTokenLabel(s: String): String = throw UnsupportedOperationException()

    /** Updating user credentials is not supported */
    override fun updateCredentials(
        r: AccountAuthenticatorResponse,
        account: Account,
        s: String,
        bundle: Bundle
    ): Bundle = throw UnsupportedOperationException()

    /** Checking features for the account is not supported */
    override fun hasFeatures(
        r: AccountAuthenticatorResponse,
        account: Account,
        strings: Array<String>
    ): Bundle = throw UnsupportedOperationException()
}


class ContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
