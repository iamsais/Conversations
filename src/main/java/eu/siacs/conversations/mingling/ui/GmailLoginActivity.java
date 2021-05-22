package eu.siacs.conversations.mingling.ui;

import android.content.Intent;
import android.os.Bundle;
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

import javax.net.ssl.HttpsURLConnection;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.databinding.ActivityLoginBinding;
import eu.siacs.conversations.mingling.api.ApiServiceController;
import eu.siacs.conversations.mingling.api.UsersApi;
import eu.siacs.conversations.mingling.api.requests.AuthenticationRequest;
import eu.siacs.conversations.mingling.api.responses.GeneralResponse;
import eu.siacs.conversations.mingling.models.dao.JsonDao;
import eu.siacs.conversations.mingling.utils.Constants;
import eu.siacs.conversations.mingling.utils.GeneralUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class GmailLoginActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 1;
    private static final String TAG = GmailLoginActivity.class.getSimpleName();
    ActivityLoginBinding binding;
    GoogleSignInClient mGoogleSignInClient;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("675234733264-pu0c34q4uc4jhrdlrraop7gghs1ueod1.apps.googleusercontent.com")
                .requestEmail()
                .build();

        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        binding.signInButton.setOnClickListener(v -> signIn());
        //binding.signout.setOnClickListener(v -> signOut());
    }

    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void signOut(){
        mGoogleSignInClient.signOut();
        // Clear Login data preference
        GeneralUtils.deletePreferenceKey(Constants.LOGIN_RESPONSE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        updateUI(account);
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
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);

            // Signed in successfully, show authenticated UI.
            updateUI(account);
        } catch (ApiException e) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
            Log.w(TAG, "signInResult:failed message=" + e.getMessage());
            updateUI(null);
        }
    }

    private void updateUI(GoogleSignInAccount account) {
        if(account != null) { // Login Success
//            binding.email.setText(account.getEmail());
//            binding.name.setText(account.getDisplayName());

            loginCallApi();
        }
    }

    private void loginCallApi(){
        Log.d(TAG, "loginCallApi");
        AuthenticationRequest authenticationRequest = new AuthenticationRequest();
        authenticationRequest.setUsername("admin@charlie.com");
        authenticationRequest.setPassword("Admin@123");

//            HashMap<String, String> loginRequestBody =  new HashMap<>();
//            loginRequestBody.put("username", "admin@charlie.com");
//            loginRequestBody.put("password", "Admin@123");


        UsersApi loginApi = ApiServiceController.createService(UsersApi.class, BuildConfig.BASE_URL, null, null);
        Call<GeneralResponse> call = loginApi.login(authenticationRequest);
        call.enqueue(new Callback<GeneralResponse>() {
            @Override
            public void onResponse(Call<GeneralResponse> call, Response<GeneralResponse> response) {
                Log.d(TAG, "Response: "+ response.body());
                Log.d(TAG, "Response Code: "+ response.code());

                if (response.isSuccessful()) {
                    // Login Successful

                    GeneralResponse generalResponse = response.body();
                    if(generalResponse.getStatusCodeValue() == HttpsURLConnection.HTTP_OK){
                        String data = generalResponse.getResultBody().getData().replace("\\\\","");

                        //Save Login Response
                        GeneralUtils.context = getBaseContext();
                        GeneralUtils.saveOrUpdatePreference(Constants.LOGIN_RESPONSE, data);

                        Log.d(TAG, "JWT: "+ JsonDao.getClientAccessToken());
                    }

                } else if (response.code() == HttpsURLConnection.HTTP_INTERNAL_ERROR) {
                    // Login Failed
                    Log.e(TAG, "Login Failed");
                }
            }

            @Override
            public void onFailure(Call<GeneralResponse> call, Throwable t) {

            }
        });
    }
}
