ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"

addCommandAlias("codeFmt", ";headerCreate;scalafmtAll;scalafmtSbt;scalafixAll")
addCommandAlias("codeVerify", ";scalafmtCheckAll;scalafmtSbtCheck;scalafixAll --check;headerCheck")

lazy val commonSettings = Seq(
  organization        := "com.firstbird",
  organizationName    := "Firstbird GmbH",
  sonatypeProfileName := "com.firstbird",
  homepage            := Some(url("https://github.com/firstbirdtech/akka-persistent-scheduler")),
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  scmInfo := Some(
    ScmInfo(homepage.value.get, "git@github.com:firstbirdtech/akka-persistent-scheduler.git")
  ),
  developers += Developer(
    "contributors",
    "Contributors",
    "hello@firstbird,com",
    url("https://github.com/firstbirdtech/akka-persistent-scheduler/graphs/contributors")),
  scalaVersion       := "2.13.14",
  crossScalaVersions := Seq("2.12.14", scalaVersion.value),
  scalacOptions ++= Seq(
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-deprecation",
    "-Xfatal-warnings",
    "-Xlint",
    "-Ywarn-dead-code"
  ),
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "utf-8",
    "-explaintypes",
    "-feature",
    "-language:higherKinds",
    "-unchecked",
    "-Xcheckinit",
    "-Xfatal-warnings"
  ),
  scalacOptions ++= (
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 13)) => Seq("-Wdead-code", "-Wunused:imports")
      case _             => Seq("-Xfuture", "-Ywarn-dead-code", "-Ywarn-unused:imports", "-Yno-adapted-args")
    }
  ),
  // show full stack traces and test case durations
  Test / testOptions += Tests.Argument("-oDF"),
  headerLicense     := Some(HeaderLicense.MIT("2021", "Akka Persistent Scheduler contributors")),
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision
)

lazy val root = project
  .in(file("."))
  .settings(commonSettings)
  .settings(publish / skip := true)
  .aggregate(core)

lazy val core = project
  .in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "akka-persistent-scheduler",
    libraryDependencies ++= Dependencies.core
  )
