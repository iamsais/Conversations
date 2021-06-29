package eu.siacs.conversations.mingl.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityStartGetPasswordBinding;
import eu.siacs.conversations.mingl.api.ApiServiceController;
import eu.siacs.conversations.mingl.api.UsersApi;
import eu.siacs.conversations.mingl.api.requests.AuthenticationRequest;
import eu.siacs.conversations.mingl.api.responses.GeneralResponse;
import eu.siacs.conversations.mingl.models.User;
import eu.siacs.conversations.mingl.models.dao.JsonDao;
import eu.siacs.conversations.mingl.utils.Constants;
import eu.siacs.conversations.mingl.utils.GeneralUtils;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.base.CharMatcher;

import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;

import eu.siacs.conversations.ui.ConversationsActivity;
import lombok.SneakyThrows;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static eu.siacs.conversations.mingl.utils.Constants.KEY_GET_STARTED;
import static eu.siacs.conversations.mingl.utils.Constants.KEY_USERNAME;
import static eu.siacs.conversations.mingl.utils.Constants.KEY_USER_PROFILE_URI;
import static eu.siacs.conversations.mingl.utils.Constants.KEY_WORKSPACE;


public class StartGetPasswordActivity extends AppCompatActivity {

    private static final String TAG = StartGetPasswordActivity.class.getSimpleName();
    ActivityStartGetPasswordBinding binding;

    Context context;
    Activity activity;
    boolean isGetStarted = false;
    String workspace, username, photoUrl;
//    public XmppConnectionService xmppConnectionService;
//    public boolean xmppConnectionServiceBound = false;
//
//    protected ServiceConnection mConnection = new ServiceConnection() {
//
//        @Override
//        public void onServiceConnected(ComponentName className, IBinder service) {
//            XmppConnectionService.XmppConnectionBinder binder = (XmppConnectionService.XmppConnectionBinder) service;
//            xmppConnectionService = binder.getService();
//            xmppConnectionServiceBound = true;
//            //registerListeners();
//            //onBackendConnected();
//        }
//
//        @Override
//        public void onServiceDisconnected(ComponentName arg0) {
//            xmppConnectionServiceBound = false;
//        }
//    };

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

        if (getIntent().hasExtra(KEY_WORKSPACE)) {
            workspace = getIntent().getStringExtra(KEY_WORKSPACE);
        }

        if (getIntent().hasExtra(KEY_USER_PROFILE_URI)) {
            photoUrl = getIntent().getStringExtra(KEY_USER_PROFILE_URI);
        }

        if (getIntent().hasExtra(KEY_USERNAME)) {
            username = getIntent().getStringExtra(KEY_USERNAME);
        }

        // Show login or register based on isGetStarted flag
        if(isGetStarted){
            binding.next.setText(getString(R.string.register));
        }else{
            binding.next.setText(getString(R.string.login));
        }

        binding.next.setOnClickListener(v -> onSubmit());
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.progress.hide();
    }

    @Override
    protected void onStart() {
        super.onStart();
//        if (!xmppConnectionServiceBound) {
//                connectToBackend();
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

//    public void connectToBackend() {
//        Intent intent = new Intent(this, XmppConnectionService.class);
//        intent.setAction("ui");
//        try {
//            startService(intent);
//        } catch (IllegalStateException e) {
//            Log.w(Config.LOGTAG, "unable to start service from " + getClass().getSimpleName());
//        }
//        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
//    }

    private void disableViews() {
        binding.password.setEnabled(false);
        binding.next.setEnabled(false);
    }

    private void enableViews() {
        binding.password.setEnabled(true);
        binding.next.setEnabled(true);
    }

    private void onSubmit() {
        GeneralUtils.hideSoftKeyboard(this);
        disableViews();

        String password = binding.password.getEditText().getText().toString().trim();

        if (TextUtils.isEmpty(password)) {
            binding.password.setError("Mandatory");
        } else if (!TextUtils.isEmpty(password)) {
            binding.password.setError(null);
        }

        if (binding.password.getError() == null
                && !TextUtils.isEmpty(workspace)
                && !TextUtils.isEmpty(username)) {
            binding.progress.show();
            if (isGetStarted)
                register(username, password, workspace);
            else
                login(username, password, workspace);
        }
    }

    private void login(String username, String password, String workspace) {

        AuthenticationRequest authenticationRequest = new AuthenticationRequest();
        authenticationRequest.setUsername(username);
        authenticationRequest.setPassword(password);

        UsersApi usersApi = ApiServiceController.createService(UsersApi.class, BuildConfig.BASE_URL, JsonDao.getClientAccessToken());
        Call<GeneralResponse> call = usersApi.login(authenticationRequest);
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
                        jabberLogin(username, password);

                        new GeneralUtils(context).openActivity(activity, ConversationsActivity.class, null, true);
                    }

                } else if (response.code() == HttpsURLConnection.HTTP_INTERNAL_ERROR) {
                    // Login Failed
                    Snackbar.make(binding.progress, "Login Failed", BaseTransientBottomBar.LENGTH_LONG).show();
                }

                binding.progress.hide();
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
//            Jid jid = Jid.ofEscaped(username.split("@")[0]+"@"+hostname);
//            Account mAccount = new Account(jid.asBareJid(), password);
//            mAccount.setPort(5222);
//            mAccount.setHostname(hostname);
//            mAccount.setOption(Account.OPTION_USETLS, true);
//            mAccount.setOption(Account.OPTION_USECOMPRESSION, true);
//            mAccount.setOption(Account.OPTION_REGISTER, false);
//            xmppConnectionService.createAccount(mAccount);
        }
    }

    private void register(String username, String password, String workspace) {
        Log.d(TAG, "tokenValidCheck");

        User user = new User();
        user.setEmail(username);
        user.setUserName(username);
        user.setPassword(password);

        String[] firstName = username.split("@");
        user.setFirstName(firstName[0]);

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
                        login(username, password, workspace);

                    } else if (generalResponse.getStatusCodeValue() == HttpsURLConnection.HTTP_NO_CONTENT) {
                        // Not Available
                        Snackbar.make(binding.progress, "User Already Exist!", BaseTransientBottomBar.LENGTH_LONG).show();
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
}
