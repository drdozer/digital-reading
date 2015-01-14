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

case class OnDiskCorpusDB(dirRoot: String)(implicit ec: ExecutionContext) extends CachingCorpusDB
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

case class RawWordsDB(corpusDB: CorpusDB)(implicit ec: ExecutionContext) extends CachingWordsDB {

  val wordPattern = """([^\s\.,?;:\=\-\*\^\\\/!']+)""".r
  protected final override def do_chapterWords(chapterId: Long) =
    for(ct <- corpusDB.chapterText(chapterId)) yield
      (wordPattern findAllIn ct map (_.toLowerCase)).to[List]

}

case class FilteredWordsDB(rawWords: WordsDB, stopWordsDB: StopWordsDB)(implicit ec: ExecutionContext) extends CachingWordsDB {

  final protected override def do_chapterWords(chapterId: Long) =
    for {
      cws <- chapterWords(chapterId)
      sw <- stopWordsDB.words
    } yield cws filter sw.contains

}

case class CorpusStatsFromDB(corpusDB: CorpusDB, wordsDB: WordsDB)(implicit ec: ExecutionContext) extends CachingCorpusStats {

  final override protected def do_corpusMeanStdev =
    for {
      stories <- corpusDB.stories
      cids = stories.stories.flatMap(_.chapterIds)
      counts <- Future.sequence(cids map chapterWordCounts)
    } yield {
      MeanStdev fromCounts counts
    }

  final override protected def do_corpusRestrictedMeanStdev(storyId: Long) =
    for {
      cmsd <- corpusMeanStdev
      swcs <- storyWordCounts(storyId)
    } yield
      cmsd filter swcs.counts.keySet.contains


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
      stories <- corpusDB.stories
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
      stories <- corpusDB.stories
      cids = stories.byStoryId(storyId).chapterIds
      counts <- Future.sequence(cids.map(chapterWordCounts))
    } yield {
      MeanStdev fromCounts counts
    }
}
