// The Play plugin
resolvers += "Sonatype Snaopshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin"          % "2.5.13")
addSbtPlugin("com.eed3si9n"      % "sbt-assembly"        % "0.14.4")
addSbtPlugin("com.github.gseitz" % "sbt-release"         % "1.0.4")
addSbtPlugin("com.geirsson"      % "sbt-scalafmt"        % "0.6.2")
addSbtPlugin("com.tapad"         % "sbt-docker-compose"  % "1.0.19")
addSbtPlugin("org.scoverage"     % "sbt-scoverage"       % "1.5.0")
addSbtPlugin("com.codacy"        % "sbt-codacy-coverage" % "1.3.8")
