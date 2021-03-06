name := "fs2-tutorial"

version := "0.1"

scalaVersion := "2.12.13"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "2.1.2",
  "co.fs2" %% "fs2-core" % "2.3.0",
  "co.fs2" %% "fs2-io" % "2.3.0",
  "co.fs2" %% "fs2-cats" % "0.5.0"
)
