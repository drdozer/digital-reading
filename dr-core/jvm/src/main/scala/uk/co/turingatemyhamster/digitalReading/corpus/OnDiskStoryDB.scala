package uk.co.turingatemyhamster.digitalReading.corpus

import java.io.File

import scala.concurrent.{ExecutionContext, Future}
import scala.io.{Codec, Source}

case class ResourceLinesStopWordsDB(resourceName: String)(implicit ec: ExecutionContext) extends CachingStopWordsDB {
  override def do_words = Future {
    val resource = this.getClass.getResourceAsStream(resourceName)
    val src = Source.fromInputStream(resource)
    val lines = src.getLines()
    val words = lines.map(_.trim.toLowerCase)
    words.to[Set]
  }
}

case class OnDiskCorpusDB(dirRoot: String)(implicit ec: ExecutionContext) extends CorpusDB.Caching
{
  protected final override def do_stories = Future {
    val metaDataFile = dirRoot + "/metadata.csv"
    Stories(for (line <- Source.fromFile(metaDataFile)(Codec.UTF8).getLines().to[List]) yield {
      val Array(storyId, chapterIds, creationDate, modificationDate, tags, title) = line.split(",")
      Story(
        storyId = storyId.toLong,
        chapterIds = chapterIds.split( """\s+""").map(_.toLong).to[List],
        creationDate = Story.asDate(creationDate),
        modificationDate = Story.asDate(modificationDate),
        tags = tags.split( """\s+""").to[List],
        title = title)
    })
  }

  def chapterFile(chapterId: Long): File = new File(s"$dirRoot/chapter_text/$chapterId")

  protected final override def do_chapterText(chapterId: Long) = Future {
    Source.fromFile(chapterFile(chapterId)).mkString
  }
}

case class RawWordsDB(corpusDB: CorpusDB)(implicit ec: ExecutionContext) extends WordsDB.Caching {

  val wordPattern = """([^\s\.,?;:\[\]\{\}#\=\-\*\^\\\/!'"]+)""".r
  protected final override def do_chapterWords(chapterId: Long) =
    for(ct <- corpusDB.chapterText(chapterId)) yield
      (wordPattern findAllIn ct map (_.toLowerCase)).to[List]

}

case class FilteredWordsDB(rawWords: WordsDB, stopWordsDB: StopWordsDB)(implicit ec: ExecutionContext) extends WordsDB.Caching {

  final protected override def do_chapterWords(chapterId: Long) =
    for {
      cws <- rawWords.chapterWords(chapterId)
      sw <- stopWordsDB.words()
    } yield cws filterNot sw.contains

}

case class CorpusStatsFromDB(corpusDB: CorpusDB, wordsDB: WordsDB)(implicit ec: ExecutionContext) extends CorpusStatsDB.Caching {

  final override protected def do_corpusMeanStdev =
    for {
      stories <- corpusDB.stories()
      cids = stories.stories.flatMap(_.chapterIds)
      counts <- Future.sequence(cids map chapterWordCounts)
    } yield {
      MeanStdev fromCounts counts
    }


  final override protected def do_chapterWordCounts(chapterId: Long) =
    for {
      words <- wordsDB.chapterWords(chapterId)
    } yield
      WordCount(
        words.foldLeft(Map.empty[String, Int])(
          (counts, word) =>
            counts + (word -> (counts.getOrElse(word, 0) + 1))))

  final override protected def do_chapterWordFrequency(chapterId: Long) =
    for {
      wc <- chapterWordCounts(chapterId)
    } yield wc.asFrequencies


  final override protected def do_storyWordCounts(storyId: Long) =
    for {
      stories <- corpusDB.stories()
      cids = stories.byStoryId(storyId).chapterIds
      counts <- Future.sequence(cids.map(chapterWordCounts))
    } yield {
      counts.reduce(_ merge _)
    }

  final override protected def do_storyWordFrequency(storyId: Long) =
    for {
      swc <- storyWordCounts(storyId)
    } yield {
      swc.asFrequencies
    }


  final override protected def do_storyMeanStdevByChapter(storyId: Long) =
    for {
      stories <- corpusDB.stories()
      cids = stories.byStoryId(storyId).chapterIds
      counts <- Future.sequence(cids.map(chapterWordCounts))
    } yield {
      MeanStdev fromCounts counts
    }

  final override protected def do_wordsByChapter(storyId: Long) =
    for {
      stories <- corpusDB.stories()
      cids = stories.byStoryId(storyId).chapterIds
      counts <- Future.sequence(cids.map(chapterWordCounts))
    } yield {
      WordProbabilities(
        (
          for(w <- counts.map(_.counts.keys).reduce(_ ++ _)) yield {
            w -> {
              val cs = counts map (_.counts.getOrElse(w, 0))
              val s = cs.sum.toDouble
              cs map (_.toDouble / s)
            }
          }
          ).toMap)
    }
}

case class SurpriseFromStatsDB(statsDB: CorpusStatsDB)(implicit ec: ExecutionContext) extends SurpriseDB {
  override def storySurprise(storyId: Long) =
    calculateSurprise(
      statsDB.corpusMeanStdev(),
      statsDB.storyWordFrequency(storyId))

  override def chapterSurprise(chapterId: Long) =
    calculateSurprise(
      statsDB.corpusMeanStdev(),
      statsDB.chapterWordFrequency(chapterId))

  def calculateSurprise(msdF: Future[MeanStdev], freqsF: Future[WordFrequency]): Future[WordWeight] = {
    for {
      msd <- msdF
      freqs <- freqsF
    } yield {
      WordWeight(freqs.frequencies.map {
        case (k, v) =>
          val m = msd.means(k)
          val s = msd.stdevs(k)
          val d = m - v
          val d2 = d * d
          val s22 = 2 * s * s

          k -> (1.0 - Math.exp(-d2 / s22))
      })
    }
  }
}