package org.zanata.jenkins
// This class use git ls-remote, thus no need to clone the repository
// TODO race condition if branch changes; use checkout.GIT_COMMIT (available after Jenkins 2.6)

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
  ScmGit(env, steps, repoUrl) {
    this.env = env
    this.steps = steps
    this.repoUrl = repoUrl
  }

  // This cannot in constructor because https://issues.jenkins-ci.org/browse/JENKINS-26313
  //  Workflow script fails if CPS-transformed methods are called from constructors
  void init(String branchTagPull){
    String[] headsGitLsRemoteLines = steps.sh([
      returnStdout: true,
      script: "git ls-remote $repoUrl  refs/heads/*",
      ]).split('\n')
    String[] pullGitLsRemoteLines

    if ( branchTagPull ==~ /PR-.*/ ) {
      this.pullRequestNum = branchTagPull.replaceFirst(/PR-/, '') as Integer
      pullGitLsRemoteLines = steps.sh([
        returnStdout: true,
        script: "git ls-remote $repoUrl refs/pull/${pullRequestNum}/head",
        ]).split('\n')
      if (pullGitLsRemoteLines) {
        this.commitId = parseCommitId(pullRequestNum as String, pullGitLsRemoteLines)
      }
    } else {
      // It can either be tag or branch
      // We look branches (heads) first
      this.commitId = parseCommitId(branchTagPull, headsGitLsRemoteLines)
      if (! this.commitId) {
        // branchTagPull is a tag
        String[] tagsGitLsRemoteLines = steps.sh([
          returnStdout: true,
          script: "git ls-remote $repoUrl refs/tags/${branchTagPull}",
          ]).split('\n')
        if (gitLsRemoteLines) {
          this.commitId = parseCommitId(branchTagPull, tagsGitLsRemoteLines)
        }
      }
    }
    assert commitId != null

    // Find branch
    this.branch = parseBranch(commitId, headsGitLsRemoteLines)

    // Find PR Num
    if (!pullGitLsRemoteLines) {
      pullGitLsRemoteLines = steps.sh([
        returnStdout: true,
        script: "git ls-remote $repoUrl refs/pull/*/head",
        ]).split('\n')
      this.pullRequestNum  = parseBranch(commitId, pullGitLsRemoteLines) as Integer
    }
  }

  @PackageScope
  static String parseCommitId(String branchTagPull, String[] gitLsRemoteLines, String type = null) {
    for(int i=0; i<gitLsRemoteLines?.length; i++) {
      String[] tokens = gitLsRemoteLines[i].split()
      // tokens[1] looks like refs/heads/master
      String[] refElems = tokens[1].split('/')
      if (refElems[2] == branchTagPull) {
        if (type && (refElems[1] != type)){
          continue
        }
        return tokens[0]
      }
    }
    return null
  }

  // Parse branches, tags, or pull request Id
  @PackageScope
  static String parseBranch(String commitId, String[] gitLsRemoteLines) {
    for (int i = 0; i < gitLsRemoteLines.length; i++) {
      // split each line by whitespace
      String[] tokens = gitLsRemoteLines[i].split()
      if (tokens[0] == commitId) {
        // format to search is refs/heads/<branch>
        return tokens[1].split('/')[2]
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

