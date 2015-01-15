package uk.co.turingatemyhamster.digitalReading
package web

import corpus._
import org.scalajs.dom

import org.scalajs.dom.HTMLDivElement
import rx._
import rx.ops._

import scala.concurrent.Future
import scala.scalajs.js.annotation.JSExport
import scala.util.{Failure, Success}
import scalatags.JsDom
import scalatags.ext.{Updater, Framework}
import JsDom.all._
import Framework._
import Updater._
import autowire._

/**
 *
 *
 * @author Matthew Pocock
 */
@JSExport(name = "CorpusBrowser")
object CorpusBrowser {
  type UpickleClientProxy[T] = autowire.ClientProxy[T, String, upickle.Reader, upickle.Writer]

  import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow

  case class AutowireClient(apiName: String) extends autowire.Client[String, upickle.Reader, upickle.Writer] {

    override def doCall(req: Request): Future[String] = {
      println(s"Servicing request $req")
        dom.extensions.Ajax.post(
          url = s"/api/$apiName/${req.path.mkString("/")}",
          data = upickle.write(req.args)
        ).map(_.responseText)
      }

    def read[Result: upickle.Reader](p: String) = upickle.read[Result](p)
    def write[Result: upickle.Writer](r: Result) = upickle.write(r)
  }

  lazy val stopWords = AutowireClient("stopWords").apply[StopWordsDB]
  lazy val corpus = AutowireClient("corpus").apply[CorpusDB]
  lazy val rawWords = AutowireClient("rawWords").apply[WordsDB]
  lazy val filteredWords = AutowireClient("filteredWords").apply[WordsDB]
  lazy val rawStats = AutowireClient("rawStats").apply[CorpusStatsDB]
  lazy val filteredStats = AutowireClient("filteredStats").apply[CorpusStatsDB]
  lazy val rawSurprise = AutowireClient("rawSurprise").apply[SurpriseDB]

  @JSExport
  def wire(corpusBrowser: HTMLDivElement, storyBrowser: HTMLDivElement): Unit = {

    println("Wiring corpus browser")

    val stories = Var(IndexedSeq.empty[Story])
    val storyListing = StoryListing(stories)
    corpusBrowser.modifyWith(
      title := "Select a story",
      storyListing.listing).render

    def storyBrowserUpdater: (Option[Story], Option[Story]) => Option[Frag] = {
      val browser = Var(None : Option[Frag])
      val storyRx = Var(None : Option[Story])

      (s1 : Option[Story], s2 : Option[Story]) => (s1, s2) match {
        case (None, None) =>
          browser() = None
          storyRx() = None
          None
        case (None, Some(story)) =>
          storyRx() = Some(story)
          val sb = StoryBrowser(rawStats, filteredStats, rawSurprise, storyRx filter (_.isDefined) map (_.get))
          browser() = Some(sb.browser)
          browser()
        case (Some(oldStory), Some(newStory)) if oldStory == newStory =>
          browser()
        case (Some(oldStory), Some(newStory)) if oldStory != newStory =>
          storyRx() = Some(newStory)
          browser()
        case (Some(oldStory), None) =>
          browser() = None
          storyRx() = None
          None
      }
    }

    storyBrowser.modifyWith(
      storyListing.selectedStory.diff[Option[Frag]] (storyBrowserUpdater, _ => None)).render

    corpus.stories().call() onComplete {
      case Success(s) =>
        stories() = s.stories.to[IndexedSeq]
      case Failure(t) =>
        throw new IllegalStateException("Unable to fetch all counts", t)
    }

    println("Wired corpus browser")
  }

}

