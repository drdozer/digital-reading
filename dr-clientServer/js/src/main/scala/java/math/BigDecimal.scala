package java.math

class MathContext {
  ???
}

object MathContext {

  def DECIMAL128: MathContext = ???
}

class BigInteger {
  ???
}

object BigInteger {
  def valueOf(v: Long): BigInteger = ???
}

/**
 *
 *
 * @author Matthew Pocock
 */
class BigDecimal {
  ???

  def this(v: Double) = this
  def this(ch: Array[Char]) = this
  def this(s: String) = this

  def this(v: Double, mc: MathContext) = this
  def this(ch: Array[Char], mc: MathContext) = this
  def this(s: String, mc: MathContext) = this

  def byteValueExact(): Byte = ???
  def compareTo(bd: BigDecimal): Int = ???
  def divide(bd: BigDecimal, mc: MathContext): BigDecimal = ???
  def doubleValue(): Double = ???
  def floatValue(): Float = ???
  def intValue(): Int = ???
  def intValueExact: Int = ???
  def longValue(): Long = ???
  def longValueExact(): Long = ???
  def precision(): Int = ???
  def scale(): Int = ???
  def scaleByPowerOfTen(n: Int): BigDecimal = ???
  def shortValueExact(): Short = ???
  def stripTrailingZeros(): BigDecimal = ???
  def toBigInteger(): BigInteger = ???
  def toBigIntegerExact(): BigInteger = ???
}

object BigDecimal {

  def valueOf(v: Double): BigDecimal = ???
  def valueOf(v: Long): BigDecimal = ???
  def valueOf(v: Long, s: Int) = ???
}
