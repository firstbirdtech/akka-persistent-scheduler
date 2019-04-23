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
      "com.typesafe.akka"      %% "akka-actor"         % "2.5.22",
      "org.scala-lang.modules" %% "scala-java8-compat"  % "0.9.0",
      "joda-time"              % "joda-time"            % "2.10.1",
      "com.typesafe.akka"      %% "akka-testkit"        % "2.5.22" % Test,
      "com.miguno.akka"        %% "akka-mock-scheduler" % "0.5.4" % Test,
      "org.mockito"            % "mockito-all"          % "1.10.19" % Test,
      "org.scalatest"          %% "scalatest"           % "3.0.7" % Test
    )
  )
