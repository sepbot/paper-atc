package qualified.software.atc.paper

import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newSingleThreadContext
import java.util.Random
import java.util.UUID

internal data class SimulationParameters(
  val time: Time,
  val consumer: (SimulatedEnvironment) -> Unit,
  val maxTracks: Int,
  val speedLimit: Int,
  val latitudeLimit: IntLimit,
  val longitudeLimit: IntLimit,
  val altitudeLimit: IntLimit
)

internal fun setupSimulator(params: SimulationParameters): Simulator {
  return _Simulator(params).start()
}

internal data class SimulatedEnvironment(val tracks: Map<UUID, Track>)

internal interface Simulator

private fun Random.direction(): TrackDirection {
  val values = TrackDirection.values()
  return TrackDirection.valueOf(values[nextInt(values.size)].toString())
}

private fun Random.nextInt(lower: Int, upper: Int): Int {
  return nextInt(upper - lower) + lower
}

private class _Simulator(private val params: SimulationParameters): Simulator {
  private val context = newSingleThreadContext("paper-atc-simulator")
  private var tracks = mapOf<UUID, Track>()
  private val random = Random()

  init {
    val newTracks = mutableMapOf<UUID, Track>()
    while (newTracks.size <= params.maxTracks) {
      newTracks[UUID.randomUUID()] = newTrack()
    }
    tracks = newTracks
  }

  private fun newTrack(): Track {
    return Track(
      id = UUID.randomUUID(),
      direction = random.direction(),
      speed = random.nextInt(1, params.speedLimit),
      position = GeographicPosition(
        latitude = random.nextInt(params.latitudeLimit.lower, params.latitudeLimit.upper),
        longitude = random.nextInt(params.longitudeLimit.lower, params.longitudeLimit.upper),
        altitude = random.nextInt(params.altitudeLimit.lower, params.altitudeLimit.upper)
      )
    )
  }

  fun start(): Simulator {
    params.time.tick {
      launch(context) {
        val updated = tracks.mapValues { entry ->
          val track = entry.value
          return@mapValues track.copy(position = Projection.nextPosition(track, track.speed))
        }.filterValues { track ->
          return@filterValues params.latitudeLimit.isWithinRangeInclusive(track.position.latitude) &&
                              params.longitudeLimit.isWithinRangeInclusive(track.position.longitude)
        }.toMutableMap()
        while (updated.size <= params.maxTracks) {
          updated[UUID.randomUUID()] = newTrack()
        }
        tracks = updated
        params.consumer.invoke(SimulatedEnvironment(tracks))
      }
    }
    return this
  }

}
