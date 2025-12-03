package com.pakeandroid.paketabs

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment

class BrowserTabFragment : Fragment() {

    interface TabHost {
        fun openNewTab(url: String)
        fun updateTabTitle(tabId: Long, title: String)
    }

    private var tabHost: TabHost? = null
    private var webView: WebView? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tabHost = when {
            parentFragment is TabHost -> parentFragment as TabHost
            context is TabHost -> context
            else -> null
        }
    }

    override fun onDetach() {
        super.onDetach()
        tabHost = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_browser_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val url = requireArguments().getString(ARG_INITIAL_URL).orEmpty()
        setupWebView(view.findViewById(R.id.webView))
        val restoredState = savedInstanceState?.getBundle(WEBVIEW_STATE)
        if (restoredState != null) {
            webView?.restoreState(restoredState)
        } else {
            webView?.loadUrl(url)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val state = Bundle()
        webView?.saveState(state)
        outState.putBundle(WEBVIEW_STATE, state)
    }

    override fun onDestroyView() {
        webView?.destroy()
        webView = null
        super.onDestroyView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(target: WebView) {
        webView = target.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    tabHost?.updateTabTitle(tabId, title.orEmpty())
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    if (request.hasGesture() && request.isForMainFrame) {
                        val targetUrl = request.url.toString()
                        tabHost?.openNewTab(targetUrl)
                        return true
                    }
                    return false
                }

                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    val hasGesture = view.hitTestResult?.type != WebView.HitTestResult.UNKNOWN_TYPE
                    return if (hasGesture) {
                        tabHost?.openNewTab(url)
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }

    private val tabId: Long
        get() = requireArguments().getLong(ARG_TAB_ID)

    /**
     * 加载指定的URL
     */
    fun loadUrl(url: String) {
        webView?.loadUrl(url)
    }

    /**
     * 刷新当前页面
     */
    fun reload() {
        webView?.reload()
    }

    companion object {
        private const val ARG_TAB_ID = "tab_id"
        private const val ARG_INITIAL_URL = "initial_url"
        private const val WEBVIEW_STATE = "webview_state"

        fun newInstance(tabId: Long, initialUrl: String): BrowserTabFragment {
            return BrowserTabFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TAB_ID, tabId)
                    putString(ARG_INITIAL_URL, initialUrl)
                }
            }
        }
    }
}

