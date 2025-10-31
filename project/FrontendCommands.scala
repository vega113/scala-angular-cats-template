object FrontendCommands {
  val dependencyInstall: Seq[String] = Seq("npm", "ci")
  val serve: Seq[String] = Seq("npm", "run", "start")
  val buildDev: Seq[String] = Seq("npm", "run", "build:dev")
  val buildProd: Seq[String] = Seq("npm", "run", "build:prod")
  val buildHerokuLocal: Seq[String] = Seq("npm", "run", "build:heroku-local")
}
