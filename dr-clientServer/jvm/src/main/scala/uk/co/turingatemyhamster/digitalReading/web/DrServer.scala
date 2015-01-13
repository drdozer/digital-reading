package uk.co.turingatemyhamster.digitalReading.web

import spray.routing.directives.OnSuccessFutureMagnet
import uk.co.turingatemyhamster.digitalReading.corpus.{OnDiskStoryDB, StoryDB}

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import spray.http.{StatusCodes, MediaTypes}
import spray.routing._
import akka.util.Timeout
import scala.concurrent.duration._

import scala.util.{Failure, Success}

/**
 *
 *
 * @author Matthew Pocock
 */
object DrServer extends App with SimpleRoutingApp with StaticContent with RestApi with Templates {

  implicit val system = ActorSystem("dr-server")

  implicit val timeout: Timeout = Timeout(5 seconds)

  override implicit val ec = ExecutionContext.Implicits.global

  override val corpus = StoryDB.cache(OnDiskStoryDB(args.head))

  println("Initializing corpus db")
  corpus.allMeanStdev

  println("Starting server")
  startServer(interface = "localhost", port = 9300) {
    get {
      respondWithMediaType(MediaTypes.`text/html`) {
        (path("index.html") | pathSingleSlash) {
          complete {
            index.render
          }
        } ~
          pathPrefix("digital_reading_proposal") {
            pathEnd {
              redirect("/digital_reading_proposal/", StatusCodes.PermanentRedirect)
            } ~
              (path("index.html") | pathSingleSlash) {
                complete(`digital_reading_proposal/index`.render)
              } ~
              path("wattpad100.html") {
                complete(`digital_reading_proposal/wattpad100`.render)
              }
          }
      } ~
        pathPrefix("api") {
          respondWithMediaType(MediaTypes.`application/json`) {
            path("stories") {
              complete(`api/stories`)
            } ~
              path("chapter_word_counts" / LongNumber / Segment ) { (chapterId: Long, preserveCase : String) =>
                complete(`api/chapter_word_counts`(chapterId, preserveCase == "true"))
              } ~
              path("all_word_counts" / Segment) { (preserveCase: String) =>
                complete(`api/all_word_counts`(preserveCase == "true"))
              } ~
              path("all_mean_stdev") {
                complete(`api/all_mean_stdev`)
              }
          }
        }
    } ~
      pathPrefix("public") {
        get {
          getFromResourceDirectory("public")
        }
      }
  }.onComplete {
    case Success(b) =>
      println(s"Successfully bound to ${b.localAddress}")
    case Failure(ex) =>
      println(ex.getMessage)
      system.shutdown()
  }
}
