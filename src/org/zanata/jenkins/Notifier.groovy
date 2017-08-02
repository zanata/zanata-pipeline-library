package org.zanata.jenkins
import groovy.time

class Notifier implements Serializable {
  private def build
  private def env
  private def steps
  private def repoUrl
  private def contextString

  Notifier(env, steps, build = null, repoUrl = null, contextString = null) {
    this.build = build
    this.env = env
    this.steps = steps
    this.repoUrl = repoUrl
    this.contextString = (contextString) ?: env.JOB_NAME
  }

  void started(def build = null ) {
    if (build != null){
      this.build = build
    }
    sendHipChat color: "GRAY", notify: true, message: "STARTED: Job " + jobLinkHtml()
    updateGitHubCommitStatus('PENDING', 'STARTED')
  }

  void testResults(def testType, def currentBuildResult, def message = '') {
    assert build != null : 'Notifier.build is null'
    // if tests have failed currentBuild.result will be 'UNSTABLE'
    String summary
    String state
    if (currentBuildResult == null || currentBuildResult == 'SUCCESS'){
      summary="TEST PASSED ($testType)"
      state='PENDING'
      sendHipChat color: "GREEN", notify: true, message: "$summary: Job " + jobLinkHtml()
    }else{
      build.result = currentBuildResult
      summary="TEST FAILED ($testType)"
      sendHipChat color: "YELLOW", notify: true, message: "$summary: Job " + jobLinkHtml()
      state=currentBuildResult
    }
    updateGitHubCommitStatus(state, summary + (message == '' ) ? '' : ": $message")
   }

  void finish(def message = ''){
    String postfix=''
    if (build.duration){
      TimeDuration duration=TimeDuration((build.duration / (1000 * 60 * 60)) % 60,
        (build.duration / (1000 * 60)) % 60,
        (build.duration / 1000 % 60,
        build.duration% 1000)
      postfix=' Duration: '+duration.toString()
    }
    if (build.result == null ){
      build.result = 'SUCCESS'
    }
    if (build.result == 'SUCCESS' ){
      successful(message + postfix);
    }else{
      failed(message + postfix);
    }
  }

  // Revised from https://issues.jenkins-ci.org/browse/JENKINS-38674
  private void updateGitHubCommitStatus(String state, String message, String context = null) {
    def ctx = context ?: contextString

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

  void successful(def message='') {
    sendHipChat color: "GRAY", notify: true, message: "SUCCESSFUL: Job " + jobLinkHtml()
    if (build){
      updateGitHubCommitStatus('SUCCESS', 'SUCCESS: ' + message)
    }
  }

  void failed(def message='') {
    sendHipChat color: "RED", notify: true, message: "FAILED: Job " + jobLinkHtml()
    if (build){
      updateGitHubCommitStatus('FAILURE', 'FAILURE: ' + message)
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
