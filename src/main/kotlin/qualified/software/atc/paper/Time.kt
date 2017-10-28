package qualified.software.atc.paper

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newSingleThreadContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal data class TimeParameters(val base: Int)

internal fun setupTime(params: TimeParameters): Time {
  return _Time(params).start()
}

internal interface Time {
  val current: Long
  fun tick(task: (Long) -> Unit)
}

private class _Time(private val params: TimeParameters): Time {
  private val t = AtomicLong(0)
  private val tickers = mutableListOf<(Long) -> Unit>()
  private val context = newSingleThreadContext("paper-atc-time")

  override val current: Long
    get() = t.get()

  fun start(): Time {
    launch(context) {
      while (true) {
        val time = t.getAndUpdate{
          if (it == Long.MAX_VALUE) {
            Long.MIN_VALUE
          } else {
            it.inc()
          }
        }
        tickers.forEach { it.invoke(time) }
        delay(params.base.toLong(), TimeUnit.SECONDS)
      }
    }
    return this
  }

  override fun tick(task: (Long) -> Unit) {
    launch(context) {
      tickers.add(task)
    }
  }
}
