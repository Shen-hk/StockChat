package com.kuikly.stockchat

import android.content.Context
import android.content.Intent
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.tencent.kuikly.core.render.android.IKuiklyRenderExport
import com.tencent.kuikly.core.render.android.adapter.KuiklyRenderAdapterManager
import com.tencent.kuikly.core.render.android.css.ktx.toMap
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegatorDelegate
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegator
import com.kuikly.stockchat.adapter.KRColorParserAdapter
import com.kuikly.stockchat.adapter.KRComposerCursorHandler
import com.kuikly.stockchat.adapter.KRFontAdapter
import com.kuikly.stockchat.adapter.KRImageAdapter
import com.kuikly.stockchat.adapter.KRLogAdapter
import com.kuikly.stockchat.adapter.KRRouterAdapter
import com.kuikly.stockchat.adapter.KRThreadAdapter
import com.kuikly.stockchat.adapter.KRUncaughtExceptionHandlerAdapter
import com.kuikly.stockchat.module.KRBridgeModule
import com.kuikly.stockchat.module.KRShareModule
import org.json.JSONObject

class KuiklyRenderActivity : AppCompatActivity(), KuiklyRenderViewBaseDelegatorDelegate {

    private lateinit var hrContainerView: ViewGroup
    private lateinit var loadingView: View
    private lateinit var errorView: View
    private var glassMode = "simplified"

    private val kuiklyRenderViewDelegator = KuiklyRenderViewBaseDelegator(this)

    /**
     * 「大且快右向横滑 → 抽屉展开」的页面回调（KRBridgeModule
     * registerDrawerFlingHost 注册）。纯观测的 dispatchTouchEvent 侦察命中后
     * 在此通知 Kuikly 页面，是否真开抽屉由页面守卫。onDestroy 清空防泄漏。
     */
    internal var drawerFlingHost: KuiklyRenderCallback? = null
    private val drawerFlingDetector = DrawerFlingDetector {
        drawerFlingHost?.invoke(emptyMap<String, Any>())
    }

    /**
     * 输入栏媒体选择结果回调（KRBridgeModule registerComposerMediaResult 注册）。
     * 图库/拍照/文档的 onActivityResult 命中后交由 KRBridgeModule 处理（复制到
     * 缓存等耗时操作），完成后 invoke 通知 Kuikly 页面。onDestroy 清空防泄漏。
     */
    internal var composerMediaResultHost: KuiklyRenderCallback? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // 只读不消费：侦察器永不返回 true/拦截，Kuikly 视图层触摸流不受影响。
        drawerFlingDetector.onTouchEvent(
            ev,
            hrContainerView.takeIf { ::hrContainerView.isInitialized }?.width ?: 0,
            resources.displayMetrics.density,
        )
        return super.dispatchTouchEvent(ev)
    }

    private val pageName: String
        get() {
            val pn = intent.getStringExtra(KEY_PAGE_NAME) ?: ""
            return if (pn.isNotEmpty()) {
                return pn
            } else {
                "ChatPage"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_hr)
        setupImmersiveMode()
        hrContainerView = findViewById(R.id.hr_container)
        loadingView = findViewById(R.id.hr_loading)
        errorView = findViewById(R.id.hr_error)
        glassMode = preferredGlassMode()
        liveActivities.add(this)
        kuiklyRenderViewDelegator.onAttach(hrContainerView, "", pageName, createPageData())
    }

    override fun onDestroy() {
        super.onDestroy()
        liveActivities.remove(this)
        drawerFlingHost = null
        composerMediaResultHost = null
        kuiklyRenderViewDelegator.onDetach()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        KRBridgeModule.handleComposerMediaResult(this, requestCode, resultCode, data)
    }

    override fun onPause() {
        super.onPause()
        KRBridgeModule.cancelActiveVoiceRecording()
        kuiklyRenderViewDelegator.onPause()
    }

    override fun onResume() {
        super.onResume()
        glassMode = preferredGlassMode()
        kuiklyRenderViewDelegator.onResume()
    }

    override fun registerExternalModule(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalModule(kuiklyRenderExport)
        with(kuiklyRenderExport) {
            moduleExport(KRBridgeModule.MODULE_NAME) {
                KRBridgeModule()
            }
            moduleExport(KRShareModule.MODULE_NAME) {
                KRShareModule()
            }
        }
    }

    override fun registerExternalRenderView(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalRenderView(kuiklyRenderExport)
        with(kuiklyRenderExport) {
            viewPropExternalHandlerExport(KRComposerCursorHandler)
        }
    }

    private fun createPageData(): Map<String, Any> {
        val pageData = argsToMap()
        pageData["appId"] = 1
        pageData["glassMode"] = glassMode
        return pageData
    }

    private fun preferredGlassMode(): String {
        // Accessibility takes priority over the visual-priority setting.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()) {
            return "simplified"
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "realtime" else "snapshot"
    }

    fun currentGlassMode(): String = glassMode

    private fun argsToMap(): MutableMap<String, Any> {
        val jsonStr = intent.getStringExtra(KEY_PAGE_DATA) ?: return mutableMapOf()
        return JSONObject(jsonStr).toMap()
    }

    private fun setupImmersiveMode() {
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            window?.statusBarColor = Color.TRANSPARENT
            window?.decorView?.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

    }

    companion object {

        private const val KEY_PAGE_NAME = "pageName"
        private const val KEY_PAGE_DATA = "pageData"

        /**
         * 存活的 KuiklyRenderActivity 注册表（主线程读写，onCreate/onDestroy
         * 成对增删，index 0 恒为任务根的对话页——冷启动默认打开 ChatPage）。
         */
        private val liveActivities = mutableListOf<KuiklyRenderActivity>()

        /** 问AI收敛的延迟：等新对话页入场动画（~0.3s）走完再收旧页。 */
        private const val ASK_AI_COLLAPSE_DELAY_MS = 400L

        init {
            initKuiklyAdapter()
        }

        fun start(context: Context, pageName: String, pageData: JSONObject) {
            val starter = Intent(context, KuiklyRenderActivity::class.java)
            starter.putExtra(KEY_PAGE_NAME, pageName)
            starter.putExtra(KEY_PAGE_DATA, pageData.toString())
            context.startActivity(starter)
            collapseForAskAiChatIfNeeded(pageName, pageData)
        }

        /**
         * 「问AI」打开的对话页不参与无限叠层：等新对话页入场动画走完（约
         * 0.4s，与 iOS 宿主同参）后收掉任务里根对话页之外、当前对话页之下
         * 的所有旧页，栈深封顶为「根对话页 + 当前对话页」。否则「详情 ⇄
         * 问AI到对话」反复横跳会把历史页一层层压栈，系统返回要逐页退完
         * 所有旧页才能回到最初。根对话页实例保留（不被销毁），新对话页按
         * 返回即回到它。
         */
        private fun collapseForAskAiChatIfNeeded(pageName: String, pageData: JSONObject) {
            if (pageName != "ChatPage" || pageData.optString("openedViaAskAi") != "1") return
            // 立即 finish 会让来源页提前播退场动画、与新页入场叠成双重跳变，
            // 延迟到入场动画结束后再收；届时新 Chat 已入注册表，所以保留首
            // 尾两个（根对话 + 当前对话），只收中间的旧页。
            Handler(Looper.getMainLooper()).postDelayed({
                liveActivities.drop(1).dropLast(1).forEach { it.finish() }
            }, ASK_AI_COLLAPSE_DELAY_MS)
        }

        private fun initKuiklyAdapter() {
            with(KuiklyRenderAdapterManager) {
                krImageAdapter = KRImageAdapter(KRApplication.application)
                krLogAdapter = KRLogAdapter
                krUncaughtExceptionHandlerAdapter = KRUncaughtExceptionHandlerAdapter
                krFontAdapter = KRFontAdapter
                krColorParseAdapter = KRColorParserAdapter(KRApplication.application)
                krRouterAdapter = KRRouterAdapter
                krThreadAdapter = KRThreadAdapter()
            }
        }
    }
}
