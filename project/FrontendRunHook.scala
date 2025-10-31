import java.io.File
import scala.sys.process.{Process, ProcessLogger}
import sbt.util.Logger

object FrontendRunHook {
  private val install: Seq[String] = FrontendCommands.dependencyInstall
  private val run: Seq[String] = FrontendCommands.serve

  private var process: Option[Process] = None
  private var shutdownHookRegistered: Boolean = false

  def apply(base: File, log: Logger): () => Unit = { () =>
    val uiDir = new File(base, "ui")
    val nodeModulesDir = new File(uiDir, "node_modules")

    if (!sys.env.get("SKIP_UI_INSTALL").contains("true")) {
      if (!nodeModulesDir.exists()) {
        log.info("[ui-dev] node_modules missing; running npm ci")
      } else {
        log.info("[ui-dev] Running npm ci to sync dependencies before dev server start")
      }
      CommandRunner.run(install, "ui-dev npm ci", uiDir, log)
    } else {
      log.info("[ui-dev] SKIP_UI_INSTALL=true; skipping npm ci")
    }

    if (process.isEmpty || !process.exists(_.isAlive())) {
      log.info("[ui-dev] Starting Angular dev server via npm...")
      val runProcess = Process(run, uiDir)
      process = Option(runProcess.run(ProcessLogger(
        out => log.info(s"[ui-dev][stdout] $out"),
        err => log.error(s"[ui-dev][stderr] $err")
      )))
      log.info("[ui-dev] Angular dev server started (http://localhost:4200).")
    } else {
      log.info("[ui-dev] Angular dev server already running; skipping restart.")
    }

    if (!shutdownHookRegistered) {
      sys.addShutdownHook {
        process.foreach { p =>
          if (p.isAlive()) {
            log.info("[ui-dev] Stopping Angular dev server...")
            p.destroy()
            log.info("[ui-dev] Angular dev server stopped.")
          }
        }
        process = None
      }
      shutdownHookRegistered = true
    }
  }
}
