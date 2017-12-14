package org.zanata.jenkins
import org.zanata.jenkins.ScmGit
import hudson.AbortException

class Reporting implements Serializable {

  // Send test coverage data to codecov.io
  static void codecov(def env, def steps, def scmGit, String credentialId = null){
    String sha = scmGit.getCommitId()
    String branch = scmGit.getBranch()
    credentialId = credentialId ?: 'codecov_' + scmGit.getRepoUrl().replaceFirst(/^.*\//, '')
    steps.echo "codecov credentialId: $credentialId"

    try {
      def codecovCmd = "curl -s https://codecov.io/bash | bash -s - -K -b ${env.BUILD_NUMBER} -B $branch -C $sha"
      if (env.CHANGE_ID){
        codecovCmd += " -P ${env.CHANGE_ID}"
      }

      steps.echo codecovCmd
      steps.withCredentials([[$class: 'StringBinding',
        credentialsId: credentialId,
        variable: 'CODECOV_TOKEN']]) {
        // NB the codecov script uses CODECOV_TOKEN
        // TODO use checkout.GIT_COMMIT with Jenkins 2.x (https://zanata.atlassian.net/browse/ZNTA-2237)
        steps.sh codecovCmd
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
