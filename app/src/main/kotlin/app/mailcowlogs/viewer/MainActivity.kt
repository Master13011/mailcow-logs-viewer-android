package app.mailcowlogs.viewer

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var progress: ProgressBar
    private lateinit var btnSettings: ImageButton
    private lateinit var prefs: SharedPreferences

    private var serverUri: Uri? = null
    private var webViewDestroyed = false
    private var errorDialog: AlertDialog? = null
    private var serverDialog: AlertDialog? = null
    private var authDialog: AlertDialog? = null
    private var settingsDialog: AlertDialog? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val lockHandler = Handler(Looper.getMainLooper())
    
    private val inactivityTimeout = 60_000L // 1 minute
    private var lastInteractionTime = 0L
    private var backgroundTime = 0L
    private var isAuthenticated = false
    private var isAuthenticating = false
    private var pageInitialized = false
    private var pendingSavedState: Bundle? = null
    
    private val inactivityRunnable = Runnable {
        if (prefs.getBoolean(KEY_LOCK, false) && isAuthenticated) {
            lockApplication()
        }
    }

    private val fileChooser = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        filePathCallback?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        serverUri = prefs.getString(KEY_SERVER, null)?.let { normalizeServer(it) }

        webView = findViewById(R.id.webview)
        refresh = findViewById(R.id.refresh)
        progress = findViewById(R.id.progress)
        btnSettings = findViewById(R.id.btnSettings)

        btnSettings.setOnClickListener { showSettingsDialog() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(R.id.root)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        configureWebView()

        refresh.setOnRefreshListener { webView.reload() }
        // Ne déclenche le "pull to refresh" que lorsque la page est tout en haut.
        refresh.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        pendingSavedState = savedInstanceState
        
        if (prefs.getBoolean(KEY_LOCK, false)) {
            isAuthenticated = false
            webView.visibility = View.INVISIBLE
            refresh.visibility = View.INVISIBLE
            btnSettings.visibility = View.INVISIBLE
        } else {
            isAuthenticated = true
            lastInteractionTime = SystemClock.elapsedRealtime()
            startNormalFlow(savedInstanceState)
            pageInitialized = true
        }
    }

    private fun startNormalFlow(savedInstanceState: Bundle?) {
        if (intent?.action == ACTION_CHANGE_SERVER || serverUri == null) {
            promptForServer(cancelable = serverUri != null)
        } else if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            loadServer()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_CHANGE_SERVER) {
            promptForServer(cancelable = serverUri != null)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!webViewDestroyed) webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
    
        if (webViewDestroyed) return
    
        webView.onResume()
    
        if (!prefs.getBoolean(KEY_LOCK, false)) return
    
        val backgroundDuration = if (backgroundTime > 0L) {
            SystemClock.elapsedRealtime() - backgroundTime
        } else {
            0L
        }
    
        backgroundTime = 0L
    
        if (!isAuthenticated) {
            authenticateUser(pendingSavedState)
            return
        }
    
        if (backgroundDuration >= inactivityTimeout ||
            SystemClock.elapsedRealtime() - lastInteractionTime >= inactivityTimeout
        ) {
            lockApplication()
        } else {
            scheduleInactivityLock()
        }
    }
    
    override fun onPause() {
        backgroundTime = SystemClock.elapsedRealtime()
    
        if (!webViewDestroyed) {
            webView.onPause()
        }
    
        super.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
    
        if (isAuthenticated &&
            prefs.getBoolean(KEY_LOCK, false)
        ) {
            lastInteractionTime = SystemClock.elapsedRealtime()
            scheduleInactivityLock()
        }
    }
    
    private fun lockApplication() {
        if (!prefs.getBoolean(KEY_LOCK, false)) return
        if (!isAuthenticated) return

        backgroundTime = 0L
        lockHandler.removeCallbacks(inactivityRunnable)
    
        isAuthenticated = false
    
        webView.visibility = View.INVISIBLE
        refresh.visibility = View.INVISIBLE
        btnSettings.visibility = View.INVISIBLE
    
        authenticateUser(null)
    }
    
    private fun scheduleInactivityLock() {
        lockHandler.removeCallbacks(inactivityRunnable)
    
        if (!isAuthenticated ||
            !prefs.getBoolean(KEY_LOCK, false)
        ) return
    
        val elapsed = SystemClock.elapsedRealtime() - lastInteractionTime
        val remaining = (inactivityTimeout - elapsed).coerceAtLeast(0L)
    
        lockHandler.postDelayed(inactivityRunnable, remaining)
    }    
    
    override fun onDestroy() {
        errorDialog?.dismiss()
        serverDialog?.dismiss()
        authDialog?.dismiss()
        settingsDialog?.dismiss()
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        destroyWebView()
        lockHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ Paramètres & Sécurité

    private fun showSettingsDialog() {
        val isLockEnabled = prefs.getBoolean(KEY_LOCK, false)
        val lockText = if (isLockEnabled) {
            "🔒 Désactiver le verrouillage (Empreinte / PIN)"
        } else {
            "🔓 Activer le verrouillage (Empreinte / PIN)"
        }

        val items = arrayOf(
            lockText,
            "🌐 Changer de serveur",
            "ℹ️ À propos"
        )

        settingsDialog?.dismiss()
        settingsDialog = AlertDialog.Builder(this)
            .setTitle("Paramètres")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> toggleBiometricLock(!isLockEnabled)
                    1 -> promptForServer(cancelable = serverUri != null)
                    2 -> showAboutDialog()
                }
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun toggleBiometricLock(enable: Boolean) {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

        if (enable && biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(
                this,
                "Aucune empreinte ni code PIN configuré sur cet appareil.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        prefs.edit().putBoolean(KEY_LOCK, enable).apply()
        
        if (enable) {
            isAuthenticated = true
            lastInteractionTime = SystemClock.elapsedRealtime()
            scheduleInactivityLock()
        } else {
            lockHandler.removeCallbacks(inactivityRunnable)
            isAuthenticated = true
        }
        val msg = if (enable) "Verrouillage activé au démarrage" else "Verrouillage désactivé"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun authenticateUser(savedInstanceState: Bundle?) {
    
        if (isAuthenticating || isAuthenticated) return
    
        isAuthenticating = true
    
        val executor = ContextCompat.getMainExecutor(this)
    
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
    
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
    
                    isAuthenticating = false
                    isAuthenticated = true
    
                    lastInteractionTime = SystemClock.elapsedRealtime()
    
                    webView.visibility = View.VISIBLE
                    refresh.visibility = View.VISIBLE
                    btnSettings.visibility = View.VISIBLE
    
                    // Ne recharge pas Mailcow si la page existe déjà.
                    if (!pageInitialized) {
                        startNormalFlow(
                            savedInstanceState ?: pendingSavedState
                        )
                        pageInitialized = true
                    }
    
                    scheduleInactivityLock()
                }
    
                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
    
                    isAuthenticating = false
    
                    finish()
                }
    
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // L'utilisateur peut réessayer son empreinte.
                }
            }
        )
    
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Mailcow Logs Viewer")
            .setSubtitle("Confirmez votre identité pour accéder aux logs")
            .setAllowedAuthenticators(
                BIOMETRIC_STRONG or DEVICE_CREDENTIAL
            )
            .build()
    
        biometricPrompt.authenticate(promptInfo)
    }
    private fun showAboutDialog() {
        settingsDialog?.dismiss()
        settingsDialog = AlertDialog.Builder(this)
            .setTitle("À propos")
            .setMessage(
                "Mailcow Logs Viewer\n\n" +
                    "Version : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // ------------------------------------------------------------------ WebView

    @SuppressLint("SetJavaScriptEnabled") // requis : l'interface est une SPA JavaScript
    private fun configureWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                return when (uri.scheme?.lowercase()) {
                    // Reste dans la WebView (y compris le fournisseur OAuth2 / OIDC éventuel).
                    "https" -> false
                    "mailto", "tel" -> {
                        openExternal(uri)
                        true
                    }
                    else -> true // http en clair, intent:, file:, javascript:, etc. : bloqués
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress.visibility = View.GONE
                refresh.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) showLoadError(error.description?.toString())
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                handler.cancel() // jamais de "continuer malgré l'erreur"
                showLoadError(getString(R.string.ssl_error))
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView,
                handler: HttpAuthHandler,
                host: String,
                realm: String?
            ) {
                if (!host.equals(serverUri?.host, ignoreCase = true)) {
                    handler.cancel()
                    return
                }
                showBasicAuthDialog(handler)
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                // Évite le crash de l'app si le processus de rendu est tué : on recrée l'écran.
                destroyWebView()
                recreate()
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
            }

            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try {
                    fileChooser.launch(params.createIntent())
                    true
                } catch (e: ActivityNotFoundException) {
                    filePathCallback = null
                    callback.onReceiveValue(null)
                    false
                }
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val uri = Uri.parse(url)
            if (!isServerUri(uri)) {
                openExternal(uri)
                return@setDownloadListener
            }
            try {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(uri)
                    .setMimeType(mimeType)
                    .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                    .addRequestHeader("User-Agent", userAgent)
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                getSystemService(DownloadManager::class.java).enqueue(request)
                Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
            } catch (e: RuntimeException) {
                openExternal(uri)
            }
        }
    }

    private fun destroyWebView() {
        if (webViewDestroyed) return
        webViewDestroyed = true
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    private fun loadServer() {
        val uri = serverUri ?: return
        webView.loadUrl(uri.toString())
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            // aucune application pour ouvrir ce lien
        }
    }

    private fun isServerUri(uri: Uri): Boolean {
        val server = serverUri ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(server.host, ignoreCase = true) &&
            effectivePort(uri) == effectivePort(server)
    }

    private fun effectivePort(uri: Uri): Int = if (uri.port == -1) 443 else uri.port

    // ------------------------------------------------------------------ Dialogues

    private fun showLoadError(detail: String?) {
        refresh.isRefreshing = false
        progress.visibility = View.GONE
        if (errorDialog?.isShowing == true || isFinishing || webViewDestroyed) return
        webView.loadUrl("about:blank")
        errorDialog = AlertDialog.Builder(this)
            .setTitle(R.string.load_error_title)
            .setMessage(detail ?: "")
            .setCancelable(false)
            .setPositiveButton(R.string.retry) { _, _ -> loadServer() }
            .setNegativeButton(R.string.change_server) { _, _ -> promptForServer(cancelable = true) }
            .show()
    }

    private fun showBasicAuthDialog(handler: HttpAuthHandler) {
        if (authDialog?.isShowing == true) {
            handler.cancel()
            return
        }
        val user = EditText(this).apply {
            hint = getString(R.string.username)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
        }
        val pass = EditText(this).apply {
            hint = getString(R.string.password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 8.dp(), 24.dp(), 0)
            addView(user)
            addView(pass)
        }
        authDialog = AlertDialog.Builder(this)
            .setTitle(R.string.login_title)
            .setView(container)
            .setPositiveButton(R.string.sign_in) { _, _ ->
                handler.proceed(user.text.toString(), pass.text.toString())
            }
            .setNegativeButton(R.string.cancel) { _, _ -> handler.cancel() }
            .setOnCancelListener { handler.cancel() }
            .show()
    }

    private fun promptForServer(cancelable: Boolean) {
        if (serverDialog?.isShowing == true) return
        errorDialog?.dismiss()

        val input = EditText(this).apply {
            hint = getString(R.string.server_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
            setText(serverUri?.toString() ?: "")
        }
        val container = LinearLayout(this).apply {
            setPadding(24.dp(), 8.dp(), 24.dp(), 0)
            addView(input, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.server_title)
            .setMessage(R.string.server_message)
            .setView(container)
            .setCancelable(cancelable)
            .setPositiveButton(R.string.save, null) // listener défini après show() pour valider
        if (cancelable) builder.setNegativeButton(R.string.cancel, null)

        val dialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val normalized = normalizeServer(input.text.toString())
            if (normalized == null) {
                input.error = getString(R.string.server_invalid)
            } else {
                saveServer(normalized)
                dialog.dismiss()
            }
        }
        serverDialog = dialog
    }

    private fun saveServer(uri: Uri) {
        val changed = serverUri?.toString() != uri.toString()
        serverUri = uri
        prefs.edit().putString(KEY_SERVER, uri.toString()).apply()
        if (changed) {
            // Évite de mélanger session/stockage entre deux serveurs différents.
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
            webView.clearCache(true)
        }
        loadServer()
    }

    /** Retourne l'origine HTTPS (schéma + hôte + port) ou null si la saisie est invalide. */
    private fun normalizeServer(raw: String): Uri? {
        var value = raw.trim()
        if (value.isEmpty()) return null
        if (!value.contains("://")) value = "https://$value"
        val uri = Uri.parse(value)
        val host = uri.host
        if (!"https".equals(uri.scheme, ignoreCase = true)) return null
        if (host.isNullOrBlank() || uri.userInfo != null) return null
        val authority = if (uri.port == -1) host else "$host:${uri.port}"
        return Uri.parse("https://$authority")
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREFS = "settings"
        private const val KEY_SERVER = "server_url"
        private const val KEY_LOCK = "lock_enabled"
        const val ACTION_CHANGE_SERVER = "app.mailcowlogs.viewer.CHANGE_SERVER"
    }
}
