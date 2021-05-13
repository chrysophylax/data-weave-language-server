package org.mule.weave.lsp.project.impl.maven

import org.jboss.shrinkwrap.resolver.api.maven.embedded.EmbeddedMaven
import org.mule.weave.lsp.project.Project
import org.mule.weave.lsp.project.components.BuildManager
import org.mule.weave.lsp.services.ClientLogger

import java.io.File

class MavenBuildManager(project: Project, pom: File, clientLogger: ClientLogger) extends BuildManager {

  override def build(): Unit = {
    EmbeddedMaven.forProject(pom)
      .useDefaultDistribution()
      .setLogger(new LSPMavenLogger(clientLogger))
      .setBatchMode(true)
      .setGoals("clean", "compile")
      .build()
  }

  override def deploy(): Unit = {
    EmbeddedMaven.forProject(pom)
      .useDefaultDistribution()
      .setLogger(new LSPMavenLogger(clientLogger))
      .setBatchMode(true)
      .setGoals("clean", "deploy")
      .build()
  }
}
