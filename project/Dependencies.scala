import sbt._

object Dependencies {

  private val akkaVersion = "2.6.21"

  def core(j8compatVersion: String) = Seq(
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.13.0",
    "org.scala-lang.modules" %% "scala-java8-compat"      % j8compatVersion,
    "com.typesafe.akka"      %% "akka-actor"              % akkaVersion,
    "com.typesafe.akka"      %% "akka-testkit"            % akkaVersion % Test,
    "com.miguno.akka"        %% "akka-mock-scheduler"     % "0.5.5"     % Test,
    "org.scalatest"          %% "scalatest"               % "3.2.19"    % Test
  )

}
