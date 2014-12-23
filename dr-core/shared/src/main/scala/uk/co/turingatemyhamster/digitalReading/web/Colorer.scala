package uk.co.turingatemyhamster.digitalReading.web

/**
 *
 *
 * @author Matthew Pocock
 */
trait Colorer[T] {
  /** Get the color for an item. The color is a http-compliant color string. */
  def color(t: T): String
}

object Colorer {
  /** Color everything in the same color. */
  def always[T](theColor: String): Colorer[T] = new Colorer[T] {
    override def color(t: T) = theColor
  }

  /** Use an arbitrary function. */
  def apply[T](f: T => String): Colorer[T] = new Colorer[T] {
    /** Get the color for an item. The color is a http-compliant color string. */
    override def color(t: T) = f(t)
  }
}
