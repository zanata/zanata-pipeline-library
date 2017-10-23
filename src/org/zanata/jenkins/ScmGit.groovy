package org.zanata.jenkins
// This class use git ls-remote, thus no need to clone the repository

import groovy.transform.PackageScope

class ScmGit implements Serializable {
  private def env
  private def steps
  private def mainRepoUrl
  private String commitId
  private Integer pullRequestNum
  private String branch
  // Head (branch) git ls-remote lines
  private String[] headsGitLsRemoteLines

  // Note: this method may be expensive, because it calls git ls-remote and iterates through all pull request heads
  ScmGit(env, steps, mainRepoUrl, String branchTagPull = 'master') {
    this.env = env
    this.steps = steps
    this.mainRepoUrl = mainRepoUrl

    this.commitId = parseCommitId(steps, branchTagPull, mainRepoUrl)
    this.branch = parseCommitId(steps, this.commitId, mainRepoUrl)

    if (branchTagPull ==~ /PR-.*/){
      this.pullRequestNum = branchPullTag.replace(/PR-/, '').toInteger()
    }
  }

  @PackageScope
  String[] loadGitLsRemoteLines(def steps, String repoUrl, String options = '', String globPattern = ''){
    // Always return new git ls-remote result if it is not main repo
    return steps.sh([
      returnStdout: true,
      script: "git ls-remote $options $repoUrl $globPattern",
      ]).split('\n')
  }

  // TODO race condition if branch changes; use checkout.GIT_COMMIT (available after Jenkins 2.6)
  // See https://zanata.atlassian.net/browse/ZNTA-2237
  static String parseCommitId(def steps, String branchTagPull, String repoUrl) {
    String[] gitLsRemoteLines
    if ( branchTagPull ==~ /PR-.*/ ) {
      // Pull request does not show real branchTagPull name
      gitLsRemoteLines = loadGitLsRemoteLines(steps, repoUrl, "refs/pull/" + branchTagPull.replace(/PR-/, '') + "/head")
      if (gitLsRemoteLines) {
        return gitLsRemoteLines[0].split()[0]
      }
    } else {
      // It can either be tag or branch
      gitLsRemoteLines = loadGitLsRemoteLines(steps, repoUrl, "refs/*/" + branchTagPull )
      if (gitLsRemoteLines) {
        return gitLsRemoteLines[0].split()[0]
      }
    }
    return null
  }

  static String parseBranch(def steps, String commitId, String repoUrl) {
    String[] gitLsRemoteLines = loadGitLsRemoteLines(steps, repoUrl, "refs/heads/*")
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

  // getGitCommitId: get the commit Id of the tip of branch
  //   branch: The branch of interested
  //   repoUrl: Specify this if you are interested in other branch
  //            (Such as pipeline-library)
  String getCommitId(String branch = this.branch, String repoUrl = mainRepoUrl) {
    if (repoUrl != mainRepoUrl)
      return parseCommitId(steps, branch, repoUrl)
    return commitId
  }

  // Note: this method may be expensive, because it calls git ls-remote and iterates through all pull request heads
  // Get pull request id
  // Returns null when commitId is not the tip of any pull request
  // If this needs to become public, please move it to PullRequests.groovy
  @PackageScope
  Integer getPullRequestNum() {
    return pullRequestNum
  }

  @PackageScope
  String getRepoUrl() {
    return getRepoUrl
  }

  // Get branch name, given commitId
  // Returns null when nothing commitId is not the tip of any branch
  // Note: this method may be expensive, because it calls git ls-remote and iterates through all heads
  @PackageScope
  String getBranch(String commitId = this.commitId, String repoUrl = mainRepoUrl) {
    if (repoUrl != mainRepoUrl || commitId != this.commitId )
      return parseBranch(steps, commitId, repoUrl)
    return branch
  }
}

