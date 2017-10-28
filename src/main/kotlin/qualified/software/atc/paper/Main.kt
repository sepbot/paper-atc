package qualified.software.atc.paper

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import java.io.File
import java.util.concurrent.TimeUnit

private data class StartupArguments(val httpPort: Int, val publicDir: File)

private val latitudeLimit = IntLimit(0, 50)
private val longitudeLimit = IntLimit(0, 50)
private val altitudeLimit = IntLimit(3, 20)

fun main(args: Array<String>) = runBlocking<Unit> {
  val startup = parseArgs(args)
  val time = setupTime(TimeParameters(
    base = 10
  ))
  val state = setupState(StateParameters(
    time = time,
    latitudeIntLimit = latitudeLimit,
    longitudeIntLimit = longitudeLimit,
    altitudeIntLimit = altitudeLimit
  ))
  val server = setupServer(ServerParameters(
    state = state,
    webPort = startup.httpPort,
    publicDir = startup.publicDir
  ))
  setupSimulator(SimulationParameters(
    time = time,
    consumer = server::simulate,
    maxTracks = 2000,
    speedLimit = 2,
    latitudeLimit = latitudeLimit,
    longitudeLimit = longitudeLimit,
    altitudeLimit = altitudeLimit
  ))
  delay(10, TimeUnit.SECONDS)
}

private fun parseArgs(args: Array<String>): StartupArguments {
  val httpPort = if (args.contains("--http-port")) {
    args[args.indexOf("--http-port") + 1].toInt()
  } else {
    8989
  }
  val publicDir = if (args.contains("--public-dir")) {
    File(args[args.indexOf("--public-dir") + 1])
  } else {
    File(".")
  }
  return StartupArguments(
    httpPort = httpPort,
    publicDir = publicDir
  )
}
