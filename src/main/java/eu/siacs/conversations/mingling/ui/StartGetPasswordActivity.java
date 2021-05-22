package eu.siacs.conversations.mingling.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import javax.net.ssl.HttpsURLConnection;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.databinding.ActivityStartGetPasswordBinding;
import eu.siacs.conversations.mingling.api.ApiServiceController;
import eu.siacs.conversations.mingling.api.UsersApi;
import eu.siacs.conversations.mingling.api.responses.GeneralResponse;
import eu.siacs.conversations.mingling.models.dao.JsonDao;
import eu.siacs.conversations.mingling.utils.GeneralUtils;
import lombok.SneakyThrows;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static eu.siacs.conversations.mingling.utils.Constants.KEY_GET_STARTED;


public class StartGetPasswordActivity extends AppCompatActivity {

    private static final String TAG = StartGetPasswordActivity.class.getSimpleName();
    ActivityStartGetPasswordBinding binding;

    Context context;
    Activity activity;
    boolean isGetStarted = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityStartGetPasswordBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        GeneralUtils.context = this;
        context = this;
        activity = this;

        if (getIntent().hasExtra(KEY_GET_STARTED)
                && getIntent().getStringExtra(KEY_GET_STARTED).equalsIgnoreCase(KEY_GET_STARTED)) {
            isGetStarted = true;
        }

        binding.next.setOnClickListener(v -> onSubmit());
        binding.register.setOnClickListener(v -> new GeneralUtils(this)
                .openActivity(this, RegisterActivity.class, null, true));
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.progress.hide();
    }

    private void onSubmit() {
        GeneralUtils.hideSoftKeyboard(this);

        String username = binding.password.getEditText().getText().toString().trim();

        if (TextUtils.isEmpty(username)) {
            binding.password.setError("Mandatory");
        } else if (!TextUtils.isEmpty(username)) {
            binding.password.setError(null);
        }

        if (binding.password.getError() == null) {
            binding.progress.show();
            //tokenValidCheck(username);
        }
    }

    private void tokenValidCheck(String username) {
        Log.d(TAG, "tokenValidCheck");

        UsersApi usersApi = ApiServiceController.createService(UsersApi.class, BuildConfig.BASE_URL, JsonDao.getClientAccessToken());
        Call<GeneralResponse> call = usersApi.checkUserAvailable(username);
        call.enqueue(new Callback<GeneralResponse>() {
            @SneakyThrows
            @Override
            public void onResponse(Call<GeneralResponse> call, Response<GeneralResponse> response) {

                Log.d(TAG, "Response: " + response.body());
                Log.d(TAG, "Response Code: " + response.code());

                if (response.isSuccessful()) {

                    GeneralResponse generalResponse = response.body();
                    if (generalResponse.getStatusCodeValue() == HttpsURLConnection.HTTP_OK) {
                        // Available
                        if (isGetStarted)
                            binding.password.setError("Email Already Registered");
                        else
                            new GeneralUtils(context).openActivity(activity, LoginActivity.class, null, true);

                    } else if (generalResponse.getStatusCodeValue() == HttpsURLConnection.HTTP_NO_CONTENT) {
                        // Not Available
                        new GeneralUtils(context).openActivity(activity, LoginActivity.class, null, true);
                    }
                }
            }

            @Override
            public void onFailure(Call<GeneralResponse> call, Throwable t) {
                binding.progress.hide();
            }
        });

    }
}
