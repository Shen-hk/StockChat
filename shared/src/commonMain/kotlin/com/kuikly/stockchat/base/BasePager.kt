package com.kuikly.stockchat.base

import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.*
import com.kuikly.stockchat.cards.theme.StockChatTheme
import com.kuikly.stockchat.data.AppearancePrefs
import com.kuikly.stockchat.data.FontScale
import com.kuikly.stockchat.data.FontScaleRuntime
import com.kuikly.stockchat.data.ThemeMode
import com.kuikly.stockchat.data.resolveStockChatTheme
import com.kuikly.stockchat.glass.GlassRenderer

internal abstract class BasePager : Pager() {
    private var nightModel: Boolean? by observable(null)

    // 外观偏好（通用设置页写入，SharedPreferencesModule 持久化）。
    // 以本页 observable 承载：attr 闭包经 appTheme 读取它们，设置变更
    // 时本页实时换肤；其他页面在 pageDidAppear 时重读落盘值刷新。
    private var appearanceModeId: String by observable(ThemeMode.SYSTEM.id)
    private var appearanceFontScaleId: String by observable(FontScale.STANDARD.id)

    /** Native hosts select G1/G2/G3 and can refresh it after a sustained frame-rate drop. */
    private var hostGlassMode: String by observable("simplified")
    private var glassRefreshGeneration = 0

    protected val hostGlassRenderer: GlassRenderer
        get() = GlassRenderer.fromHostMode(hostGlassMode)

    override fun createExternalModules(): Map<String, Module>? {
        val externalModules = hashMapOf<String, Module>()
        externalModules[BridgeModule.MODULE_NAME] = BridgeModule()
        return externalModules
    }

    override fun created() {
        super.created()
        isNightMode()
        reloadAppearancePrefs()
        hostGlassMode = pageData.params.optString("glassMode")
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        // 首帧就绪后把最终主题明暗态同步给宿主（Android 状态栏图标方向）。
        // 此前 created/themeDidChanged 触发的同步都被门闩拦下，这里补首拍。
        statusBarSyncReady = true
        syncedStatusBarDark = null
        syncStatusBarIconsToHost()
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        // 从通用设置页返回时重读落盘偏好（设置页是独立 pager，跨页
        // 无响应式通道，只能靠生命周期同步）。
        reloadAppearancePrefs()
        val generation = ++glassRefreshGeneration
        refreshGlassMode(generation)
    }

    private fun reloadAppearancePrefs() {
        val prefs = acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
        appearanceModeId = prefs.getItem(AppearancePrefs.KEY_THEME_MODE).ifEmpty { ThemeMode.SYSTEM.id }
        appearanceFontScaleId = prefs.getItem(AppearancePrefs.KEY_FONT_SCALE).ifEmpty { FontScale.STANDARD.id }
        // fontSizeScaled/lineHeightScaled 扩展按 pagerId 查表取系数
        FontScaleRuntime.update(pagerId, FontScale.fromId(appearanceFontScaleId).factor)
        syncStatusBarIconsToHost()
    }

    /**
     * 把最终主题（系统夜间态 + App 内换肤覆盖）的明暗态同步给宿主，
     * Android 侧驱动状态栏图标方向；其他宿主静默忽略。去重：每个 pager
     * 只在明暗态翻转时真正发桥调用，避免 pageDidAppear 重复刷写。
     * 门闩：viewDidLoad 之前原生桥可能未挂载，桥调用会被丢且去重位已
     * 置值导致后续补同步跳过——故首拍同步固定在 viewDidLoad。
     */
    private fun syncStatusBarIconsToHost() {
        if (!statusBarSyncReady) return
        val dark = appIsDarkTheme()
        if (syncedStatusBarDark == dark) return
        syncedStatusBarDark = dark
        runCatching { acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).setStatusBarIconsDark(dark) }
    }

    private var syncedStatusBarDark: Boolean? = null
    private var statusBarSyncReady = false

    /**
     * 全 App 统一主题入口：系统夜间态 + 用户主题偏好 + 字号档位。
     * attr 闭包内经此读取的 observable（nightModel/两个外观偏好）是
     * 页面重渲染的驱动，与既有 isNightMode() 读取同一响应式通道。
     * 缓存以三个输入为 key：每次调用都先读齐三个 observable（保证
     * 响应式依赖注册，R1），命中则复用上次解析结果（避免每帧分配）。
     */
    protected fun appTheme(): StockChatTheme {
        val night = isNightMode()
        val mode = appearanceModeId
        val scale = appearanceFontScaleId
        val key = themeCacheKey
        if (key != null && themeCache != null && key.first == night && key.second == mode && key.third == scale) {
            return themeCache!!
        }
        val resolved = resolveStockChatTheme(night, mode, scale)
        themeCacheKey = Triple(night, mode, scale)
        themeCache = resolved
        return resolved
    }

    /** 当前最终主题是否为深色（显式偏好优先于系统态）。
     *  注意不能用 appTheme() == Dark 判等：返回值是 type 已按字号档缩放的
     *  copy，非标准档时与 Dark 单例永不相等。改比 page 底色（两套色板唯一）。 */
    protected fun appIsDarkTheme(): Boolean = appTheme().page == StockChatTheme.Dark.page

    /** 设置页选中态的响应式读取口（读 observable，attr 内读取即注册依赖）。 */
    protected fun appearanceThemeModeId(): String = appearanceModeId

    protected fun appearanceFontScaleId(): String = appearanceFontScaleId

    /** 设置页写入偏好后调用：重读落盘值并驱动本页重渲染。 */
    protected fun reloadAppearance() = reloadAppearancePrefs()

    private var themeCacheKey: Triple<Boolean, String, String>? = null
    private var themeCache: StockChatTheme? = null

    override fun pageDidDisappear() {
        glassRefreshGeneration++
        super.pageDidDisappear()
    }

    override fun pageWillDestroy() {
        glassRefreshGeneration++
        super.pageWillDestroy()
    }

    protected open fun hostGlassModeDidChange(renderer: GlassRenderer) = Unit

    private fun refreshGlassMode(generation: Int) {
        setTimeout(1_000) {
            if (generation != glassRefreshGeneration || !isAppeared || isWillDestroy()) return@setTimeout
            val candidate = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).getGlassMode()
            if (candidate == "realtime" || candidate == "snapshot" || candidate == "simplified") {
                if (candidate != hostGlassMode) {
                    hostGlassMode = candidate
                    hostGlassModeDidChange(hostGlassRenderer)
                }
            }
            refreshGlassMode(generation)
        }
    }

    override fun themeDidChanged(data: JSONObject) {
        super.themeDidChanged(data)
        nightModel = data.optBoolean(IS_NIGHT_MODE_KEY)
        // 系统昼夜切换：SYSTEM 模式下最终明暗态随之翻转，同步宿主状态栏
        syncStatusBarIconsToHost()
    }

    // 是否为夜间模式
    override fun isNightMode(): Boolean {
        if (nightModel == null) {
            nightModel = pageData.params.optBoolean(IS_NIGHT_MODE_KEY)
        }
        return nightModel!!
    }

    // 不开启调试UI模式
    override fun debugUIInspector(): Boolean {
        return false
    }

    companion object {
        const val IS_NIGHT_MODE_KEY = "isNightMode"
    }

}
