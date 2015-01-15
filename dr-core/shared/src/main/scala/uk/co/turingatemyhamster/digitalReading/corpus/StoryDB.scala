package uk.co.turingatemyhamster.digitalReading.corpus

import java.util.Date

import upickle._

import scala.concurrent.Future

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

case class MeanStdev(means: Map[String, Double], stdevs: Map[String, Double]) {
  def filter(p: String => Boolean) = MeanStdev(
    means = means filterKeys p,
    stdevs = stdevs filterKeys p
  )
}

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

case class Stories(stories: List[Story]) {
  lazy val byStoryId = stories.map(s => s.storyId -> s).toMap
}

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

  def rescaleBy(p: WordWeight): WordFrequency =
    WordFrequency(frequencies map {
      case (w, f) =>
        val x = p.probabilities.getOrElse(w, 0.0)
        w -> (f * x)
    }).normalize

}

case class WordWeight(probabilities: Map[String, Double])

case class WordProbabilities(probabilities: Map[String, List[Double]])

object FutureMemoiser {
  def apply[A, B](f: A => Future[B]): A => Future[B] = {
    val cache = scala.collection.concurrent.TrieMap.empty[A, Future[B]]

    (a: A) => cache.getOrElseUpdate(a, f(a))
  }
}

trait StopWordsDB {
  def words(): Future[Set[String]]
}

trait CachingStopWordsDB extends StopWordsDB {
  def do_words: Future[Set[String]]
  override final lazy val words = do_words
}

object StopWordsDB {
  def cache(stopWordsDB: StopWordsDB): StopWordsDB = new CachingStopWordsDB {
    override def do_words = stopWordsDB.words
  }
}

/**
 * A corpus as a textual artifact.
 *
 * @author Matthew Pocock
 */
trait CorpusDB {
  /**
   * A listing of all of the stories.
   *
   * note: this will not scale as the corpus scales
   *
   * @return  the Stories making up this corpus
   */
  def stories(): Future[Stories]

  /**
   * Full-text of a chapter.
   */
  def chapterText(chapterId: Long): Future[String]
}

object CorpusDB {

  trait Caching extends CorpusDB {
  
    protected def do_stories: Future[Stories]
    final override lazy val stories = do_stories
  
    protected def do_chapterText(chapterId: Long): Future[String]
    private val cache_chapterText = FutureMemoiser(do_chapterText)
    final override def chapterText(chapterId: Long) = cache_chapterText(chapterId)
  
  }
  
  def cache(db: CorpusDB): CorpusDB = new Caching {
    override def do_stories = db.stories
    override def do_chapterText(chapterId: Long) = db.chapterText(chapterId)
  }
}


trait WordsDB {
  /**
   * The chapter text.
   */
  def chapterWords(chapterId: Long): Future[List[String]]
}

object WordsDB {
  
  trait Caching extends WordsDB {
    protected def do_chapterWords(chapterId: Long): Future[List[String]]
    private val cache_chapterWords = FutureMemoiser(do_chapterWords)
    final override def chapterWords(chapterId: Long) = cache_chapterWords(chapterId)
  }
  
  def cache(wordsDB: WordsDB): WordsDB = new Caching {
    override protected def do_chapterWords(chapterId: Long) = wordsDB.chapterWords(chapterId)
  }
}


trait CorpusStatsDB {
  def corpusMeanStdev(): Future[MeanStdev]

  def storyWordCounts(storyId: Long): Future[WordCount]
  def storyWordFrequency(storyId: Long): Future[WordFrequency]
  def storyMeanStdevByChapter(storyId: Long): Future[MeanStdev]

  def chapterWordCounts(chapterId: Long): Future[WordCount]
  def chapterWordFrequency(chapterId: Long): Future[WordFrequency]

  def wordsByChapter(storyId: Long): Future[WordProbabilities]
}

object CorpusStatsDB {

  trait Caching extends CorpusStatsDB {
  
    protected def do_corpusMeanStdev: Future[MeanStdev]
    lazy val corpusMeanStdev = do_corpusMeanStdev
  

    protected def do_storyWordCounts(storyId: Long): Future[WordCount]
    private val cache_storyWordCounts = FutureMemoiser(do_storyWordCounts)
    final override def storyWordCounts(storyId: Long) = cache_storyWordCounts(storyId)
  
    protected def do_storyWordFrequency(storyId: Long): Future[WordFrequency]
    private val cache_storyWordFrequency = FutureMemoiser(do_storyWordFrequency)
    final override def storyWordFrequency(storyId: Long) = cache_storyWordFrequency(storyId)
  
    protected def do_storyMeanStdevByChapter(storyId: Long): Future[MeanStdev]
    private val cache_storyMeanStdevByChapter = FutureMemoiser(do_storyMeanStdevByChapter)
    final override def storyMeanStdevByChapter(storyId: Long) = cache_storyMeanStdevByChapter(storyId)
  
  
    protected def do_chapterWordCounts(chapterId: Long): Future[WordCount]
    private val cache_chapterWordCounts = FutureMemoiser(do_chapterWordCounts)
    final override def chapterWordCounts(chapterId: Long) = cache_chapterWordCounts(chapterId)
  
    protected def do_chapterWordFrequency(chapterId: Long): Future[WordFrequency]
    private val cache_chapterWordFrequency = FutureMemoiser(do_chapterWordFrequency)
    final override def chapterWordFrequency(chapterId: Long) = cache_chapterWordFrequency(chapterId)


    protected def do_wordsByChapter(storyId: Long): Future[WordProbabilities]
    private val cache_wordsByChapter = FutureMemoiser(do_wordsByChapter)
    final override def wordsByChapter(storyId: Long) = cache_wordsByChapter(storyId)
  }

  def cache(cs: CorpusStatsDB): CorpusStatsDB = new Caching {

    final override protected def do_corpusMeanStdev = cs.corpusMeanStdev

    final override protected def do_storyWordCounts(storyId: Long) = cs.storyWordCounts(storyId)
    final override protected def do_storyWordFrequency(storyId: Long) = cs.storyWordFrequency(storyId)
    final override protected def do_storyMeanStdevByChapter(storyId: Long) = cs.storyMeanStdevByChapter(storyId)

    final override protected def do_chapterWordCounts(chapterId: Long) = cs.chapterWordCounts(chapterId)
    final override protected def do_chapterWordFrequency(chapterId: Long) = cs.chapterWordFrequency(chapterId)

    override protected def do_wordsByChapter(storyId: Long) = cs.wordsByChapter(storyId)
  }
}


trait SurpriseDB {
  def storySurprise(storyId: Long): Future[WordWeight]
  def chapterSurprise(chapterId: Long): Future[WordWeight]
}

object SurpriseDB {

  trait Caching extends SurpriseDB {
    protected def do_storySurprise(storyId: Long): Future[WordWeight]
    private val cache_storySurprise = FutureMemoiser(do_storySurprise)
    final override def storySurprise(storyId: Long) = cache_storySurprise(storyId)

    protected def do_chapterSurprise(chapterId: Long): Future[WordWeight]
    private val cache_chapterSurprise = FutureMemoiser(do_chapterSurprise)
    final override def chapterSurprise(chapterId: Long) = cache_chapterSurprise(chapterId)
  }

  def cache(surpriseDB: SurpriseDB): SurpriseDB = new Caching {
    override protected def do_storySurprise(storyId: Long) = surpriseDB.storySurprise(storyId)
    override protected def do_chapterSurprise(chapterId: Long) = surpriseDB.chapterSurprise(chapterId)
  }
}