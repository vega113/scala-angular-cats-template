object FrontendCommands {
  val dependencyInstall: String = "npm ci"
  val serve: String = "npm run start"
  val buildDev: String = "npm run build:dev"
  val buildProd: String = "npm run build:prod"
  val buildHerokuLocal: String = "npm run build:heroku-local"
}
