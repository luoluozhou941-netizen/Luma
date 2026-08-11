package com.luoluo.luma

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : FragmentActivity() {

    private lateinit var webView: WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    class LumaJsBridge(private val activity: MainActivity) {
        @JavascriptInterface
        fun exportData(jsonData: String, fileName: String) {
            activity.runOnUiThread {
                try {
                    val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: activity.filesDir
                    dir.mkdirs()
                    val file = File(dir, fileName)
                    FileOutputStream(file).use { it.write(jsonData.toByteArray(Charsets.UTF_8)) }
                    Toast.makeText(activity, "✅ 已导出: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(activity, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        /**
         * 网页那边想加密一段文字（比如刚填好的API key），调这个方法。
         * requestId 是网页自己生成的一个随便什么字符串，用来在回调里对上号
         * （网页那边可能同时发出好几个请求，靠这个 id 区分哪个结果对应哪个请求）。
         */
        @JavascriptInterface
        fun requestEncrypt(plainText: String, requestId: String) {
            activity.runOnUiThread {
                activity.showBiometricThenEncrypt(plainText, requestId)
            }
        }

        /** 网页那边想解密一段之前存过的密文，调这个方法。 */
        @JavascriptInterface
        fun requestDecrypt(packedCipherText: String, requestId: String) {
            activity.runOnUiThread {
                activity.showBiometricThenDecrypt(packedCipherText, requestId)
            }
        }
    }

    /** 弹一次指纹/面容验证，验证通过后加密，结果送回网页那边。 */
    private fun showBiometricThenEncrypt(plainText: String, requestId: String) {
        try {
            val cipher = CryptoManager.prepareEncryptCipher()
            val cryptoObject = BiometricPrompt.CryptoObject(cipher)

            val prompt = BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        try {
                            val authedCipher = result.cryptoObject!!.cipher!!
                            val encryptedBytes = authedCipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                            val packed = CryptoManager.packEncrypted(authedCipher.iv, encryptedBytes)
                            sendCryptoResultToJs(requestId, success = true, payload = packed)
                        } catch (e: Exception) {
                            sendCryptoResultToJs(requestId, success = false, payload = "加密失败: ${e.message}")
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        sendCryptoResultToJs(requestId, success = false, payload = "验证未通过: $errString")
                    }

                    override fun onAuthenticationFailed() {
                        // 指纹没对上这种情况，系统自己会让用户重试，这里不用做什么
                    }
                }
            )
            prompt.authenticate(biometricPromptInfo(), cryptoObject)
        } catch (e: Exception) {
            sendCryptoResultToJs(requestId, success = false, payload = "准备加密时出错: ${e.message}")
        }
    }

    /** 弹一次指纹/面容验证，验证通过后解密，结果送回网页那边。 */
    private fun showBiometricThenDecrypt(packedCipherText: String, requestId: String) {
        try {
            val (iv, encryptedBytes) = CryptoManager.unpackEncrypted(packedCipherText)
            val cipher = CryptoManager.prepareDecryptCipher(iv)
            val cryptoObject = BiometricPrompt.CryptoObject(cipher)

            val prompt = BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        try {
                            val authedCipher = result.cryptoObject!!.cipher!!
                            val plainBytes = authedCipher.doFinal(encryptedBytes)
                            sendCryptoResultToJs(requestId, success = true, payload = String(plainBytes, Charsets.UTF_8))
                        } catch (e: Exception) {
                            sendCryptoResultToJs(requestId, success = false, payload = "解密失败: ${e.message}")
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        sendCryptoResultToJs(requestId, success = false, payload = "验证未通过: $errString")
                    }

                    override fun onAuthenticationFailed() {
                        // 指纹没对上这种情况，系统自己会让用户重试，这里不用做什么
                    }
                }
            )
            prompt.authenticate(biometricPromptInfo(), cryptoObject)
        } catch (e: Exception) {
            sendCryptoResultToJs(requestId, success = false, payload = "准备解密时出错: ${e.message}")
        }
    }

    private fun biometricPromptInfo(): BiometricPrompt.PromptInfo =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("验证身份")
            .setSubtitle("需要验证一下才能继续")
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

    /**
     * 把结果送回网页那边。网页那边需要提前准备好一个全局函数：
     * window.LumaCryptoCallback = function(requestId, success, payload) { ... }
     * success 是 true/false，payload 成功时是结果内容，失败时是错误说明文字。
     * （这部分JS代码等做网页那边的接入时再一起写。）
     */
    private fun sendCryptoResultToJs(requestId: String, success: Boolean, payload: String) {
        runOnUiThread {
            val safeRequestId = org.json.JSONObject.quote(requestId)
            val safePayload = org.json.JSONObject.quote(payload)
            val js = "window.LumaCryptoCallback && window.LumaCryptoCallback($safeRequestId, $success, $safePayload)"
            webView.evaluateJavascript(js, null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val uris = if (data?.clipData != null) {
                    Array(data.clipData!!.itemCount) { i ->
                        data.clipData!!.getItemAt(i).uri
                    }
                } else {
                    data?.data?.let { arrayOf(it) } ?: arrayOf()
                }
                fileChooserCallback?.onReceiveValue(uris)
            } else {
                fileChooserCallback?.onReceiveValue(null)
            }
            fileChooserCallback = null
        }

        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F5EFE2"))
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F5EFE2"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = filePathCallback
                    val mimeTypes: Array<String> = fileChooserParams?.acceptTypes
                        ?.filter { it.isNotBlank() }
                        ?.toTypedArray()
                        ?.takeIf { it.isNotEmpty() }
                        ?: arrayOf("application/json", "text/plain")
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = mimeTypes.firstOrNull() ?: "*/*"
                        if (mimeTypes.size > 1) {
                            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                        }
                    }
                    try {
                        fileChooserLauncher.launch(Intent.createChooser(intent, "选择文件"))
                    } catch (e: Exception) {
                        fileChooserCallback?.onReceiveValue(null)
                        fileChooserCallback = null
                        return false
                    }
                    return true
                }
            }
            addJavascriptInterface(LumaJsBridge(this@MainActivity), "LumaBridge")
        }
        container.addView(webView)

        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setContentView(container)

        container.post { ViewCompat.requestApplyInsets(container) }

        container.post {
            if (container.paddingTop == 0) {
                var statusBarHeight = 0
                val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
                if (resId > 0) statusBarHeight = resources.getDimensionPixelSize(resId)
                var navBarHeight = 0
                val navId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
                if (navId > 0) navBarHeight = resources.getDimensionPixelSize(navId)
                if (statusBarHeight > 0) {
                    container.setPadding(0, statusBarHeight, 0, navBarHeight)
                }
            }
        }

        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/luma/index.html")
        } else {
            webView.restoreState(savedInstanceState)
        }

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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
