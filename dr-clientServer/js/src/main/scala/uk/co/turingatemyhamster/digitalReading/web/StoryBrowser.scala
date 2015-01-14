package uk.co.turingatemyhamster.digitalReading
package web

import corpus._

import org.scalajs.dom.{Element, Event, HTMLDivElement}
import rx._
import rx.ops._
import sbolv.RelativePosition

import scala.concurrent.Future
import scala.scalajs.js.annotation.JSExport
import scala.util.{Failure, Success}
import scalatags.JsDom
import scalatags.ext.SeqDiff.Entered
import scalatags.ext.{Updater, Framework}
import JsDom.all._
//import JsDom.attrs.{`class` => _, _}
import Framework._
import Updater._
import JsDom.{svgTags => svg, svgAttrs => svga}

/**
 *
 *
 * @author Matthew Pocock
 */
case class StoryBrowser(rawStatsDB: CorpusStatsDB,
                        filteredStatsDB: CorpusStatsDB,
                        story: Rx[Story]) {
  import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow

  val rawFrequencies:       Var[Either[String, WordFrequency]] = Var(Left("Fetching"))
  val filteredFrequencies:  Var[Either[String, WordFrequency]] = Var(Left("Fetching"))
  val scaledBySupprise:     Var[Either[String, WordFrequency]] = Var(Left("Fetching"))
  val stopFrames:           Var[Either[String, List[WordFrequency]]] = Var(Left("Fetching"))
  
//  val chapterWordCountWorker = Obs(chapterIds) {
//    chapterWordCounts() = Left("Fetching")
//    val lookups = for(cid <- chapterIds()) yield
//      storyDB.chapterWordCounts(cid, false)
//    Future.sequence(lookups).onComplete {
//      case Success(wcs) =>
//        chapterWordCounts() = Right(wcs)
//      case Failure(t) =>
//        chapterWordCounts() = Left("Error!")
//        throw new IllegalStateException("Unable to fetch chapter word counts", t)
//    }
//  }
//
//  val storyWordCounts = Rx {
//    
//  }
//
//  val filteredByStopWords = Rx {
//    storyWordCounts().right.map { swc =>
//        val sws = stopWords()
//        swc filter (w => !(sws contains w))
//    }
//  }
//
//  val storyWordFrequencies: Rx[Either[String, WordFrequency]] = Rx {
//    storyWordCounts().right.map(_.asFrequencies)
//  }
//
//  val filteredByStopWordsFrequencies = Rx {
//    filteredByStopWords().right.map(_.asFrequencies)
//  }
//
//  val scaledFrequencies = Rx {
//    val ams = allMeanStdev()
//
//    if(ams.means.isEmpty) {
//      Left("Calculating")
//    } else {
//      storyWordFrequencies().right.map { swf =>
//        def scaleFactor(w: String) = {
//          ams.means get w match {
//            case Some(m) =>
//              val s = ams.stdevs(w)
//              val d = m - swf.frequencies(w)
//              val d2 = d*d
//              val s22 = 2 * s * s
//
//              1.0 - Math.exp(-d2 / s22)
//            case None =>
//              1.0
//          }
//        }
//
//        WordFrequency(swf.frequencies.map { case(w, f) =>
//            w -> f * scaleFactor(w)
//        }).normalize
//      }
//    }
//  }
//
//  val stopFrames = Rx {
//    for {
//      sf <- scaledFrequencies().right
//      swc <- storyWordCounts().right
//      cc <- chapterWordCounts().right
//    } yield {
//      val rescaledChapterFreqs = cc map { cc_i =>
//        WordFrequency(
//          for((w, f) <- sf.frequencies)
//          yield w -> (f * cc_i.counts.getOrElse(w, 0) * cc.length / swc.counts(w)))
//      }
//      sf +: rescaledChapterFreqs :+ sf
//    }
//  }

  val allWordle = Wordle(rawFrequencies map {
    case Left(l) => WordFrequency(Map(l -> 0.1))
    case Right(wf) => wf
  })

  val filteredWordle = Wordle(filteredFrequencies map {
    case Left(l) => WordFrequency(Map(l -> 0.1))
    case Right(wf) => wf
  })

  val scaledWordle = Wordle(scaledBySupprise map {
    case Left(l) => WordFrequency(Map(l -> 0.1))
    case Right(wf) => wf
  })

  val animatedWordle = Wordle(scaledBySupprise map {
    case Left(l) => WordFrequency(Map(l -> 0.1))
    case Right(wf) => wf
  }, stopFrames = stopFrames map {
    case Left(_) => List()
    case Right(sw) => sw
  })

  val allWordsWordleSvg = svg.svg(width := 320, height := 320)(
    svg.g(`class` := "wordle",
      svga.transform := "translate(160 160)")(
        allWordle.wordle))

  val filteredWordleSvg = svg.svg(width := 320, height := 320)(
    svg.g(`class` := "wordle",
      svga.transform := "translate(160 160)")(
        filteredWordle.wordle))

  val surprisingWordleSvg = svg.svg(width := 320, height := 320)(
    svg.g(`class` := "wordle",
      svga.transform := "translate(160, 160)")(
        scaledWordle.wordle))

  val animatedWordleSvg = svg.svg(width := 320, height := 320)(
    svg.g(`class` := "wordle",
      svga.transform := "translate(160, 160)")(
        animatedWordle.wordle))

  val browser: Element = div(
    h1("Stats in black-and-white"),
    div(
      h2("Title: ", story.map(_.title)),
      div(`class` := "wordle_div")(
        h3("Raw word counts"),
        p(
          """
             |The words in this wordle are scaled proportionally to their frequency in the story.
             | This is the number of times that each word appears, divided by the total number of words
             | in the story.
             | The font size is scaled by the square root of the word frequency.
          """.stripMargin),
        allWordsWordleSvg,
        p(
          """
            |You will often see very boring words dominating these wordles.
            | Those words that are significant in the story can get lost in the noise.
          """.stripMargin)
      ),
      div(`class` := "wordle_div")(
        h3("After stoplist"),
        p(
          """
            |Here the words have been filtered using a top-list. This removes common words, allowing the story-specific
            | words to show. Word frequencies are calculated as before, after removing all words in the stop-list.
          """.stripMargin),
        filteredWordleSvg,
        p(
          """
            |In this display you can better see words that give some feel for the story. However, there is typically
            | not so much differentiation between the remaining words. They tend to be quite 'flat' without dominating
            | words.
          """.stripMargin)
      ),
      div(`class` := "wordle_div")(
        h3("Scaled by surprise"),
        p(
          """
            |Now instead of using a stop-list, we scale by how surprised we are by the word frequency. Words that are
            | used at the same rate in many stories have their frequency down-scaled. Those that are highly-variable are
            | upscaled.
          """.stripMargin),
        surprisingWordleSvg,
        p(
          """
            |The scaled by surprise wordle gives a better range of word size scalings than the stop-list, and highlights
            | more story-specific words than the full wordle.
            | Often nuisance words are prominent, but rather than being noisy, they are telling you about how that story
            | is written.
            | For example, stories that have a lot of first-person narrative typically have a dominating use of 'I'.
            | Key characters are often prominent, and far more visible than in the stop-list filtered wordle as
            | characters are generally quite specific to the story.
          """.stripMargin)
      ),
      div(`class` := "wordle_div")(
        h3("Animated"),
        p(
          """
            |So far the diplays have been static.
            | Here the words are animated by their use in each chapter.
            | The animations start and end with the surprise-scaled wordle.
          """.stripMargin
        ),
        animatedWordleSvg,
        p()
      )
    )
  ).render
}
