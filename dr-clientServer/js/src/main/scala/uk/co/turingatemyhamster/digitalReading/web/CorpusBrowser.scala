package uk.co.turingatemyhamster.digitalReading
package web

import corpus._
import org.scalajs.dom.extensions.Ajax

import org.scalajs.dom.{Element, Event, HTMLDivElement}
import rx._
import rx.ops._

import scala.scalajs.js.annotation.JSExport
import scala.util.{Failure, Success}
import scalatags.JsDom
import scalatags.ext.SeqDiff.Entered
import scalatags.ext.{Updater, Framework}
import JsDom.all._
//import JsDom.attrs.{`class` => _, _}
import Framework._
import Updater._

/**
 *
 *
 * @author Matthew Pocock
 */
@JSExport(name = "CorpusBrowser")
object CorpusBrowser {
  import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
  lazy val storyDB = StoryDB.cache(RemoteStoryDB())

  @JSExport
  def wire(corpusBrowser: HTMLDivElement, storyBrowser: HTMLDivElement): Unit = {

    val stories = Var(IndexedSeq.empty[Story])
    val storyListing = StoryListing(stories)
    corpusBrowser.modifyWith(
      title := "Select a story",
      storyListing.listing).render

    val stopWords = Var(Set.empty[String])

    val allMeanStdev = Var(MeanStdev(Map.empty, Map.empty))

    def updateStoryBrowser: (Option[Story], Option[Story]) => Option[Frag] = {
      val browser = Var(None : Option[Frag])
      val storyRx = Var(None : Option[Story])

      (s1 : Option[Story], s2 : Option[Story]) => (s1, s2) match {
        case (None, None) =>
          browser() = None
          storyRx() = None
          None
        case (None, Some(story)) =>
          storyRx() = Some(story)
          val sb = StoryBrowser(storyDB, stopWords, allMeanStdev, storyRx filter (_.isDefined) map (_.get))
          browser() = Some(sb.browser)
          browser()
        case (Some(oldStory), Some(newStory)) if (oldStory != newStory) =>
          storyRx() = Some(newStory)
          browser()
        case (Some(oldStory), None) =>
          browser() = None
          storyRx() = None
          None
      }
    }

    storyBrowser.modifyWith(storyListing.selectedStory.diff[Option[Frag]] (updateStoryBrowser, _ => None)).render

    storyDB.all onComplete {
      case Success(s) =>
        stories() = s.stories.to[IndexedSeq]
      case Failure(t) =>
        throw new IllegalStateException("Unable to fetch all counts", t)
    }

    storyDB.allMeanStdev onComplete {
      case Success(s) =>
        allMeanStdev() = s
      case Failure(t) =>
        throw new IllegalStateException("Unable to fetch all mean and stdev values", t)
    }

    Ajax.get("/public/data/english.stopwords.txt") map (_.responseText.split("\n").map(_.trim())) onComplete {
      case Success(words) => stopWords() = words.to[Set]
      case Failure(t) =>
        throw new IllegalStateException("Unable to fetch stopwords", t)
    }
  }

}

