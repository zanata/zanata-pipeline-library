package org.zanata.jenkins
// This class use git ls-remote, thus no need to clone the repository

import groovy.transform.PackageScope

class ScmGit implements Serializable {
  private def env
  private def steps
  private def mainRepoUrl

  ScmGit(env, steps, mainRepoUrl) {
    this.env = env
    this.steps = steps
    this.mainRepoUrl = mainRepoUrl
  }

  @PackageScope
  String[] loadGitLsRemoteLines(String repoUrl = mainRepoUrl, String globPattern = ''){
    // Always return new git ls-remote result if it is not main repo
    steps.echo "repoUrl: $repoUrl   globPattern: $globPattern"
    return steps.sh([
      returnStdout: true,
      script: "git ls-remote  " + repoUrl + " " + globPattern
      ]).split('\n')
  }


  // TODO race condition if branch changes; use checkout.GIT_COMMIT (available after Jenkins 2.6)
  // See https://zanata.atlassian.net/browse/ZNTA-2237
  // getGitCommitId: get the commit Id of the tip of branch
  //   branch: The branch of interested
  //   repoUrl: Specify this if you are interested in other branch
  //            (Such as pipeline-library)
  String getCommitId(String branch, String repoUrl = mainRepoUrl) {
    String[] gitLsRemoteLines
    if ( branch ==~ /PR-.*/ ) {
      // Pull request does not show real branch name
      gitLsRemoteLines = loadGitLsRemoteLines(repoUrl, "refs/pull/" + env.CHANGE_ID + "/head")
      steps.echo "gitLsRemoteLines: repoUrl: ${repoUrl} branch: ${branch} lines: " + gitLsRemoteLines?.length
      steps.echo "gitLsRemoteLines: " + gitLsRemoteLines?.toString()
      if (gitLsRemoteLines) {
        return gitLsRemoteLines[0].split()[0]
      }
    } else {
      steps.echo "gitLsRemoteLines: repoUrl: ${repoUrl} branch: ${branch} lines: " + gitLsRemoteLines?.length
      steps.echo "gitLsRemoteLines: " + gitLsRemoteLines?.toString()
      // It can either be tag or branch
      gitLsRemoteLines = loadGitLsRemoteLines(repoUrl, "refs/*/" + branch )
      if (gitLsRemoteLines) {
        return gitLsRemoteLines[0].split()[0]
      }
    }
    steps.echo "gitLsRemoteLines: null"
    return null
  }

  // Note: this method may be expensive, because it calls git ls-remote and iterates through all pull request heads
  // Get pull request id given commitId
  // Returns null when nothing commitId is not the tip of any pull request
  // If this needs to become public, please move it to PullRequests.groovy
  @PackageScope
  Integer getPullRequestNum(String commitId, String repoUrl = mainRepoUrl) {
    String[] gitLsRemoteLines = loadGitLsRemoteLines(repoUrl, "refs/pull/*/head")
    for (int i = 0; i < gitLsRemoteLines.length; i++) {
      // split each line by whitespace
      String[] tokens = gitLsRemoteLines[i].split()
      if (tokens[0] == commitId) {
        // format to search is refs/pull/<pullId>/head
        return tokens[1].split('/')[2].toInteger()
      }
    }
    return null
  }

  // Get branch name, given commitId
  // Returns null when nothing commitId is not the tip of any branch
  // Note: this method may be expensive, because it calls git ls-remote and iterates through all heads
  @PackageScope
  String getBranch(String commitId, String repoUrl = mainRepoUrl) {
    String[] gitLsRemoteLines = loadGitLsRemoteLines(repoUrl, "refs/heads/*")
    for (int i = 0; i < gitLsRemoteLines.length; i++) {
      // split each line by whitespace
      String[] tokens = gitLsRemoteLines[i].split()
      if (tokens[0] == commitId) {
        // format to search is refs/heads/<branch>
        return tokens[1].split('/')[3]
      }
    }
    return null
  }
}

