package com.wutsi.kokibot.skill

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillMatcherTest {
    val skill = SkillMetadata(
        name = "Test Skill",
        description = "A skill for testing",
        tools = emptyList(),
        categories = listOf("quality"),
        keywords = listOf("junit", "unit test"),
        requiredBins = listOf("java"),
        requiredEnv = listOf("HOME"),
    )

    @Test
    fun `matches by name`() {
        val matcher = SkillMatcher()
        assertTrue(matcher.matches("I want to use Test Skill", skill))
    }

    @Test
    fun `matches by keywords`() {
        val matcher = SkillMatcher()
        assertTrue(matcher.matches("I want to perform unit testings junit", skill))
    }

    @Test
    fun `matches by category`() {
        val matcher = SkillMatcher()
        assertTrue(matcher.matches("I want to test the code quality", skill))
    }

    @Test
    fun `no matches`() {
        val matcher = SkillMatcher()
        assertFalse(matcher.matches("Yo man", skill))
    }

    @Test
    fun `missing env`() {
        val xskill = skill.copy(requiredEnv = listOf("X0X0X0X"))

        val matcher = SkillMatcher()
        assertFalse(matcher.matches("I want to use Test Skill", xskill))
    }

    @Test
    fun `missing bin`() {
        val xskill = skill.copy(requiredBins = listOf("X0X0X0X"))

        val matcher = SkillMatcher()
        assertFalse(matcher.matches("I want to use Test Skill", xskill))
    }
}
