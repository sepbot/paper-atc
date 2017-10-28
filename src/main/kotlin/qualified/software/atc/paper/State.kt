package qualified.software.atc.paper

import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newSingleThreadContext
import java.util.UUID

internal data class StateParameters(
  val time: Time,
  val latitudeIntLimit: IntLimit,
  val longitudeIntLimit: IntLimit,
  val altitudeIntLimit: IntLimit
)

internal fun setupState(params: StateParameters): State {
  return _State(params)
}

internal sealed class StateInstruction {
  abstract val action: String
  abstract val id: UUID

  data class Create(
    override val id: UUID,
    val from: GeographicPosition,
    val to: GeographicPosition,
    val direction: TrackDirection,
    val duration: Int
  ): StateInstruction() {
    override val action = "create"
  }

  data class Update(
    override val id: UUID,
    val from: GeographicPosition,
    val to: GeographicPosition,
    val direction: TrackDirection,
    val duration: Int
  ): StateInstruction() {
    override val action = "update"
  }

  data class Delete(override val id: UUID): StateInstruction() {
    override val action = "delete"
  }
}

internal interface State {
  fun update(snapshot: List<Track>)
  fun register(id: UUID, consumer: (List<StateInstruction>) -> Unit)
  fun deregister(id: UUID)
}

private data class Subscriber(
  var last: Map<UUID, Track>,
  val projection: MutableList<GeographicPosition>,
  val consumer: (List<StateInstruction>) -> Unit
)

private class _State(private val params: StateParameters): State {
  private val subscribers = mutableMapOf<UUID, Subscriber>()
  private val snapshots = LinkedHashMap<UUID, List<Track>>()
  private val context = newSingleThreadContext("paper-atc-state")

  override fun register(id: UUID, consumer: (List<StateInstruction>) -> Unit) {
    subscribers[id] = Subscriber(mapOf(), mutableListOf(), consumer)
  }

  override fun deregister(id: UUID) {
    subscribers.remove(id)
  }

  override fun update(snapshot: List<Track>) {
    launch(context) {
      val snapshotId = UUID.randomUUID()
      val tracks = snapshot.map { it.id to it }.toMap()
      subscribers.forEach { _, sub ->
        val instructions = mutableListOf<StateInstruction>()
        snapshot.forEach { track ->
          if (!sub.last.containsKey(track.id)) {
            val projection = Projection.positions(
              track = track,
              latitudeIntLimit = params.latitudeIntLimit,
              longitudeIntLimit = params.longitudeIntLimit
            )
            instructions.add(
              StateInstruction.Create(
                id = track.id,
                direction = track.direction,
                from = track.position,
                to = projection.last(),
                duration = projection.size
              )
            )
          } else {
            // TODO: Update Projection
          }
        }
        sub.last.forEach { _, (id) ->
          if (!tracks.containsKey(id)) {
            instructions.add(StateInstruction.Delete(id = id))
          }
        }
        sub.last = tracks
        if (instructions.isNotEmpty()) {
          sub.consumer.invoke(instructions)
        }
      }
    }
  }

}
