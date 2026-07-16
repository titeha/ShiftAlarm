package ru.titeha.shiftalarm.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorSetupTest {

  @Test
  fun `xiaomi и суббренды распознаются`() {
    for (m in listOf("Xiaomi", "xiaomi", "Redmi", "POCO")) {
      val guide = VendorSetup.forManufacturer(m)
      assertTrue("m=$m", guide != null && guide.vendorName.contains("Xiaomi"))
      assertTrue(guide!!.autostartComponents.any { it.contains("miui") })
      assertTrue(guide.steps.isNotEmpty())
    }
  }

  @Test
  fun `huawei oppo vivo samsung распознаются`() {
    assertTrue(VendorSetup.forManufacturer("HUAWEI")!!.vendorName.contains("Huawei"))
    assertTrue(VendorSetup.forManufacturer("OPPO")!!.vendorName.contains("Oppo"))
    assertTrue(VendorSetup.forManufacturer("vivo")!!.vendorName.contains("Vivo"))
    assertTrue(VendorSetup.forManufacturer("samsung")!!.steps.isNotEmpty())
  }

  @Test
  fun `неизвестный или пустой производитель — null`() {
    assertNull(VendorSetup.forManufacturer("Google"))
    assertNull(VendorSetup.forManufacturer(""))
    assertNull(VendorSetup.forManufacturer(null))
  }

  @Test
  fun `ссылка dontkillmyapp задана`() {
    assertEquals("https://dontkillmyapp.com", VendorSetup.DONT_KILL_MY_APP_URL)
  }
}
