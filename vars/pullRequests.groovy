@NonCPS
void ensureJobDescription() {
  if (env.CHANGE_ID) {
    try {
      def job = manager.build.project
      // we only want to do this once, to avoid hammering the github api
      if (!job.description || !job.description.contains(env.CHANGE_URL)) {
        def sourceBranchLabel = getSourceBranchLabel()
        def abbrTitle = strings.truncateAtWord(env.CHANGE_TITLE, 50)
        def prDesc = """<a title=\"${env.CHANGE_TITLE}\" href=\"${env.CHANGE_URL}\">PR #${env.CHANGE_ID} by ${env.CHANGE_AUTHOR}</a>:
                       |$abbrTitle
                       |merging ${sourceBranchLabel} to ${env.CHANGE_TARGET}""".stripMargin()
        // ideally we would show eg sourceRepo/featureBranch ⭆ master
        // but there is no env var with that info

        println "description: " + prDesc
        //currentBuild.description = prDesc
        job.description = prDesc
        job.save()
        null // avoid returning non-Serializable Job
      }
    } catch (e) {
      // org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException is not in Maven Central,
      // so we can't use it in Jenkins Pipeline Unit tests:
      if (e.toString() contains 'RejectedAccessException') {
        // allow for Jenkins In-process Script Approval
        throw e
      }
      // NB we don't want to fail the build just because of a problem in this method
      println e
      e.printStackTrace() // not sure how to log this to the build log
    }
  }
}

@NonCPS
private String getSourceBranchLabel() {
  println "checking github api for pull request details"

  tokens = "${env.CHANGE_URL}".tokenize('/')
  org = tokens[2]
  repo = tokens[3]
  pr = tokens[5]

  // FIXME use github credentials to avoid rate limiting
  def prUrl = new URL("https://api.github.com/repos/${org}/${repo}/pulls/${pr}")
  def sourceBranchLabel = new groovy.json.JsonSlurper().parseText(prUrl.text).head.label
  return sourceBranchLabel
}
