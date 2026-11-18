package com.example.bluetooth

import com.example.model.BatteryProtocol
import com.example.model.EarbudBrandPreset
import java.util.Locale

data class ParsedBatteryPayload(
  val leftPercent: Int?,
  val rightPercent: Int?,
  val casePercent: Int?,
  val isLeftCharging: Boolean = false,
  val isRightCharging: Boolean = false,
  val isCaseCharging: Boolean = false,
  val isLidOpen: Boolean? = null,
  val protocol: BatteryProtocol,
)

object TwsBatteryParser {

  const val FAST_PAIR_UUID = "0000fe2c-0000-1000-8000-00805f9b34fb"
  const val XIAOMI_MI_SERVICE_UUID = "0000fe95-0000-1000-8000-00805f9b34fb"
  const val XIAOMI_FAST_CONNECT_UUID = "0000fd2d-0000-1000-8000-00805f9b34fb"
  const val BATTERY_SERVICE_UUID = "0000180f-0000-1000-8000-00805f9b34fb"
  const val BATTERY_LEVEL_CHAR_UUID = "00002a19-0000-1000-8000-00805f9b34fb"

  const val APPLE_MANUFACTURER_ID = 0x004C
  const val XIAOMI_MANUFACTURER_ID = 0x038F
  const val XIAOMI_ALT_MANUFACTURER_ID = 0x0157

  fun guessBrandPreset(deviceName: String?): EarbudBrandPreset {
    val name = deviceName?.lowercase(Locale.ROOT).orEmpty()
    return when {
      name.contains("xiaomi") ||
        name.contains("redmi") ||
        name.contains("poco") ||
        name.contains("airdots") ||
        name.contains("mi true") ||
        name.contains("buds") && name.contains("mi") -> EarbudBrandPreset.XIAOMI_REDMI
      name.contains("airpods") || name.contains("beats") -> EarbudBrandPreset.APPLE_CLONE
      name.contains("pixel buds") ||
        name.contains("jbl") ||
        name.contains("sony") ||
        name.contains("nothing") ||
        name.contains("realme") ||
        name.contains("oneplus") -> EarbudBrandPreset.FAST_PAIR
      else -> EarbudBrandPreset.AUTO
    }
  }

  /**
   * Quantizes raw battery percentages to 5% steps (100, 95, 90, ..., 5, 0)
   * so widget updates only fire on meaningful 5% changes and save battery.
   */
  fun quantizeToFive(percent: Int?): Int? {
    if (percent == null) return null
    val clamped = percent.coerceIn(0, 100)
    return (((clamped + 2) / 5) * 5).coerceIn(0, 100)
  }

  /**
   * Decodes a standard 1-byte TWS battery byte where bit 7 (0x80) = charging flag
   * and bits 0..6 (0x7F) = battery percentage (0..100). 0x7F means unknown / not in range.
   */
  fun decodeBatteryByte(rawByte: Int): Pair<Int?, Boolean> {
    val unsigned = rawByte and 0xFF
    if (unsigned == 0xFF || (unsigned and 0x7F) == 0x7F) return null to false
    val charging = (unsigned and 0x80) != 0
    val pct = unsigned and 0x7F
    return if (pct in 0..100) pct to charging else null to false
  }

  /**
   * Google Fast Pair Service Data (0xFE2C).
   * Supports LTV-encoded Fast Pair advertising frames containing Battery Notification (type 0x03/0x04)
   * as well as direct 3-byte [left, right, case] battery tuples.
   */
  fun parseFastPairData(data: ByteArray): ParsedBatteryPayload? {
    if (data.size < 3) return null

    // Direct 3-byte [L, R, Case] tuple
    if (data.size == 3) {
      return buildFromThreeBytes(
        data[0].toInt(),
        data[1].toInt(),
        data[2].toInt(),
        BatteryProtocol.FAST_PAIR_BLE,
      )
    }

    // LTV scan starting at offset 0 or 1 (byte 0 can be flags/version 0x00 or 0x02)
    for (startOffset in 0..1) {
      var idx = startOffset
      while (idx < data.size) {
        val header = data[idx].toInt() and 0xFF
        val length = (header ushr 4) and 0x0F
        val type = header and 0x0F
        if (length == 0) {
          idx += 1
          continue
        }
        if ((type == 0x03 || type == 0x04) && length == 3 && idx + 3 < data.size) {
          val parsed =
            buildFromThreeBytes(
              data[idx + 1].toInt(),
              data[idx + 2].toInt(),
              data[idx + 3].toInt(),
              BatteryProtocol.FAST_PAIR_BLE,
            )
          if (parsed != null) {
            return if (type == 0x04) parsed.copy(isLidOpen = false) else parsed
          }
        }
        idx += 1 + length
      }
    }
    return null
  }

  /**
   * Xiaomi / Redmi Buds BLE advertisement parser for Service Data (0xFD2D / 0xFE95)
   * and Manufacturer Data (0x038F / 0x0157), including Redmi Buds 4/5/6 Pro.
   */
  fun parseXiaomiBleData(data: ByteArray): ParsedBatteryPayload? {
    if (data.size < 4) return null

    // XiaoMi FastConnect (0xFD2D / 0x038F) common TWS frame patterns:
    // 3 consecutive battery bytes [L, R, Case] in payload (indices 6..14 depending on model/version)
    // where each valid byte has pct in 0..100 or 0x7F unknown.
    val firstByte = data[0].toInt() and 0xFF
    if (data.size >= 10 && firstByte in listOf(0x01, 0x02, 0x03, 0x20, 0x21, 0x41, 0x42)) {
      for (offset in listOf(8, 9, 10, 7, 11, 6, 12)) {
        if (offset + 2 < data.size) {
          val bL = data[offset].toInt() and 0xFF
          val bR = data[offset + 1].toInt() and 0xFF
          val bC = data[offset + 2].toInt() and 0xFF
          if (bL == 0 && bR == 0 && bC == 0) continue
          val candidate = buildFromThreeBytes(bL, bR, bC, BatteryProtocol.XIAOMI_BLE)
          val hasBothBuds = candidate?.leftPercent != null && candidate.rightPercent != null
          val hasSingleBudWithExplicitSentinel =
            candidate != null &&
              ((candidate.leftPercent != null) xor (candidate.rightPercent != null)) &&
              (bL == 0x7F || bR == 0x7F || candidate.casePercent != null)
          if (hasBothBuds || hasSingleBudWithExplicitSentinel) {
            val lidByte = data.getOrNull(offset - 1)?.toInt()?.and(0xFF)
            val isLidOpen = if (lidByte != null && (lidByte and 0x01) != 0) true else null
            return candidate!!.copy(isLidOpen = isLidOpen)
          }
        }
      }
    }

    // Pattern B: MiBeacon 0xFE95 frame with battery object ID (0x100A -> len 1)
    if (data.size >= 8) {
      for (i in 0..(data.size - 5)) {
        val b0 = data[i].toInt() and 0xFF
        val b1 = data[i + 1].toInt() and 0xFF
        // Object ID 0x100A (little-endian: 0x0A, 0x10) -> length 1, value 0..100
        if (b0 == 0x0A && b1 == 0x10 && i + 3 < data.size && (data[i + 2].toInt() and 0xFF) == 1) {
          val pct = data[i + 3].toInt() and 0xFF
          if (pct in 0..100) {
            return ParsedBatteryPayload(
              leftPercent = pct,
              rightPercent = pct,
              casePercent = null,
              protocol = BatteryProtocol.XIAOMI_BLE,
            )
          }
        }
      }
    }

    return null
  }

  /**
   * Apple / Universal TWS Proximity BLE (Manufacturer ID 0x004C, Type 0x07).
   */
  fun parseAppleProximityData(data: ByteArray): ParsedBatteryPayload? {
    if (data.size < 10) return null
    val packetType = data[0].toInt() and 0xFF
    if (packetType != 0x07) return null

    val statusFlags = data[5].toInt() and 0xFF
    val flipped = (statusFlags and 0x20) == 0

    val budsByte = data[6].toInt() and 0xFF
    val caseAndCharging = data[7].toInt() and 0xFF

    val rawBud1 = (budsByte ushr 4) and 0x0F
    val rawBud2 = budsByte and 0x0F
    val rawCase = caseAndCharging and 0x0F
    val chargeFlags = (caseAndCharging ushr 4) and 0x0F

    val bud1Pct = if (rawBud1 in 0..10) rawBud1 * 10 else null
    val bud2Pct = if (rawBud2 in 0..10) rawBud2 * 10 else null
    val casePct = if (rawCase in 0..10) rawCase * 10 else null

    if (bud1Pct == null && bud2Pct == null && casePct == null) return null

    val bud1Charging = (chargeFlags and 0x01) != 0
    val bud2Charging = (chargeFlags and 0x02) != 0
    val caseCharging = (chargeFlags and 0x04) != 0

    val leftPct = if (flipped) bud2Pct else bud1Pct
    val rightPct = if (flipped) bud1Pct else bud2Pct
    val leftChg = if (flipped) bud2Charging else bud1Charging
    val rightChg = if (flipped) bud1Charging else bud2Charging

    return ParsedBatteryPayload(
      leftPercent = leftPct,
      rightPercent = rightPct,
      casePercent = casePct,
      isLeftCharging = leftChg,
      isRightCharging = rightChg,
      isCaseCharging = caseCharging,
      isLidOpen = (data[8].toInt() and 0xFF) != 0,
      protocol = BatteryProtocol.APPLE_PROXIMITY_BLE,
    )
  }

  /**
   * Parses HFP vendor-specific AT+IPHONEACCEV args array.
   * Format: [numPairs, key1, value1, key2, value2, ...]
   */
  fun parseIphoneAccEv(args: Array<Any>?): ParsedBatteryPayload? {
    if (args == null || args.size < 3) return null
    val ints = args.mapNotNull { (it as? Number)?.toInt() }
    if (ints.size < 3) return null
    val count = ints[0].coerceAtMost((ints.size - 1) / 2)
    var batteryPct: Int? = null
    var charging = false
    for (i in 0 until count) {
      val key = ints[1 + i * 2]
      val value = ints[2 + i * 2]
      when (key) {
        1 -> if (value in 0..9) batteryPct = (value + 1) * 10
        2 -> charging = value == 1
      }
    }
    if (batteryPct == null) return null
    return ParsedBatteryPayload(
      leftPercent = batteryPct,
      rightPercent = batteryPct,
      casePercent = null,
      isLeftCharging = charging,
      isRightCharging = charging,
      protocol = BatteryProtocol.BLUETOOTH_CLASSIC,
    )
  }

  private fun buildFromThreeBytes(
    rawLeft: Int,
    rawRight: Int,
    rawCase: Int,
    protocol: BatteryProtocol,
  ): ParsedBatteryPayload? {
    val (leftPct, leftChg) = decodeBatteryByte(rawLeft)
    val (rightPct, rightChg) = decodeBatteryByte(rawRight)
    val (casePct, caseChg) = decodeBatteryByte(rawCase)
    if (leftPct == null && rightPct == null && casePct == null) return null
    return ParsedBatteryPayload(
      leftPercent = leftPct,
      rightPercent = rightPct,
      casePercent = casePct,
      isLeftCharging = leftChg,
      isRightCharging = rightChg,
      isCaseCharging = caseChg,
      protocol = protocol,
    )
  }
}
