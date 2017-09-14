package org.zanata.jenkins
import org.zanata.jenkins.ScmGit
import hudson.AbortException

class Reporting implements Serializable {
  private def build
  private def env
  private def steps
  private def repoUrl
  private ScmGit scmGit
  // Commit Id of main repo
  private String currentCommitId = null

  Reporting(env, steps, repoUrl) {
    this.build = build
    this.env = env
    this.steps = steps
    this.repoUrl = repoUrl
    scmGit = new ScmGit(env, steps, repoUrl)
  }

  // Send test coverage data to codecov.io
  void codecov(){
    String sha = scmGit.getCommitId(env.BRANCH_NAME)
    try {
      withCredentials(
        [[$class: 'StringBinding',
        credentialsId: 'codecov_zanata-platform',
        variable: 'CODECOV_TOKEN']]) {
        // NB the codecov script uses CODECOV_TOKEN
        steps.sh "curl -s https://codecov.io/bash | bash -s - -K -B ${env.BRANCH_NAME} -C ${sha} -P ${env.CHANGE_ID} -b ${env.BUILD_NUMBER}"
      }
    } catch (InterruptedException e) {
      throw e
    } catch (hudson.AbortException e) {
      throw e
    } catch (e) {
      steps.echo "[WARNING] Ignoring codecov error: $e"
    }
  }

}
