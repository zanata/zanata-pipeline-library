void started() {
  hipchatSend color: "GRAY", notify: true, message: "STARTED: Job " + jobLink()
}

void testResults(def testType) {
  // if tests have failed currentBuild.result will be 'UNSTABLE'
  if (currentBuild.result != null) {
    hipchatSend color: "YELLOW", notify: true, message: "TESTS FAILED ($testType): Job " + jobLink()
  } else {
    hipchatSend color: "GREEN", notify: true, message: "TESTS PASSED ($testType): Job " + jobLink()
  }
}

void successful() {
  hipchatSend color: "GRAY", notify: true, message: "SUCCESSFUL: Job " + jobLink()
}

void failed() {
  hipchatSend color: "RED", notify: true, message: "FAILED: Job " + jobLink()
}

String jobLink() {
  "<a href=\"${env.BUILD_URL}\">${env.JOB_NAME} #${env.BUILD_NUMBER}</a>"
}
