package org.zanata.jenkins

class Notifier implements Serializable {
  private def env
  private def steps

  Notifier(env, steps) {
    this.env = env
    this.steps = steps
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
