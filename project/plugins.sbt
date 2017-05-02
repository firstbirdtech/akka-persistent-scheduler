addSbtPlugin("com.dwijnand"  % "sbt-dynver"          % "1.2.0")
addSbtPlugin("me.lessis"     % "bintray-sbt"         % "0.3.0-8-g6d0c3f8")

resolvers += Resolver.url("2m-sbt-plugin-releases", url("https://dl.bintray.com/2m/sbt-plugin-releases/"))(
  Resolver.ivyStylePatterns)