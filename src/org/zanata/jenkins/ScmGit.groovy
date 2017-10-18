package org.zanata.jenkins

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

  // TODO race condition if branch changes; use checkout.GIT_COMMIT (available after Jenkins 2.6)
  // See https://zanata.atlassian.net/browse/ZNTA-2237
  // getGitCommitId: get the commit Id of the tip of branch
  //   branch: The branch of interested
  //   repoUrl: Specify this if you are interested in other branch
  //            (Such as pipeline-library)
  String getCommitId(String branch, String repoUrl = mainRepoUrl) {
    if ( branch ==~ /PR-.*/ ) {
      // Pull request does not show real branch name
      String line = steps.sh([
        returnStdout: true,
        script: "git ls-remote " + repoUrl + " " + "refs/pull/" + env.CHANGE_ID + "/head"
        ])
      return line.split()[0]
    } else {
      // It can either be tag or branch
      String resultBuf = steps.sh([
        returnStdout: true,
        script: "git ls-remote --heads --tags " + repoUrl
        ])
      String[] lines = resultBuf.split('\n')
      for(int i=0; i<lines.length; i++){
        String[] tokens = lines[i].split()
        if (tokens[1] == 'refs/tags/' + branch) {
          // tag
          return tokens[0]
        } else if (tokens[1] == 'refs/heads/' + branch) {
          // branch
          return tokens[0]
        }
      }
    }
    return null
  }

  // Note: this method may be expensive, because it calls git ls-remote and iterates through all pull request heads
  // Get pull request id given commitId
  // Returns 0 when nothing commitId is not the tip of any pull request
  // If this needs to become public, please move it to PullRequests.groovy
  @PackageScope
  Integer getPullRequestNum(String commitId, String repoUrl = mainRepoUrl) {
    String resultBuf = steps.sh([
        returnStdout: true,
        script: "git ls-remote  " + repoUrl
    ])
    String[] lines = resultBuf.split('\n')
    for (int i = 0; i < lines.length; i++) {
      // split each line by whitespace
      String[] tokens = lines[i].split()
      if (tokens[0] == commitId) {
        // format to search is refs/pull/<pullId>/head
        String[] elem = tokens[1].split('/')
        if (elem[1] == 'pull') {
          return elem[2].toInteger()
        }
      }
    }
    return null
  }
}

