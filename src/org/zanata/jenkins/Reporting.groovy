package org.zanata.jenkins
import org.zanata.jenkins.ScmGit
import hudson.AbortException
import com.cloudbees.groovy.cps.NonCPS


class Reporting implements Serializable {

  // Send test coverage data to codecov.io
  @NonCPS
  static void codecov(def env, def steps, String repoUrl, String credentialId = null){
    ScmGit scmGit = new ScmGit(env, steps, repoUrl)
    String sha = scmGit.getCommitId(env.BRANCH_NAME)
    String branch = scmGit.getBranch(sha)
    credentialId = credentialId ?: 'codecov_' + repoUrl.replace(/^.*\//, '')

    try {
      withCredentials(
        [[$class: 'StringBinding',
        credentialsId: credentialId,
        variable: 'CODECOV_TOKEN']]) {
        // NB the codecov script uses CODECOV_TOKEN
        // TODO use checkout.GIT_COMMIT with Jenkins 2.x (https://zanata.atlassian.net/browse/ZNTA-2237)
        def codecovCmd = "curl -s https://codecov.io/bash | bash -s - -K -b ${env.BUILD_NUMBER} -B $branch -C $sha"
        if (env.CHANGE_ID){
          codecovCmd += " -P ${env.CHANGE_ID}"
        }

        steps.echo codecovCmd
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
