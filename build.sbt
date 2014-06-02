import play.PlayImport.PlayKeys._

name := "docker-registry"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

routesImport += "binders._, services._"

scalaVersion := "2.11.1"


libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "org.scalatest" %% "scalatest" % "2.1.7" % "test"
)


