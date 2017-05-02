val playSlickVersion    = "2.1.0"

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
        "com.typesafe.akka"          %% "akka-stream"                         % "2.4.17",
        "org.postgresql"             % "postgresql"                           % "42.0.0",
        "org.scala-lang.modules"     %% "scala-java8-compat"                  % "0.8.0",
        "joda-time"                  % "joda-time"                            % "2.9.4",
        "org.joda"                   % "joda-convert"                         % "1.8.1",
        "com.typesafe.akka"          %% "akka-testkit"                        % "2.4.17",
        "com.miguno.akka"            %% "akka-mock-scheduler"                 % "0.5.1" % Test,
        "org.mockito"                % "mockito-all"                          % "1.10.19" % Test,
        "org.scalatestplus.play"     %% "scalatestplus-play"                  % "2.0.0" % Test
    )
  )
