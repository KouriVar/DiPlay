package com.shilapi.xcertplay

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en-w1920dp-h1080dp-land")
class DeepalUpstreamIntegrationTest {
    @Test fun deepalHomeOpensTheNewSettingsAndReleasesItsBackground() {
        val context = RuntimeEnvironment.getApplication()
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java,
            Intent(context, DiPlayActivity::class.java).putExtra("page", "home")).setup()
        val activity = controller.get()
        try {
            val home = descendants(activity.window.decorView).filterIsInstance<DeepalHomeView>().single()
            descendants(home).single { it.contentDescription == "软件设置" }.performClick()
            assertEquals("settings", ReflectionHelpers.getField<String>(activity, "page"))
            assertTrue(ReflectionHelpers.getField<Boolean>(home, "released"))
            assertTrue(descendants(activity.window.decorView).any {
                it.contentDescription == activity.getString(R.string.settings_search)
            })
            activity.onBackPressedDispatcher.onBackPressed()
            assertTrue(descendants(activity.window.decorView).any { it is DeepalHomeView })
        } finally {
            controller.pause().stop().destroy()
            AppAppearanceRuntime.resetForTest()
        }
    }

    @Test fun deepalStateStillUsesTheSavedPhoneAfterUpstreamMerge() {
        val context = RuntimeEnvironment.getApplication()
        DiPlayPreferences.savePhone(context, "00:11:22:33:44:55", "Deepal iPhone")
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java,
            Intent(context, DiPlayActivity::class.java).putExtra("page", "home")).setup()
        try {
            val activity = controller.get()
            val home = descendants(activity.window.decorView).filterIsInstance<DeepalHomeView>().single()
            assertTrue(descendants(home).filterIsInstance<TextView>().any { it.text == "Deepal iPhone" })
            CarPlayBackgroundSession.active = true
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "refreshStatus")
            // Authentication errors take priority in source-only builds.
            val setupError = ReflectionHelpers.getField<String?>(activity, "setupError")
            assertTrue(descendants(home).filterIsInstance<TextView>().any {
                it.text == if (setupError == null) "CarPlay 已连接" else activity.getString(R.string.setup_needs_attention)
            })
        } finally {
            CarPlayBackgroundSession.clear()
            controller.pause().stop().destroy()
            context.getSharedPreferences("diplay", 0).edit().clear().commit()
            AppAppearanceRuntime.resetForTest()
        }
    }

    @Test fun oldDefaultCarNameMigratesButPersonalNamesStayIntact() {
        val context = RuntimeEnvironment.getApplication()
        try {
            AirPlayPersistence.saveOemLabel(context, "BYD")
            assertEquals("深蓝汽车", AirPlayPersistence.loadOemLabel(context))
            AirPlayPersistence.saveOemLabel(context, "My car")
            assertEquals("My car", AirPlayPersistence.loadOemLabel(context))
        } finally {
            AirPlayPersistence.saveOemLabel(context, AirPlayPersistence.DEFAULT_OEM_LABEL)
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
