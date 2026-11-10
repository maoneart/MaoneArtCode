package com.maoneart.code

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.webkit.*
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.maoneart.code.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequestCode = 1001

    private val prefs by lazy {
        getSharedPreferences("maoneart_code_prefs", Context.MODE_PRIVATE)
    }

    private var autoReconnectJob: Job? = null
    private var isToolbarVisible = true
    private var isStickyCtrl = false
    private var isStickyAlt = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBackPressHandler()
        setupWebView()
        setupToolbarButtons()
        setupDashboardListeners()

        checkServerAndConnect()
    }

    private fun getServerUrl(): String {
        return prefs.getString("server_url", "http://127.0.0.1:8090/?folder=/sdcard/www")
            ?: "http://127.0.0.1:8090/?folder=/sdcard/www"
    }

    private fun setServerUrl(url: String) {
        prefs.edit().putString("server_url", url).apply()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val web = binding.webView
        val settings = web.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.setSupportMultipleWindows(true)
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = true

        // Use desktop-class user agent so VS Code provides full desktop layout
        val defaultUa = settings.userAgentString
        settings.userAgentString = defaultUa.replace("Mobile", "Desktop")

        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE
                binding.offlineDashboard.visibility = View.GONE
                binding.webView.visibility = View.VISIBLE
                if (isToolbarVisible) {
                    binding.codingToolbar.visibility = View.VISIBLE
                    binding.btnToggleToolbar.visibility = View.VISIBLE
                    binding.btnToggleToolbar.text = "▲ Sembunyikan Toolbar"
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    showOfflineDashboard("Gagal terhubung ke Code Server.")
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                return try {
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    startActivityForResult(intent, fileChooserRequestCode)
                    true
                } catch (e: Exception) {
                    fileUploadCallback = null
                    false
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (binding.offlineDashboard.visibility == View.VISIBLE || binding.webView.url == null) {
            checkServerAndConnect()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoReconnectJob?.cancel()
    }

    private fun checkServerAndConnect() {
        binding.progressBar.visibility = View.VISIBLE
        val targetUrl = getServerUrl()

        lifecycleScope.launch(Dispatchers.IO) {
            val isAlive = pingServer(targetUrl)
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                if (isAlive) {
                    autoReconnectJob?.cancel()
                    binding.offlineDashboard.visibility = View.GONE
                    binding.webView.visibility = View.VISIBLE
                    if (binding.webView.url == null || binding.webView.url != targetUrl) {
                        binding.webView.loadUrl(targetUrl)
                    }
                } else {
                    showOfflineDashboard("🔴 Code Server Belum Aktif (Port 8090)")
                }
            }
        }
    }

    private fun pingServer(targetUrl: String): Boolean {
        return try {
            val uri = Uri.parse(targetUrl)
            val host = uri.host ?: "127.0.0.1"
            val port = if (uri.port != -1) uri.port else 8090
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 1200)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun startAutoReconnectWatcher() {
        autoReconnectJob?.cancel()
        autoReconnectJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1500)
                val targetUrl = getServerUrl()
                if (pingServer(targetUrl)) {
                    withContext(Dispatchers.Main) {
                        checkServerAndConnect()
                    }
                    break
                }
            }
        }
    }

    private fun showOfflineDashboard(statusMsg: String) {
        binding.progressBar.visibility = View.GONE
        binding.webView.visibility = View.GONE
        binding.codingToolbar.visibility = View.GONE
        binding.btnToggleToolbar.visibility = View.GONE
        binding.offlineDashboard.visibility = View.VISIBLE
        binding.tvServerStatus.text = statusMsg
        startAutoReconnectWatcher()
    }

    private fun setupDashboardListeners() {
        binding.btnCopyCommand.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Code Command", "code"))
            Toast.makeText(this, "Perintah 'code' disalin ke clipboard!", Toast.LENGTH_SHORT).show()
        }

        binding.btnLaunchTermux.setOnClickListener {
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage("com.termux")
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(this, "Aplikasi Termux tidak ditemukan.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal membuka Termux: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRetry.setOnClickListener {
            checkServerAndConnect()
        }

        binding.btnSettings.setOnClickListener {
            showPortSettingsModal()
        }
    }

    private fun setupToolbarButtons() {
        binding.btnKeyEsc.setOnClickListener { dispatchWebKey("Escape", 27) }
        binding.btnKeyTab.setOnClickListener { dispatchWebKey("Tab", 9) }

        binding.btnKeyCtrl.setOnClickListener {
            isStickyCtrl = !isStickyCtrl
            binding.btnKeyCtrl.setTextColor(if (isStickyCtrl) Color.parseColor("#34C759") else Color.parseColor("#007ACC"))
        }

        binding.btnKeyAlt.setOnClickListener {
            isStickyAlt = !isStickyAlt
            binding.btnKeyAlt.setTextColor(if (isStickyAlt) Color.parseColor("#34C759") else Color.parseColor("#CCCCCC"))
        }

        // F1: Command Palette
        binding.btnKeyPalette.setOnClickListener { dispatchWebKey("F1", 112) }

        // Ctrl+S: Save
        binding.btnKeySave.setOnClickListener { dispatchWebKeyCombination("s", ctrl = true) }

        // Ctrl+`: Integrated Terminal
        binding.btnKeyTerminal.setOnClickListener { dispatchWebKeyCombination("`", ctrl = true) }

        // Ctrl+B: Toggle Sidebar Explorer
        binding.btnKeySidebar.setOnClickListener { dispatchWebKeyCombination("b", ctrl = true) }

        // Reload
        binding.btnReload.setOnClickListener { binding.webView.reload() }

        // Toggle Toolbar Pill
        binding.btnToggleToolbar.setOnClickListener {
            isToolbarVisible = !isToolbarVisible
            binding.codingToolbar.visibility = if (isToolbarVisible) View.VISIBLE else View.GONE
            binding.btnToggleToolbar.text = if (isToolbarVisible) "▲ Sembunyikan Toolbar" else "▼ Toolbar Tuts"
        }
    }

    private fun dispatchWebKey(key: String, keyCode: Int) {
        val ctrl = isStickyCtrl
        val alt = isStickyAlt
        val js = """
            (function() {
                const e = new KeyboardEvent('keydown', {
                    key: '$key',
                    code: '$key',
                    keyCode: $keyCode,
                    which: $keyCode,
                    ctrlKey: $ctrl,
                    altKey: $alt,
                    bubbles: true,
                    cancelable: true
                });
                document.activeElement ? document.activeElement.dispatchEvent(e) : document.dispatchEvent(e);
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(js, null)
        resetStickyModifiers()
    }

    private fun dispatchWebKeyCombination(key: String, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false) {
        val activeCtrl = ctrl || isStickyCtrl
        val activeAlt = alt || isStickyAlt
        val js = """
            (function() {
                const e = new KeyboardEvent('keydown', {
                    key: '$key',
                    ctrlKey: $activeCtrl,
                    shiftKey: $shift,
                    altKey: $activeAlt,
                    bubbles: true,
                    cancelable: true
                });
                document.activeElement ? document.activeElement.dispatchEvent(e) : document.dispatchEvent(e);
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(js, null)
        resetStickyModifiers()
    }

    private fun resetStickyModifiers() {
        if (isStickyCtrl) {
            isStickyCtrl = false
            binding.btnKeyCtrl.setTextColor(Color.parseColor("#007ACC"))
        }
        if (isStickyAlt) {
            isStickyAlt = false
            binding.btnKeyAlt.setTextColor(Color.parseColor("#CCCCCC"))
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_S -> {
                    dispatchWebKeyCombination("s", ctrl = true)
                    return true
                }
                KeyEvent.KEYCODE_P -> {
                    val shift = event.isShiftPressed
                    dispatchWebKeyCombination("p", ctrl = true, shift = shift)
                    return true
                }
                KeyEvent.KEYCODE_W -> {
                    // Intercept Ctrl+W to close tab in IDE, don't let Android close activity
                    dispatchWebKeyCombination("w", ctrl = true)
                    return true
                }
                KeyEvent.KEYCODE_B -> {
                    dispatchWebKeyCombination("b", ctrl = true)
                    return true
                }
                KeyEvent.KEYCODE_GRAVE -> {
                    dispatchWebKeyCombination("`", ctrl = true)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.visibility == View.VISIBLE && binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    showConfirmExitModal()
                }
            }
        })
    }

    private fun showPortSettingsModal() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val view = layoutInflater.inflate(R.layout.dialog_port_settings, null)
        dialog.setContentView(view)

        val etUrl = view.findViewById<EditText>(R.id.etServerUrl)
        val btnSave = view.findViewById<Button>(R.id.btnSavePort)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelPort)

        etUrl.setText(getServerUrl())

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val newUrl = etUrl.text.toString().trim()
            if (newUrl.isNotEmpty()) {
                setServerUrl(newUrl)
                dialog.dismiss()
                checkServerAndConnect()
            }
        }

        dialog.show()
    }

    private fun showConfirmExitModal() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val view = layoutInflater.inflate(R.layout.dialog_confirm_exit, null)
        dialog.setContentView(view)

        val btnCancel = view.findViewById<Button>(R.id.btnCancelExit)
        val btnConfirm = view.findViewById<Button>(R.id.btnConfirmExit)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            finish()
        }

        dialog.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == fileChooserRequestCode) {
            val results = if (resultCode == RESULT_OK && data != null) {
                val dataString = data.dataString
                if (dataString != null) {
                    arrayOf(Uri.parse(dataString))
                } else null
            } else null

            fileUploadCallback?.onReceiveValue(results)
            fileUploadCallback = null
        }
    }
}
