package org.zanata.jenkins

class Notifier implements Serializable {
  public static final String CONTEXT_UNIT = "UNIT"
  public static final String CONTEXT_JBOSSEAP = "JBOSSEAP"
  public static final String CONTEXT_WILDFLY8 = "WILDFLY8"
  // Build is finished
  public static final String CONTEXT_FINISH = "FINISH"
  private def env
  private def steps
  private def repoUrl

  Notifier(env, steps, repoUrl='') {
    this.env = env
    this.steps = steps
    this.repoUrl = repoUrl
  }

  void started() {
    sendHipChat color: "GRAY", notify: true, message: "STARTED: Job " + jobLinkHtml()
  }

  void testResults(def testType, def currentBuildResult) {
    // if tests have failed currentBuild.result will be 'UNSTABLE'
    if (currentBuildResult == null || currentBuildResult == 'SUCCESS') {
      sendHipChat color: "GREEN", notify: true, message: "TESTS PASSED ($testType): Job " + jobLinkHtml()
    } else {
      sendHipChat color: "YELLOW", notify: true, message: "TESTS FAILED ($testType): Job " + jobLinkHtml()
    }
  }

  // context: the current progress of the build, like UNIT, WILDFLY8, JBOSSEAP
  void updateBuildStatus(String context, String result, String message = '' ){
    switch(context){
      case CONTEXT_UNIT:
        if (result == 'SUCCESS'){
          currentBuild.result = null
        }
        testResult(context, result);
        break;
      case CONTEXT_WILDFLY8:
      case CONTEXT_JBOSSEAP:
        currentBuild.result = result
        testResult(context, result);
        return;
      case CONTEXT_FINISH:
        if (result == 'SUCCESS' ){
          successful();
        }else{
          failed();
        }
        break;
      default:
        currentBuild.result = result;
        break;
    }
    setGitHubCommitStatus(context, message)
  }

  // Revised from https://issues.jenkins-ci.org/browse/JENKINS-38674
  void setGitHubCommitStatus(String context, String message = '' ) {
    def msg = message + ' ' + context + ': '\
      + ((currentBuild.duration)? ' Duration: ' + currentBuild.durationString : '')\
      + ((currentBuild.description)? ' Desc: ' + currentBuild.description: '')

    step([
      $class: 'GitHubCommitStatusSetter',
      // Ensure it picked up from the specified URL, not zanata-pipeline-library
      reposSource: [$class: "ManuallyEnteredRepositorySource", url: repoUrl ],

      errorHandlers: [[$class: 'ShallowAnyErrorHandler']],
      statusResultSource: [
        $class: 'ConditionalStatusResultSource',
        results: [
          [$class: 'BetterThanOrEqualBuildResult', result: 'SUCCESS', state: 'SUCCESS', message: msg ],
          [$class: 'BetterThanOrEqualBuildResult', result: 'UNSTABLE', state: 'UNSTABLE', message: msg ],
          [$class: 'BetterThanOrEqualBuildResult', result: 'FAILURE', state: 'FAILURE', message: msg ],
        ]
      ]
    ])
  }


  void successful() {
    sendHipChat color: "GRAY", notify: true, message: "SUCCESSFUL: Job " + jobLinkHtml()
  }

  void failed() {
    sendHipChat color: "RED", notify: true, message: "FAILED: Job " + jobLinkHtml()
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
