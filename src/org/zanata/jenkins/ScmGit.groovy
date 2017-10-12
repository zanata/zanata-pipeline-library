package org.zanata.jenkins

class ScmGit implements Serializable {
  private def env
  private def steps
  private def mainRepoUrl

  ScmGit(env, steps, mainRepoUrl) {
    this.env = env
    this.steps = steps
    this.mainRepoUrl = mainRepoUrl
  }

  // TODO race condition if branch changes; use checkout.GIT_COMMIT (available after Jenkins 2.6)
  // See https://zanata.atlassian.net/browse/ZNTA-2237
  // getGitCommitId: get the commit Id of the tip of branch
  //   branch: The branch of interested
  //   repoUrl: Specify this if you are interested in other branch
  //            (Such as pipeline-library)
  public String getCommitId(String branch, String repoUrl = mainRepoUrl) {
    String refString = null
    if ( branch ==~ /PR-.*/ ) {
      // Pull request does not show real branch name
      refString = "refs/pull/" + env.CHANGE_ID + "/head"
    } else {
      refString = "refs/heads/" + branch
    }
    String commitLine = steps.sh([
      returnStdout: true,
      script: "git ls-remote " + repoUrl + " " + refString,
      ])
      return commitLine.split()[0]
  }
}

