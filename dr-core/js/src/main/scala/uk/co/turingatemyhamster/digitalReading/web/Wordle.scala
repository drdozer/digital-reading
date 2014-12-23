package uk.co.turingatemyhamster.digitalReading
package web

import corpus._
import org.scalajs.dom
import org.scalajs.dom.{SVGTextElement, SVGLocatable, Node, SVGElement}
import sbolv.geom.{Box, Point2, QuadTree}

import scala.collection.immutable.NumericRange
import scala.scalajs.js.Dynamic
import scalatags.{generic, JsDom}
import JsDom.all._
import JsDom.{svgTags => svg}
import JsDom.{svgAttrs => svga}

import rx._
import rx.ops._

import scalatags.ext._
import Framework._

/**
 *
 *
 * @author Matthew Pocock
 */
case class Wordle(frequencies: Rx[WordFrequency],
                  colors: Rx[Colorer[String]] = Var(Colorer.always[String]("black")),
                  stopFrames: Rx[List[WordFrequency]] = Var(List.empty),
                  frameLength: Rx[String] = Var("2s"))
{
  val idSuffix = RandomUtil.rand.nextLong()

  def textIdForWord(word: String) = s"text_${word}_$idSuffix"
  def groupIdForWord(word: String) = s"group_${word}_$idSuffix"
  def animateIdForWord(word: String, frame: Int) = s"animation_${word}_${frame}_$idSuffix"
  def fontSize(c: Double) = 200.0 * Math.sqrt(c)

  val topNWords = Rx {
    val fs = frequencies()
    fs.byFrequencies.take(400).to[Seq]
  }

  val wordFrags = Rx {
    Dynamic.global.window.requestAnimationFrame({() => layoutWords()})
    val cs = colors()

    topNWords() map { case(w, c) =>
      svg.g(
        id := groupIdForWord(w),
        `class` := "wordle_word_group"
      )(
          svg.text(
            id := textIdForWord(w),
            `class` := "wordle_word",
            svga.visibility := "hidden",
            svga.fontSize := fontSize(c),
            svga.fill := cs.color(w))(
              w
            )
        )
    }
  }

  def layoutWords(): Unit = {
    val spiral = RandomUtil.spiral()
    var boxes = QuadTree(Point2(0, 0))

    topNWords() foreach { case (w, c) =>
      val textG = dom.document.getElementById(groupIdForWord(w)).asInstanceOf[SVGElement with SVGLocatable]
      val text = dom.document.getElementById(textIdForWord(w)).asInstanceOf[SVGElement with SVGLocatable]

      // center the text by its box
      val firstBox = Box(text.getBBox())
      val firstBoxC = firstBox.centre
      text.modifyWith(
        svga.x := -firstBoxC.x,
        svga.y := -firstBoxC.y,
        svga.visibility := "visible").render

      val centredBox = Box(text.getBBox())

      // check it doesn't overlap previous boxes, advance along a spiral
      //        val (overlap, nonOverlap) = spiral span (p => boxes.overlap(centredBox translate p))
      val nonOverlap = spiral dropWhile (p => boxes.overlap(centredBox translate p))
      val offset = nonOverlap.head
      textG.modifyWith(svga.transform := offset.asSVGTranslate).render

      val finalBox = centredBox translate offset

      boxes = boxes.insert(finalBox)
    }
  }


  Obs(stopFrames) {

    println(s"Stop-frames: ${stopFrames().length} with delay ${frameLength()}")

    val frames = stopFrames()

    def applyAnimations(): Unit = {
      if(frames.length > 2) {
        val framePairs = (frames.head :: frames) zip frames
        for (((fOld, fNew), i) <- framePairs.zipWithIndex) {
          for (w <- topNWords().map(_._1)) {
            val oldF = fOld.frequencies getOrElse (w, 0.0)
            val newF = fNew.frequencies getOrElse (w, 0.0)
            val text = dom.document.getElementById(textIdForWord(w)).asInstanceOf[SVGTextElement]
            val prevI = if(i == 0) framePairs.length - 1 else i - 1
            val afterPrevious = s"${animateIdForWord(w, prevI)}.end"
            text.modifyWith(
              svga.fontSize :=
                (fontSize(oldF) to fontSize(newF) dur frameLength() begin afterPrevious).freeze.modifyWith (
                svga.id := animateIdForWord(w, i)
                )).render
          }
        }

        for (w <- topNWords().map(_._1)) {
          val animId = animateIdForWord(w, 0)
          val zeroAnim = dom.document.getElementById(animId)
          Dynamic.global.window.requestAnimationFrame({() => {
            println(s"Begin animation for $animId")
            scala.scalajs.js.Dynamic(zeroAnim).beginElement()
          }})
        }
      }
    }
    Dynamic.global.window.requestAnimationFrame(() => applyAnimations())
  }

  val currentFrame: Var[Option[Int]] = Var(None)

  val wordle = svg.g(
    wordFrags.map(fs => svg.g(fs))
  ).render
}
