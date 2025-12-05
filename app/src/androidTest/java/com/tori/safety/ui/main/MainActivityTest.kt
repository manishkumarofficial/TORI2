package com.tori.safety.ui.main

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tori.safety.R
import com.tori.safety.ui.contacts.ContactsActivity
import com.tori.safety.ui.monitoring.MonitoringActivity
import com.tori.safety.ui.settings.SettingsActivity
import com.tori.safety.ui.triplog.TripLogActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun testStartMonitoringButton_launchesMonitoringActivity() {
        onView(withId(R.id.btn_start_monitoring)).perform(click())
        intended(hasComponent(MonitoringActivity::class.java.name))
    }

    @Test
    fun testSettingsButton_launchesSettingsActivity() {
        onView(withId(R.id.btn_settings)).perform(click())
        intended(hasComponent(SettingsActivity::class.java.name))
    }

    @Test
    fun testTripLogCard_launchesTripLogActivity() {
        onView(withId(R.id.card_trip_log)).perform(click())
        intended(hasComponent(TripLogActivity::class.java.name))
    }

    @Test
    fun testContactsCard_launchesContactsActivity() {
        onView(withId(R.id.card_contacts)).perform(click())
        intended(hasComponent(ContactsActivity::class.java.name))
    }
}
