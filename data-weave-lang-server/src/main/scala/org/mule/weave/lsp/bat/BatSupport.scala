package org.mule.weave.lsp.bat

import java.io.{File, FileInputStream, FileOutputStream}
import java.util.zip.ZipInputStream

import org.mule.weave.lsp.services.MessageLoggerService
import org.mule.weave.v2.deps.DependencyManager

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.reflect.io.File.separator
import scala.sys.process._
trait BatSupport {
  val DEFAULT_BAT_WRAPPER_VERSION: String
  val batHome: File
  val wrapperFolder: File
  val maven: DependencyManager
  val logger: MessageLoggerService
  val NEXUS: String
  private val isWindows: Boolean = System.getProperty("os.name").toLowerCase.contains("win")
  def run(workdir: String, testPath: String): String = {
    val workspacePath = workdir.replaceAll("\"", "")
    println(s"Starting BAT execution: $testPath in folder $workspacePath" )
    val workspaceFile = new File(workspacePath)
    val executableName: String = if(isWindows) s"$wrapperFolder${separator}bin${separator}bat.bat" else s"$wrapperFolder${separator}bin${separator}bat"
    val stream: Stream[String] = Process(Seq(executableName,testPath.replaceAll("\"", "")), workspaceFile).lineStream
    stream.foreach(println)
    stream.mkString
  }

  def setupBat(): Unit = {
    if (!isBatInstalled) {
      downloadAndInstall()
    } else
      logger.logInfo("BAT CLI already installed!")
  }

  def isBatInstalled: Boolean = {
    if (batHome.exists() && wrapperFolder.exists()) {
      val files: Array[File] = wrapperFolder.listFiles()
      val checkFolders: Future[Boolean] = flattenBooleans(boolSeq(existsFile(files, "bin"), existsFile(files, "lib")))
      val verificationResult: Future[Boolean] = checkFolders.flatMap { result =>
        if (result) {
          flattenBooleans(boolSeq(verifyBinContents(files), verifyLibContents(files)))
        } else
          Future.successful(result)
      }
      Await.result(verificationResult, 20.seconds)
    }
    else
      false
  }

  def downloadAndInstall(): Boolean = {
    if(wrapperFolder.exists())
      wrapperFolder.delete()
    val wrapperFile = downloadBat()
    unzipWrapper(wrapperFile)
    val installed = isBatInstalled
    if(installed)
      print("BAT CLI installed successfully")
    else
      print("BAT CLI wasn't installed")
    installed
  }

  def downloadBat(): File = {
    import java.io.File
    import java.net.URL

    import sys.process._
    if(!batHome.exists()) {
      batHome.mkdir()
    }
    if(wrapperFolder.exists())
      wrapperFolder.delete()
    val batWrapperFileName = s"bat-wrapper-$DEFAULT_BAT_WRAPPER_VERSION.zip"
    val path = batHome.getAbsolutePath + s"/$batWrapperFileName"
    val url = s"$NEXUS/com/mulesoft/bat/bat-wrapper/$DEFAULT_BAT_WRAPPER_VERSION/$batWrapperFileName"
    val file = new File(path)
    if(!file.exists())
      file.createNewFile()
    (new URL(url) #> file !!)
    file
  }

  def unzipWrapper(wrapperFile: File): Unit = {
    val fileInputStream = new FileInputStream(wrapperFile)
    val zis = new ZipInputStream(fileInputStream)
    Stream.continually(zis.getNextEntry).takeWhile(_ != null).foreach{ file =>
      val name = file.getName

      val newFile = new File(wrapperFile.getParentFile.getAbsolutePath + separator +  name)
      if(!newFile.exists() && file.isDirectory) {
          newFile.mkdir()
      } else {
        newFile.createNewFile()
        newFile.setExecutable(true)
        val outputStream = new FileOutputStream(newFile)
        val buffer = new Array[Byte](1024)
        Stream.continually(zis.read(buffer)).takeWhile(_ != -1).foreach(outputStream.write(buffer, 0, _))
      }
    }
  }

  private def verifyBinContents(files: Array[File]): Future[Boolean] = {
    val binFolderFiles: Array[File] = findFile(files, "/bin").get.listFiles()
    flattenBooleans(
      boolSeq(existsFile(binFolderFiles, "/bat"), existsFile(binFolderFiles, "/bat.bat")))
  }

  private def verifyLibContents(files: Array[File]): Future[Boolean] = {
    val libFolderFiles: Array[File] = findFile(files, "/lib").get.listFiles()
    flattenBooleans(
      boolSeq(
        existsFile(libFolderFiles, s"bat-wrapper-$DEFAULT_BAT_WRAPPER_VERSION.jar"),
        existsFile(libFolderFiles, "maven-repository-metadata-", end = false),
        existsFile(libFolderFiles, "plexus-utils-", end = false)
      ))
  }

  private def findFile(files: Array[File], name: String, end: Boolean = true): Option[File] = {
    if (end)
      files.find(f => f.getAbsolutePath.endsWith(name))
    else
      files.find(f => f.getAbsolutePath.contains(name))
  }

  private def boolSeq(booleans: Future[Boolean]*): Future[Seq[Boolean]] = {
    Future.sequence(booleans.toSeq)
  }

  private def flattenBooleans(booleansFuture: Future[Seq[Boolean]]): Future[Boolean] = {
    booleansFuture.map(_.reduce(_ && _))
  }

  private def existsFile(files: Array[File], name: String, end: Boolean = true): Future[Boolean] = Future {
    findFile(files, name, end).isDefined
  }


}
