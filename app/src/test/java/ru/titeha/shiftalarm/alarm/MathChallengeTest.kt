package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MathChallengeTest {

  @Test
  fun `генерирует ровно три примера`() {
    val problems = MathChallenge.generate(Random(1))
    assertEquals(MathChallenge.PROBLEM_COUNT, problems.size)
  }

  @Test
  fun `слагаемые двузначные, результат неотрицательный, ответ сходится`() {
    // Много сидов — проверяем инварианты по всему диапазону.
    for (seed in 0 until 500) {
      MathChallenge.generate(Random(seed)).forEach { p ->
        assertTrue("a двузначное: ${p.a}", p.a in 10..99)
        assertTrue("b двузначное: ${p.b}", p.b in 10..99)
        assertTrue("результат ≥ 0: ${p.text}", p.answer >= 0)
        assertEquals(if (p.plus) p.a + p.b else p.a - p.b, p.answer)
      }
    }
  }

  @Test
  fun `детерминированность по сидy`() {
    assertEquals(MathChallenge.generate(Random(42)), MathChallenge.generate(Random(42)))
  }

  @Test
  fun `текст примера показывает знак`() {
    assertEquals("50 + 20", MathProblem(50, 20, true).text)
    assertEquals("50 − 20", MathProblem(50, 20, false).text)
  }
}
