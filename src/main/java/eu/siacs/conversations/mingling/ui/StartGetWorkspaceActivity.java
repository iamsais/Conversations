package eu.siacs.conversations.mingling.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.databinding.ActivityStartGetWorkspaceBinding;
import eu.siacs.conversations.mingling.api.ApiServiceController;
import eu.siacs.conversations.mingling.api.OrgApi;
import eu.siacs.conversations.mingling.api.responses.GeneralResponse;
import eu.siacs.conversations.mingling.models.Organization;
import eu.siacs.conversations.mingling.models.dao.JsonDao;
import eu.siacs.conversations.mingling.utils.Constants;
import eu.siacs.conversations.mingling.utils.GeneralUtils;
import lombok.SneakyThrows;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static eu.siacs.conversations.mingling.utils.Constants.KEY_GET_STARTED;
import static eu.siacs.conversations.mingling.utils.Constants.KEY_USERNAME;
import static eu.siacs.conversations.mingling.utils.Constants.KEY_USER_PROFILE_URI;
import static eu.siacs.conversations.mingling.utils.Constants.KEY_WORKSPACE;


public class StartGetWorkspaceActivity extends AppCompatActivity {

    private static final String TAG = StartGetWorkspaceActivity.class.getSimpleName();
    ActivityStartGetWorkspaceBinding binding;

    Context context;
    Activity activity;
    boolean isGetStarted = false;
    String username;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityStartGetWorkspaceBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        GeneralUtils.context = this;
        context = this;
        activity = this;

        if (getIntent().hasExtra(KEY_GET_STARTED)
                && getIntent().getStringExtra(KEY_GET_STARTED).equalsIgnoreCase(KEY_GET_STARTED)) {
            isGetStarted = true;
        }

        if (getIntent().hasExtra(KEY_USERNAME)) {
            username = getIntent().getStringExtra(KEY_USERNAME);
        }

        // Show login or register based on isGetStarted flag
        if(isGetStarted){
            binding.llLogin.setVisibility(View.VISIBLE);
            binding.llRegister.setVisibility(View.GONE);
        }else{
            binding.llLogin.setVisibility(View.GONE);
            binding.llRegister.setVisibility(View.VISIBLE);
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

    private void disableViews(){
        binding.workplace.setEnabled(false);
        binding.next.setEnabled(false);
    }

    private void enableViews(){
        binding.workplace.setEnabled(true);
        binding.next.setEnabled(true);
    }

    private void onSubmit() {
        GeneralUtils.hideSoftKeyboard(this);
        disableViews();
        String workplace = binding.workplace.getEditText().getText().toString().trim();

        if (TextUtils.isEmpty(workplace)) {
            binding.workplace.setError("Mandatory");
        } else if (!TextUtils.isEmpty(workplace)) {
            if (!workplace.matches("[a-zA-Z0-9_-]+")) {
                binding.workplace.setError("Invalid Characters");
            } else {
                binding.workplace.setError(null);
            }
        }

        if (binding.workplace.getError() == null) {
            binding.progress.show();
            tokenValidCheck(workplace);
        }
    }

    private void tokenValidCheck(String key) {
        Log.d(TAG, "tokenValidCheck");

        OrgApi orgApi = ApiServiceController.createService(OrgApi.class, BuildConfig.BASE_URL, JsonDao.getClientAccessToken());
        Call<GeneralResponse> call = orgApi.checkOrgAvailability(key);
        call.enqueue(new Callback<GeneralResponse>() {
            @SneakyThrows
            @Override
            public void onResponse(Call<GeneralResponse> call, Response<GeneralResponse> response) {

                Log.d(TAG, "Response: " + response.body());
                Log.d(TAG, "Response Code: " + response.code());

                if (response.isSuccessful()) {

                    GeneralResponse generalResponse = response.body();
                    if (generalResponse.getStatusCodeValue() == HttpsURLConnection.HTTP_OK) {

                        String data = generalResponse.getResultBody().getData().replace("\\\\", "");

                        //Save Login Response
                        GeneralUtils.context = getBaseContext();
                        GeneralUtils.saveOrUpdatePreference(Constants.ORG_RESPONSE, data);

                        Map<String, String> values = new HashMap<>();
                        // Available
                        if (isGetStarted) { // Registration
                            values.put(KEY_GET_STARTED, KEY_GET_STARTED);
                            values.put(KEY_WORKSPACE, key);
                            values.put(KEY_USERNAME, username);
                            new GeneralUtils(context).openActivity(activity, StartGetPasswordActivity.class, values, true);
                        }else { // Sign-in
                            values.put(KEY_WORKSPACE, key);
                            new GeneralUtils(context).openActivity(activity, StartGetEmailActivity.class, values, true);
                        }

                    } else if (generalResponse.getStatusCodeValue() == HttpsURLConnection.HTTP_NO_CONTENT) {
                        // Not Available
                        binding.workplace.setError("Enter a valid workspace");
                        enableViews();
                        binding.progress.hide();
                    }

                }else{
                    Snackbar.make(binding.progress,"Something went wrong!", BaseTransientBottomBar.LENGTH_LONG).show();
                }

                enableViews();
                binding.progress.hide();
            }

            @Override
            public void onFailure(Call<GeneralResponse> call, Throwable t) {
                Snackbar.make(binding.progress,"Something went wrong!", BaseTransientBottomBar.LENGTH_LONG).show();
                enableViews();
                binding.progress.hide();
            }
        });

    }
}
