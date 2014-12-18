package uk.co.turingatemyhamster.digitalReading.corpus

import org.scalajs.dom.extensions.Ajax
import rx.ops.DomScheduler

import scala.concurrent.{Future, ExecutionContext}
import upickle._


object RemoteStoryDB {
  def apply(apiUrl: String = "/api")(implicit ec: ExecutionContext): StoryDB[Future] = new StoryDB[Future] {
    override def all =
      for(req <- Ajax.get(url = s"$apiUrl/stories")) yield
        read[Stories](req.responseText)

    override def chapterText(chapterId: Long) =
      for(req <- Ajax.get(url = s"$apiUrl/chapter_text/$chapterId")) yield
        req.responseText

    override def chapterWordCounts(chapterId: Long, preserveCase: Boolean) =
      for(req <- Ajax.get(url = s"$apiUrl/chapter_word_counts/$chapterId/$preserveCase")) yield
        read[WordCount](req.responseText)

    override def allWordCounts(preserveCase: Boolean) =
      for(req <- Ajax.get(url = s"$apiUrl/all_word_counts/$preserveCase")) yield
        read[WordCount](req.responseText)

    override def allMeanStdev =
      for(req <- Ajax.get(url = s"$apiUrl/all_mean_stdev")) yield
        read[MeanStdev](req.responseText)
  }

  import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow

  lazy val instance = {
    StoryDB.cache(RemoteStoryDB("/api"))
  }
  lazy val stopWords = for(res <- Ajax.get("/public/data/english.stopwords.txt")) yield
    res.responseText.split("""\n+""")

  def vitalStatistics = for(corpus <- instance.all) yield CorpusVitalStatistics(
    storyCount = corpus.stories.length,
    chapterCount = corpus.stories.map(_.chapterIds.length).sum,
    firstPublication = corpus.stories.map(_.creationDate).min,
    lastPublication = corpus.stories.map(_.creationDate).max
  )

}

case class CorpusVitalStatistics(storyCount: Int,
                                 chapterCount: Int,
                                 firstPublication: java.util.Date,
                                 lastPublication: java.util.Date)
