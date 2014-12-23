package uk.co.turingatemyhamster.digitalReading.web

import org.scalajs.dom.extensions.Color
import sbolv.geom.Point2

import scala.util.Random

object RandomUtil {
  val rand = new Random()

  def color(): Color = Color(byte(), byte(), byte())
  def byte(): Int = rand.nextInt(256)
  def inRange(min: Double, max: Double) = {
    val diff = max - min
    rand.nextDouble() * diff + min
  }
  def id() = rand.nextString(10)

  def spiral(stride: Double = 5.0,
             initialR: Double = 2.0,
             theta: Double = RandomUtil.inRange(0.0, 2.0*Math.PI)) =
  {
    val p0 = Point2(x = Math.cos(theta) * initialR,
                    y = Math.sin(theta) * initialR)

    def next(p: Point2): Point2 = p + p.normal.unit * stride

    def sp(p: Point2): Stream[Point2] = Stream.cons(p, sp(next(p)))

    sp(p0)
  }

}
