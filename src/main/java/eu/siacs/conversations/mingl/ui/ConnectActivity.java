package eu.siacs.conversations.mingl.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.security.KeyChainAliasCallback;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.common.base.CharMatcher;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityConnectBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.PresenceTemplate;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.ui.ImportBackupActivity;
import eu.siacs.conversations.ui.PublishProfilePictureActivity;
import eu.siacs.conversations.ui.StartConversationActivity;
import eu.siacs.conversations.ui.UiCallback;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.ui.util.SoftKeyboardUtils;
import eu.siacs.conversations.utils.MenuDoubleTabUtil;
import eu.siacs.conversations.utils.SignupUtils;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.pep.Avatar;
import me.drakeet.support.toast.ToastCompat;

import static eu.siacs.conversations.mingl.utils.Constants.KEY_PASSWORD;
import static eu.siacs.conversations.mingl.utils.Constants.KEY_USERNAME;
import static eu.siacs.conversations.utils.PermissionUtils.allGranted;

public class ConnectActivity extends XmppActivity implements OnAccountUpdate, KeyChainAliasCallback {

    public static final String EXTRA_OPENED_FROM_NOTIFICATION = "opened_from_notification";

    private static final int REQUEST_DATA_SAVER = 0xf244;
    private static final int REQUEST_CHANGE_STATUS = 0xee11;
    private static final int REQUEST_ORBOT = 0xff22;
    private static final int REQUEST_IMPORT_BACKUP = 0x63fb;
    private static final String TAG = ConnectActivity.class.getSimpleName();
    private final AtomicBoolean mPendingReconnect = new AtomicBoolean(false);
    private final AtomicBoolean redirectInProgress = new AtomicBoolean(false);
    private final PendingItem<PresenceTemplate> mPendingPresenceTemplate = new PendingItem<>();

    String password;
    String hostname;
    int numericPort = 5222;

    private AlertDialog mCaptchaDialog = null;
    private Jid jidToConnect;
    private boolean mInitMode = false;
    private boolean mExisting = false;
    private Boolean mForceRegister = null;
    private boolean mUsernameMode = Config.DOMAIN_LOCK != null;
    private boolean mShowOptions = false;
    private boolean useOwnProvider = false;
    private boolean register = false;
    private Account mAccount;

    private boolean mFetchingAvatar = false;
    private String mSavedInstanceAccount;
    private boolean mSavedInstanceInit = false;
    private XmppUri pendingUri = null;
    private boolean mUseTor;
    private ActivityConnectBinding binding;
    Timer pollingTimer;


    private void connect(){
        final boolean wasDisabled = mAccount != null && mAccount.getStatus() == Account.State.DISABLED;

        Log.i(TAG, "connect");
        runOnUiThread(() -> binding.progress.show());

        if (mInitMode && mAccount != null) {
            mAccount.setOption(Account.OPTION_DISABLED, false);
        }
        if (mAccount != null && mAccount.getStatus() == Account.State.DISABLED) {
            mAccount.setOption(Account.OPTION_DISABLED, false);
            if (!xmppConnectionService.updateAccount(mAccount)) {
                ToastCompat.makeText(ConnectActivity.this, R.string.unable_to_update_account, ToastCompat.LENGTH_SHORT).show();
            }
            return;
        }

        if (inNeedOfSaslAccept()) {
            Log.i(TAG, "SASL");
            mAccount.setKey(Account.PINNED_MECHANISM_KEY, String.valueOf(-1));
            if (!xmppConnectionService.updateAccount(mAccount)) {
                ToastCompat.makeText(ConnectActivity.this, R.string.unable_to_update_account, ToastCompat.LENGTH_SHORT).show();
            }
            return;
        }

        final Jid jid;
        try {
            if (mUsernameMode) {
                jid = Jid.ofEscaped(jidToConnect, getUserModeDomain(), null);
            } else {
                jid = Jid.ofEscaped(jidToConnect);
            }
        } catch (final NullPointerException | IllegalArgumentException e) {
            return;
        }

        if (jid.getLocal() == null) {
            if (mUsernameMode) {
                Toast.makeText(ConnectActivity.this, getString(R.string.invalid_username), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ConnectActivity.this, getString(R.string.invalid_jid), Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (mAccount != null) {
            mAccount.setJid(jid);
            mAccount.setPort(numericPort);
            mAccount.setHostname(hostname);

            mAccount.setPassword(password);
            mAccount.setOption(Account.OPTION_REGISTER, false);

            if (!xmppConnectionService.updateAccount(mAccount)) {
                ToastCompat.makeText(ConnectActivity.this, R.string.unable_to_update_account, ToastCompat.LENGTH_SHORT).show();
                return;
            }
        } else {
            if (xmppConnectionService.findAccountByJid(jid) != null) {
                ToastCompat.makeText(ConnectActivity.this, R.string.account_already_exists, ToastCompat.LENGTH_SHORT).show();
                return;
            }
            mAccount = new Account(jid.asBareJid(), password);
            mAccount.setPort(numericPort);
            mAccount.setHostname(hostname);
            mAccount.setOption(Account.OPTION_USETLS, true);
            mAccount.setOption(Account.OPTION_USECOMPRESSION, true);
            mAccount.setOption(Account.OPTION_REGISTER, false);
            xmppConnectionService.createAccount(mAccount);
        }

        if (mAccount.isEnabled()
                && !mInitMode) {
            //finish();
            Log.d(TAG, "Account Enabled");
            startPollingAccountStatus();
        }

    }

    private void startPollingAccountStatus(){
        runOnUiThread(() -> binding.progress.show());
        pollingTimer = new Timer();
        pollingTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateStatus();
            }
        }, 100, 1000);
    }

    private void updateStatus() {
        Log.d(TAG, "updateStatus");
        if (mAccount != null){
            switch (mAccount.getStatus()){
                case ONLINE:
                    pollingTimer.cancel();
                    next();
                    break;
                case OFFLINE:
                case DISABLED:
                    this.binding.message.setText(R.string.offline);
                    break;
                case CONNECTING:
                case REGISTRATION_SUCCESSFUL:
                    this.binding.message.setText(R.string.account_status_connecting);
                    break;
            }
        }
        if (mAccount != null
                && (mAccount.getStatus() == Account.State.CONNECTING || mAccount.getStatus() == Account.State.REGISTRATION_SUCCESSFUL || mFetchingAvatar)) {
            this.binding.message.setText(R.string.account_status_connecting);
        } else if (mAccount != null && mAccount.getStatus() == Account.State.DISABLED && !mInitMode) {
            this.binding.message.setText(R.string.connect_gathering_info);
        }
    }

    public void refreshUiReal() {
        if (mAccount != null
                && mAccount.getStatus() != Account.State.ONLINE
                && mFetchingAvatar) {
            Intent intent = new Intent(this, StartConversationActivity.class);
            StartConversationActivity.addInviteUri(intent, getIntent());
            startActivity(intent);
            overridePendingTransition(R.anim.left_in, R.anim.left_out);
            finish();
        } else if (mInitMode && mAccount != null && mAccount.getStatus() == Account.State.ONLINE) {
            runOnUiThread(this::next);
        }

    }

    private void next() {

        boolean isConversationsListEmpty = xmppConnectionService.isConversationsListEmpty(null);
        if (isConversationsListEmpty && redirectInProgress.compareAndSet(false, true)) {
            final Intent intent = SignupUtils.getRedirectionIntent(this);
            runOnUiThread(() -> {
                startActivity(intent);
                overridePendingTransition(R.anim.left_in, R.anim.left_out);
            });
            finish();
        }

//        if (redirectInProgress.compareAndSet(false, true)) {
//            Intent intent = new Intent(this, EnterNameActivity.class);
//            intent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().asBareJid().toString());
//            startActivity(intent);
//            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
//            finish();
//        }
    }

    @Override
    public boolean onNavigateUp() {
        deleteAccountAndReturnIfNecessary();
        return super.onNavigateUp();
    }

    @Override
    public void onBackPressed() {
        deleteAccountAndReturnIfNecessary();
        super.onBackPressed();
    }

    private void deleteAccountAndReturnIfNecessary() {
        if (mInitMode && mAccount != null && !mAccount.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY)) {
            xmppConnectionService.deleteAccount(mAccount);
        }

        final boolean magicCreate = mAccount != null && mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE) && !mAccount.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY);
        final Jid jid = mAccount == null ? null : mAccount.getJid();

        if (SignupUtils.isSupportTokenRegistry() && jid != null && magicCreate && !jid.getDomain().equals(Config.MAGIC_CREATE_DOMAIN)) {
            final Jid preset;
            if (mAccount.isOptionSet(Account.OPTION_FIXED_USERNAME)) {
                preset = jid.asBareJid();
            } else {
                preset = jid.getDomain();
            }
            final Intent intent = SignupUtils.getTokenRegistrationIntent(this, preset, mAccount.getKey(Account.PRE_AUTH_REGISTRATION_TOKEN));
            StartConversationActivity.addInviteUri(intent, getIntent());
            startActivity(intent);
            return;
        }

        final List<Account> accounts = xmppConnectionService == null ? null : xmppConnectionService.getAccounts();
        if (accounts != null && accounts.size() == 0 && Config.MAGIC_CREATE_DOMAIN != null) {
            Intent intent = SignupUtils.getSignUpIntent(this, mForceRegister != null && mForceRegister);
            StartConversationActivity.addInviteUri(intent, getIntent());
            startActivity(intent);
            overridePendingTransition(R.anim.left_in, R.anim.left_out);
        }
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }

    protected void finishInitialSetup(final Avatar avatar) {
        runOnUiThread(() -> {
            SoftKeyboardUtils.hideSoftKeyboard(ConnectActivity.this);
            final Intent intent;
            final XmppConnection connection = mAccount.getXmppConnection();
            final boolean wasFirstAccount = xmppConnectionService != null && xmppConnectionService.getAccounts().size() == 1;
            if (avatar != null || (connection != null && !connection.getFeatures().pep())) {
                intent = new Intent(getApplicationContext(), StartConversationActivity.class);
                if (wasFirstAccount) {
                    intent.putExtra("init", true);
                }
                intent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().asBareJid().toEscapedString());
            } else {
                intent = new Intent(getApplicationContext(), PublishProfilePictureActivity.class);
                intent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().asBareJid().toEscapedString());
                intent.putExtra("setup", true);
            }
            if (wasFirstAccount) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            }
            StartConversationActivity.addInviteUri(intent, getIntent());
            startActivity(intent);
            overridePendingTransition(R.anim.left_in, R.anim.left_out);
            finish();
        });
    }


//    @Override
//    protected void processFingerprintVerification(XmppUri uri) {
//        processFingerprintVerification(uri, true);
//    }

    protected void processFingerprintVerification(XmppUri uri, boolean showWarningToast) {
        if (mAccount != null && mAccount.getJid().asBareJid().equals(uri.getJid()) && uri.hasFingerprints()) {
            if (xmppConnectionService.verifyFingerprints(mAccount, uri.getFingerprints())) {
                ToastCompat.makeText(this, R.string.verified_fingerprints, ToastCompat.LENGTH_SHORT).show();
            }
        } else if (showWarningToast) {
            ToastCompat.makeText(this, R.string.invalid_barcode, ToastCompat.LENGTH_SHORT).show();
        }
    }

    @Override
    protected String getShareableUri(boolean http) {
        if (mAccount != null) {
            return http ? mAccount.getShareableLink() : mAccount.getShareableUri();
        } else {
            return null;
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            this.mSavedInstanceAccount = savedInstanceState.getString("account");
            this.mSavedInstanceInit = savedInstanceState.getBoolean("initMode", false);
        }
//        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_connect);
        binding = ActivityConnectBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        //setSupportActionBar((Toolbar) binding.toolbar);
//        configureActionBar(getSupportActionBar());
//        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);

        runOnUiThread(() -> binding.progress.hide());
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "onStart");
        super.onStart();
        final Intent intent = getIntent();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        } else if (intent != null) {
            try {
                jidToConnect = Jid.ofEscaped(intent.getStringExtra(KEY_USERNAME));
            } catch (final IllegalArgumentException | NullPointerException ignored) {
                this.jidToConnect = null;
            }

            if (jidToConnect != null && intent.getData() != null && intent.getBooleanExtra("scanned", false)) {
                final XmppUri uri = new XmppUri(intent.getData());
                if (xmppConnectionServiceBound) {
                    processFingerprintVerification(uri, false);
                } else {
                    this.pendingUri = uri;
                }
            }

            String username = intent.getStringExtra(KEY_USERNAME);
            password = intent.getStringExtra(KEY_PASSWORD);

            String hostname = CharMatcher.whitespace().removeFrom(BuildConfig.HOSTNAME);
            jidToConnect = Jid.ofEscaped(username.split("@")[0] + "@" + hostname);

            //boolean openedFromNotification = intent.getBooleanExtra(EXTRA_OPENED_FROM_NOTIFICATION, false);
        }
    }

    @Override
    public void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        Log.i(TAG, "onNewIntent");

        if (intent != null && intent.getData() != null) {
            final XmppUri uri = new XmppUri(intent.getData());
            if (xmppConnectionServiceBound) {
                processFingerprintVerification(uri, false);
            } else {
                this.pendingUri = uri;
            }
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        if (mAccount != null) {
            savedInstanceState.putString("account", mAccount.getJid().asBareJid().toEscapedString());
            savedInstanceState.putBoolean("existing", mExisting);
            savedInstanceState.putBoolean("initMode", mInitMode);
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    protected void onBackendConnected() {
        boolean init = true;
        Log.d(TAG, "ConnectActivity onBackendConnected(): setIsInForeground = true");
        xmppConnectionService.getNotificationService().setIsInForeground(true);

        if (mSavedInstanceAccount != null) {
            try {
                this.mAccount = xmppConnectionService.findAccountByJid(Jid.ofEscaped(mSavedInstanceAccount));
                this.mInitMode = mSavedInstanceInit;
                init = false;
            } catch (IllegalArgumentException e) {
                this.mAccount = null;
            }

        } else if (this.jidToConnect != null) {
            this.mAccount = xmppConnectionService.findAccountByJid(jidToConnect);
        }

        if (mAccount != null) {
            Log.i(TAG, "mAccount: "+ mAccount.toString());
            this.mInitMode |= this.mAccount.isOptionSet(Account.OPTION_REGISTER);
            this.mUsernameMode |= mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE) && mAccount.isOptionSet(Account.OPTION_REGISTER) && !useOwnProvider;
//            if (mPendingFingerprintVerificationUri != null) {
//                processFingerprintVerification(mPendingFingerprintVerificationUri, false);
//                mPendingFingerprintVerificationUri = null;
//            }
        }

        if (pendingUri != null) {
            processFingerprintVerification(pendingUri, false);
            pendingUri = null;
        }
        runOnUiThread(() -> binding.progress.show());
        connect();
    }

    private String getUserModeDomain() {
        if (mAccount != null && mAccount.getJid().getDomain() != null) {
            return mAccount.getServer();
        } else {
            return Config.DOMAIN_LOCK;
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        if (item.getItemId() == android.R.id.home) {
            deleteAccountAndReturnIfNecessary();
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean inNeedOfSaslAccept() {
        return mAccount != null && mAccount.getLastErrorStatus() == Account.State.DOWNGRADE_ATTACK && mAccount.getKeyAsInt(Account.PINNED_MECHANISM_KEY, -1) >= 0;
    }

    private void generateSignature(Intent intent, PresenceTemplate template) {
        xmppConnectionService.getPgpEngine().generateSignature(intent, mAccount, template.getStatusMessage(), new UiCallback<String>() {
            @Override
            public void success(String signature) {
                xmppConnectionService.changeStatus(mAccount, template, signature);
            }

            @Override
            public void error(int errorCode, String object) {
                Log.d(Config.LOGTAG, mAccount.getJid().asBareJid() + ": error generating signature. Code: " + errorCode + " Object: " + object);
                xmppConnectionService.changeStatus(mAccount, template, null);
            }

            @Override
            public void userInputRequired(PendingIntent pi, String object) {
                mPendingPresenceTemplate.push(template);
                try {
                    startIntentSenderForResult(pi.getIntentSender(), REQUEST_CHANGE_STATUS, null, 0, 0, 0);
                } catch (final IntentSender.SendIntentException ignored) {
                }
            }
        });
    }

    @Override
    public void alias(String alias) {
        if (alias != null) {
            xmppConnectionService.updateKeyInAccount(mAccount, alias);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0) {
            if (allGranted(grantResults)) {
                switch (requestCode) {
                    case REQUEST_IMPORT_BACKUP:
                        startActivity(new Intent(this, ImportBackupActivity.class));
                        break;
                }
            } else {
                ToastCompat.makeText(this, R.string.no_storage_permission, ToastCompat.LENGTH_SHORT).show();
            }
        }
    }
}
