package uk.co.turingatemyhamster.digitalReading
package web

import corpus._

import org.scalajs.dom.{Event, HTMLDivElement}
import rx._
import rx.ops._

import scala.scalajs.js.annotation.JSExport
import scala.util.{Failure, Success}
import scalatags.JsDom
import scalatags.ext.SeqDiff.Entered
import scalatags.ext.{Updater, Framework}
import JsDom.all._
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

    corpusBrowser.modifyWith(storyListing.listing).render
    storyBrowser.modifyWith(storyListing.selectedStory.map(_.map(_.title))).render

    storyDB.all onComplete {
      case Success(s) =>
        stories() = s.stories.to[IndexedSeq]
      case Failure(t) =>
        throw t
    }
  }

}

case class StoryListing(stories: Rx[IndexedSeq[Story]]) {

  val selectedStory = Var(None : Option[Story])

  val rowUpdate = new Updater[Story] {
    override def onEntered(en: Entered[Story]) = {
      def tidyTags(tags: List[String]) = {
        val tidied = tags map (_.trim) filter (_.length > 0) mkString ", "
        if(tidied.length > 30)
          tidied.substring(0, 27) + "..."
        else
          tidied
      }
      val story = en.item
      Some(
        tr(Events.click := ((_: Event) => selectedStory() = Some(story)))(
          td(story.title),
          td(tidyTags(story.tags)),
          td(story.chapterIds.length),
          td(story.creationDate.toString),
          td(story.modificationDate.toString)
        )
      )
    }
  }

  val rows = stories updateWith rowUpdate

  val listing =
    div(`class` := "outer")(
      div(`class` := "innera")(
        table(
          caption("Stories"),
          thead(
            tr(
              th("Title"), th("Tags"), th("Chapters"), th("Created"), th("Last modified")
            )
          ),
          tfoot(
            tr(
              th("Title"), th("Tags"), th("Chapters"), th("Created"), th("Last modified")
            )
          ),
          tbody(
            rows
          )
        )
      )
    ).render

}