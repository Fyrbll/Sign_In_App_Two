package com.kartik.android.signinapptwo;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import com.citrus.sdk.Callback;
import com.citrus.sdk.CitrusClient;
import com.citrus.sdk.Environment;
import com.citrus.sdk.classes.AccessToken;
import com.citrus.sdk.response.CitrusError;
import com.citrus.sdk.response.CitrusResponse;

import java.util.concurrent.CountDownLatch;

/**
 * Created by kartik on 09/07/15.
 */
public class Authenticator extends AbstractAccountAuthenticator {

    // tag for logs
    private final String TAG = "MainActivity";

    // important constants
    private static final String ACCOUNT_TYPE = "com.citrus.oauth_citruspay";
    private static final String ACCOUNT_NAME = "for.login.tests@gmail.com";
    private static final String PASSWORD = "$2300sOom";

    // AccountManager member
    private static AccountManager mAccountManager;

    // application context
    private final Context mContext;

    // simple constructor that initialises AccountManager
    public Authenticator(Context context) {
        super(context);
        mContext = context;
        mAccountManager = AccountManager.get(mContext);
    }

    // tries to sign in with given credentials, and adds account to
    // account list when sign in succeeds
    @Override
    public Bundle addAccount(
            AccountAuthenticatorResponse r,
            String s,
            String s2,
            String[] strings,
            Bundle bundle) throws NetworkErrorException {

        //------------------------------------------------------------
        // putting account's username and password in received bundle
        //------------------------------------------------------------

        bundle.putString("KEY_USERNAME", ACCOUNT_NAME);
        bundle.putString("KEY_PASSWORD", PASSWORD);

        //-------------------------------------
        // creating Bundle to store results
        //-------------------------------------

        final Bundle addAccount_bundle = new Bundle();

        //----------------------------------------------------------------------------
        // signing in, storing sign in results in signIn_bundle, to verify credentials
        //----------------------------------------------------------------------------

        Bundle signIn_bundle = null;
        try {
            signIn_bundle = signIn(bundle);
        } catch (InterruptedException e) {
            err("signIn threw an exception " + e.getMessage());
        }

        //-----------------------------------------------------------------
        // adding or not adding account based on whether sign in succeeded
        //-----------------------------------------------------------------

        if (signIn_bundle.getBoolean("KEY_SUCCESS")) {

            // creating the account to be stored on the device
            Account citrusAccount = new Account(ACCOUNT_NAME, s);

            // storing the account on the device
            boolean storageSuccess = mAccountManager.addAccountExplicitly(citrusAccount,
                    PASSWORD, null);

            if (storageSuccess) {
                addAccount_bundle.putString("KEY_ACCOUNT_NAME", ACCOUNT_NAME);
                addAccount_bundle.putString("KEY_ACCOUNT_TYPE", s);
                addAccount_bundle.putInt("KEY_CODE", 0);
            } else {
                addAccount_bundle.putString("KEY_ERROR_CODE", "2");
                addAccount_bundle.putString("KEY_ERROR_MESSAGE", "Detail Storage Error");
                addAccount_bundle.putInt("KEY_CODE", 2);
            }

        } else {
            addAccount_bundle.putString("KEY_ERROR_CODE", "1");
            addAccount_bundle.putString("KEY_ERROR_MESSAGE", "Sign-in Error");
            addAccount_bundle.putInt("KEY_CODE", 1);
        }

        //-------------------------------------
        // returning result
        //-------------------------------------

        return addAccount_bundle;
    }

    // to get an authentication token
    @Override
    public Bundle getAuthToken(
            AccountAuthenticatorResponse r,
            Account account,
            String s,
            Bundle bundle) throws NetworkErrorException {

        //------------------------------------------------------------
        // putting account's username and password in received bundle
        //------------------------------------------------------------

        bundle.putString("KEY_USERNAME", account.name);
        bundle.putString("KEY_PASSWORD", mAccountManager.getPassword(account));

        //-------------------------------------
        // creating Bundle to store results
        //-------------------------------------

        final Bundle getAuthToken_bundle = new Bundle();

        //----------------------------------------------------------------------------
        // signing in, storing sign in results in signIn_bundle, to verify credentials
        //----------------------------------------------------------------------------

        Bundle signIn_bundle = null;
        try {
            signIn_bundle = signIn(bundle);
        } catch (InterruptedException e) {
            err("signIn threw an exception " + e.getMessage());
        }

        //-----------------------------------------------------------------------
        // asking server for authtoken or not based on whether sign in succeeded
        //-----------------------------------------------------------------------

        if (signIn_bundle.getBoolean("KEY_SUCCESS")) {
            try {
                getAuthTokenHelper(getAuthToken_bundle, bundle);
            } catch (InterruptedException e) {
                err("getAuthTokenHelper threw an exception " + e.getMessage());
            }
        } else {
            getAuthToken_bundle.putString("KEY_ERROR_CODE", "1");
            getAuthToken_bundle.putString("KEY_ERROR_MESSAGE", "Sign-in Error");
            getAuthToken_bundle.putInt("KEY_CODE", 1);
        }

        //-------------------------------------
        // returning result
        //-------------------------------------

        return getAuthToken_bundle;

    }

    private Bundle getAuthTokenHelper(final Bundle getAuthToken_bundle, Bundle accountOptions) throws InterruptedException {

        //-------------------------------------------
        // setting up citrusClient and countDownLatch
        //-------------------------------------------

        // this will allow the program to pause execution until the thread to request the authtoken finishes execution
        final CountDownLatch latch = new CountDownLatch(1);

        // this acts as the inteface to the server
        final CitrusClient citrusClient = CitrusClient.getInstance(mContext);
        citrusClient.init(accountOptions.getString("KEY_SIGNUP_ID"), accountOptions.getString("KEY_SIGNUP_SECRET"),
                accountOptions.getString("KEY_SIGNIN_ID"), accountOptions.getString("KEY_SIGNIN_SECRET"),
                accountOptions.getString("KEY_VANITY"), Environment.SANDBOX);

        //-----------------------------------------------------------------
        // requesting the token from the server and handling the response
        //-----------------------------------------------------------------

        Log.d(TAG, "Getting auth token...");
        citrusClient.getPrepaidToken(new Callback<AccessToken>() {
            @Override
            public void success(AccessToken accessToken) {
                getAuthToken_bundle.putString("KEY_ACCOUNT_NAME", ACCOUNT_NAME);
                getAuthToken_bundle.putString("KEY_ACCOUNT_TYPE", ACCOUNT_TYPE);
                getAuthToken_bundle.putString("KEY_AUTHTOKEN", accessToken.getAccessToken());
                err("ACCESS TOKEN PROVIDED BY CLIENT: " + accessToken.getAccessToken());
                getAuthToken_bundle.putInt("KEY_CODE", 0);
                latch.countDown();
            }

            @Override
            public void error(CitrusError error) {
                getAuthToken_bundle.putString("KEY_ERROR_CODE", "2");
                getAuthToken_bundle.putString("KEY_ERROR_MESSAGE", "Authtoken error");
                getAuthToken_bundle.putInt("KEY_CODE", 2);
                latch.countDown();
            }
        });

        //-----------------------------------------
        // waiting for the request thread to finish
        //-----------------------------------------

        latch.await();

        //--------------------------------------------------------------------------
        // returning the bundle of results received from its caller, back to caller
        //--------------------------------------------------------------------------

        return getAuthToken_bundle;
    }

    // to sign in
    private Bundle signIn(Bundle accountOptions) throws InterruptedException {

        //-------------------------------------
        // setting up required variables
        //-------------------------------------

        // bundle to store the success/failure status of sign in operation
        final Bundle signIn_bundle = new Bundle();
        // this will allow the program to pause execution until the thread to complete signin finishes execution
        final CountDownLatch latch = new CountDownLatch(1);
        // this acts as the inteface to the server
        final CitrusClient citrusClient = CitrusClient.getInstance(mContext);
        citrusClient.init(accountOptions.getString("KEY_SIGNUP_ID"), accountOptions.getString("KEY_SIGNUP_SECRET"),
                accountOptions.getString("KEY_SIGNIN_ID"), accountOptions.getString("KEY_SIGNIN_SECRET"),
                accountOptions.getString("KEY_VANITY"), Environment.SANDBOX);

        //-----------------------------------------
        // signing in and handling server response
        //-----------------------------------------

        log("Signing in...");
        citrusClient.signIn(accountOptions.getString("KEY_USERNAME"),
                accountOptions.getString("KEY_PASSWORD"), new com.citrus.sdk.Callback<CitrusResponse>() {
                    @Override
                    public void success(CitrusResponse citrusResponse) {
                        signIn_bundle.putBoolean("KEY_SUCCESS", true);
                        latch.countDown();
                    }

                    @Override
                    public void error(CitrusError error) {
                        signIn_bundle.putBoolean("KEY_SUCCESS", false);
                        latch.countDown();
                    }
                });

        //-----------------------------------------
        // waiting for the request thread to finish
        //-----------------------------------------

        latch.await();

        //--------------------------------------------------
        // returning bundle with sign in result information
        //--------------------------------------------------

        return signIn_bundle;

    }

    // to display debug messages
    private void log(String message) {
        Log.d(TAG, message);
    }

    // to display errors
    private void err(String message) {
        Log.e(TAG, message);
    }



    //-------------------------------------
    //---Dummy methods---------------------
    //-------------------------------------

    // Editing properties is not supported
    @Override
    public Bundle editProperties(
            AccountAuthenticatorResponse r, String s) {
        throw new UnsupportedOperationException();
    }
    // Ignore attempts to confirm credentials
    @Override
    public Bundle confirmCredentials(
            AccountAuthenticatorResponse r,
            Account account,
            Bundle bundle) throws NetworkErrorException {
        return null;
    }
    // Getting a label for the auth token is not supported
    @Override
    public String getAuthTokenLabel(String s) {
        throw new UnsupportedOperationException();
    }
    // Updating user credentials is not supported
    @Override
    public Bundle updateCredentials(
            AccountAuthenticatorResponse r,
            Account account,
            String s, Bundle bundle) throws NetworkErrorException {
        throw new UnsupportedOperationException();
    }
    // Checking features for the account is not supported
    @Override
    public Bundle hasFeatures(
            AccountAuthenticatorResponse r,
            Account account, String[] strings) throws NetworkErrorException {
        throw new UnsupportedOperationException();
    }

}
