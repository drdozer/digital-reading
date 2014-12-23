package uk.co.turingatemyhamster.digitalReading
package web

import java.util.concurrent.Future

import corpus._

import org.scalajs.dom.Event
import rx._
import rx.ops._

import scala.util.{Failure, Success}
import scalatags.JsDom
import scalatags.ext.SeqDiff.Entered
import scalatags.ext.{ScoreFunction, Updater, Framework}
import JsDom.all._
//import JsDom.attrs.{`class` => _, _}
import Framework._
import Updater._

/**
 *
 *
 * @author Matthew Pocock
 */
case class StoryListing(stories: Rx[IndexedSeq[Story]]) {

  val selectedStory = Var(None : Option[Story])

  implicit val storyScoreFunction: ScoreFunction[Story] = new ScoreFunction[Story] {
    override def indelCost(t: Story) = -1

    override def matchCost(t1: Story, t2: Story) = if(t1.storyId == t2.storyId) 0 else -3
  }

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
      tr(Events.click := ((_: Event) => selectedStory() = Some(story)),
        `class` := Rx { if(selectedStory() contains story) "selected" else "" })(
          td(story.title),
          td(tidyTags(story.tags)),
          td(story.chapterIds.length),
          td(story.creationDate.toString),
          td(story.modificationDate.toString)
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
