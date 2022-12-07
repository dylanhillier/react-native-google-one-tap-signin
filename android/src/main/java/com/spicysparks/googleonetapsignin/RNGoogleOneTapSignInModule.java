package com.spicysparks.googleonetapsignin;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableMap;
import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.CredentialRequest;
import com.google.android.gms.auth.api.credentials.CredentialRequestResponse;
import com.google.android.gms.auth.api.credentials.Credentials;
import com.google.android.gms.auth.api.credentials.CredentialsClient;
import com.google.android.gms.auth.api.credentials.IdentityProviders;
import com.google.android.gms.auth.api.identity.BeginSignInRequest;
import com.google.android.gms.auth.api.identity.BeginSignInResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SavePasswordRequest;
import com.google.android.gms.auth.api.identity.SavePasswordResult;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.identity.SignInCredential;
import com.google.android.gms.auth.api.identity.SignInPassword;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.HashMap;
import java.util.Map;

import com.spicysparks.googleonetapsignin.PendingAuthRecovery;
import com.spicysparks.googleonetapsignin.PromiseWrapper;

import static com.spicysparks.googleonetapsignin.PromiseWrapper.ASYNC_OP_IN_PROGRESS;
import static com.spicysparks.googleonetapsignin.Utils.getExceptionCode;
import static com.spicysparks.googleonetapsignin.Utils.getUserProperties;


public class RNGoogleOneTapSignInModule extends ReactContextBaseJavaModule {

    private String webClientId;
    private boolean useTokenSignIn = true;
    private boolean usePasswordSignIn = false;

    private SignInClient oneTapClient;
    private BeginSignInRequest signInRequest;

    private CredentialsClient credentialsClient;

    private boolean showOneTapDialog = true;

    private static final int REQ_ONE_TAP = 2;
    private static final int REQUEST_CODE_GIS_SAVE_PASSWORD = 3; /* unique request id */
    public static final int RC_SIGN_IN = 9001;
    public static final int REQUEST_CODE_RECOVER_AUTH = 53294;
    public static final String MODULE_NAME = "RNGoogleOneTapSignIn";
    public static final String PLAY_SERVICES_NOT_AVAILABLE = "PLAY_SERVICES_NOT_AVAILABLE";
    public static final String ERROR_USER_RECOVERABLE_AUTH = "ERROR_USER_RECOVERABLE_AUTH";
    private static final String SHOULD_RECOVER = "SHOULD_RECOVER";

    private PendingAuthRecovery pendingAuthRecovery;

    private PromiseWrapper promiseWrapper;

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    public RNGoogleOneTapSignInModule(final ReactApplicationContext reactContext) {
        super(reactContext);

        oneTapClient = Identity.getSignInClient(reactContext);

        credentialsClient = Credentials.getClient(reactContext);

        promiseWrapper = new PromiseWrapper();
        reactContext.addActivityEventListener(new RNGoogleOneTapSignInActivityEventListener());
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("BUTTON_SIZE_ICON", SignInButton.SIZE_ICON_ONLY);
        constants.put("BUTTON_SIZE_STANDARD", SignInButton.SIZE_STANDARD);
        constants.put("BUTTON_SIZE_WIDE", SignInButton.SIZE_WIDE);
        constants.put("BUTTON_COLOR_AUTO", SignInButton.COLOR_AUTO);
        constants.put("BUTTON_COLOR_LIGHT", SignInButton.COLOR_LIGHT);
        constants.put("BUTTON_COLOR_DARK", SignInButton.COLOR_DARK);
        constants.put("SIGN_IN_CANCELLED",
                String.valueOf(GoogleSignInStatusCodes.SIGN_IN_CANCELLED));
        constants.put("SIGN_IN_REQUIRED", String.valueOf(CommonStatusCodes.SIGN_IN_REQUIRED));
        constants.put("IN_PROGRESS", ASYNC_OP_IN_PROGRESS);
        constants.put(PLAY_SERVICES_NOT_AVAILABLE, PLAY_SERVICES_NOT_AVAILABLE);
        return constants;
    }

    @ReactMethod
    public void playServicesAvailable(boolean showPlayServicesUpdateDialog, Promise promise) {
        Activity activity = getCurrentActivity();

        if (activity == null) {
            Log.w(MODULE_NAME, "could not determine playServicesAvailable, activity is null");
            promise.reject(MODULE_NAME, "activity is null");
            return;
        }

        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int status = googleApiAvailability.isGooglePlayServicesAvailable(activity);

        if (status != ConnectionResult.SUCCESS) {
            if (showPlayServicesUpdateDialog
                    && googleApiAvailability.isUserResolvableError(status)) {
                int requestCode = 2404;
                googleApiAvailability.getErrorDialog(activity, status, requestCode).show();
            }
            promise.reject(PLAY_SERVICES_NOT_AVAILABLE, "Play services not available");
        } else {
            promise.resolve(true);
        }
    }

    /**
     * Default configuration option values have been added to maintain backward compatibility with
     * the previous implementation, which only supported Google Token ID requests.
     * 
     * These option are: useTokenSignIn = true, usePasswordSignIn = false
     */
    @ReactMethod
    public void configure(final ReadableMap config, final Promise promise) {
        this.webClientId = config.hasKey("webClientId") ? config.getString("webClientId") : null;
        this.useTokenSignIn =
                config.hasKey("useTokenSignIn") && config.getBoolean("useTokenSignIn");
        this.usePasswordSignIn =
                !config.hasKey("usePasswordSignIn") || config.getBoolean("usePasswordSignIn");

        promise.resolve(null);
    }

    @ReactMethod
    public void signInSilently(final Promise promise) {
        if (oneTapClient == null) {
            rejectWithNullClientError(promise);
            return;
        }

        final Activity activity = getCurrentActivity();

        if (activity == null) {
            promise.reject(MODULE_NAME, "activity is null");
            return;
        }

        signInRequest = BeginSignInRequest.builder()
                .setPasswordRequestOptions(BeginSignInRequest.PasswordRequestOptions.builder()
                        .setSupported(this.usePasswordSignIn).build())
                .setGoogleIdTokenRequestOptions(BeginSignInRequest.GoogleIdTokenRequestOptions
                        .builder().setSupported(this.useTokenSignIn)
                        // Your server's client ID, not your Android client ID.
                        .setServerClientId(webClientId)
                        // Only show accounts previously used to sign in.
                        .setFilterByAuthorizedAccounts(true).build())
                .setAutoSelectEnabled(true).build();

        promiseWrapper.setPromiseWithInProgressCheck(promise, "signIn");
        UiThreadUtil.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                oneTapClient.beginSignIn(signInRequest)
                        .addOnSuccessListener(activity, new OnSuccessListener<BeginSignInResult>() {
                            @Override
                            public void onSuccess(BeginSignInResult result) {
                                try {
                                    activity.startIntentSenderForResult(
                                            result.getPendingIntent().getIntentSender(),
                                            REQ_ONE_TAP, null, 0, 0, 0);
                                } catch (IntentSender.SendIntentException e) {
                                    promise.reject(MODULE_NAME, e.getLocalizedMessage());
                                }
                            }
                        }).addOnFailureListener(activity, new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // No saved credentials found. Launch the One Tap sign-up flow, or
                                // do nothing and continue presenting the signed-out UI.
                                promise.reject(MODULE_NAME, e.getLocalizedMessage());
                            }
                        });
            }
        });
    }

    @ReactMethod
    public void signIn(final Promise promise) {

        if (oneTapClient == null) {
            rejectWithNullClientError(promise);
            return;
        }

        if (!showOneTapDialog) {
            promise.reject(MODULE_NAME, "one-tap sign-in disabled until app restart");
            return;
        }

        final Activity activity = getCurrentActivity();

        if (activity == null) {
            promise.reject(MODULE_NAME, "activity is null");
            return;
        }

        signInRequest = BeginSignInRequest.builder()
                .setPasswordRequestOptions(BeginSignInRequest.PasswordRequestOptions.builder()
                        .setSupported(this.usePasswordSignIn).build())
                .setGoogleIdTokenRequestOptions(BeginSignInRequest.GoogleIdTokenRequestOptions
                        .builder().setSupported(this.useTokenSignIn)
                        // Your server's client ID, not your Android client ID.
                        .setServerClientId(webClientId)
                        // Show all accounts on the device.
                        .setFilterByAuthorizedAccounts(false).build())
                .build();

        promiseWrapper.setPromiseWithInProgressCheck(promise, "signIn");
        UiThreadUtil.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                oneTapClient.beginSignIn(signInRequest)
                        .addOnSuccessListener(activity, new OnSuccessListener<BeginSignInResult>() {
                            @Override
                            public void onSuccess(BeginSignInResult result) {
                                try {
                                    activity.startIntentSenderForResult(
                                            result.getPendingIntent().getIntentSender(),
                                            REQ_ONE_TAP, null, 0, 0, 0);
                                } catch (IntentSender.SendIntentException e) {
                                    promise.reject(MODULE_NAME, e.getLocalizedMessage());
                                }
                            }
                        }).addOnFailureListener(activity, new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // No saved credentials found. Launch the One Tap sign-up flow, or
                                // do nothing and continue presenting the signed-out UI.
                                promise.reject(MODULE_NAME, e.getLocalizedMessage());
                            }
                        });
            }
        });
    }

    @ReactMethod
    public void savePassword(final String userId, final String password, final Promise promise) {
        Log.d("[savePassword]", "invoked");
        if (userId.isEmpty() || password.isEmpty()) {
            promise.reject(MODULE_NAME, "both userid and password must have values");
            Log.d("[savePassword]", "user id and/or password was not set");
            return;
        }

        final Activity activity = getCurrentActivity();

        if (activity == null) {
            promise.reject(MODULE_NAME, "activity is null");
            Log.d("[savePassword]", "activity was null");
            return;
        }

        SignInPassword signInPassword = new SignInPassword(userId, password);
        SavePasswordRequest savePasswordRequest =
                SavePasswordRequest.builder().setSignInPassword(signInPassword).build();

        Log.d("[savePassword]", "save password request built");
        promiseWrapper.setPromiseWithInProgressCheck(promise, "savePassword");
        Identity.getCredentialSavingClient(activity).savePassword(savePasswordRequest)
                .addOnSuccessListener(new OnSuccessListener<SavePasswordResult>() {
                    @Override
                    public void onSuccess(SavePasswordResult result) {
                        try {
                            Log.d("[savePassword]", "starting intent for sender");
                            activity.startIntentSenderForResult(
                                    result.getPendingIntent().getIntentSender(),
                                    REQUEST_CODE_GIS_SAVE_PASSWORD, /* fillInIntent= */ null,
                                    /* flagsMask= */ 0, /* flagsValue= */ 0, /* extraFlags= */ 0,
                                    /* options= */ null);
                        } catch (IntentSender.SendIntentException e) {
                            promise.reject(MODULE_NAME, e.getLocalizedMessage());
                        }
                    }
                }).addOnFailureListener(activity, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // This can happen if an attempt to save a password is made after the user
                        // has opted to 'never' save password when prompted.
                        Log.d("[savePassword]", "failure to set password.. game over.");
                        promise.reject(MODULE_NAME, e.getLocalizedMessage());
                    }
                });

        Log.d("[savePassword]", "exiting");
    }

    @ReactMethod
    public void deletePassword(final String userId, final String password, final Promise promise) {

        final Activity activity = getCurrentActivity();

        if (activity == null) {
            promise.reject(MODULE_NAME, "activity is null");
            return;
        }

        // Important: only store passwords in this field. Android autofill uses this value to
        // complete sign-in forms, so repurposing this field will likely cause errors.
        Credential credential = new Credential.Builder(userId).setPassword(password).build();

        promiseWrapper.setPromiseWithInProgressCheck(promise, "deletePassword");
        credentialsClient.delete(credential).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                promise.resolve(task.isSuccessful());
            }
        });
    }

    private class RNGoogleOneTapSignInActivityEventListener extends BaseActivityEventListener {
        @Override
        public void onActivityResult(Activity activity, final int requestCode, final int resultCode,
                final Intent intent) {
            Log.d("[onActivityResult]", "activity=" + activity + " requestcode=" + requestCode
                    + " resultCode=" + resultCode);
            if (requestCode == REQ_ONE_TAP) {
                handleSignInTaskResult(intent, resultCode);
            } else if (requestCode == REQUEST_CODE_GIS_SAVE_PASSWORD) {
                handleSavePasswordTaskResult(resultCode);
            }
        }
    }

    @ReactMethod
    public void signOut(final Promise promise) {
        if (oneTapClient == null) {
            rejectWithNullClientError(promise);
            return;
        }

        oneTapClient.signOut().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                handleSignOutOrRevokeAccessTask(task, promise);
            }
        });
    }

    private void handleSignInTaskResult(@NonNull Intent intent, int resultCode) {
        if (resultCode == Activity.RESULT_OK) {
            try {
                SignInCredential credential = oneTapClient.getSignInCredentialFromIntent(intent);

                WritableMap userParams = getUserProperties(credential);
                promiseWrapper.resolve(userParams);

            } catch (ApiException e) {
                int code = e.getStatusCode();
                switch (code) {
                    case CommonStatusCodes.CANCELED:
                    case CommonStatusCodes.NETWORK_ERROR:
                    default:
                        String errorDescription = GoogleSignInStatusCodes.getStatusCodeString(code);
                        promiseWrapper.reject(String.valueOf(code), errorDescription);
                }
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            /**
             * User chose not to proceed with google sign-in or password selection. Continuing to
             * show the One-Tap dialog after the user closes it will result in: `Error: 16: Caller
             * has been temporarily blocked due to too many canceled sign-in prompts.`. This error
             * will prevent One-Tap dialog from displaying for 24 hours.
             * https://developers.google.com/identity/one-tap/android/get-saved-credentials#
             * disable-one-tap
             **/
            showOneTapDialog = false;
            Log.d("[handleSignInTaskResult]", "log in was cancelled");
            promiseWrapper.resolve(false);
        }
    }

    private void handleSavePasswordTaskResult(int resultCode) {
        if (resultCode == Activity.RESULT_OK) {
            /* password was saved */
            promiseWrapper.resolve(true);
        } else if (resultCode == Activity.RESULT_CANCELED) {
            /* password saving was cancelled */
            promiseWrapper.resolve(false);
        }
    }

    private void handleSignOutOrRevokeAccessTask(@NonNull Task<Void> task, final Promise promise) {
        if (task.isSuccessful()) {
            promise.resolve(null);
        } else {
            int code = getExceptionCode(task);
            String errorDescription = GoogleSignInStatusCodes.getStatusCodeString(code);
            promise.reject(String.valueOf(code), errorDescription);
        }
    }

    private void rejectWithNullClientError(Promise promise) {
        promise.reject(MODULE_NAME, "oneTapClient is null - call configure first");
    }

}
