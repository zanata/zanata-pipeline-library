package org.zanata.jenkins

class Notifier implements Serializable {
  public static final String CONTEXT_STARTED = "STARTED"
  public static final String CONTEXT_UNIT = "UNIT"
  public static final String CONTEXT_JBOSSEAP = "JBOSSEAP"
  public static final String CONTEXT_WILDFLY8 = "WILDFLY8"
  // Build is finished
  public static final String CONTEXT_FINISH = "FINISH"
  private def build
  private def env
  private def steps
  private def repoUrl

  Notifier(env, steps, build = null, repoUrl = null) {
    this.build = build
    this.env = env
    this.steps = steps
    this.repoUrl = repoUrl
  }

  void started(def build = null) {
    sendHipChat color: "GRAY", notify: true, message: "STARTED: Job " + jobLinkHtml()
    updateGitHubCommitStatus('STARTED: ')
  }

  void testResults(def testType, def currentBuildResult, def message = '') {
    // if tests have failed currentBuild.result will be 'UNSTABLE'
    String summary
    if (currentBuildResult == 'SUCCESS'){
      build.result = null
    }else{
      build.result = currentBuildResult
    }
    if (build.result == null) {
      summary="TEST PASSED ($testType)"
      sendHipChat color: "GREEN", notify: true, message: "$summary: Job " + jobLinkHtml()
    } else {
      summary="TEST FAILED ($testType)"
      sendHipChat color: "YELLOW", notify: true, message: "$summary: Job " + jobLinkHtml()
    }
    updateGitHubCommitStatus("$summary: $message ")
   }

  void finish(String message = ''){
    if (build.result == null ){
      build.result = 'SUCCESS'
    }
    if (build.result == 'SUCCESS' ){
      successful(message);
    }else{
      failed(message);
    }
  }

  // Revised from https://issues.jenkins-ci.org/browse/JENKINS-38674
  private void updateGitHubCommitStatus(String message) {
    def msg = message\
      + ((build.durationString)? ' Duration: ' + build.durationString : '')\
      + ((build.description)? ' Desc: ' + build.description: '')

    steps.step([
      $class: 'GitHubCommitStatusSetter',
      // Use properties GithubProjectProperty
      reposSource: [$class: "ManuallyEnteredRepositorySource", url: repoUrl ],
      errorHandlers: [[$class: 'ShallowAnyErrorHandler']],
      statusResultSource: [
        $class: 'ConditionalStatusResultSource',
        results: [
          [$class: 'BetterThanOrEqualBuildResult', result: 'SUCCESS', state: 'SUCCESS', message: msg ],
          [$class: 'BetterThanOrEqualBuildResult', result: 'UNSTABLE', state: 'UNSTABLE', message: msg ],
          [$class: 'BetterThanOrEqualBuildResult', result: 'FAILURE', state: 'FAILURE', message: msg ],
          [$class: 'AnyBuildResult', state: 'PENDING', message: msg ],
        ]
      ]
    ])
  }


  void successful(String message='') {
    sendHipChat color: "GRAY", notify: true, message: "SUCCESSFUL: Job " + jobLinkHtml()
    if (build){
      updateGitHubCommitStatus(build.result + ': ' + message)
    }
  }

  void failed(String message='') {
    sendHipChat color: "RED", notify: true, message: "FAILED: Job " + jobLinkHtml()
    if (build){
      updateGitHubCommitStatus(build.result + ': ' + message)
    }
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
