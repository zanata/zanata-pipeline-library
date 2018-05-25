package org.zanata.jenkins

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper
import static org.apache.commons.lang.StringEscapeUtils.escapeHtml

/**
 * @author Sean Flanigan <a href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 */
class PullRequests implements Serializable {

  @NonCPS
  static void ensureJobDescription(env, manager, steps) {
    if (env.CHANGE_ID) {
      try {
        def job = manager.build.project
        // we only want to do this once, to avoid hammering the github api
        if (!job.description || !job.description.contains(env.CHANGE_URL)) {
          // PR body and source branch info aren't available in env, so we use GitHub API:
          def pullRequest = getPullRequest(env.CHANGE_URL, steps)
          def sourceBranchLabel = pullRequest?.head?.label ?: "[unknown]"
          def prBody = pullRequest?.body ?: "[body not available]"
          def quoteSafeTitle = escapeHtml(env.CHANGE_TITLE).replace('"', "'")
          def safeTitle = escapeHtml(env.CHANGE_TITLE)
          def safeBody = escapeHtml(prBody)
          def prDesc = """<a title=\"$quoteSafeTitle\" href=\"${env.CHANGE_URL}\">
                       |PR #${env.CHANGE_ID} by ${env.CHANGE_AUTHOR}</a>:<br />
                       |$safeTitle<br />
                       |merging ${sourceBranchLabel} to ${env.CHANGE_TARGET}<br />
                       |$safeBody""".
              stripMargin()

          steps.echo "description: " + prDesc
          //currentBuild.description = prDesc
          job.description = prDesc
          job.save()
          null // avoid returning non-Serializable Job
        }
      } catch (e) {
        // org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException is not in Maven Central,
        // so we can't use it in Jenkins Pipeline Unit tests:
        if (e.toString().contains('RejectedAccessException')) {
          // allow for Jenkins In-process Script Approval
          throw e
        }
        // NB we don't want to fail the build just because of a problem in this method
        steps.echo StackTraces.getStackTrace(e)
      }
    }
  }

  @NonCPS
  private static Object getPullRequest(String changeURL, def steps) {
    steps.echo "checking github api for pull request details"

    def tokens = changeURL.tokenize('/')
    def org = tokens[2]
    def repo = tokens[3]
    def pr = tokens[5]

    // FIXME use github credentials to avoid rate limiting
    // https://developer.github.com/v3/pulls/#get-a-single-pull-request
    def prUrl = new URL(
        "https://api.github.com/repos/${org}/${repo}/pulls/${pr}")
    try {
      return new JsonSlurper().parseText(prUrl.text)
    } catch (Exception e) {
      // NB we don't want to lose entire job description because of rate limiting
      steps.echo StackTraces.getStackTrace(e)
      return null
    }
  }
}
