name := "jtag-example-ice40hx8k"

version := "0"

scalaVersion := "2.11.7"

scalacOptions := Seq("-deprecation", "-feature")

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

libraryDependencies += "edu.berkeley.cs" %% "chisel3" % "3.1-SNAPSHOT"

lazy val chiselJtag = RootProject(file("../.."))

lazy val main = (project in file(".")).
  dependsOn(chiselJtag)