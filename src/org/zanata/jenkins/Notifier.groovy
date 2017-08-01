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
    updateGitHubCommitStatus('STARTED: ')
  }

  void testResults(def testType, def currentBuildResult, def message = '') {
    assert build != null : 'Notifier.build is null'
    // if tests have failed currentBuild.result will be 'UNSTABLE'
    String summary
    if (currentBuildResult == null || currentBuildResult == 'SUCCESS'){
      // For some reason, currentBuildResult==null triggers NPE
      // at hudson.model.Result.fromString(Result.java:152)
      build.result = null
      summary="TEST PASSED ($testType)"
      sendHipChat color: "GREEN", notify: true, message: "$summary: Job " + jobLinkHtml()
    }else{
      build.result = currentBuildResult
      summary="TEST FAILED ($testType)"
      sendHipChat color: "YELLOW", notify: true, message: "$summary: Job " + jobLinkHtml()
    }
    updateGitHubCommitStatus("$summary: $message ")
   }

  void finish(String message = ''){
    String postfix=((build.durationString)? ' Duration: ' + build.durationString : '')
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
  private void updateGitHubCommitStatus(String message, String context = null) {
    def msg = message +
      ((build.durationString)? ' Duration: ' + build.durationString : '') +
      ((build.description)? ' Desc: ' + build.description: '')
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
