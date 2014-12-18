package uk.co.turingatemyhamster.digitalReading.web

import uk.co.turingatemyhamster.digitalReading.corpus.{OnDiskStoryDB, StoryDB}

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.ActorSystem
import spray.http.{StatusCodes, MediaTypes}
import spray.routing.SimpleRoutingApp

import scala.util.{Failure, Success}

/**
 *
 *
 * @author Matthew Pocock
 */
object DrServer extends App with SimpleRoutingApp with StaticContent with RestApi with Templates {

  implicit val system = ActorSystem("dr-server")

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
        path("stories") {
          complete(`api/stories`)
        }
      }
    } ~
      respondWithMediaType(MediaTypes.`application/json`) {
        pathPrefix("public") {
          get {
            getFromResourceDirectory("public")
          }
        }
      }
  }.onComplete {
    case Success(b) =>
      println(s"Successfully bound to ${b.localAddress}")
    case Failure(ex) =>
      println(ex.getMessage)
      system.shutdown()
  }

  override def corpus = StoryDB.cache(OnDiskStoryDB(args.head))
}
