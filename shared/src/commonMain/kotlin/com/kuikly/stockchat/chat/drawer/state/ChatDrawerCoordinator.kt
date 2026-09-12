package com.kuikly.stockchat.chat.drawer.state

import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.Timer
import kotlin.math.abs

enum class DrawerGesturePhase { IDLE, DRAGGING, SETTLING }

/** Phase and offset stay atomic so the DSL consumes one animation driver per frame. */
data class DrawerGestureMotion(
    val phase: DrawerGesturePhase = DrawerGesturePhase.IDLE,
    val offsetX: Float = 0f,
)

internal interface ChatDrawerStatePort {
    var open: Boolean
    var mounted: Boolean
    var presented: Boolean
    var motion: DrawerGestureMotion
}

internal class ChatDrawerState : ChatDrawerStatePort {
    override var open: Boolean by observable(false)
    override var mounted: Boolean by observable(false)
    override var presented: Boolean by observable(false)
    override var motion: DrawerGestureMotion by observable(DrawerGestureMotion())
}

internal class PlainChatDrawerState : ChatDrawerStatePort {
    override var open = false
    override var mounted = false
    override var presented = false
    override var motion = DrawerGestureMotion()
}

internal fun interface DrawerScheduledTask { fun cancel() }

internal fun interface DrawerScheduler {
    fun schedule(delayMillis: Int, task: () -> Unit): DrawerScheduledTask
}

internal class KuiklyDrawerScheduler : DrawerScheduler {
    override fun schedule(delayMillis: Int, task: () -> Unit): DrawerScheduledTask {
        val timer = Timer()
        timer.schedule(delayMillis, delayMillis.coerceAtLeast(16)) {
            task()
            timer.cancel()
        }
        return DrawerScheduledTask(timer::cancel)
    }
}

internal enum class ChatDrawerEffect { BLUR_COMPOSER, HAPTIC_IMPACT, RESET_HISTORY_QUERY }

/** Sole owner of drawer presentation timing and horizontal gesture decisions. */
internal class ChatDrawerCoordinator(
    val state: ChatDrawerStatePort,
    private val scheduler: DrawerScheduler,
    private val onEffect: (ChatDrawerEffect) -> Unit,
) {
    private var open = false
    private var mounted = false
    private var presented = false
    private var motion = DrawerGestureMotion()
    private var presentationVersion = 0
    private var gestureStartX = 0f
    private var gestureLastDx = 0f
    private val tasks = mutableListOf<DrawerScheduledTask>()

    fun setOpen(target: Boolean) {
        if (target) onEffect(ChatDrawerEffect.BLUR_COMPOSER)
        setMotion(DrawerGestureMotion())
        val wasOpen = open
        val version = ++presentationVersion
        setOpenState(target)
        if (target) {
            setMounted(true)
            setPresented(false)
            onEffect(ChatDrawerEffect.RESET_HISTORY_QUERY)
            schedule(0) { if (presentationVersion == version) setPresented(true) }
            if (!wasOpen) schedule(395) {
                if (version == presentationVersion && open && presented) onEffect(ChatDrawerEffect.HAPTIC_IMPACT)
            }
        } else {
            setPresented(false)
            schedule(350) {
                if (presentationVersion == version && !presented) setMounted(false)
            }
            if (wasOpen) schedule(300) {
                if (version == presentationVersion && !open && !presented) onEffect(ChatDrawerEffect.HAPTIC_IMPACT)
            }
        }
    }

    fun onPan(phase: String, pageX: Float) {
        when (phase) {
            "start" -> {
                if (motion.phase != DrawerGesturePhase.IDLE) return
                gestureStartX = pageX
                gestureLastDx = 0f
                val base = if (open) 0f else -DRAWER_WIDTH
                if (!open) {
                    onEffect(ChatDrawerEffect.BLUR_COMPOSER)
                    setMounted(true)
                }
                setMotion(DrawerGestureMotion(DrawerGesturePhase.DRAGGING, base))
            }
            "move" -> {
                if (motion.phase != DrawerGesturePhase.DRAGGING) return
                val raw = if (open) pageX - gestureStartX else -DRAWER_WIDTH + pageX - gestureStartX
                val next = raw.coerceIn(-DRAWER_WIDTH, 0f)
                gestureLastDx = next - motion.offsetX
                setMotion(DrawerGestureMotion(DrawerGesturePhase.DRAGGING, next))
            }
            "end", "cancel" -> finishPan(phase)
        }
    }

    fun onDestroy() {
        ++presentationVersion
        tasks.forEach(DrawerScheduledTask::cancel)
        tasks.clear()
    }

    private fun finishPan(phase: String) {
        if (motion.phase != DrawerGesturePhase.DRAGGING) return
        val base = if (open) 0f else -DRAWER_WIDTH
        val travel = motion.offsetX - base
        val flickTowardClose = open && (gestureLastDx < 0f || motion.offsetX < 0f)
        val flickTowardOpen = !open && gestureLastDx >= OPEN_FLICK_DX
        when {
            phase == "cancel" -> settle(open)
            abs(travel) < ACCIDENTAL_DRAG && !flickTowardClose && !flickTowardOpen -> cancelGesture()
            else -> {
                val progress = (motion.offsetX + DRAWER_WIDTH) / DRAWER_WIDTH
                settle(if (open) progress >= 0.5f && !flickTowardClose else progress >= 0.5f || flickTowardOpen)
            }
        }
    }

    private fun cancelGesture() {
        setMotion(DrawerGestureMotion())
        if (!open) setMounted(false)
    }

    private fun settle(targetOpen: Boolean) {
        onEffect(ChatDrawerEffect.HAPTIC_IMPACT)
        setMotion(DrawerGestureMotion(DrawerGesturePhase.SETTLING, if (targetOpen) 0f else -DRAWER_WIDTH))
        val version = ++presentationVersion
        setOpenState(targetOpen)
        setMounted(true)
        setPresented(targetOpen)
        if (!targetOpen) schedule(375) {
            if (version == presentationVersion && !presented) setMounted(false)
        }
        schedule(440) {
            if (motion.phase == DrawerGesturePhase.SETTLING) setMotion(DrawerGestureMotion())
        }
    }

    private fun schedule(delay: Int, block: () -> Unit) {
        tasks += scheduler.schedule(delay, block)
    }

    private fun setOpenState(value: Boolean) { open = value; state.open = value }
    private fun setMounted(value: Boolean) { mounted = value; state.mounted = value }
    private fun setPresented(value: Boolean) { presented = value; state.presented = value }
    private fun setMotion(value: DrawerGestureMotion) { motion = value; state.motion = value }

    private companion object {
        const val DRAWER_WIDTH = 292f
        const val OPEN_FLICK_DX = 9f
        const val ACCIDENTAL_DRAG = 8f
    }
}
