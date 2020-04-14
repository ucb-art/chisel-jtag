name := "chisel-jtag"

version := "0.1"

organization := "edu.berkeley.cs"

scalaVersion := "2.12.10"

scalacOptions := Seq("-deprecation", "-feature", "-Xsource:2.11")

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
val defaultVersions = Map(
  "chisel-iotesters" -> "1.4-SNAPSHOT",
  "chiseltest" -> "0.2-SNAPSHOT"
)

// enables using control-c in sbt CLI
cancelable in Global := true

libraryDependencies ++= (Seq("chiseltest","chisel-iotesters").map {
  dep: String => "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))
})

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.5",
  "org.scalacheck" %% "scalacheck" % "1.12.4"
)
