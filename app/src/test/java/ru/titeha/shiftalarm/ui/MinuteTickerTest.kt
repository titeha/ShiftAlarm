package ru.titeha.shiftalarm.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class MinuteTickerTest {
    @Test
    fun `начало минуты обнуляет секунды и наносекунды`() {
        val source = LocalDateTime.of(
            2026,
            7,
            15,
            10,
            24,
            37,
            456_000_000
        )

        assertEquals(
            LocalDateTime.of(
                2026,
                7,
                15,
                10,
                24
            ),
            minuteStart(source)
        )
    }

    @Test
    fun `на точной границе до следующей минуты остаётся шестьдесят секунд`() {
        val source = LocalDateTime.of(
            2026,
            7,
            15,
            10,
            24,
            0,
            0
        )

        assertEquals(
            60_000L,
            millisUntilNextMinute(source)
        )
    }

    @Test
    fun `в середине минуты учитываются секунды и миллисекунды`() {
        val source = LocalDateTime.of(
            2026,
            7,
            15,
            10,
            24,
            30,
            500_000_000
        )

        assertEquals(
            29_500L,
            millisUntilNextMinute(source)
        )
    }

    @Test
    fun `за одну миллисекунду до границы возвращается одна миллисекунда`() {
        val source = LocalDateTime.of(
            2026,
            7,
            15,
            10,
            24,
            59,
            999_000_000
        )

        assertEquals(
            1L,
            millisUntilNextMinute(source)
        )
    }

    @Test
    fun `наносекунды меньше миллисекунды не дают нулевую задержку`() {
        val source = LocalDateTime.of(
            2026,
            7,
            15,
            10,
            24,
            59,
            999_999_999
        )

        assertEquals(
            1L,
            millisUntilNextMinute(source)
        )
    }
}
