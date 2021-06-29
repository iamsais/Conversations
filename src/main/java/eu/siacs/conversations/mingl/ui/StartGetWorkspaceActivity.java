package eu.siacs.conversations.mingl.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.base.CharMatcher;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityStartGetWorkspaceBinding;
import eu.siacs.conversations.mingl.api.ApiServiceController;
import eu.siacs.conversations.mingl.api.OrgApi;
import eu.siacs.conversations.mingl.api.UsersApi;
import eu.siacs.conversations.mingl.api.responses.GeneralResponse;
import eu.siacs.conversations.mingl.models.User;
import eu.siacs.conversations.mingl.models.dao.JsonDao;
import eu.siacs.conversations.mingl.utils.Constants;
import eu.siacs.conversations.mingl.utils.GeneralUtils;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.WelcomeActivity;
import eu.siacs.conversations.ui.XmppActivity;
import lombok.SneakyThrows;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class StartGetWorkspaceActivity extends XmppActivity {

    private static final String TAG = StartGetWorkspaceActivity.class.getSimpleName();
//    public XmppConnectionService xmppConnectionService;
//    public boolean xmppConnectionServiceBound = false;
//    protected ServiceConnection mConnection = new ServiceConnection() {
//
//        @Override
//        public void onServiceConnected(ComponentName className, IBinder service) {
//            XmppConnectionService.XmppConnectionBinder binder = (XmppConnectionService.XmppConnectionBinder) service;
//            xmppConnectionService = binder.getService();
//            xmppConnectionServiceBound = true;
//        }
//
//        @Override
//        public void onServiceDisconnected(ComponentName arg0) {
//            xmppConnectionServiceBound = false;
//        }
//    };
    ActivityStartGetWorkspaceBinding binding;
    Context context;
    Activity activity;
    boolean isGetStarted = false;
    String username, idToken;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityStartGetWorkspaceBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        GeneralUtils.context = this;
        context = this;
        activity = this;

        if (getIntent().hasExtra(Constants.KEY_GET_STARTED)
                && getIntent().getStringExtra(Constants.KEY_GET_STARTED).equalsIgnoreCase(Constants.KEY_GET_STARTED)) {
            isGetStarted = true;
        }

        if (getIntent().hasExtra(Constants.KEY_USERNAME)) {
            username = getIntent().getStringExtra(Constants.KEY_USERNAME);
        }

        if (getIntent().hasExtra(Constants.KEY_GOOGLE_ID_TOKEN)) {
            idToken = getIntent().getStringExtra(Constants.KEY_GOOGLE_ID_TOKEN);
        }


        // Show login or register based on isGetStarted flag
        if (isGetStarted) {
            binding.llLogin.setVisibility(View.VISIBLE);
            binding.llRegister.setVisibility(View.GONE);
        } else {
            binding.llLogin.setVisibility(View.GONE);
            binding.llRegister.setVisibility(View.VISIBLE);
        }

        binding.next.setOnClickListener(v -> onSubmit());
        binding.register.setOnClickListener(v -> new GeneralUtils(this)
                .openActivity(this, WelcomeActivity.class, null, true));
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
//        if (!xmppConnectionServiceBound) {
//            connectToBackend();
//        }
    }

    @Override
    protected void onStop() {
        super.onStop();
//        if (xmppConnectionServiceBound) {
//            unbindService(mConnection);
//            xmppConnectionServiceBound = false;
//        }
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.progress.hide();
    }

//    public void connectToBackend() {
//        Intent intent = new Intent(this, XmppConnectionService.class);
//        intent.setAction("ui");
//        try {
//            startService(intent);
//        } catch (IllegalStateException e) {
//            Log.w(TAG, "unable to start service from " + getClass().getSimpleName());
//        }
//        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
//    }

    private void disableViews() {
        binding.workplace.setEnabled(false);
        binding.next.setEnabled(false);
    }

    private void enableViews() {
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
            workspaceValidCheck(workplace);
        }else{
            enableViews();
        }
    }

    private void workspaceValidCheck(String key) {
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
                            if (idToken == null) {
                                values.put(Constants.KEY_GET_STARTED, Constants.KEY_GET_STARTED);
                                values.put(Constants.KEY_WORKSPACE, key);
                                values.put(Constants.KEY_USERNAME, username);
                                new GeneralUtils(context).openActivity(activity,StartGetPasswordActivity.class, values, true);
                            } else {
                                register(username, idToken);
                            }
                        } else { // Sign-in
                            values.put(Constants.KEY_WORKSPACE, key);
                            new GeneralUtils(context).openActivity(activity, StartGetEmailActivity.class, values, true);
                        }

                    } else if (generalResponse.getStatusCodeValue() == HttpsURLConnection.HTTP_NO_CONTENT) {
                        // Not Available
                        binding.workplace.setError("Enter a valid workspace");
                        enableViews();
                        binding.progress.hide();
                    }

                } else {
                    Snackbar.make(binding.progress, "Something went wrong!", BaseTransientBottomBar.LENGTH_LONG).show();
                }

                enableViews();
                binding.progress.hide();
            }

            @Override
            public void onFailure(Call<GeneralResponse> call, Throwable t) {
                Snackbar.make(binding.progress, "Something went wrong!", BaseTransientBottomBar.LENGTH_LONG).show();
                enableViews();
                binding.progress.hide();
            }
        });

    }


    private void login(String username, String idToken) {

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

                        String jidName=username.split("@")[0] + Constants.GOOGLE_REG_PASSWORD_SECRET;
                        String password = Base64.encodeToString(jidName.getBytes(), Base64.NO_WRAP);

                        if (password != null)
                            jabberLogin(username, password);

                        new GeneralUtils(context).openActivity(activity, ConversationsActivity.class, null, true);
                    }

                } else if (response.code() == HttpsURLConnection.HTTP_INTERNAL_ERROR) {
                    // Login Failed
                    Snackbar.make(binding.progress, "Login Failed", BaseTransientBottomBar.LENGTH_LONG).show();
                }
            }


            @Override
            public void onFailure(Call<GeneralResponse> call, Throwable t) {
                binding.progress.hide();
            }
        });
    }

    /**
     * Jabber Login
     */
    private void jabberLogin(String username, String password) {
        if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
            String hostname = CharMatcher.whitespace().removeFrom(BuildConfig.HOSTNAME);
//            Jid jid = Jid.ofEscaped(username.split("@")[0] + "@" + hostname);
//            Account mAccount = new Account(jid.asBareJid(), password);
//            mAccount.setPort(5222);
//            mAccount.setHostname(hostname);
//            mAccount.setOption(Account.OPTION_USETLS, true);
//            mAccount.setOption(Account.OPTION_USECOMPRESSION, true);
//            mAccount.setOption(Account.OPTION_REGISTER, false);
//            xmppConnectionService.createAccount(mAccount);

            enableViews();
            binding.progress.hide();
        }
    }

    private void register(String username, String idToken) {
        Log.d(TAG, "Register");

        User user = new User();
        user.setEmail(username);
        user.setUserName(username);

        String[] firstName = username.split("@");
        user.setFirstName(firstName[0]);
        String jidName=firstName[0] + Constants.GOOGLE_REG_PASSWORD_SECRET;

        String password = Base64.encodeToString(jidName.getBytes(), Base64.NO_WRAP);
        Log.i(TAG, "Password: " + password);
        user.setPassword(password);

        user.setOrganizations(Arrays.asList(JsonDao.getOrganizationId()));

        UsersApi usersApi = ApiServiceController.createService(UsersApi.class, BuildConfig.BASE_URL, JsonDao.getClientAccessToken());
        Call<GeneralResponse> call = usersApi.register(user);
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
                        Log.d(TAG, "Response Data: " + data);

                        // Login after registration
                        login(username, idToken);

                    } else if (generalResponse.getStatusCodeValue() == HttpsURLConnection.HTTP_NO_CONTENT) {
                        // Not Available
                        Snackbar.make(binding.progress, "User Already Exist!", BaseTransientBottomBar.LENGTH_LONG).show();
                    } else if (generalResponse.getStatusCodeValue() == HttpsURLConnection.HTTP_INTERNAL_ERROR) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "Response Message: " + generalResponse.getResultBody().getMessage());
                                enableViews();
                                binding.progress.hide();
                                Snackbar.make(binding.progress, "Failed! Try again later", BaseTransientBottomBar.LENGTH_LONG)
                                        .setBackgroundTint(getResources().getColor(R.color.red800))
                                        .setTextColor(getResources().getColor(R.color.white))
                                        .show();
                            }
                        });
                    }
                } else {
                    Snackbar.make(binding.progress, "Something went wrong!", BaseTransientBottomBar.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<GeneralResponse> call, Throwable t) {
                Snackbar.make(binding.progress, "Something went wrong!", BaseTransientBottomBar.LENGTH_LONG).show();
                enableViews();
                binding.progress.hide();
            }
        });

    }
}
