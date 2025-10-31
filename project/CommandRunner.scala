import java.io.File
import scala.sys.process.{Process, ProcessLogger}
import sbt.util.Logger

object CommandRunner {
  def run(command: Seq[String], label: String, cwd: File, log: Logger): Unit = {
    val stdout = new StringBuilder
    val stderr = new StringBuilder
    log.info(s"[$label] Executing: ${command.mkString(" ")} (cwd=${cwd.getAbsolutePath})")
    val exitCode = Process(command, cwd).!(ProcessLogger(
      out => {
        stdout.append(out).append('\n')
        log.info(s"[$label][stdout] $out")
      },
      err => {
        stderr.append(err).append('\n')
        log.error(s"[$label][stderr] $err")
      }
    ))
    if (exitCode != 0) {
      val summary = s"$label failed with exit code $exitCode"
      val details = new StringBuilder(summary)
      if (stdout.nonEmpty) details.append("\nstdout:\n").append(stdout)
      if (stderr.nonEmpty) details.append("\nstderr:\n").append(stderr)
      sys.error(details.toString)
    }
  }
}
