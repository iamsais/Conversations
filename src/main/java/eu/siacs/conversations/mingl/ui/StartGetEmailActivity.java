package eu.siacs.conversations.mingl.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.base.CharMatcher;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityStartGetEmailBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.mingl.api.ApiServiceController;
import eu.siacs.conversations.mingl.api.UsersApi;
import eu.siacs.conversations.mingl.api.responses.GeneralResponse;
import eu.siacs.conversations.mingl.models.dao.JsonDao;
import eu.siacs.conversations.mingl.utils.Constants;
import eu.siacs.conversations.mingl.utils.GeneralUtils;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.xmpp.Jid;
import lombok.SneakyThrows;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static eu.siacs.conversations.mingl.utils.Constants.GOOGLE_REG_PASSWORD_SECRET;
import static eu.siacs.conversations.mingl.utils.Constants.KEY_GET_STARTED;
import static eu.siacs.conversations.mingl.utils.Constants.KEY_GOOGLE_ID_TOKEN;
import static eu.siacs.conversations.mingl.utils.Constants.KEY_PASSWORD;
import static eu.siacs.conversations.mingl.utils.Constants.KEY_USERNAME;
import static eu.siacs.conversations.mingl.utils.Constants.KEY_USER_PROFILE_URI;
import static eu.siacs.conversations.mingl.utils.Constants.KEY_WORKSPACE;
import static eu.siacs.conversations.services.EventReceiver.EXTRA_NEEDS_FOREGROUND_SERVICE;

public class StartGetEmailActivity extends XmppActivity {

    private static final String TAG = StartGetEmailActivity.class.getSimpleName();
    private static final int RC_SIGN_IN = 1;

    ActivityStartGetEmailBinding binding;
    Context context;
    Activity activity;
    boolean isGetStarted = false;
    String workspace;
    GoogleSignInClient mGoogleSignInClient;
    GoogleSignInAccount account;
    private FirebaseAuth mAuth;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityStartGetEmailBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        GeneralUtils.context = this;
        context = this;
        activity = this;

        onBackendConnected();

        FirebaseApp.initializeApp(context);

        if (getIntent().hasExtra(KEY_GET_STARTED)
                && getIntent().getStringExtra(KEY_GET_STARTED).equalsIgnoreCase(KEY_GET_STARTED)) {
            isGetStarted = true;
        }

        if (getIntent().hasExtra(KEY_WORKSPACE)) {
            workspace = getIntent().getStringExtra(KEY_WORKSPACE);
        }

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("675234733264-hh98d9r4iuu87eqmeh696fi5v4e8h92p.apps.googleusercontent.com")
                .requestEmail()
                .build();

        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        mAuth.signOut();

        if (account == null) {
            signIn();
        }

        binding.signInButton.setOnClickListener(v -> {
            signIn();
        });
    }

    @Override
    protected void refreshUiReal() {
    }

    @Override
    protected void onBackendConnected() {
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.progress.hide();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                // Google Sign In was successful, authenticate with Firebase
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.getId());
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                // Google Sign In failed, update UI appropriately
                Log.d(TAG, "Google sign in failed: " + e);
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithCredential:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            processAccountData(user);
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.d(TAG, "signInWithCredential:failure: " + task.getException());
                            processAccountData(null);
                        }
                    }
                });
    }

    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

//    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
//        try {
//            account = completedTask.getResult(ApiException.class);
//
//            // Signed in successfully, show authenticated UI.
//            processAccountData(account);
//        } catch (ApiException e) {
//            // The ApiException status code indicates the detailed failure reason.
//            // Please refer to the GoogleSignInStatusCodes class reference for more information.
//            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
//            Log.w(TAG, "signInResult:failed message=" + e.getMessage());
//            e.printStackTrace();
//
//            processAccountData(null);
//        }
//    }

    private void processAccountData(FirebaseUser account) {
        if (account != null && account.getEmail() != null) {
            runOnUiThread(() -> binding.progress.show());
            GeneralUtils.saveOrUpdatePreference(KEY_USER_PROFILE_URI, account.getPhotoUrl().toString());

            Log.d(TAG, "Google Account - ID Token: " + account.getIdToken(false));
            onSubmit(account.getEmail(), account.getIdToken(false).toString());
        }
    }

    private void onSubmit(String email, String idToken) {
        GeneralUtils.hideSoftKeyboard(this);
        if (!TextUtils.isEmpty(email) && email.matches("[a-zA-Z0-9._-]+@[a-z]+.[a-z]+")) {
            binding.progress.show();
            emailValidCheck(email, idToken);
        } else {
            Log.d(TAG, "Email is empty or not Valid");
        }
    }

    @SneakyThrows
    private void emailValidCheck(String email, String idToken) {
        Log.d(TAG, "emailValidCheck");
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
                    /**
                     * Email exist
                     */
                    if (generalResponse.getStatusCodeValue() == HttpsURLConnection.HTTP_OK) {
                        // Available
                        if (isGetStarted) {
                            // Login
                            if (idToken == null)
                                Log.d(TAG, "Email Already Registered");
                            else
                                login(email, idToken);
                        } else {
                            // Register
                            if (idToken == null) {
                                Map<String, String> values = new HashMap<>();
                                values.put(KEY_WORKSPACE, workspace);
                                values.put(KEY_USERNAME, email);
                                if (isGetStarted)
                                    values.put(KEY_GET_STARTED, KEY_GET_STARTED);
                                new GeneralUtils(context).openActivity(activity, StartGetPasswordActivity.class, values, true);
                            } else
                                login(email, idToken);
                        }
                    }
                    /**
                     * Email does not exist
                     */
                    else if (generalResponse.getStatusCodeValue() == HttpsURLConnection.HTTP_NO_CONTENT) {
                        // Not Available

                        if (isGetStarted) {
                            // Register
                            Map<String, String> values = new HashMap<>();
                            values.put(KEY_WORKSPACE, workspace);
                            values.put(KEY_USERNAME, email);
                            values.put(KEY_GOOGLE_ID_TOKEN, idToken);
                            values.put(KEY_GET_STARTED, KEY_GET_STARTED);
                            new GeneralUtils(context).openActivity(activity, StartGetWorkspaceActivity.class, values, true);

                        } else {
                            Log.d(TAG, "Email not associated with workspace \"" + workspace + "\"");
                        }
                    }

                } else {
                    Snackbar.make(binding.progress, "Something went wrong!", BaseTransientBottomBar.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<GeneralResponse> call, Throwable t) {
                Log.d(TAG, "onFailure: " + t.getMessage());
                binding.progress.hide();
            }
        });
    }

    private void login(String username, String idToken) {
        Log.d(TAG, "login()" );
        UsersApi usersApi = ApiServiceController.createService(UsersApi.class, BuildConfig.BASE_URL, JsonDao.getClientAccessToken());
        Call<GeneralResponse> call = usersApi.googleLogin(idToken, username);
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
                        GeneralUtils.saveOrUpdatePreference(Constants.USER_LOGIN_RESPONSE, data);

                        Log.d(TAG, "User JWT: " + JsonDao.getUserAccessToken());

                        String jidName = username.split("@")[0] + GOOGLE_REG_PASSWORD_SECRET;
                        String password = Base64.encodeToString(jidName.getBytes(), Base64.NO_WRAP);

                        Log.i(TAG, "password: " + password);

                        if (password != null) {
                            openConnectActivity(username, password);
                        }

                    }

                } else if (response.code() == HttpsURLConnection.HTTP_INTERNAL_ERROR) {
                    // Login Failed
                    Snackbar.make(binding.progress, "Login Failed", BaseTransientBottomBar.LENGTH_LONG).show();
                }
            }


            @Override
            public void onFailure(Call<GeneralResponse> call, Throwable t) {
                Log.d(TAG, "onFailure: " + t.getMessage());
                binding.progress.hide();
            }
        });
    }

    private void openConnectActivity(String username, String password) {
        Map<String, String> values = new HashMap<>();
        values.put(KEY_USERNAME, username);
        values.put(KEY_PASSWORD, password);

        Intent intent = new Intent(activity, ConnectActivity.class);
        intent.putExtra("init", true);
        intent.putExtra(EXTRA_NEEDS_FOREGROUND_SERVICE, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        intent.putExtra(KEY_USERNAME, username);
        intent.putExtra(KEY_PASSWORD, password);
        runOnUiThread(() -> {
            startActivity(intent);
            overridePendingTransition(R.anim.left_in, R.anim.left_out);
        });
    }

    /**
     * Jabber Login
     */
    private void jabberLogin(String username, String password) {
        if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
            String hostname = CharMatcher.whitespace().removeFrom(BuildConfig.HOSTNAME);
            Jid jid = Jid.ofEscaped(username.split("@")[0] + "@" + hostname);

            Log.i(TAG, "jid: " + jid.asBareJid());
            Log.i(TAG, "hostname: " + hostname);

            Account mAccount = new Account(jid.asBareJid(), password);
            mAccount.setPort(5222);
            mAccount.setHostname(hostname);
            mAccount.setOption(Account.OPTION_USETLS, true);
            mAccount.setOption(Account.OPTION_USECOMPRESSION, true);
            mAccount.setOption(Account.OPTION_REGISTER, false);
            //xmppConnectionService.createAccount(mAccount);

            //enableViews();
            binding.progress.hide();
            new GeneralUtils(context).openActivity(activity, ConversationsActivity.class, null, true);
        }
    }
}
