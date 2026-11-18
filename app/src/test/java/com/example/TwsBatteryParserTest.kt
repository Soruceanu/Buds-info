package com.example

import com.example.bluetooth.TwsBatteryParser
import com.example.model.BatteryProtocol
import com.example.model.EarbudBrandPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwsBatteryParserTest {

  @Test
  fun `decodeBatteryByte decodes percentage and charging flag`() {
    assertEquals(85 to false, TwsBatteryParser.decodeBatteryByte(85))
    assertEquals(42 to true, TwsBatteryParser.decodeBatteryByte(0x80 or 42))
    assertEquals(null to false, TwsBatteryParser.decodeBatteryByte(0x7F))
    assertEquals(null to false, TwsBatteryParser.decodeBatteryByte(0xFF))
  }

  @Test
  fun `parseFastPairData decodes LTV battery notification frame`() {
    // Version 0x00, header 0x33 (len=3, type=3), L=90% not charging, R=85% charging, Case=60% charging
    val payload =
      byteArrayOf(
        0x00,
        0x33,
        90.toByte(),
        (0x80 or 85).toByte(),
        (0x80 or 60).toByte(),
      )
    val parsed = TwsBatteryParser.parseFastPairData(payload)
    assertNotNull(parsed)
    assertEquals(90, parsed!!.leftPercent)
    assertEquals(85, parsed.rightPercent)
    assertEquals(60, parsed.casePercent)
    assertFalse(parsed.isLeftCharging)
    assertTrue(parsed.isRightCharging)
    assertTrue(parsed.isCaseCharging)
    assertNull(parsed.isLidOpen)
    assertEquals(BatteryProtocol.FAST_PAIR_BLE, parsed.protocol)
  }

  @Test
  fun `parseFastPairData marks type 0x04 as closed lid`() {
    // Version 0x00, header 0x34 (len=3, type=4 = Hide UI / lid closed)
    val payload =
      byteArrayOf(
        0x00,
        0x34,
        (0x80 or 90).toByte(),
        85.toByte(),
        70.toByte(),
      )
    val parsed = TwsBatteryParser.parseFastPairData(payload)
    assertNotNull(parsed)
    assertEquals(false, parsed!!.isLidOpen)
    assertEquals(90, parsed.leftPercent)
    assertEquals(85, parsed.rightPercent)
    assertEquals(70, parsed.casePercent)
  }

  @Test
  fun `parseXiaomiBleData decodes FastConnect frame`() {
    // 12-byte Xiaomi FastConnect style packet with L/R/Case at offset 8..10
    val payload =
      byteArrayOf(
        0x01,
        0x02,
        0x03,
        0x04,
        0x05,
        0x06,
        0x07,
        0x01,
        95.toByte(),
        (0x80 or 72).toByte(),
        50.toByte(),
        0x00,
      )
    val parsed = TwsBatteryParser.parseXiaomiBleData(payload)
    assertNotNull(parsed)
    assertEquals(95, parsed!!.leftPercent)
    assertEquals(72, parsed.rightPercent)
    assertEquals(50, parsed.casePercent)
    assertTrue(parsed.isRightCharging)
    assertEquals(true, parsed.isLidOpen)
    assertEquals(BatteryProtocol.XIAOMI_BLE, parsed.protocol)
  }

  @Test
  fun `parseXiaomiBleData decodes single bud when other bud and case are 0x7F`() {
    val payload =
      byteArrayOf(
        0x01,
        0x02,
        0x03,
        0x04,
        0x05,
        0x06,
        0x07,
        0x00,
        0x7F.toByte(), // left bud inside closed case
        85.toByte(),   // right bud active
        0x7F.toByte(), // closed case
        0x00,
      )
    val parsed = TwsBatteryParser.parseXiaomiBleData(payload)
    assertNotNull(parsed)
    assertNull(parsed!!.leftPercent)
    assertEquals(85, parsed.rightPercent)
    assertNull(parsed.casePercent)
  }

  @Test
  fun `parseAppleProximityData decodes L R Case nibbles`() {
    val payload = ByteArray(12)
    payload[0] = 0x07
    payload[5] = 0x20 // not flipped: bud1=left, bud2=right
    payload[6] = ((8 shl 4) or 7).toByte() // left=80%, right=70%
    payload[7] = ((0x04 shl 4) or 6).toByte() // case charging, case=60%
    payload[8] = 0x01 // lid open

    val parsed = TwsBatteryParser.parseAppleProximityData(payload)
    assertNotNull(parsed)
    assertEquals(80, parsed!!.leftPercent)
    assertEquals(70, parsed.rightPercent)
    assertEquals(60, parsed.casePercent)
    assertTrue(parsed.isCaseCharging)
    assertEquals(true, parsed.isLidOpen)
  }

  @Test
  fun `parseIphoneAccEv decodes HFP battery level`() {
    val args: Array<Any> = arrayOf(2, 1, 7, 2, 1) // level 7 -> 80%, dock charging -> true
    val parsed = TwsBatteryParser.parseIphoneAccEv(args)
    assertNotNull(parsed)
    assertEquals(80, parsed!!.leftPercent)
    assertEquals(80, parsed.rightPercent)
    assertNull(parsed.casePercent)
    assertTrue(parsed.isLeftCharging)
  }

  @Test
  fun `guessBrandPreset identifies Xiaomi and Redmi models`() {
    assertEquals(EarbudBrandPreset.XIAOMI_REDMI, TwsBatteryParser.guessBrandPreset("Redmi Buds 5 Pro"))
    assertEquals(EarbudBrandPreset.XIAOMI_REDMI, TwsBatteryParser.guessBrandPreset("Xiaomi Buds 4"))
    assertEquals(EarbudBrandPreset.FAST_PAIR, TwsBatteryParser.guessBrandPreset("JBL Tune Beam"))
  }

  @Test
  fun `quantizeToFive steps percentages every 5 percent`() {
    assertNull(TwsBatteryParser.quantizeToFive(null))
    assertEquals(100, TwsBatteryParser.quantizeToFive(100))
    assertEquals(100, TwsBatteryParser.quantizeToFive(98))
    assertEquals(95, TwsBatteryParser.quantizeToFive(97))
    assertEquals(95, TwsBatteryParser.quantizeToFive(95))
    assertEquals(90, TwsBatteryParser.quantizeToFive(92))
    assertEquals(90, TwsBatteryParser.quantizeToFive(90))
    assertEquals(5, TwsBatteryParser.quantizeToFive(5))
    assertEquals(0, TwsBatteryParser.quantizeToFive(0))
  }
}
