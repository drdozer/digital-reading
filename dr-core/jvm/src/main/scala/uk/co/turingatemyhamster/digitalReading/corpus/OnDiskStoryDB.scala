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

    val storyById = (storyList map { s => s.storyId -> s }).toMap

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

      override def storyWordCounts(storyId: Long, preserveCase: Boolean) = {
        val counts = for(
          chapterId <- storyById(storyId).chapterIds
        ) yield chapterWordCounts(chapterId, preserveCase)

        counts reduce (_ merge _)
      }

      override def storyMeanStdev(storyId: Long) = {
        val counts = for(
          chapterId <- storyById(storyId).chapterIds
        ) yield chapterWordCounts(chapterId, false)

        MeanStdev fromCounts counts
      }

      override def allMeanStdev: MeanStdev = meanStdev(all.stories.flatMap(_.chapterIds))

      def meanStdev(ids: List[Long]): MeanStdev = {
        val counts = for(
          stories <- all.stories;
          chapterId <- stories.chapterIds) yield
        {
          chapterWordCounts(chapterId, preserveCase = false)
        }

        MeanStdev fromCounts counts
      }
    }
  }
}