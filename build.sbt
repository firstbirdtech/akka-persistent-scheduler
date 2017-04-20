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
        "org.postgresql"             % "postgresql"                           % "42.0.0",
        "com.miguno.akka"            %% "akka-mock-scheduler"                 % "0.5.1",
        "org.scala-lang.modules"     %% "scala-java8-compat"                   % "0.8.0",
        "eu.firstbird"               %% "hummingbird-commons-scala-play-test" % commonsScalaVersion % Test,
        "org.mockito"                % "mockito-all"                          % "1.10.19" % Test,
        "org.scalatestplus.play"     %% "scalatestplus-play"                  % "2.0.0" % Test
    )
  )
