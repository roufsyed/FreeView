package com.rouf.freeview

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** On-device checks that the FAQ screen lists every question and expands answers on tap. */
@RunWith(AndroidJUnit4::class)
class FaqActivityTest {

    @Test
    fun launches_andListsAllQuestions() {
        ActivityScenario.launch(FaqActivity::class.java).use { scenario ->
            scenario.onActivity {
                val list = it.findViewById<RecyclerView>(R.id.faq_list)
                assertEquals(14, list.adapter?.itemCount)
            }
            onView(withText(R.string.faq_q_about)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun tappingQuestion_expandsAnswer() {
        ActivityScenario.launch(FaqActivity::class.java).use {
            onView(withText(R.string.faq_q_about)).perform(click())
            onView(withText(R.string.faq_a_about)).check(matches(isDisplayed()))
        }
    }
}
