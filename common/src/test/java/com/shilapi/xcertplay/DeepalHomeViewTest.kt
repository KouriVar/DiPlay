package com.shilapi.xcertplay

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class DeepalHomeViewTest {
    @Test fun theFourCardsAndEntryButtonKeepTheirOwnActions() {
        val calls = mutableListOf<String>()
        val home = DeepalHomeView(RuntimeEnvironment.getApplication(),
            onHome = { calls += "home" }, onChoosePhone = { calls += "choose" },
            onConnect = { calls += "connect" }, onSettings = { calls += "settings" })
        for (label in listOf("车机主页", "选择设备", "连接手机", "软件设置")) {
            descendants(home).single { it.contentDescription == label }.performClick()
        }
        descendants(home).filterIsInstance<TextView>().single { it.text == "进入 CarPlay" }.performClick()
        assertEquals(listOf("home", "choose", "connect", "settings", "connect"), calls)
        assertFalse(descendants(home).filterIsInstance<TextView>().any { "USB" in it.text })
        home.releaseBackground()
    }

    @Test fun connectionStateUsesTheSavedPhoneAndShowsAuthenticationErrors() {
        val home = DeepalHomeView(RuntimeEnvironment.getApplication(), {}, {}, {}, {})
        home.updateState("Test iPhone", "已准备连接", true, null, false)
        assertTrue(descendants(home).filterIsInstance<TextView>().any { it.text == "Test iPhone" })
        home.updateState(null, "需要检查设置", false, "Authentication unavailable", true)
        val texts = descendants(home).filterIsInstance<TextView>().toList()
        assertFalse(texts.any { it.text == "Test iPhone" })
        assertTrue(texts.any { it.text == "连接同一 Wi-Fi" })
        assertTrue(texts.any { it.text == "Authentication unavailable" && it.visibility == View.VISIBLE })
        assertFalse(texts.single { it.text == "进入 CarPlay" }.isEnabled)
        home.releaseBackground()
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
}
