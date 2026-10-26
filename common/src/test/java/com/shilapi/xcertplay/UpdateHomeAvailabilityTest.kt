package com.shilapi.xcertplay

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.update.UpdateAvailability
import com.shilapi.xcertplay.update.UpdateRelease
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en-w900dp-h600dp-land")
class UpdateHomeAvailabilityTest {
    private val application: Application = RuntimeEnvironment.getApplication()

    @After fun clear() = UpdateAvailability.clearAllForTest(application)

    @Test fun cachedUpdateOpensTheExistingDownloadFlowFromDeepalSettings() {
        val release = release()
        UpdateAvailability.save(application, release)
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get()
        try {
            assertFalse(homeHasUpdateChip(activity))
            openAboutFromHome(activity)
            val update = downloadButton(activity)
            assertTrue(update.isClickable)
            assertTrue(update.isFocusable)
            assertEquals(SettingsCategory.ABOUT, ReflectionHelpers.getField<SettingsCategory>(activity, "settingsCategory"))
            assertEquals(release, ReflectionHelpers.getField<UpdateRelease>(activity, "updateRelease"))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Config(sdk = [29], qualifiers = "en-w400dp-h900dp-port")
    @Test fun compactHomeKeepsItsLayoutAndTheUpdateIsAvailableInSettings() {
        UpdateAvailability.save(application, release())
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get()
        try {
            assertFalse(homeHasUpdateChip(activity))
            openAboutFromHome(activity)
            val update = downloadButton(activity)
            // The existing update button retains a 60 dp touch target.
            assertTrue(update.layoutParams.height >= (48 * activity.resources.displayMetrics.density).toInt())
            assertTrue(update.isClickable)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun anUpdateFoundInTheBackgroundAppearsInSettingsAfterReturningHome() {
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get()
        try {
            assertFalse(homeHasUpdateChip(activity))
            controller.pause().stop()
            UpdateAvailability.save(application, release())
            controller.start().resume()
            assertFalse(homeHasUpdateChip(activity))
            openAboutFromHome(activity)
            assertTrue(downloadButton(activity).isShown)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun homeHasUpdateChip(activity: DiPlayActivity) =
        descendants(activity.window.decorView).filterIsInstance<TextView>().any {
            it.text == activity.getString(R.string.update_available_home)
        }

    private fun openAboutFromHome(activity: DiPlayActivity) {
        descendants(activity.window.decorView).first {
            it.contentDescription == "软件设置" ||
                (it is android.widget.Button && it.text == activity.getString(R.string.settings))
        }.performClick()
        descendants(activity.window.decorView).single {
            it.contentDescription == activity.getString(R.string.settings_open_category,
                activity.getString(R.string.about))
        }.performClick()
    }

    private fun downloadButton(activity: DiPlayActivity) =
        descendants(activity.window.decorView).filterIsInstance<TextView>().single {
            it.text == activity.getString(R.string.update_download)
        }

    private fun release() = UpdateRelease(
        tagName = "v99.0.0",
        apkName = "DiPlay.apk",
        apkUrl = "https://example.com/DiPlay.apk",
        checksumsUrl = "https://example.com/SHA256SUMS.txt",
    )

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            yieldAll(descendants(view.getChildAt(index)))
        }
    }
}
