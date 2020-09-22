import sbt.Keys._
import sbt._

object Common extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins      = plugins.JvmPlugin

  override lazy val projectSettings = Seq(
    organization := "com.firstbird",
    organizationName := "Firstbird GmbH",
    homepage := Some(url("https://github.com/firstbirdtech/akka-persistent-scheduler")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/firstbirdtech/akka-persistent-scheduler"),
        "git@github.com:firstbirdtech/akka-persistent-scheduler.git")),
    developers += Developer(
      "contributors",
      "Contributors",
      "hello@firstbird,com",
      url("https://github.com/firstbirdtech/akka-persistent-scheduler/graphs/contributors")),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    scalaVersion := "2.13.3",
    crossScalaVersions := Seq("2.12.12", scalaVersion.value),
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
    scalacOptions ++= (
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 13)) => Seq.empty[String]
        case _             => Seq("-Xfuture", "-Yno-adapted-args")
      }
    ),
    // show full stack traces and test case durations
    testOptions in Test += Tests.Argument("-oDF")
  )
}
