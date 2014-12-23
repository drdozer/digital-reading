package uk.co.turingatemyhamster.digitalReading.corpus

import java.util.Date

import upickle._

object Helper {
  implicit class EnhancedReadWriter[A](val _rw: ReadWriter[A]) extends AnyVal {
    def compose[B](down: B => A, up: A => B): ReadWriter[B] = ReadWriter[B](
      _write = a => _rw.write(down(a)),
      _read = { case (b) => up(_rw.read(b)) })
  }
}

case class Story(storyId: Long,
                 chapterIds: List[Long],
                 creationDate: Date,
                 modificationDate: Date,
                 tags: List[String],
                 title: String)

object Story {
  import Helper._

  implicit val dateReadWrite: ReadWriter[Date] = (implicitly[ReadWriter[Long]]).compose(
      down = _.getTime,
      up = new Date(_) )
  implicit val storyWriter = Writer.macroW[Story]
  implicit val storyReader = Reader.macroR[Story]


  val SlashDate = """(\d+)/(\d+)/(\d+)"""r
  val DashDate = """(\d+)-(\d+)-(\d+)"""r

  def asDate(dateString: String): Date = {
    dateString match {
      case SlashDate(month, day, year) =>
        new Date(year.toInt, month.toInt, day.toInt)
      case DashDate(year, month, day) =>
        new Date(year.toInt, month.toInt, day.toInt)
    }
  }

  implicit val storyOrd: Ordering[Story] = Ordering.by(_.storyId)
}

case class MeanStdev(means: Map[String, Double], stdevs: Map[String, Double])

object MeanStdev {
  def fromCounts(counts: List[WordCount]): MeanStdev = {
    val freqs = counts.map(_.asFrequencies)

    val n = freqs.length.toDouble
    val freqsSum = freqs reduce (_ merge _)
    val means = freqsSum.frequencies.mapValues(_ / n)
    val stdevs = means.map { case(w, mean) =>
      val variance = freqs.map { wc =>
        val d = wc.frequencies.getOrElse(w, 0.0) - mean
        d * d
      } .sum / n
      w -> Math.sqrt(variance)
    }

    MeanStdev(means = means, stdevs = stdevs)
  }
}

case class Stories(stories: List[Story])

case class WordCount(counts: Map[String, Int]) {
  def merge(wc: WordCount) = WordCount(
    counts ++ wc.counts.map { case (k, v) => k -> (v + counts.getOrElse(k, 0)) }
  )

  def byCounts = counts.to[Array].sortBy(0 - _._2)

  def asFrequencies: WordFrequency = {
    val total = counts.values.sum.toDouble
    WordFrequency(counts mapValues (c => c.toDouble / total))
  }

  def filter(p: String => Boolean): WordCount = WordCount(counts.filter(wc => p(wc._1)))
}

case class WordFrequency(frequencies: Map[String, Double]) {
  def byFrequencies = frequencies.to[Array].sortBy(0 - _._2)

  def merge(wf: WordFrequency) = WordFrequency(
    frequencies ++ wf.frequencies.map { case (k, v) => k -> (v + frequencies.getOrElse(k, 0.0)) }
  )

  def normalize = {
    val t = frequencies.values.sum
    WordFrequency(frequencies.mapValues(_ / t))
  }
}

trait StoryDB[C[_]] {
  def all: C[Stories]
  def chapterText(chapterId: Long): C[String]
  def storyWordCounts(storyId: Long, preserveCase: Boolean): C[WordCount]
  def storyMeanStdev(storyId: Long): C[MeanStdev]
  def chapterWordCounts(chapterId: Long, preserveCase: Boolean): C[WordCount]
  def allWordCounts(preserveCase: Boolean): C[WordCount]
  def allMeanStdev: C[MeanStdev]
}

object StoryDB {
  type Identity[T] = T

  def cache[C[_]](cached: StoryDB[C]): StoryDB[C] = new StoryDB[C] {
    lazy val all = cached.all

    private var chapterWordCounts_cache: Map[(Long, Boolean), C[WordCount]] = Map.empty
    override def chapterWordCounts(chapterId: Long, preserveCase: Boolean) = {
      val k = (chapterId, preserveCase)
      chapterWordCounts_cache get k match {
        case Some(v) => v
        case None => cached.synchronized {
          chapterWordCounts_cache get k match {
            case Some(v) => v
            case None =>
              val v = cached.chapterWordCounts(chapterId, preserveCase)
              chapterWordCounts_cache = chapterWordCounts_cache + (k -> v)
              v
          }
        }
      }
    }

    private var chapterText_cache: Map[Long, C[String]] = Map.empty
    override def chapterText(chapterId: Long) = {
      if(!chapterText_cache.contains(chapterId)) {
        chapterText_cache = chapterText_cache + (chapterId -> cached.chapterText(chapterId))
      }
      chapterText_cache(chapterId)
    }

    private var storyWordCounts_cache: Map[(Long, Boolean), C[WordCount]] = Map.empty
    override def storyWordCounts(storyId: Long, preserveCase: Boolean) = {
      val k = (storyId, preserveCase)
      storyWordCounts_cache get k match {
        case Some(v) => v
        case None => cached.synchronized {
          storyWordCounts_cache get k match {
            case Some(v) => v
            case None =>
              val v = cached.storyWordCounts(storyId, preserveCase)
              storyWordCounts_cache = storyWordCounts_cache + (k -> v)
              v
          }
        }
      }
    }

    private var storyMeanStdev_cache: Map[Long, C[MeanStdev]] = Map.empty
    override def storyMeanStdev(storyId: Long) = {
      val k = storyId
      storyMeanStdev_cache get k match {
        case Some(v) => v
        case None => cached.synchronized {
          storyMeanStdev_cache get k match {
            case Some(v) => v
            case None =>
              val v = cached.storyMeanStdev(storyId)
              storyMeanStdev_cache = storyMeanStdev_cache + (k -> v)
              v
          }
        }
      }
    }

    lazy val allWordCounts_cache_false = cached.allWordCounts(false)
    lazy val allWordCounts_cache_true = cached.allWordCounts(true)

    override def allWordCounts(preserveCase: Boolean) =
      if(preserveCase) allWordCounts_cache_true
      else allWordCounts_cache_false

    lazy val allMeanStdev = cached.allMeanStdev
  }
}