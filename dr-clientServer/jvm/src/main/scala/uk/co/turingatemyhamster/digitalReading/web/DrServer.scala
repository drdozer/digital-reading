package uk.co.turingatemyhamster.digitalReading.web

import uk.co.turingatemyhamster.digitalReading.corpus._

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

  implicit val ec = ExecutionContext.Implicits.global

  def autowireService(router: AutowireServer.Router): spray.routing.Route = path(Segments) { s =>
    extract(_.request.entity.asString) { e =>
      complete {
        router(
          autowire.Core.Request(s, upickle.read[Map[String, String]](e)))
      }
    }
  }

  println("Initializing corpus db")
  val startup = for {
    _ <- rawStatsDB.corpusMeanStdev
    _ = println("Starting server")
    serverStartup <- startServer(interface = "0.0.0.0", port = 9300) {
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
        }
      } ~
        pathPrefix("public") {
          get {
            getFromResourceDirectory("public")
          }
        } ~
        post {
          pathPrefix("api") {
            respondWithMediaType(MediaTypes.`application/json`) {
              pathPrefix("stopWords") {
                autowireService(AutowireServer.stopWordsDB)
              } ~
              pathPrefix("corpus") {
                autowireService(AutowireServer.corpusDB)
              } ~
              pathPrefix("rawWords") {
                autowireService(AutowireServer.rawWordsDB)
              } ~
              pathPrefix("filteredWords") {
                autowireService(AutowireServer.filteredWordsDB)
              } ~
              pathPrefix("rawStats") {
                autowireService(AutowireServer.rawStatsDB)
              } ~
              pathPrefix("filteredStats") {
                autowireService(AutowireServer.filteredStatsDB)
              }
            }

          }
        }
    }
  } yield serverStartup

  startup.onComplete {
    case Success(b) =>
      println(s"Successfully bound to ${b.localAddress}")
    case Failure(ex) =>
      println(ex.getMessage)
      system.shutdown()
  }

  override lazy val stopWordsDB = ResourceLinesStopWordsDB("/public/data/english.stopwords.txt")

  lazy val corpusBaseDir = args.head
  override lazy val corpusDB = OnDiskCorpusDB(corpusBaseDir)

  override lazy val rawWordsDB = RawWordsDB(corpusDB)

  override lazy val filteredWordsDB = FilteredWordsDB(rawWordsDB, stopWordsDB)

  override lazy val rawStatsDB = CorpusStatsFromDB(corpusDB, rawWordsDB)

  override lazy val filteredStatsDB = CorpusStatsFromDB(corpusDB, filteredWordsDB)
}
