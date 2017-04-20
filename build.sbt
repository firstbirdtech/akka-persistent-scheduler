val playSlickVersion    = "2.1.0"
val commonsVersion      = "1.0.47"
val commonsScalaVersion = "0.19"


lazy val akkaPersistentScheduler = project
  .in(file("."))
  .disablePlugins(Publish)
  .enablePlugins(NoPublish)
  .aggregate(core)

lazy val core = project
  .in(file("core"))
  .settings(
    name := "akka-persistent-scheduler",
    libraryDependencies ++= Seq(
        "com.typesafe.akka"          %% "akka-testkit"                        % "2.4.17",
        "com.typesafe.play"          %% "play-slick"                          % playSlickVersion,
        "com.typesafe.play"          %% "play-slick-evolutions"               % playSlickVersion,
        "org.postgresql"             % "postgresql"                           % "42.0.0",
        "nl.grons"                   %% "metrics-scala"                       % "3.5.6",
        "io.dropwizard.metrics"      % "metrics-jvm"                          % "3.2.0",
        "org.coursera"               % "metrics-datadog"                      % "1.1.10",
        "com.iheart"                 %% "ficus"                               % "1.4.0",
        "eu.firstbird"               % "hummingbird-commons-logging"          % commonsVersion,
        "eu.firstbird"               % "hummingbird-commons-metrics"          % commonsVersion,
        "eu.firstbird"               % "hummingbird-client"                   % "2.3.48",
        "eu.firstbird"               % "backbone-client"                      % "1.1.12",
        "eu.firstbird"               % "hummingbird-events"                   % "0.0.76",
        "net.codingwell"             %% "scala-guice"                         % "4.1.0",
        "eu.firstbird"               %% "hummingbird-commons-scala-play"      % commonsScalaVersion,
        "eu.firstbird"               %% "hummingbird-commons-scala-play-test" % commonsScalaVersion % Test,
        "org.typelevel"              %% "cats-core"                           % "0.9.0",
        "com.typesafe.scala-logging" %% "scala-logging"                       % "3.5.0",
        "com.miguno.akka"            %% "akka-mock-scheduler"                 % "0.5.1",
        "org.scala-lang.modules"     %% "scala-java8-compat"                  % "0.8.0",
        "org.mockito"                % "mockito-all"                          % "1.10.19" % Test,
        "org.scalatestplus.play"     %% "scalatestplus-play"                  % "2.0.0" % Test
    )
  )
