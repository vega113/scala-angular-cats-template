import java.io.File
import scala.sys.process.Process

object FrontendRunHook {
  private val install: String = FrontendCommands.dependencyInstall
  private val run: String = FrontendCommands.serve

  private var process: Option[Process] = None
  private var shutdownHookRegistered: Boolean = false

  def apply(base: File): () => Unit = { () =>
    val uiDir = new File(base, "ui")
    val nodeModulesDir = new File(uiDir, "node_modules")

    if (!nodeModulesDir.exists()) {
      println("Installing npm dependencies for Angular dev server...")
      val installProcess = Process(install, uiDir)
      val installExitCode = installProcess.!
      if (installExitCode != 0) {
        sys.error(s"npm install failed with exit code $installExitCode")
      }
    }

    if (process.isEmpty || !process.exists(_.isAlive())) {
      println("Starting Angular dev server via npm...")
      val runProcess = Process(run, uiDir)
      process = Option(runProcess.run())
      println("Angular dev server started.")
    } else {
      println("Angular dev server already running; skipping restart.")
    }

    if (!shutdownHookRegistered) {
      sys.addShutdownHook {
        process.foreach { p =>
          if (p.isAlive()) {
            println("Stopping Angular dev server...")
            p.destroy()
            println("Angular dev server stopped.")
          }
        }
        process = None
      }
      shutdownHookRegistered = true
    }
  }
}
