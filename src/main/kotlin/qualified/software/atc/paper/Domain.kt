package qualified.software.atc.paper

import java.util.UUID

internal enum class TrackDirection {
  N, NE, E, SE, S, SW, W, NW;
}

internal data class GeographicPosition(val latitude: Int, val longitude: Int, val altitude: Int)

internal data class Track(
  val id: UUID,
  val direction: TrackDirection,
  val speed: Int,
  val position: GeographicPosition
)

internal object Projection {

  fun nextPosition(track: Track, speed: Int = track.speed): GeographicPosition {
    val position = when(track.direction) {
      TrackDirection.N ->
        track.position.copy(longitude = track.position.longitude + speed)
      TrackDirection.NE ->
        track.position.copy(longitude = track.position.longitude + speed, latitude = track.position.latitude + speed)
      TrackDirection.E ->
        track.position.copy(latitude = track.position.latitude + speed)
      TrackDirection.SE ->
        track.position.copy(longitude = track.position.longitude - speed, latitude = track.position.latitude + speed)
      TrackDirection.S ->
        track.position.copy(longitude = track.position.longitude - speed)
      TrackDirection.SW ->
        track.position.copy(longitude = track.position.longitude - speed, latitude = track.position.latitude - speed)
      TrackDirection.W ->
        track.position.copy(latitude = track.position.latitude - speed)
      TrackDirection.NW ->
        track.position.copy(longitude = track.position.longitude + speed, latitude = track.position.latitude - speed)
    }
    return position
  }

  fun positions(
    track: Track,
    latitudeIntLimit: IntLimit,
    longitudeIntLimit: IntLimit,
    speed: Int = track.speed
  ): List<GeographicPosition> {
    val positions = mutableListOf(track.position)
    do {
      positions.add(nextPosition(track.copy(position = positions.last()), speed))
    } while (
        latitudeIntLimit.isWithinRangeInclusive(positions.last().latitude) &&
        longitudeIntLimit.isWithinRangeInclusive(positions.last().longitude)
      )
    return positions
  }

}
