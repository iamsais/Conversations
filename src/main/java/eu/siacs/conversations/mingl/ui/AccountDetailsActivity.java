package eu.siacs.conversations.mingl.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.common.base.Optional;

import org.openintents.openpgp.util.OpenPgpUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.databinding.ActivityAccountDetailsBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnRosterUpdate;
import eu.siacs.conversations.ui.BlockContactDialog;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.MediaBrowserActivity;
import eu.siacs.conversations.ui.OmemoActivity;
import eu.siacs.conversations.ui.RtpSessionActivity;
import eu.siacs.conversations.ui.ScanActivity;
import eu.siacs.conversations.ui.SettingsActivity;
import eu.siacs.conversations.ui.adapter.MediaAdapter;
import eu.siacs.conversations.ui.interfaces.OnMediaLoaded;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.GridManager;
import eu.siacs.conversations.ui.util.JidDialog;
import eu.siacs.conversations.ui.util.PresenceSelector;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.EmojiWrapper;
import eu.siacs.conversations.utils.Emoticons;
import eu.siacs.conversations.utils.MenuDoubleTabUtil;
import eu.siacs.conversations.utils.TimeFrameUtils;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.OnKeyStatusUpdated;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.OngoingRtpSession;
import eu.siacs.conversations.xmpp.jingle.RtpCapability;
import me.drakeet.support.toast.ToastCompat;

import static eu.siacs.conversations.ui.ConversationFragment.REQUEST_START_AUDIO_CALL;
import static eu.siacs.conversations.ui.ConversationFragment.REQUEST_START_VIDEO_CALL;


public class AccountDetailsActivity extends OmemoActivity implements OnAccountUpdate, OnRosterUpdate, OnUpdateBlocklist, OnKeyStatusUpdated, OnMediaLoaded {

    public static final String ACTION_VIEW_CONTACT = "view_contact";
    static Boolean isTouched = false;
    private final int REQUEST_SYNC_CONTACTS = 0x28cf;
    OnTouchListener onMuteNotificationTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            isTouched = true;
            return false;
        }
    };
    private ActivityAccountDetailsBinding binding;
    private Contact contact;
    private Conversation mConversation;
    private ConversationFragment mConversationFragment;
    private MediaAdapter mMediaAdapter;
    private boolean mAdvancedMode = false;
    private boolean mIndividualNotifications = false;
    private DialogInterface.OnClickListener removeFromRoster = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            xmppConnectionService.deleteContactOnServer(contact);
        }
    };
    private OnCheckedChangeListener mOnSendCheckedChange = new OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                    xmppConnectionService.stopPresenceUpdatesTo(contact);
                } else {
                    contact.setOption(Contact.Options.PREEMPTIVE_GRANT);
                }
            } else {
                contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
                xmppConnectionService.sendPresencePacket(contact.getAccount(), xmppConnectionService.getPresenceGenerator().stopPresenceUpdatesTo(contact));
            }
        }
    };
    private OnCheckedChangeListener mOnReceiveCheckedChange = new OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                xmppConnectionService.sendPresencePacket(contact.getAccount(),
                        xmppConnectionService.getPresenceGenerator()
                                .requestPresenceUpdatesFrom(contact));
            } else {
                xmppConnectionService.sendPresencePacket(contact.getAccount(),
                        xmppConnectionService.getPresenceGenerator()
                                .stopPresenceUpdatesFrom(contact));
            }
        }
    };
    private Jid accountJid;
    private Jid contactJid;
    private boolean showDynamicTags = false;
    private boolean showLastSeen = false;
    private boolean showInactiveOmemo = false;
    private String messageFingerprint;
    private TextView mNotifyStatusText;
    private OnCheckedChangeListener onMuteNotificationCheckedChangeListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isTouched) {
                isTouched = false;
                if (isChecked) {
                    final AlertDialog.Builder builder1 = new AlertDialog.Builder(AccountDetailsActivity.this);
                    builder1.setTitle(R.string.mute_notification);
                    final int[] durations = getResources().getIntArray(R.array.mute_options_durations);
                    final CharSequence[] labels = new CharSequence[durations.length];
                    for (int i = 0; i < durations.length; ++i) {
                        if (durations[i] == -1) {
                            labels[i] = getString(R.string.until_further_notice);
                        } else {
                            labels[i] = TimeFrameUtils.resolve(AccountDetailsActivity.this, 1000L * durations[i]);
                        }
                    }
                    builder1.setItems(labels, (dialog1, which1) -> {
                        final long till;
                        if (durations[which1] == -1) {
                            till = Long.MAX_VALUE;
                        } else {
                            till = System.currentTimeMillis() + (durations[which1] * 1000);
                        }
                        mConversation.setMutedTill(till);
                        xmppConnectionService.updateConversation(mConversation);
                        populateView();
                    });
                    builder1.create().show();
                } else {
                    mConversation.setMutedTill(0);
                    mConversation.setAttribute(Conversation.ATTRIBUTE_ALWAYS_NOTIFY, true);
                }

                xmppConnectionService.updateConversation(mConversation);
                populateView();
            }
        }
    };
    private OnClickListener mNotifyStatusClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(AccountDetailsActivity.this);
            builder.setTitle(R.string.pref_notification_settings);

            String[] choices = {
                    getString(R.string.notify_on_all_messages),
                    getString(R.string.notify_never)
            };
            final AtomicInteger choice;
            if (mConversation.alwaysNotify()) {
                choice = new AtomicInteger(0);
            } else {
                choice = new AtomicInteger(1);
            }
            builder.setSingleChoiceItems(choices, choice.get(), (dialog, which) -> choice.set(which));
            builder.setNegativeButton(R.string.cancel, null);
            builder.setPositiveButton(R.string.ok, (DialogInterface.OnClickListener) (dialog, which) -> {
                if (choice.get() == 1) {
                    final AlertDialog.Builder builder1 = new AlertDialog.Builder(AccountDetailsActivity.this);
                    builder1.setTitle(R.string.disable_notifications);
                    final int[] durations = getResources().getIntArray(R.array.mute_options_durations);
                    final CharSequence[] labels = new CharSequence[durations.length];
                    for (int i = 0; i < durations.length; ++i) {
                        if (durations[i] == -1) {
                            labels[i] = getString(R.string.until_further_notice);
                        } else {
                            labels[i] = TimeFrameUtils.resolve(AccountDetailsActivity.this, 1000L * durations[i]);
                        }
                    }
                    builder1.setItems(labels, (dialog1, which1) -> {
                        final long till;
                        if (durations[which1] == -1) {
                            till = Long.MAX_VALUE;
                        } else {
                            till = System.currentTimeMillis() + (durations[which1] * 1000);
                        }
                        mConversation.setMutedTill(till);
                        xmppConnectionService.updateConversation(mConversation);
                        populateView();
                    });
                    builder1.create().show();
                } else {
                    mConversation.setMutedTill(0);
                    mConversation.setAttribute(Conversation.ATTRIBUTE_ALWAYS_NOTIFY, String.valueOf(choice.get() == 0));
                }
                xmppConnectionService.updateConversation(mConversation);
                populateView();
            });
            builder.create().show();
        }
    };

    private void checkContactPermissionAndShowAddDialog() {
        if (hasContactsPermission()) {
            showAddToPhoneBookDialog();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_SYNC_CONTACTS);
        }
    }

    private boolean hasContactsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void showAddToPhoneBookDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.action_add_phone_book));
        builder.setMessage(getString(R.string.add_phone_book_text, contact.getJid().toEscapedString()));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.add), (dialog, which) -> {
            final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.setType(Contacts.CONTENT_ITEM_TYPE);
            intent.putExtra(Intents.Insert.IM_HANDLE, contact.getJid().toEscapedString());
            intent.putExtra(Intents.Insert.IM_PROTOCOL, CommonDataKinds.Im.PROTOCOL_JABBER);
            intent.putExtra("finishActivityOnSaveCompleted", true);
            try {
                startActivityForResult(intent, 0);
            } catch (ActivityNotFoundException e) {
                ToastCompat.makeText(AccountDetailsActivity.this, R.string.no_application_found_to_view_contact, ToastCompat.LENGTH_SHORT).show();
            }
        });
        builder.create().show();
    }

    @Override
    public void onRosterUpdate() {
        refreshUi();
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }

    @Override
    public void OnUpdateBlocklist(final Status status) {
        refreshUi();
    }

    @Override
    protected void refreshUiReal() {
        invalidateOptionsMenu();
        populateView();
    }

    @Override
    protected String getShareableUri(boolean http) {
//        if (http) {
//            return Config.inviteUserURL + XmppUri.lameUrlEncode(contact.getJid().asBareJid().toEscapedString());
//        } else {
        return "xmpp:" + contact.getJid().asBareJid().toEscapedString();
//        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityAccountDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Toolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        CollapsingToolbarLayout toolBarLayout = binding.toolbarLayout;
        //toolBarLayout.setTitle(getTitle());

        this.mAdvancedMode = getPreferences().getBoolean("advanced_mode", false);
        showInactiveOmemo = savedInstanceState != null && savedInstanceState.getBoolean("show_inactive_omemo", false);
        if (getIntent().getAction().equals(ACTION_VIEW_CONTACT)) {
            try {
                this.accountJid = Jid.ofEscaped(getIntent().getExtras().getString(EXTRA_ACCOUNT));
            } catch (final IllegalArgumentException ignored) {
            }
            try {
                this.contactJid = Jid.ofEscaped(getIntent().getExtras().getString("contact"));
            } catch (final IllegalArgumentException ignored) {
            }
        }
        this.messageFingerprint = getIntent().getStringExtra("fingerprint");

        binding.showInactiveDevices.setOnClickListener(v -> {
            showInactiveOmemo = !showInactiveOmemo;
            populateView();
        });
        binding.addContactButton.setOnClickListener(v -> showAddToRosterDialog(contact));

        this.binding.notificationStatusButton.setOnTouchListener(onMuteNotificationTouchListener);
        this.binding.notificationStatusButton.setOnCheckedChangeListener(onMuteNotificationCheckedChangeListener);

        this.mNotifyStatusText = findViewById(R.id.notification_status_text);

        mMediaAdapter = new MediaAdapter(this, R.dimen.media_size);
        this.binding.media.setAdapter(mMediaAdapter);
        GridManager.setupLayoutManager(this, this.binding.media, R.dimen.media_size);

        binding.blockContact.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                BlockContactDialog.show(AccountDetailsActivity.this, contact);
            }
        });

        binding.audioCall.setOnClickListener(v -> {
            checkPermissionAndTriggerAudioCall();
        });

        binding.videoCall.setOnClickListener(v -> {
            checkPermissionAndTriggerVideoCall();
        });

        binding.actionMessage.setOnClickListener(v -> {
            //checkPermissionAndTriggerVideoCall();
        });


        binding.audioCall.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.primaryColor), PorterDuff.Mode.SRC_IN);
        binding.videoCall.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.primaryColor), PorterDuff.Mode.SRC_IN);
    }

    private void blockViewRefresh() {
        final XmppConnection connection = contact.getAccount().getXmppConnection();
        if (connection != null && connection.getFeatures().blocking()) {
            if (this.contact.isBlocked()) {
                binding.blockContactText.setText(getString(R.string.unblock_contact));
                binding.blockContactText.setTextColor(ContextCompat.getColor(getBaseContext(), R.color.grey500));
                binding.blockContactIcon.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.grey500), PorterDuff.Mode.SRC_IN);
            } else {
                binding.blockContactText.setText(getString(R.string.block_contact));
                binding.blockContactText.setTextColor(ContextCompat.getColor(getBaseContext(), R.color.red800));
                binding.blockContactIcon.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.red800), PorterDuff.Mode.SRC_IN);
            }
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        savedInstanceState.putBoolean("show_inactive_omemo", showInactiveOmemo);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        } else {
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            this.showDynamicTags = preferences.getBoolean(SettingsActivity.SHOW_DYNAMIC_TAGS, getResources().getBoolean(R.bool.show_dynamic_tags));
            this.showLastSeen = preferences.getBoolean("last_activity", getResources().getBoolean(R.bool.last_activity));
        }
        binding.mediaWrapper.setVisibility(Compatibility.hasStoragePermission(this) ? View.VISIBLE : View.GONE);
        mMediaAdapter.setAttachments(Collections.emptyList());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0)
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (requestCode == REQUEST_SYNC_CONTACTS && xmppConnectionServiceBound) {
                    showAddToPhoneBookDialog();
                    xmppConnectionService.loadPhoneContacts();
                    xmppConnectionService.startContactObserver();
                }
            }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem menuItem) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton(getString(R.string.cancel), null);
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                break;

            case R.id.action_share_http:
                shareLink(true);
                break;
            case R.id.action_share_uri:
                shareLink(false);
                break;
            case R.id.action_edit_contact:
                editContact();
                break;
//            case R.id.action_block:
//                BlockContactDialog.show(this, contact);
//                break;
//            case R.id.action_unblock:
//                BlockContactDialog.show(this, contact);
//                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void editContact() {
        Uri systemAccount = contact.getSystemAccount();
        if (systemAccount == null) {
            quickEdit(contact.getServerName(), R.string.contact_name, value -> {
                contact.setServerName(value);
                AccountDetailsActivity.this.xmppConnectionService.pushContactToServer(contact);
                populateView();
                return null;
            }, true);
        } else {
            Intent intent = new Intent(Intent.ACTION_EDIT);
            intent.setDataAndType(systemAccount, Contacts.CONTENT_ITEM_TYPE);
            intent.putExtra("finishActivityOnSaveCompleted", true);
            try {
                startActivity(intent);
                overridePendingTransition(R.anim.left_in, R.anim.left_out);
            } catch (ActivityNotFoundException e) {
                ToastCompat.makeText(AccountDetailsActivity.this, R.string.no_application_found_to_view_contact, ToastCompat.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        if (mConversation == null) {
            return true;
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.contact_details, menu);
        final MenuItem menuAudioCall = menu.findItem(R.id.action_audio_call);
        final MenuItem menuOngoingCall = menu.findItem(R.id.action_ongoing_call);
        final MenuItem menuVideoCall = menu.findItem(R.id.action_video_call);

        final MenuItem menuSecurity = menu.findItem(R.id.action_security);
        if (menuSecurity != null)
            menuSecurity.setVisible(false);

        if (contact == null) {
            return true;
        }
        if (this.mConversation != null) {
            if (xmppConnectionService.hasInternetConnection()) {
                menuAudioCall.setVisible(false);
                menuVideoCall.setVisible(false);
                menuOngoingCall.setVisible(false);
            } else {
                final Optional<OngoingRtpSession> ongoingRtpSession = xmppConnectionService.getJingleConnectionManager().getOngoingRtpConnection(this.mConversation.getContact());
                if (ongoingRtpSession.isPresent()) {
                    menuOngoingCall.setVisible(true);
                    menuAudioCall.setVisible(false);
                    menuVideoCall.setVisible(false);
                } else {
                    menuOngoingCall.setVisible(false);
                    final RtpCapability.Capability rtpCapability = RtpCapability.check(this.mConversation.getContact());
                    menuAudioCall.setVisible(rtpCapability != RtpCapability.Capability.NONE);
                    menuVideoCall.setVisible(rtpCapability == RtpCapability.Capability.VIDEO);
                }
            }
        }

        return super.onCreateOptionsMenu(menu);
    }

    private void populateView() {
        if (contact == null) {
            return;
        }

        if (mConversation != null) {
            long mutedTill = mConversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0);

            if (mutedTill == Long.MAX_VALUE) {
                this.binding.notificationStatusButton.setChecked(true);
            } else if (System.currentTimeMillis() < mutedTill) {
                this.binding.notificationStatusButton.setChecked(true);
            } else {
                this.binding.notificationStatusButton.setChecked(false);
            }
        }

        if (getSupportActionBar() != null) {
            final ActionBar ab = getSupportActionBar();
            if (ab != null) {
                ab.setCustomView(R.layout.ab_title);
                ab.setDisplayShowCustomEnabled(true);
                TextView abtitle = findViewById(android.R.id.text1);
                TextView absubtitle = findViewById(android.R.id.text2);
                abtitle.setText(R.string.contact_details);
                abtitle.setSelected(true);
                abtitle.setClickable(false);
                absubtitle.setVisibility(View.GONE);
                absubtitle.setClickable(false);
            }
        }

        invalidateOptionsMenu();
        binding.toolbarLayout.setTitle(contact.getDisplayName());

        if (contact.showInRoster()) {
            binding.detailsSendPresence.setVisibility(View.VISIBLE);
            binding.detailsSendPresence.setOnCheckedChangeListener(null);
            binding.detailsReceivePresence.setVisibility(View.VISIBLE);
            binding.detailsReceivePresence.setOnCheckedChangeListener(null);
            binding.addContactButton.setVisibility(View.VISIBLE);
            binding.addContactButton.setText(getString(R.string.action_delete_contact));
            binding.addContactButton.getBackground().setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.orange500), PorterDuff.Mode.MULTIPLY);
            //binding.addContactButton.setTextColor(getWarningTextColor());
            binding.addContactButton.setOnClickListener(view -> {
                final AlertDialog.Builder deleteFromRosterDialog = new AlertDialog.Builder(AccountDetailsActivity.this);
                deleteFromRosterDialog.setNegativeButton(getString(R.string.cancel), null)
                        .setTitle(getString(R.string.action_delete_contact))
                        .setMessage(JidDialog.style(this, R.string.remove_contact_text, contact.getJid().toEscapedString()))
                        .setPositiveButton(getString(R.string.delete), removeFromRoster).create().show();
            });
            List<String> statusMessages = contact.getPresences().getStatusMessages();
            if (statusMessages.size() == 0) {
                binding.statusMessage.setVisibility(View.GONE);
                binding.statusHeading.setVisibility(View.GONE);
            } else if (statusMessages.size() == 1) {
                final String message = statusMessages.get(0);
                binding.statusMessage.setVisibility(View.VISIBLE);
                binding.statusHeading.setVisibility(View.VISIBLE);
                final Spannable span = new SpannableString(message);
                if (Emoticons.isOnlyEmoji(message)) {
                    span.setSpan(new RelativeSizeSpan(2.0f), 0, message.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                binding.statusMessage.setText(span);
            } else {
                StringBuilder builder = new StringBuilder();
                binding.statusMessage.setVisibility(View.VISIBLE);
                binding.statusHeading.setVisibility(View.VISIBLE);
                int s = statusMessages.size();
                for (int i = 0; i < s; ++i) {
                    builder.append(statusMessages.get(i));
                    if (i < s - 1) {
                        builder.append("\n");
                    }
                }
                binding.statusMessage.setText(EmojiWrapper.transform(builder));
            }
            if (contact.getOption(Contact.Options.FROM)) {
                binding.detailsSendPresence.setText(R.string.send_presence_updates);
                binding.detailsSendPresence.setChecked(true);
            } else if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                binding.detailsSendPresence.setChecked(false);
                binding.detailsSendPresence.setText(R.string.send_presence_updates);
            } else {
                binding.detailsSendPresence.setText(R.string.preemptively_grant);
                if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
                    binding.detailsSendPresence.setChecked(true);
                } else {
                    binding.detailsSendPresence.setChecked(false);
                }
            }
            if (contact.getOption(Contact.Options.TO)) {
                binding.detailsReceivePresence.setText(R.string.receive_presence_updates);
                binding.detailsReceivePresence.setChecked(true);
            } else {
                binding.detailsReceivePresence.setText(R.string.ask_for_presence_updates);
                if (contact.getOption(Contact.Options.ASKING)) {
                    binding.detailsReceivePresence.setChecked(true);
                } else {
                    binding.detailsReceivePresence.setChecked(false);
                }
            }
            if (contact.getAccount().isOnlineAndConnected()) {
                binding.detailsReceivePresence.setEnabled(true);
                binding.detailsSendPresence.setEnabled(true);
            } else {
                binding.detailsReceivePresence.setEnabled(false);
                binding.detailsSendPresence.setEnabled(false);
            }
            binding.detailsSendPresence.setOnCheckedChangeListener(this.mOnSendCheckedChange);
            binding.detailsReceivePresence.setOnCheckedChangeListener(this.mOnReceiveCheckedChange);
        } else {
            binding.addContactButton.setVisibility(View.VISIBLE);
            binding.addContactButton.setText(getString(R.string.add_contact));
            binding.addContactButton.getBackground().clearColorFilter();
            binding.addContactButton.setTextColor(ContextCompat.getColor(getBaseContext(), R.color.black));
            binding.addContactButton.setOnClickListener(view -> showAddToRosterDialog(contact));
            binding.detailsSendPresence.setVisibility(View.GONE);
            binding.detailsReceivePresence.setVisibility(View.GONE);
            binding.statusMessage.setVisibility(View.GONE);
            binding.statusHeading.setVisibility(View.GONE);
        }

//        if (contact.isBlocked() && !this.showDynamicTags) {
//            binding.detailsLastseen.setVisibility(View.VISIBLE);
//            binding.detailsLastseen.setText(R.string.contact_blocked);
//        } else {
//            if (showLastSeen && contact.getLastseen() > 0 && contact.getPresences().allOrNonSupport(Namespace.IDLE)) {
//                binding.detailsLastseen.setVisibility(View.VISIBLE);
//                binding.detailsLastseen.setText(UIHelper.lastseen(getApplicationContext(), contact.isActive(), contact.getLastseen()));
//            } else {
//                binding.detailsLastseen.setVisibility(View.GONE);
//            }
//        }

        String account;
        if (Config.DOMAIN_LOCK != null) {
            account = contact.getAccount().getJid().getEscapedLocal();
        } else {
            account = contact.getAccount().getJid().asBareJid().toEscapedString();
        }

        AvatarWorkerTask.loadAvatar(contact, binding.ivProfilePics, R.dimen.avatar_on_details_screen_size);

        binding.ivProfilePics.setOnClickListener(this::onBadgeClick);
        binding.ivProfilePics.setOnLongClickListener(v -> {
            //ShowAvatarPopup(AccountDetailsActivity.this, contact);
            return true;
        });

        binding.detailsContactKeys.removeAllViews();
        boolean hasKeys = false;
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final AxolotlService axolotlService = contact.getAccount().getAxolotlService();
        if (Config.supportOmemo() && axolotlService != null) {
            final Collection<XmppAxolotlSession> sessions = axolotlService.findSessionsForContact(contact);
            boolean anyActive = false;
            for (XmppAxolotlSession session : sessions) {
                anyActive = session.getTrust().isActive();
                if (anyActive) {
                    break;
                }
            }
            boolean skippedInactive = false;
            boolean showsInactive = false;
            for (final XmppAxolotlSession session : sessions) {
                final FingerprintStatus trust = session.getTrust();
                hasKeys |= !trust.isCompromised();
                if (!trust.isActive() && anyActive) {
                    if (showInactiveOmemo) {
                        showsInactive = true;
                    } else {
                        skippedInactive = true;
                        continue;
                    }
                }
                if (!trust.isCompromised()) {
                    boolean highlight = session.getFingerprint().equals(messageFingerprint);
                    addFingerprintRow(binding.detailsContactKeys, session, highlight);
                }
            }
            if (showsInactive || skippedInactive) {
                binding.showInactiveDevices.setText(showsInactive ? R.string.hide_inactive_devices : R.string.show_inactive_devices);
                binding.showInactiveDevices.setVisibility(View.VISIBLE);
            } else {
                binding.showInactiveDevices.setVisibility(View.GONE);
            }
        } else {
            binding.showInactiveDevices.setVisibility(View.GONE);
        }
        binding.scanButton.setVisibility(hasKeys && isCameraFeatureAvailable() ? View.VISIBLE : View.GONE);
        if (hasKeys) {
            binding.scanButton.setOnClickListener((v) -> ScanActivity.scan(this));
        }
        if (Config.supportOpenPgp() && contact.getPgpKeyId() != 0) {
            hasKeys = true;
            View view = inflater.inflate(R.layout.contact_key, binding.detailsContactKeys, false);
            TextView key = view.findViewById(R.id.key);
            TextView keyType = view.findViewById(R.id.key_type);
            keyType.setText(R.string.openpgp_key_id);
            if ("pgp".equals(messageFingerprint)) {
                keyType.setTextColor(ContextCompat.getColor(this, R.color.colorAccent));
            }
            key.setText(OpenPgpUtils.convertKeyIdToHex(contact.getPgpKeyId()));
            final OnClickListener openKey = v -> launchOpenKeyChain(contact.getPgpKeyId());
            view.setOnClickListener(openKey);
            key.setOnClickListener(openKey);
            keyType.setOnClickListener(openKey);
            binding.detailsContactKeys.addView(view);
        }
        binding.keysWrapper.setVisibility(hasKeys ? View.VISIBLE : View.GONE);

        blockViewRefresh();
    }

    private void onBadgeClick(View view) {
        final Uri systemAccount = contact.getSystemAccount();
        if (systemAccount == null) {
            checkContactPermissionAndShowAddDialog();
        } else {
            final Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(systemAccount);
            try {
                startActivity(intent);
            } catch (final ActivityNotFoundException e) {
                ToastCompat.makeText(this, R.string.no_application_found_to_view_contact, ToastCompat.LENGTH_SHORT).show();
            }
        }
    }

    public void onBackendConnected() {
        if (accountJid != null && contactJid != null) {
            Account account = xmppConnectionService.findAccountByJid(accountJid);
            if (account == null) {
                return;
            }
            this.mConversation = xmppConnectionService.findConversation(account, contactJid, false);

            this.contact = account.getRoster().getContact(contactJid);
            if (mPendingFingerprintVerificationUri != null) {
                processFingerprintVerification(mPendingFingerprintVerificationUri);
                mPendingFingerprintVerificationUri = null;
            }
            if (Compatibility.hasStoragePermission(this)) {
                final int limit = GridManager.getCurrentColumnCount(this.binding.media);
                xmppConnectionService.getAttachments(account, contact.getJid().asBareJid(), limit, this);
                this.binding.showMedia.setOnClickListener((v) -> MediaBrowserActivity.launch(this, contact));
            }
//            this.mIndividualNotifications = xmppConnectionService.hasIndividualNotification(mConversation);
            populateView();
        }
    }

    @Override
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
        refreshUi();
    }

    @Override
    protected void processFingerprintVerification(XmppUri uri) {
        if (contact != null && contact.getJid().asBareJid().equals(uri.getJid()) && uri.hasFingerprints()) {
            if (xmppConnectionService.verifyFingerprints(contact, uri.getFingerprints())) {
                ToastCompat.makeText(this, R.string.verified_fingerprints, ToastCompat.LENGTH_SHORT).show();
            }
        } else {
            ToastCompat.makeText(this, R.string.invalid_barcode, ToastCompat.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMediaLoaded(List<Attachment> attachments) {
        runOnUiThread(() -> {
            int limit = GridManager.getCurrentColumnCount(binding.media);
            mMediaAdapter.setAttachments(attachments.subList(0, Math.min(limit, attachments.size())));
            binding.mediaWrapper.setVisibility(attachments.size() > 0 ? View.VISIBLE : View.GONE);
        });
    }

    private boolean hasPermissions(int requestCode, String... permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final List<String> missingPermissions = new ArrayList<>();
            for (String permission : permissions) {
                if (Config.ONLY_INTERNAL_STORAGE && permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    continue;
                }
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(permission);
                }
            }
            if (missingPermissions.size() == 0) {
                return true;
            } else {
                requestPermissions(missingPermissions.toArray(new String[missingPermissions.size()]), requestCode);
                return false;
            }
        } else {
            return true;
        }
    }

    private void checkPermissionAndTriggerAudioCall() {
        if (hasPermissions(REQUEST_START_AUDIO_CALL, Manifest.permission.RECORD_AUDIO)) {
            triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VOICE_CALL);
        }
    }

    private void checkPermissionAndTriggerVideoCall() {
        if (hasPermissions(REQUEST_START_VIDEO_CALL, Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)) {
            triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL);
        }
    }


    private void triggerRtpSession(final String action) {
        if (xmppConnectionService.getJingleConnectionManager().isBusy()) {
            Toast.makeText(getBaseContext(), R.string.only_one_call_at_a_time, Toast.LENGTH_LONG).show();
            return;
        }
        final Contact contact = mConversation.getContact();
        if (contact.getPresences().anySupport(Namespace.JINGLE_MESSAGE)) {
            triggerRtpSession(contact.getAccount(), contact.getJid().asBareJid(), action);
        } else {
            final RtpCapability.Capability capability;
            if (action.equals(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL)) {
                capability = RtpCapability.Capability.VIDEO;
            } else {
                capability = RtpCapability.Capability.AUDIO;
            }
            PresenceSelector.selectFullJidForDirectRtpConnection(this, contact, capability, fullJid -> {
                triggerRtpSession(contact.getAccount(), fullJid, action);
            });
        }
    }

    private void triggerRtpSession(final Account account, final Jid with, final String action) {
        final Intent intent = new Intent(this, RtpSessionActivity.class);
        intent.setAction(action);
        intent.putExtra(RtpSessionActivity.EXTRA_ACCOUNT, account.getJid().toEscapedString());
        intent.putExtra(RtpSessionActivity.EXTRA_WITH, with.toEscapedString());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}