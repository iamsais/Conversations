package com.mingling.android;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.mingl.api.ApiServiceController;
import eu.siacs.conversations.mingl.api.GeneralApi;
import eu.siacs.conversations.mingl.api.UsersApi;
import eu.siacs.conversations.mingl.api.requests.AuthenticationRequest;
import eu.siacs.conversations.mingl.api.responses.GeneralResponse;
import eu.siacs.conversations.mingl.models.dao.JsonDao;
import eu.siacs.conversations.mingl.utils.Constants;
import eu.siacs.conversations.mingl.utils.GeneralUtils;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.WelcomeActivity;
import lombok.SneakyThrows;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StartUI extends AppCompatActivity
        implements EasyPermissions.PermissionCallbacks {

    private static final int NeededPermissions = 1000;
    private static final String TAG = StartUI.class.getSimpleName();
    String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };
    String userSessionToken = null;
    String sessionToken = null;

    Handler handler;
    Runnable r = new Runnable() {
        @Override
        public void run() {
//			startActivity(new Intent(SplashActivity.this, GetStartedActivity.class));
//			finish();
            requestNeededPermissions();
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_ui);

        handler = new Handler(Looper.getMainLooper());
        GeneralUtils.context = this;

        userSessionToken = JsonDao.getUserAccessToken();
        sessionToken = null;

        if (TextUtils.isEmpty(userSessionToken))
            sessionToken = JsonDao.getClientAccessToken();
        else
            handler.postDelayed(r, 2000);

        if (TextUtils.isEmpty(sessionToken))
            loginCallApi();
        else
            tokenValidCheck(sessionToken);
    }

    private void tokenValidCheck(String sessionToken) {
        Log.d(TAG, "tokenValidCheck");

        GeneralApi generalApi = ApiServiceController.createService(GeneralApi.class, BuildConfig.BASE_URL, sessionToken);
        Call<GeneralResponse> call = generalApi.checkTokenValid();
        call.enqueue(new Callback<GeneralResponse>() {
            @SneakyThrows
            @Override
            public void onResponse(Call<GeneralResponse> call, Response<GeneralResponse> response) {

                Log.d(TAG, "Response: " + response.body());
                Log.d(TAG, "Response Code: " + response.code());

                if (!response.isSuccessful()) {
                    // Token expired
                    loginCallApi();
                } else {
                    handler.postDelayed(r, 2000);
                }
            }

            @Override
            public void onFailure(Call<GeneralResponse> call, Throwable t) {
                finish();
            }
        });

    }

    private void loginCallApi() {
        Log.d(TAG, "loginCallApi");
        AuthenticationRequest authenticationRequest = new AuthenticationRequest();
        authenticationRequest.setUsername("admin@charlie.com");
        authenticationRequest.setPassword("Admin@123");

        UsersApi loginApi = ApiServiceController.createService(UsersApi.class, BuildConfig.BASE_URL, null, null);
        Call<GeneralResponse> call = loginApi.login(authenticationRequest);
        call.enqueue(new Callback<GeneralResponse>() {
            @SneakyThrows
            @Override
            public void onResponse(Call<GeneralResponse> call, Response<GeneralResponse> response) {

                Log.d(TAG, "Response: " + response.body());
                Log.d(TAG, "Response Code: " + response.code());

                if (response.isSuccessful()) {
                    // Login Successful

                    GeneralResponse generalResponse = response.body();
                    if (generalResponse.getStatusCodeValue() == HttpsURLConnection.HTTP_OK) {
                        String data = generalResponse.getResultBody().getData().replace("\\\\", "");

                        //Save Login Response
                        GeneralUtils.context = getBaseContext();
                        GeneralUtils.saveOrUpdatePreference(Constants.LOGIN_RESPONSE, data);

                        Log.d(TAG, "JWT: " + JsonDao.getClientAccessToken());

                        handler.postDelayed(r, 2000);
                    }

                } else if (response.code() == HttpsURLConnection.HTTP_INTERNAL_ERROR) {
                    // Login Failed
                    Log.e(TAG, "Login Failed");
                    finish();
                }
            }

            @Override
            public void onFailure(Call<GeneralResponse> call, Throwable t) {
                finish();
            }
        });

    }

    @AfterPermissionGranted(NeededPermissions)
    private void requestNeededPermissions() {
        String PREF_FIRST_START = "FirstStart";
        SharedPreferences FirstStart = getApplicationContext().getSharedPreferences(PREF_FIRST_START, Context.MODE_PRIVATE);
        long FirstStartTime = FirstStart.getLong(PREF_FIRST_START, 0);
        if (EasyPermissions.hasPermissions(this, perms)) {
            // Already have permission, start ConversationsActivity
            Log.d(Config.LOGTAG, "All permissions granted, starting " + getString(R.string.app_name) + "(" + FirstStartTime + ")");
            runOnUiThread(() -> {
                if (TextUtils.isEmpty(userSessionToken))
                    new GeneralUtils(StartUI.this)
                            .openActivity(StartUI.this, WelcomeActivity.class, null, true);
                else
                    new GeneralUtils(StartUI.this)
                            .openActivity(StartUI.this, ConversationsActivity.class, null, true);

                finish();
            });
        } else {
            // set first start to 0 if there are permissions to request
            Log.d(Config.LOGTAG, "Requesting required permissions");
            SharedPreferences.Editor editor = FirstStart.edit();
            editor.putLong(PREF_FIRST_START, 0);
            editor.commit();
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, getString(R.string.request_permissions_message),
                    NeededPermissions, perms);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        Log.d(Config.LOGTAG, "Permissions granted:" + requestCode);
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        Log.d(Config.LOGTAG, "Permissions denied:" + requestCode);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.request_permissions_message_again))
                .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                        overridePendingTransition(R.anim.left_in, R.anim.left_out);
                    }
                })
                .setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .create();
        dialog.show();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}