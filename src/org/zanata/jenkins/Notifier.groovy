package org.zanata.jenkins

/**
 * @author Sean Flanigan <a href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 * @author Alex Eng <a href="mailto:aeng@redhat.com">aeng@redhat.com</a>
 */
class Notifier implements Serializable {
  private def build
  private def env
  private def steps
  private def repoUrl
  private def jobContext

  Notifier(env, steps, build = null, repoUrl = null, jobContext = env.JOB_NAME) {
    this.build = build
    this.env = env
    this.steps = steps
    this.repoUrl = repoUrl
    this.jobContext = jobContext
    steps.echo '[WARN] build is null; skipping the finish() method'
  }

  void started() {
    sendHipChat color: "GRAY", notify: true, message: "STARTED: Job " + jobLinkHtml()
    updateGitHubCommitStatus('PENDING', 'STARTED')
  }

  /* testResults: publish the current test results to GitHub and Hipchat
   *
   * testType: e.g. UNIT, WILDFLY, JBOSSEAP
   *
   * currentBuildResult: the current build state
   *   null | 'SUCCESS' | 'PENDING' :
   *     GitHub state 'PENDING', test passed, but still building
   *
   *   'UNSTABLE' | 'FAILURE' :
   *     GitHub state 'FAILURE', test failed.
   *
   *   'ERROR':
   *     GitHub state 'ERROR', build error.
   *
   * message: Additional message to be shown.
   */
  void testResults(testType, currentBuildResult, message = '') {
    // if tests have failed currentBuild.result will be 'UNSTABLE'
    String summary
    String githubState
    String hipChatColor
    if (currentBuildResult == null || currentBuildResult == 'SUCCESS' ||
        currentBuildResult == 'PENDING') {
      summary="TEST PASSED ($testType)"
      githubState='PENDING'
      hipChatColor='GREEN'
      sendHipChat color: "GREEN", notify: true, message: "$summary: Job " + jobLinkHtml()
    } else if (currentBuildResult == 'ERROR') {
      summary="TEST $currentBuildResult ($testType)"
      hipChatColor='YELLOW'
      githubState='ERROR'
    } else {
      summary="TEST FAILED ($testType)"
      hipChatColor='YELLOW'
      githubState='FAILURE'
    }
    sendHipChat color: hipChatColor, notify: true, message: "$summary: Job " + jobLinkHtml()
    updateGitHubCommitStatus(githubState, summary + (message == '' ) ? '' : ": $message")
  }

  void finish(message = ''){
    if (build == null ){
      steps.echo '[WARN] build is null, skipping the finish() method'
      return
    }

    String postfix=''
    if (build.duration>0){
      int millisecond = build.duration % 1000
      int second = build.duration.intdiv(1000) % 60
      int minute = (build.duration.intdiv(1000 * 60)) % 60
      int hour = (build.duration.intdiv(1000 * 60 * 60)) % 60
      postfix=' Duration: ' +
        ((hour > 0 ) ? hour + ' hr ' : '') +
        ((minute > 0 ) ? minute + ' min ' : '') +
        ((second > 0 ) ? second + ' sec ' : '') +
        ((millisecond > 0 )? millisecond + ' ms' : '')
    }
    if (( build.result ?: 'SUCCESS') == 'SUCCESS' ) {
      successful(message.toString() + postfix);
    } else if ( build.result ==  'UNSTABLE' ) {
      failed(message.toString() + postfix);
    } else {
      error(message.toString() + postfix);
    }
  }

  // Revised from https://issues.jenkins-ci.org/browse/JENKINS-38674
  // GitHub commit supports following states: 'pending', 'success', 'error' or 'failure'.
  // See: https://developer.github.com/v3/repos/statuses/
  private void updateGitHubCommitStatus(String state, String message, String overrideContext = null) {
    def ctx = overrideContext ?: jobContext

    if (repoUrl == null) {
      steps.echo '[WARN] repoUrl is null; skipping GitHub Status'
      return
    }

    steps.step([
      $class: 'GitHubCommitStatusSetter',
      // Use properties GithubProjectProperty
      reposSource: [$class: "ManuallyEnteredRepositorySource", url: repoUrl ],
      contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: ctx],
      errorHandlers: [[$class: 'ShallowAnyErrorHandler']],
      statusResultSource: [
        $class: 'ConditionalStatusResultSource',
        results: [
          [$class: 'AnyBuildResult', state: state, message: message ],
        ]
      ]
    ])
  }

  // Build success without failed tests
  void successful(message='') {
    sendHipChat color: "GRAY", notify: true, message: "SUCCESSFUL: Job " + jobLinkHtml()
    updateGitHubCommitStatus('SUCCESS', 'SUCCESS: ' + message.toString())
  }

  // Used when tests failure, but compile completed
  void failed(message='') {
    sendHipChat color: "RED", notify: true, message: "FAILED: Job " + jobLinkHtml()
    updateGitHubCommitStatus('FAILURE', 'FAILURE: ' + message.toString())
  }

  // Used when build failure. e.g. build system/script failed, or compile error
  void error(message='') {
    sendHipChat color: "RED", notify: true, message: "ERROR: Job " + jobLinkHtml()
    updateGitHubCommitStatus('ERROR', 'ERROR: ' + message.toString())
  }

  private String jobLinkHtml() {
    "<a href=\"${env.BUILD_URL}\">${env.JOB_NAME} #${env.BUILD_NUMBER}</a>"
  }

  private void sendHipChat(Map p) {
    try {
      steps.hipchatSend(
              color: p.color,
              failOnError: p.failOnError ?: false,
              message: p.message,
              notify: p.notify ?: false,
              sendAs: p.sendAs,
              textFormat: p.textFormat ?: false,
              v2enabled: p.v2enabled ?: false)
    } catch (NoSuchMethodError ignored) {
      steps.echo("hipchatSend skipped: no such method")
    } catch (Exception e) {
      // org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException is not in Maven Central,
      // so we can't use it in Jenkins Pipeline Unit tests:
      if (e.toString().contains('RejectedAccessException')) {
        // allow for Jenkins In-process Script Approval
        throw e
      }
      steps.echo("hipchatSend failed: " + e)
    }
  }
}
