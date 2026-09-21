package org.telegram.ui.fresh.settings;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.fresh.instagram.client.InstagramApiClient;
import org.fresh.instagram.model.session.InstagramSession;
import org.fresh.instagram.model.session.LoginResult;
import org.fresh.instagram.network.TelegramProxyBridge;
import org.fresh.instagram.session.InstagramSessionManager;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.MainTabsActivity;

public class FreshInstagramSettingsActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate {

    private RecyclerListView listView;
    private ListAdapter adapter;
    private InstagramSessionManager sessionManager;
    private InstagramApiClient apiClient;

    private int rowCount;
    private int accountHeaderRow;
    private int accountProfileRow;
    private int accountActionRow;
    private int accountDividerRow;

    private int networkHeaderRow;
    private int tlsFragmentationRow;
    private int cleanDnsRow;
    private int telegramProxyBridgeRow;
    private int networkDividerRow;

    private int aboutHeaderRow;
    private int aboutVersionRow;
    private int aboutTelegramCoreRow;
    private int aboutEngineRow;

    private boolean enableTlsFragmentation = true;
    private boolean enableCleanDns = true;
    private boolean enableTelegramProxyBridge = true;

    public FreshInstagramSettingsActivity() {
        super();
    }

    public FreshInstagramSettingsActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        Context context = ApplicationLoader.applicationContext;
        sessionManager = new InstagramSessionManager(context);
        apiClient = new InstagramApiClient(sessionManager);
        updateRows();
        return super.onFragmentCreate();
    }

    private void updateRows() {
        rowCount = 0;
        accountHeaderRow = rowCount++;
        accountProfileRow = rowCount++;
        accountActionRow = rowCount++;
        accountDividerRow = rowCount++;

        networkHeaderRow = rowCount++;
        tlsFragmentationRow = rowCount++;
        cleanDnsRow = rowCount++;
        telegramProxyBridgeRow = rowCount++;
        networkDividerRow = rowCount++;

        aboutHeaderRow = rowCount++;
        aboutVersionRow = rowCount++;
        aboutTelegramCoreRow = rowCount++;
        aboutEngineRow = rowCount++;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(0);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("تنظیمات Fresh");

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = contentView;

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 16));

        adapter = new ListAdapter(context);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((view, position) -> {
            if (position == accountActionRow) {
                if (sessionManager != null && sessionManager.isLoggedIn()) {
                    showLogoutDialog();
                } else {
                    showLoginDialog();
                }
            } else if (position == tlsFragmentationRow) {
                enableTlsFragmentation = !enableTlsFragmentation;
                TelegramProxyBridge.enableDpiBypass = enableTlsFragmentation;
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enableTlsFragmentation);
                }
            } else if (position == cleanDnsRow) {
                enableCleanDns = !enableCleanDns;
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enableCleanDns);
                }
            } else if (position == telegramProxyBridgeRow) {
                enableTelegramProxyBridge = !enableTelegramProxyBridge;
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enableTelegramProxyBridge);
                }
            }
        });

        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    private void showLoginDialog() {
        if (getParentActivity() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("ورود به حساب اینستاگرام");

        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12), AndroidUtilities.dp(24), AndroidUtilities.dp(12));

        TextView infoText = new TextView(getParentActivity());
        infoText.setText("اطلاعات ورود مستقیماً با رمزنگاری RSA و AES-256 بومی به سرورهای اینستاگرام ارسال می‌شود.");
        infoText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        infoText.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        layout.addView(infoText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        EditText usernameEdit = new EditText(getParentActivity());
        usernameEdit.setHint("نام کاربری (Username)");
        usernameEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        usernameEdit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        usernameEdit.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        layout.addView(usernameEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        EditText passwordEdit = new EditText(getParentActivity());
        passwordEdit.setHint("رمز عبور (Password)");
        passwordEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        passwordEdit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        passwordEdit.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        layout.addView(passwordEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        builder.setView(layout);

        builder.setPositiveButton("ورود", (dialog, which) -> {
            String user = usernameEdit.getText().toString().trim();
            String pass = passwordEdit.getText().toString().trim();
            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(getParentActivity(), "لطفاً نام کاربری و رمز عبور را وارد کنید", Toast.LENGTH_SHORT).show();
                return;
            }
            performLogin(user, pass);
        });

        builder.setNegativeButton("انصراف", null);
        showDialog(builder.create());
    }

    private void performLogin(String user, String pass) {
        if (getParentActivity() == null) return;

        AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_MESSAGE);
        progress.setMessage("در حال برقراری ارتباط ایمن و ورود...");
        progress.setCanceledOnTouchOutside(false);
        progress.show();

        new Thread(() -> {
            try {
                LoginResult result = apiClient.loginBlocking(user, pass);
                AndroidUtilities.runOnUIThread(() -> {
                    progress.dismiss();
                    if (result instanceof LoginResult.Success) {
                        BulletinFactory.of(FreshInstagramSettingsActivity.this).createSimpleBulletin(
                                R.drawable.msg_reactions_filled,
                                "ورود موفقیت‌آمیز به اینستاگرام"
                        ).show();
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    } else if (result instanceof LoginResult.TwoFactorNeeded) {
                        showTwoFactorDialog((LoginResult.TwoFactorNeeded) result);
                    } else if (result instanceof LoginResult.Failed) {
                        LoginResult.Failed failed = (LoginResult.Failed) result;
                        BulletinFactory.of(FreshInstagramSettingsActivity.this).createErrorBulletin(failed.getErrorMessage()).show();
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    progress.dismiss();
                    BulletinFactory.of(FreshInstagramSettingsActivity.this).createErrorBulletin("خطا در ورود: " + e.getLocalizedMessage()).show();
                });
            }
        }).start();
    }

    private void showTwoFactorDialog(LoginResult.TwoFactorNeeded twoFactor) {
        if (getParentActivity() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("تایید دو مرحله‌ای اینستاگرام");

        EditText codeEdit = new EditText(getParentActivity());
        codeEdit.setHint("کد امنیتی (SMS یا Authenticator)");
        codeEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);

        builder.setView(codeEdit);

        builder.setPositiveButton("تایید", (dialog, which) -> {
            String code = codeEdit.getText().toString().trim();
            if (code.isEmpty()) return;

            new Thread(() -> {
                try {
                    String identifier = twoFactor.getTwoFactorInfo() != null ? twoFactor.getTwoFactorInfo().getTwoFactorIdentifier() : "";
                    LoginResult result = apiClient.verifyTwoFactorBlocking(
                            twoFactor.getUsername(),
                            identifier,
                            code,
                            "2"
                    );
                    AndroidUtilities.runOnUIThread(() -> {
                        if (result instanceof LoginResult.Success) {
                            BulletinFactory.of(FreshInstagramSettingsActivity.this).createSimpleBulletin(
                                    R.drawable.msg_reactions_filled,
                                    "ورود با موفقیت تکمیل شد"
                            ).show();
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                        } else if (result instanceof LoginResult.Failed) {
                            BulletinFactory.of(FreshInstagramSettingsActivity.this).createErrorBulletin(((LoginResult.Failed) result).getErrorMessage()).show();
                        }
                    });
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }).start();
        });

        builder.setNegativeButton("انصراف", null);
        showDialog(builder.create());
    }

    private void showLogoutDialog() {
        if (getParentActivity() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("خروج از اینستاگرام");
        builder.setMessage("آیا می‌خواهید از حساب کاربری اینستاگرام خود خارج شوید؟");
        builder.setPositiveButton("خروج", (dialog, which) -> {
            if (sessionManager != null) {
                sessionManager.clearSession();
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            BulletinFactory.of(FreshInstagramSettingsActivity.this).createSimpleBulletin(
                    R.drawable.msg_retry,
                    "حساب اینستاگرام خارج شد"
            ).show();
        });
        builder.setNegativeButton("انصراف", null);
        showDialog(builder.create());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onParentScrollToTop() {
        if (listView != null) {
            listView.smoothScrollToPosition(0);
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int pos = holder.getAdapterPosition();
            return pos == accountActionRow || pos == tlsFragmentationRow || pos == cleanDnsRow || pos == telegramProxyBridgeRow;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: // Header
                    view = new HeaderCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1: // TextCheck
                    view = new TextCheckCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2: // TextSettings
                    view = new TextSettingsCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3: // Divider
                    view = new TextInfoPrivacyCell(context);
                    break;
                case 4: // Profile card
                default:
                    view = createProfileCard(context);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        private View createProfileCard(Context context) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

            BackupImageView avatar = new BackupImageView(context);
            avatar.setRoundRadius(AndroidUtilities.dp(24));
            avatar.setTag("avatar");
            card.addView(avatar, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

            LinearLayout textLayout = new LinearLayout(context);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            card.addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));

            TextView title = new TextView(context);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setTag("title");
            textLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            TextView subtitle = new TextView(context);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            subtitle.setTag("subtitle");
            textLayout.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            return card;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int viewType = getItemViewType(position);
            InstagramSession session = sessionManager != null ? sessionManager.getActiveSession() : null;
            boolean loggedIn = session != null && sessionManager.isLoggedIn();

            if (viewType == 0) { // Header
                HeaderCell cell = (HeaderCell) holder.itemView;
                if (position == accountHeaderRow) {
                    cell.setText("حساب کاربری اینستاگرام");
                } else if (position == networkHeaderRow) {
                    cell.setText("ضد سانسور و پایداری شبکه");
                } else if (position == aboutHeaderRow) {
                    cell.setText("درباره سوپراپ Fresh");
                }
            } else if (viewType == 1) { // TextCheck
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                if (position == tlsFragmentationRow) {
                    cell.setTextAndCheck("تکه‌تکه‌سازی پکت‌های TLS (دور زدن SNI)", enableTlsFragmentation, true);
                } else if (position == cleanDnsRow) {
                    cell.setTextAndCheck("سامانه DNS پاک ضد مسموم‌سازی (Clean Anycast)", enableCleanDns, true);
                } else if (position == telegramProxyBridgeRow) {
                    cell.setTextAndCheck("هدایت ترافیک اینستاگرام از پروکسی تلگرام", enableTelegramProxyBridge, false);
                }
            } else if (viewType == 2) { // TextSettings
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                if (position == accountActionRow) {
                    if (loggedIn) {
                        cell.setTextAndValue("خروج از حساب کاربری", "قطع اتصال", false);
                    } else {
                        cell.setTextAndValue("ورود به حساب اینستاگرام", "اتصال", false);
                    }
                } else if (position == aboutVersionRow) {
                    cell.setTextAndValue("نسخه سوپراپ فرش", "2.0 (Dual-Engine)", true);
                } else if (position == aboutTelegramCoreRow) {
                    cell.setTextAndValue("هسته اصلی تلگرام", "12.10.3 (Pristine)", true);
                } else if (position == aboutEngineRow) {
                    cell.setTextAndValue("موتور بومی اینستاگرام", "Reverse-Engineered Private API", false);
                }
            } else if (viewType == 4) { // Profile card
                View card = holder.itemView;
                BackupImageView avatar = card.findViewWithTag("avatar");
                TextView title = card.findViewWithTag("title");
                TextView subtitle = card.findViewWithTag("subtitle");

                if (loggedIn) {
                    title.setText("@" + session.getUsername());
                    subtitle.setText(session.getFullName() != null && !session.getFullName().isEmpty() ? session.getFullName() : "حساب متصل شده");
                    avatar.setImage("https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150", "48_48", null);
                } else {
                    title.setText("مهمان (حساب متصل نیست)");
                    subtitle.setText("جهت همگام‌سازی فید و استوری‌های خود وارد شوید");
                    AvatarDrawable avatarDrawable = new AvatarDrawable();
                    avatarDrawable.setInfo(1, "Fresh", null);
                    avatar.setImage(null, null, avatarDrawable, null);
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == accountHeaderRow || position == networkHeaderRow || position == aboutHeaderRow) {
                return 0; // Header
            } else if (position == tlsFragmentationRow || position == cleanDnsRow || position == telegramProxyBridgeRow) {
                return 1; // TextCheck
            } else if (position == accountActionRow || position == aboutVersionRow || position == aboutTelegramCoreRow || position == aboutEngineRow) {
                return 2; // TextSettings
            } else if (position == accountDividerRow || position == networkDividerRow) {
                return 3; // Divider
            } else if (position == accountProfileRow) {
                return 4; // Profile card
            }
            return 2;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }
    }
}
