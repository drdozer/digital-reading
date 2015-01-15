
import com.typesafe.sbt.less.Import.LessKeys
import com.typesafe.sbt.web.Import._
import com.typesafe.sbt.web.SbtWeb
import sbt._
import sbt.Keys._
import com.inthenow.sbt.scalajs._
import com.inthenow.sbt.scalajs.SbtScalajs._
import scala.scalajs.sbtplugin.ScalaJSPlugin._
import ScalaJSKeys._
import bintray.Plugin._
import bintray.Keys._
import org.eclipse.jgit.lib._
import xerial.sbt.Pack._
import spray.revolver.RevolverPlugin._


object DigitalReadingBuild extends Build {

  val logger = ConsoleLogger()

  val baseVersion = "0.1.0"

  val drCore = XModule(id = "dr-core", defaultSettings = buildSettings, baseDir = "dr-core")

  lazy val core            = drCore.project(corePlatformJvm, corePlatformJs)
  lazy val corePlatformJvm = drCore.jvmProject(coreSharedJvm)
    .settings(corePlatformJvmSettings: _*)
  lazy val corePlatformJs  = drCore.jsProject(coreSharedJs)
    .settings(corePlatformJsSettings: _*)
  lazy val coreSharedJvm   = drCore.jvmShared()
    .settings(coreSharedSettingsJvm : _*)
  lazy val coreSharedJs    = drCore.jsShared(coreSharedJvm)
    .settings(coreSharedSettingsJs : _*)

  val drClientServer = XModule(id = "dr-clientServer", defaultSettings = buildSettings, baseDir = "dr-clientServer")

  lazy val clientServer             = drClientServer.project(clientServerPlatformJvm, clientServerPlatformJs)
  lazy val clientServerPlatformJvm  = drClientServer.jvmProject(clientServerSharedJvm).dependsOn(corePlatformJvm)
    .enablePlugins(SbtWeb)
    .settings(clientServerPlatformJvmSettings : _*)
  lazy val clientServerPlatformJs   = drClientServer.jsProject(clientServerSharedJs).dependsOn(corePlatformJs)
    .settings(clientServerPlatformJsSettings : _*)
  lazy val clientServerSharedJvm    = drClientServer.jvmShared().dependsOn(coreSharedJvm)
  lazy val clientServerSharedJs     = drClientServer.jsShared(clientServerSharedJvm).dependsOn(coreSharedJs)

  lazy val buildSettings: Seq[Setting[_]] = bintrayPublishSettings ++ Seq(
    organization := "uk.co.turingatemyhamster",
    scalaVersion := "2.11.4",
    crossScalaVersions := Seq("2.11.4", "2.10.4"),
    scalacOptions ++= Seq("-deprecation", "-unchecked"),
    version := makeVersion(baseVersion),
    resolvers += Resolver.url(
      "bintray-scalajs-releases",
      url("http://dl.bintray.com/scala-js/scala-js-releases/"))(
        Resolver.ivyStylePatterns),
    resolvers += "bintray/non" at "http://dl.bintray.com/non/maven",
    resolvers ++= Seq("snapshots", "releases").map(Resolver.sonatypeRepo),
    resolvers += "spray repo" at "http://repo.spray.io",
    resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
    resolvers += "drdozer Bintray Repo" at "http://dl.bintray.com/content/drdozer/maven",
    publishMavenStyle := true,
    repository in bintray := "maven",
    bintrayOrganization in bintray := None,
    licenses +=("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))
  )

  lazy val coreSharedSettingsJvm = utest.jsrunner.Plugin.utestJvmSettings ++ Seq(
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2",
      "com.lihaoyi" %% "upickle" % "0.2.5",
      "com.lihaoyi" %% "utest" % "0.2.4" % "test"
    )
  )

  lazy val coreSharedSettingsJs = utest.jsrunner.Plugin.utestJsSettings ++ Seq(
    libraryDependencies ++= Seq(
      "org.scalajs" %%% "scala-parser-combinators" % "1.0.2",
      "com.scalarx" %%% "scalarx" % "0.2.6",
      "com.lihaoyi" %%% "upickle" % "0.2.5",
      "com.lihaoyi" %% "utest" % "0.2.4" % "test"
    )
  )

  lazy val corePlatformJvmSettings = Seq(
  )

  lazy val corePlatformJsSettings = Seq(
    libraryDependencies ++= Seq(
      "uk.co.turingatemyhamster" %%% "scalatags-ext" % "0.1.3",
      "uk.co.turingatemyhamster" %%% "sbolv-util" % "0.1.3",
      "com.scalatags" %%% "scalatags" % "0.4.2",
      "org.scala-lang.modules.scalajs" %%% "scalajs-dom" % "0.6"
    )
  )

  lazy val clientServerPlatformJvmSettings = packAutoSettings ++ Revolver.settings ++ Seq(
    libraryDependencies ++= Seq(
      "io.spray" %% "spray-routing" % "1.3.2",
      "io.spray" %% "spray-can" % "1.3.2",
      "com.scalatags" %% "scalatags" % "0.4.2",
      "com.lihaoyi" %% "autowire" % "0.2.3",
      "com.typesafe.akka" %% "akka-actor" % "2.3.7"
    ),
    ScalaJSKeys.emitSourceMaps := true,
//    (crossTarget in (clientServerPlatformJs, Compile, fastOptJS)) := crossTarget.value / "classes" / "public" / "javascript",
//    (resources in Compile) += {
//      (fastOptJS in (clientServerPlatformJs, Compile)).value
//      (artifactPath in (clientServerPlatformJs, Compile, fastOptJS)).value
//    },
    (crossTarget in (clientServerPlatformJs, Compile, fullOptJS)) := crossTarget.value / "classes" / "public" / "javascript",
    (resources in Compile) += {
      (fullOptJS in (clientServerPlatformJs, Compile)).value
      (artifactPath in (clientServerPlatformJs, Compile, fullOptJS)).value
    },
    includeFilter in (Assets, LessKeys.less) := "*.less",
    excludeFilter in (Assets, LessKeys.less) := "_*.less",
    WebKeys.packagePrefix in Assets := "public/",
    (managedClasspath in Runtime) += (packageBin in Assets).value
  )

  lazy val clientServerPlatformJsSettings = Seq(
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "autowire" % "0.2.3"
    )
  )

  def fetchGitBranch(): String = {
    val builder = new RepositoryBuilder()
    builder.setGitDir(file(".git"))
    val repo = builder.readEnvironment().findGitDir().build()
    val gitBranch = repo.getBranch
    logger.info(s"Git branch reported as: $gitBranch")
    repo.close()
    val travisBranch = Option(System.getenv("TRAVIS_BRANCH"))
    logger.info(s"Travis branch reported as: $travisBranch")

    travisBranch getOrElse gitBranch

    val branch = (travisBranch getOrElse gitBranch) replaceAll ("/", "_")
    logger.info(s"Computed branch is $branch")
    branch
  }

  def makeVersion(baseVersion: String): String = {
    val branch = fetchGitBranch()
    if(branch == "master") {
      baseVersion
    } else {
      val tjn = Option(System.getenv("TRAVIS_JOB_NUMBER"))
      s"$branch-$baseVersion${
        tjn.map("." + _) getOrElse ""
      }"
    }
  }
}
