package com.example.ai_quota_monitor_android.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A service page that is really a login page must be recognised, otherwise the card silently
 * stops updating: the step waits out its whole timeout every cycle and the user is never told
 * to log in again.
 */
class LoginPageDetectionTest {

    @Test
    fun `openrouter sign-in page is a login page`() {
        assertTrue(
            isLoginUrl("https://openrouter.ai/sign-in?redirect_url=https%3A%2F%2Fopenrouter.ai%2Fsettings%2Fcredits"),
        )
    }

    @Test
    fun `hyphen-free and underscore spellings are login pages too`() {
        assertTrue(isLoginUrl("https://example.test/signin"))
        assertTrue(isLoginUrl("https://example.test/sign_in"))
    }

    @Test
    fun `the usual login paths are recognised`() {
        assertTrue(isLoginUrl("https://github.com/login"))
        assertTrue(isLoginUrl("https://chatgpt.com/auth/login"))
        assertTrue(isLoginUrl("https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(isLoginUrl("https://example.test/sso/start"))
        assertTrue(isLoginUrl("https://example.test/sessions/new"))
    }

    @Test
    fun `matching ignores case`() {
        assertTrue(isLoginUrl("https://OpenRouter.ai/Sign-In"))
    }

    @Test
    fun `the monitored data pages are not login pages`() {
        assertFalse(isLoginUrl("https://openrouter.ai/settings/credits"))
        assertFalse(isLoginUrl("https://openrouter.ai/activity"))
        assertFalse(isLoginUrl("https://claude.ai/new#settings/usage"))
        assertFalse(isLoginUrl("https://platform.claude.com/settings/billing"))
        assertFalse(isLoginUrl("https://platform.openai.com/settings/organization/billing/overview"))
        assertFalse(isLoginUrl("https://github.com/settings/copilot"))
        assertFalse(isLoginUrl("https://chatgpt.com/settings/usage?tab=overview"))
    }
}
