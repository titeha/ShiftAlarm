package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.titeha.shiftalarm.data.AlarmEntity

/**
 * AUD-10 (R-02): чистое решение по отметке «разовый прозвонил до разблокировки».
 * Вынесено из Android-обёртки, чтобы reconcile покрывался тестами без Robolectric.
 */
class OneShotReconcileTest {

  private fun alarm(id: Long, deleteAfterFiring: Boolean, enabled: Boolean = true) =
    AlarmEntity(id = id, deleteAfterFiring = deleteAfterFiring, enabled = enabled)

  @Test
  fun `отметка с deleteAfterFiring - удалить`() {
    val a = alarm(1, deleteAfterFiring = true)
    assertEquals(FiredOneShotAction.Delete(a), firedOneShotAction(a))
  }

  @Test
  fun `отметка без deleteAfterFiring - выключить`() {
    val a = alarm(2, deleteAfterFiring = false)
    assertEquals(FiredOneShotAction.Disable(a), firedOneShotAction(a))
  }

  @Test
  fun `будильник отсутствует - только снять отметку`() {
    assertEquals(FiredOneShotAction.ClearMarkOnly, firedOneShotAction(null))
  }

  @Test
  fun `повторное применение по выключенному будильнику идемпотентно`() {
    val a = alarm(3, deleteAfterFiring = false, enabled = false)
    // Повторный reconcile не ломает: решение то же, действие безопасно применяется снова.
    assertEquals(FiredOneShotAction.Disable(a), firedOneShotAction(a))
    assertEquals(FiredOneShotAction.Disable(a), firedOneShotAction(a))
  }

  @Test
  fun `несколько отметок обрабатываются независимо`() {
    val actions = listOf(
      firedOneShotAction(alarm(10, deleteAfterFiring = true)),
      firedOneShotAction(alarm(11, deleteAfterFiring = false)),
      firedOneShotAction(null),
    )
    assertTrue(actions[0] is FiredOneShotAction.Delete)
    assertTrue(actions[1] is FiredOneShotAction.Disable)
    assertEquals(FiredOneShotAction.ClearMarkOnly, actions[2])
  }
}
