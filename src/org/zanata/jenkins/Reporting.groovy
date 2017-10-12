package org.zanata.jenkins
import org.zanata.jenkins.ScmGit
import hudson.AbortException
import com.cloudbees.groovy.cps.NonCPS


class Reporting implements Serializable {

  // Send test coverage data to codecov.io
  @NonCPS
  static void codecov(def env, def steps, String repoUrl){
    ScmGit scmGit = new ScmGit(env, steps, repoUrl)
    String sha = scmGit.getCommitId(env.BRANCH_NAME)
    try {
      withCredentials(
        [[$class: 'StringBinding',
        credentialsId: 'codecov_zanata-platform',
        variable: 'CODECOV_TOKEN']]) {
        // NB the codecov script uses CODECOV_TOKEN
        // TODO use checkout.GIT_COMMIT after Jenkins 2.6 (https://zanata.atlassian.net/browse/ZNTA-2237)
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
