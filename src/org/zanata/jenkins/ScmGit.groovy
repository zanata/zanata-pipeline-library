package org.zanata.jenkins
// This class use git ls-remote, thus no need to clone the repository

import com.cloudbees.groovy.cps.NonCPS
import groovy.transform.PackageScope

class ScmGit implements Serializable {
  private def env
  private def steps
  private def repoUrl
  private String commitId
  private Integer pullRequestNum
  private String branch
  // Head (branch) git ls-remote lines
  private String[] headsGitLsRemoteLines

  // Note: this method may be expensive, because it calls git ls-remote and iterates through all pull request heads
  ScmGit(env, steps, repoUrl, String branchTagPull = 'master') {
    this.env = env
    this.steps = steps
    this.repoUrl = repoUrl
    String[] gitLsRemoteLines

    // TODO race condition if branch changes; use checkout.GIT_COMMIT (available after Jenkins 2.6)
    if ( branchTagPull ==~ /PR-.*/ ) {
      this.pullRequestNum = branchPullTag.replace(/PR-/, '').toInteger()
      // Pull request does not show real branchTagPull name
      gitLsRemoteLines = steps.sh([
        returnStdout: true,
        script: "git ls-remote $repoUrl refs/pull/${pullRequestNum}/head",
        ]).split('\n')
      if (gitLsRemoteLines) {
        this.commitId = gitLsRemoteLines[0].split()[0]
      }
    } else {
      // It can either be tag or branch
      gitLsRemoteLines = steps.sh([
        returnStdout: true,
        script: "git ls-remote $repoUrl refs/*/${branchTagPull}",
        ]).split('\n')
      if (gitLsRemoteLines) {
        this.commitId = gitLsRemoteLines[0].split()[0]
      }
    }
    assert commitId != null
    gitLsRemoteLines = steps.sh([
      returnStdout: true,
      script: "git ls-remote $repoUrl refs/heads/*",
      ]).split('\n')

    this.branch = this.parseBranch(this.commitId, gitLsRemoteLines)
  }

  @PackageScope
  static String parseBranch(String commitId, String[] gitLsRemoteLines) {
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
  String getCommitId() {
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
    return repoUrl
  }

  // Get branch name
  // Returns null when nothing commitId is not the tip of any branch
  // Note: this method may be expensive, because it calls git ls-remote and iterates through all heads
  @PackageScope
  String getBranch() {
    return branch
  }
}

