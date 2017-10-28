package qualified.software.atc.paper

import java.nio.ByteBuffer

internal data class IntLimit(val lower: Int, val upper: Int) {
  fun isWithinRangeInclusive(number: Int): Boolean {
    return number in lower..upper
  }
}

internal fun ByteBuffer.getArray(): ByteArray {
  if (this.hasArray()) {
    return this.array()
  } else {
    val array = ByteArray(this.remaining())
    val pos = this.position()
    this.get(array)
    this.position(pos)
    return array
  }
}
