package eu.siacs.conversations.mingling.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.databinding.ActivityStartGetEmailBinding;
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
import static eu.siacs.conversations.mingling.utils.Constants.KEY_USERNAME;
import static eu.siacs.conversations.mingling.utils.Constants.KEY_USER_PROFILE_URI;
import static eu.siacs.conversations.mingling.utils.Constants.KEY_WORKSPACE;


public class StartGetEmailActivity extends AppCompatActivity {

    private static final String TAG = StartGetEmailActivity.class.getSimpleName();
    private static final int RC_SIGN_IN = 1;
    ActivityStartGetEmailBinding binding;

    Context context;
    Activity activity;
    boolean isGetStarted = false;
    String workspace;
    GoogleSignInClient mGoogleSignInClient;
    GoogleSignInAccount account;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityStartGetEmailBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        GeneralUtils.context = this;
        context = this;
        activity = this;

        if (getIntent().hasExtra(KEY_GET_STARTED)
                && getIntent().getStringExtra(KEY_GET_STARTED).equalsIgnoreCase(KEY_GET_STARTED)) {
            isGetStarted = true;
        }

        if (getIntent().hasExtra(KEY_WORKSPACE)) {
            workspace = getIntent().getStringExtra(KEY_WORKSPACE);
        }

        // Show login or register based on isGetStarted flag
        if(isGetStarted){
            binding.llLogin.setVisibility(View.VISIBLE);
            binding.llRegister.setVisibility(View.GONE);
        }else{
            binding.llLogin.setVisibility(View.GONE);
            binding.llRegister.setVisibility(View.VISIBLE);
        }

        binding.next.setOnClickListener(v -> {
            if(binding.username.getEditText().getText() != null) {
                String email = binding.username.getEditText().getText().toString().trim();
                onSubmit(email);
            }
        });
        binding.register.setOnClickListener(v -> new GeneralUtils(this)
                .openActivity(this, RegisterActivity.class, null, true));

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("675234733264-pu0c34q4uc4jhrdlrraop7gghs1ueod1.apps.googleusercontent.com")
                .requestEmail()
                .build();

        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        mGoogleSignInClient.signOut();

        if(account == null) {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.progress.hide();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            account = completedTask.getResult(ApiException.class);

            // Signed in successfully, show authenticated UI.
            processAccountData(account);
        } catch (ApiException e) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
            Log.w(TAG, "signInResult:failed message=" + e.getMessage());
            processAccountData(null);
        }
    }

    private void disableViews() {
        binding.username.setEnabled(false);
        binding.next.setEnabled(false);
    }

    private void enableViews() {
        binding.username.setEnabled(true);
        binding.next.setEnabled(true);
    }

    private void processAccountData(GoogleSignInAccount account) {
        if (account != null && account.getEmail() != null) {
            GeneralUtils.saveOrUpdatePreference(KEY_USER_PROFILE_URI, account.getPhotoUrl().toString());
            Log.i(TAG, "Google Account - ID Token: "+account.getIdToken());
            Log.i(TAG, "Google Account - Auth Code: "+account.getServerAuthCode());
            Log.i(TAG, "Google Account - Granted Scopes: "+account.getGrantedScopes());
            onSubmit(account.getEmail());
        }
    }

    private void onSubmit(String email) {
        GeneralUtils.hideSoftKeyboard(this);
        disableViews();

        if (TextUtils.isEmpty(email)) {
            binding.username.setError("Mandatory");
        } else if (!TextUtils.isEmpty(email)) {
            if (!email.matches("[a-zA-Z0-9._-]+@[a-z]+.[a-z]+")) {
                binding.username.setError("Invalid Email");
            } else {
                binding.username.setError(null);
            }
        }

        if (binding.username.getError() == null) {
            binding.username.getEditText().setText(email);
            binding.progress.show();
            emailValidCheck(email);
        }
    }

    private void emailValidCheck(String email) {
        Log.d(TAG, "tokenValidCheck");

        UsersApi usersApi = ApiServiceController.createService(UsersApi.class, BuildConfig.BASE_URL, JsonDao.getClientAccessToken());
        Call<GeneralResponse> call = usersApi.checkUserAvailable(email, workspace);
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
                            binding.username.setError("Email Already Registered");
                        else {
                            Map<String, String> values = new HashMap<>();
                            values.put(KEY_WORKSPACE, workspace);
                            values.put(KEY_USERNAME, email);
                            if (isGetStarted)
                                values.put(KEY_GET_STARTED, KEY_GET_STARTED);
                            new GeneralUtils(context).openActivity(activity, StartGetPasswordActivity.class, values, true);
                        }

                    } else if (generalResponse.getStatusCodeValue() == HttpsURLConnection.HTTP_NO_CONTENT) {
                        // Not Available
                        Map<String, String> values = new HashMap<>();
                        values.put(KEY_WORKSPACE, workspace);
                        values.put(KEY_USERNAME, email);

                        if (isGetStarted) {
                            values.put(KEY_GET_STARTED, KEY_GET_STARTED);
                            new GeneralUtils(context).openActivity(activity, StartGetWorkspaceActivity.class, values, true);
                        } else {
                            binding.username.setError("Email not associated with workspace \"" + workspace + "\"");
                        }
                    }


                } else {
                    Snackbar.make(binding.progress, "Something went wrong!", BaseTransientBottomBar.LENGTH_LONG).show();
                }

                enableViews();
                binding.progress.hide();
            }

            @Override
            public void onFailure(Call<GeneralResponse> call, Throwable t) {
                enableViews();
                binding.progress.hide();
            }
        });
    }
}
