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

  Notifier(env, steps, repoUrl = '') {
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

  // build: Build object. Use currentBuild
  // context: the current progress of the build, like UNIT, WILDFLY8, JBOSSEAP
  // message: The message to be print. Default is ''
  // result: The result to be set
  void updateBuildStatus(def build, String context, String message = '', String result = null ){
    if ( result != null ){
      build.result = result
    }
    switch(context){
      case CONTEXT_UNIT:
        if (result == 'SUCCESS'){
          build.result = null
        }
        testResults(context, result);
        break;
      case CONTEXT_WILDFLY8:
      case CONTEXT_JBOSSEAP:
        testResults(context, result);
        break;
      case CONTEXT_FINISH:
        if (build.result == null ){
          build.result = 'SUCCESS'
        }
        if (result == 'SUCCESS' ){
          successful();
        }else{
          failed();
        }
        break;
      default:
        break;
    }
    setGitHubCommitStatus(build, context, message)
  }

  // Revised from https://issues.jenkins-ci.org/browse/JENKINS-38674
  void setGitHubCommitStatus(def build, String context, String message = '' ) {
    def msg = message + ' ' + context + ': '\
      + ((build.durationString)? ' Duration: ' + build.durationString : '')\
      + ((build.description)? ' Desc: ' + build.description: '')

    echo "env.CHANGE_URL= ${env.CHANGE_URL}"
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
