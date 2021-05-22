package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import javax.net.ssl.HttpsURLConnection;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.mingling.api.ApiServiceController;
import eu.siacs.conversations.mingling.api.GeneralApi;
import eu.siacs.conversations.mingling.api.UsersApi;
import eu.siacs.conversations.mingling.api.requests.AuthenticationRequest;
import eu.siacs.conversations.mingling.api.responses.GeneralResponse;
import eu.siacs.conversations.mingling.models.dao.JsonDao;
import eu.siacs.conversations.mingling.ui.GetStartedActivity;
import eu.siacs.conversations.mingling.utils.Constants;
import eu.siacs.conversations.mingling.utils.GeneralUtils;
import lombok.SneakyThrows;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = SplashActivity.class.getSimpleName();

    Handler handler;
    Runnable r = new Runnable() {
        @Override
        public void run() {
			startActivity(new Intent(SplashActivity.this, GetStartedActivity.class));
			finish();
//            new GeneralUtils(SplashActivity.this)
//                    .openActivity(SplashActivity.this, GetStartedActivity.class, null, true);
//
//            finish();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler(Looper.getMainLooper());
        GeneralUtils.context = this;
        String sessionToken = JsonDao.getClientAccessToken();
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
                }else{
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
}
