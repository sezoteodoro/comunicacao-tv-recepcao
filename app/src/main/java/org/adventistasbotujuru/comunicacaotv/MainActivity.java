package org.adventistasbotujuru.comunicacaotv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.StorageController;

public class MainActivity extends Activity {

    private static final String PORTAL_URL =
            "https://comunicacao.adventistasbotujuru.org/?ambiente=recepcao&tv=1";
    private static final String SESSION_CONTEXT = "comunicacao-tv-recepcao";
    private static final String EXTENSION_URI = "resource://android/assets/tvhelper/";
    private static final String EXTENSION_ID = "comunicacao-tv-helper@adventistasbotujuru.org";
    private static final long HIDDEN_MENU_DELAY_MS = 3000L;
    private static final long RETRY_DELAY_MS = 10000L;

    private static GeckoRuntime runtime;

    private GeckoView geckoView;
    private GeckoSession session;
    private View loadingOverlay;
    private View offlineOverlay;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean canGoBack = false;
    private boolean backPressed = false;
    private boolean settingsOpened = false;

    private final Runnable settingsRunnable = () -> {
        if (backPressed && !isFinishing()) {
            settingsOpened = true;
            showAdminMenu();
        }
    };

    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed()) return;
            if (hasInternet()) {
                hideOffline();
                if (session != null) session.reload();
            } else {
                handler.postDelayed(this, RETRY_DELAY_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("tv_settings", MODE_PRIVATE);
        geckoView = findViewById(R.id.geckoView);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        offlineOverlay = findViewById(R.id.offlineOverlay);

        enterImmersiveMode();
        ensureRuntime();
        installTvHelper();
        createSessionAndLoad();
    }

    private void ensureRuntime() {
        if (runtime != null) return;

        GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                .build();
        runtime = GeckoRuntime.create(getApplicationContext(), settings);
    }

    private void installTvHelper() {
        runtime.getWebExtensionController()
                .ensureBuiltIn(EXTENSION_URI, EXTENSION_ID)
                .accept(
                        extension -> { },
                        error -> runOnUiThread(() ->
                                Toast.makeText(this,
                                        "O modo TV abriu sem os ajustes auxiliares. Recarregue se necessário.",
                                        Toast.LENGTH_LONG).show())
                );
    }

    private void createSessionAndLoad() {
        if (session != null) {
            try { session.close(); } catch (Exception ignored) { }
        }

        GeckoSessionSettings sessionSettings = new GeckoSessionSettings.Builder()
                .contextId(SESSION_CONTEXT)
                .build();

        session = new GeckoSession(sessionSettings);
        session.setContentDelegate(new GeckoSession.ContentDelegate() { });

        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onCanGoBack(GeckoSession geckoSession, boolean value) {
                canGoBack = value;
            }
        });

        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession geckoSession, String url) {
                showLoading();
            }

            @Override
            public void onPageStop(GeckoSession geckoSession, boolean success) {
                hideLoading();
                if (success) {
                    hideOffline();
                    geckoView.requestFocus();
                    geckoSession.setActive(true);
                    geckoSession.setFocused(true);
                } else if (!hasInternet()) {
                    showOffline();
                }
            }
        });

        session.open(runtime);
        geckoView.setSession(session);
        geckoView.setFocusable(true);
        geckoView.setFocusableInTouchMode(true);
        geckoView.requestFocus();
        session.setActive(true);
        session.setFocused(true);
        session.loadUri(PORTAL_URL);
    }

    private void showLoading() {
        if (offlineOverlay.getVisibility() != View.VISIBLE) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoading() {
        loadingOverlay.setVisibility(View.GONE);
    }

    private void showOffline() {
        hideLoading();
        offlineOverlay.setVisibility(View.VISIBLE);
        handler.removeCallbacks(retryRunnable);
        handler.postDelayed(retryRunnable, RETRY_DELAY_MS);
    }

    private void hideOffline() {
        offlineOverlay.setVisibility(View.GONE);
        handler.removeCallbacks(retryRunnable);
    }

    private boolean hasInternet() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Segure VOLTAR por 3 segundos para abrir as configurações ocultas.
        // Um toque curto em VOLTAR navega apenas se houver histórico.
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                backPressed = true;
                settingsOpened = false;
                handler.postDelayed(settingsRunnable, HIDDEN_MENU_DELAY_MS);
                return true;
            }

            if (event.getAction() == KeyEvent.ACTION_UP) {
                backPressed = false;
                handler.removeCallbacks(settingsRunnable);
                if (settingsOpened) {
                    settingsOpened = false;
                    return true;
                }
                if (canGoBack && session != null) {
                    session.goBack();
                }
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private void showAdminMenu() {
        backPressed = false;
        handler.removeCallbacks(settingsRunnable);

        final boolean autoStart = prefs.getBoolean("auto_start", false);
        String autoLabel = autoStart ? "Desativar início automático" : "Ativar início automático";

        String[] items = {
                "Recarregar Recepção",
                "Voltar para a entrada da Recepção",
                "Trocar usuário / limpar sessão",
                autoLabel,
                "Sair do aplicativo"
        };

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Comunicação TV • Configurações")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            if (session != null) session.reload();
                            break;
                        case 1:
                            if (session != null) session.loadUri(PORTAL_URL);
                            break;
                        case 2:
                            confirmClearSession();
                            break;
                        case 3:
                            boolean newValue = !autoStart;
                            prefs.edit().putBoolean("auto_start", newValue).apply();
                            Toast.makeText(this,
                                    newValue ? "Início automático ativado." : "Início automático desativado.",
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case 4:
                            finishAffinity();
                            break;
                    }
                })
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnDismissListener(d -> {
            enterImmersiveMode();
            if (session != null) session.setFocused(true);
            geckoView.requestFocus();
        });
        dialog.show();
    }

    private void confirmClearSession() {
        new AlertDialog.Builder(this)
                .setTitle("Trocar usuário")
                .setMessage("A sessão da Recepção, cookies e armazenamento do portal serão apagados neste aplicativo. Deseja continuar?")
                .setPositiveButton("Limpar sessão", (dialog, which) -> clearPortalSession())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void clearPortalSession() {
        showLoading();

        if (session != null) {
            try {
                session.setFocused(false);
                session.setActive(false);
                session.close();
            } catch (Exception ignored) { }
            session = null;
        }

        long flags = StorageController.ClearFlags.SITE_DATA |
                StorageController.ClearFlags.AUTH_SESSIONS;

        runtime.getStorageController()
                .clearData(flags)
                .accept(
                        ignored -> runOnUiThread(() -> {
                            Toast.makeText(this, "Sessão limpa.", Toast.LENGTH_SHORT).show();
                            createSessionAndLoad();
                        }),
                        error -> runOnUiThread(() -> {
                            Toast.makeText(this, "Não foi possível limpar toda a sessão. Reiniciando o acesso.", Toast.LENGTH_LONG).show();
                            createSessionAndLoad();
                        })
                );
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        if (session != null) {
            session.setActive(true);
            session.setFocused(true);
        }
    }

    @Override
    protected void onPause() {
        if (session != null) session.setFocused(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (session != null) {
            try {
                session.setFocused(false);
                session.setActive(false);
                session.close();
            } catch (Exception ignored) { }
        }
        super.onDestroy();
    }
}
