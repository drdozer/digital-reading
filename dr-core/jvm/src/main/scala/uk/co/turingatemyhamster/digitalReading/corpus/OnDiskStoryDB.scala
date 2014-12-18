package uk.co.turingatemyhamster.digitalReading.corpus

import java.io.File

import scala.io.{Codec, Source}

/**
 *
 *
 * @author Matthew Pocock
 */

object OnDiskStoryDB {
  def apply(dirRoot: String): StoryDB[StoryDB.Identity] = {
    val metaDataFile = dirRoot + "/metadata.csv"
    val storyList = for(line <- Source.fromFile(metaDataFile)(Codec.UTF8).getLines().to[List]) yield {
      val Array(storyId, chapterIds, creationDate, modificationDate, tags, title) = line.split(",")
      Story(
        storyId = storyId.toLong,
        chapterIds = chapterIds.split("""\s+""").map(_.toLong).to[List],
        creationDate = Story.asDate(creationDate),
        modificationDate = Story.asDate(modificationDate),
        tags = tags.split("""\s+""").to[List],
        title = title)
    }
    val allMetaData = Stories(storyList)

    def chapterFile(chapterId: Long): File = new File(s"$dirRoot/chapter_text/$chapterId")

    def countWords(text: String, preserveCase: Boolean): Map[String, Int] = {
      val rawWords =
        if(preserveCase) text.split("""[?!),.:'"]*\s+[('"]*""")
        else text.split("""('s)?[?!),.:'"]*\s+[('"]*""")
      val processedWords = if(preserveCase) rawWords else rawWords.map(_.toLowerCase)
      processedWords.foldLeft(Map.empty[String, Int])((counts, word) =>
        counts + (word -> (counts.getOrElse(word, 0) + 1)))
    }

    new StoryDB[StoryDB.Identity] {
      override def all = allMetaData
      override def chapterText(chapterId: Long) = Source.fromFile(chapterFile(chapterId)).mkString
      override def chapterWordCounts(chapterId: Long, preserveCase: Boolean) =
        WordCount(countWords(chapterText(chapterId), preserveCase))

      override def allWordCounts(preserveCase: Boolean) = {
        val counts = for(
          stories <- all.stories;
          chapterId <- stories.chapterIds) yield
        {
          chapterWordCounts(chapterId, preserveCase)
        }

        counts reduce (_ merge _)
      }

      override def allMeanStdev: MeanStdev = meanStdev(all.stories.flatMap(_.chapterIds))

      def meanStdev(ids: List[Long]): MeanStdev = {
        val counts = for(
          stories <- all.stories;
          chapterId <- stories.chapterIds) yield
        {
          chapterWordCounts(chapterId, preserveCase = false)
        }

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
  }
}