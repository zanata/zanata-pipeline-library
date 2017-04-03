class notify implements Serializable {
  private def env

  void init(def env) {
    this.env = env
  }

  void started() {
    sendHipChat color: "GRAY", notify: true, message: "STARTED: Job " + jobLink()
  }

  void testResults(def testType, def currentBuild) {
    // if tests have failed currentBuild.result will be 'UNSTABLE'
    if (currentBuild.result != null) {
      sendHipChat color: "YELLOW", notify: true, message: "TESTS FAILED ($testType): Job " + jobLink()
    } else {
      sendHipChat color: "GREEN", notify: true, message: "TESTS PASSED ($testType): Job " + jobLink()
    }
  }

  void successful() {
    sendHipChat color: "GRAY", notify: true, message: "SUCCESSFUL: Job " + jobLink()
  }

  void failed() {
    sendHipChat color: "RED", notify: true, message: "FAILED: Job " + jobLink()
  }

  String jobLink() {
    "<a href=\"${env.BUILD_URL}\">${env.JOB_NAME} #${env.BUILD_NUMBER}</a>"
  }

  private void sendHipChat(Map p) {
    try {
      hipchatSend(
              color: p.color,
              failOnError: p.failOnError ?: false,
              message: p.message,
              notify: p.notify ?: false,
              sendAs: p.sendAs,
              textFormat: p.textFormat ?: false,
              v2enabled: p.v2enabled ?: false)
    } catch (NoSuchMethodError e) {
      println("hipchatSend skipped: no such method")
      return
    } catch (Exception e) {
      // TODO how to print stack trace to the build log?
      e.printStackTrace()
      println("hipchatSend failed" + e)
      return
    }
  }
}
