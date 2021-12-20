import sbt._

object Dependencies {

  private val akkaVersion = "2.6.18"

  val core = Seq(
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.4",
    "org.scala-lang.modules" %% "scala-java8-compat"      % "0.9.1",
    "com.typesafe.akka"      %% "akka-actor"              % akkaVersion,
    "com.typesafe.akka"      %% "akka-testkit"            % akkaVersion % Test,
    "com.miguno.akka"        %% "akka-mock-scheduler"     % "0.5.5"     % Test,
    "org.scalatest"          %% "scalatest"               % "3.2.9"     % Test
  )

}
