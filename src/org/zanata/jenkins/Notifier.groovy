package org.zanata.jenkins

/**
 * @author Sean Flanigan <a href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 * @author Alex Eng <a href="mailto:aeng@redhat.com">aeng@redhat.com</a>
 * @author Ding-Yi Chen <a href="mailto:dchen@redhat.com">dchen@redhat.com</a>
 */
class Notifier implements Serializable {
    private def build
    private def env
    private def steps
    private def repoUrl
    private def jobContext
    private def pipelineLibraryScmGit
    private def mainScmGit
    private String pipelineLibraryBranch
    // Whether to update commit status of pipeline-library
    private boolean notifyPipelineLibraryScm = false
    // Whether to update commit status of the main repo (the repo that call the pipelinie-library)
    // Commit Id of main repo
    private String currentCommitId = null
    private String durationStr = null
    private static final String libraryRepoUrl = "https://github.com/zanata/zanata-pipeline-library.git"

    Notifier(env, steps, build = null, pipelineLibraryScmGit, mainScmGit,
             jobContext = env.JOB_NAME) {
        this.build = build
        this.env = env
        this.steps = steps
        this.jobContext = jobContext
        this.pipelineLibraryScmGit = pipelineLibraryScmGit
        this.mainScmGit = mainScmGit
    }

    void started() {
        sendHipChat color: "GRAY", notify: true,
            message: "STARTED: Job " + jobLinkHtml()

        // Determine whether pipeline-library need to be informed
        steps.echo "pipelineLibrary commitId: " +
            pipelineLibraryScmGit.getCommitId()

        // Notify pipeline library scm if using library feature branch
        Integer pipelinePRNum = pipelineLibraryScmGit.getPullRequestNum()
        if (pipelinePRNum) {
            steps.echo "pipelineLibrary PR num: " + pipelinePRNum
            // pipeline-library is in pull request
            notifyPipelineLibraryScm = true
        }
        updateGitHubCommitStatus('PENDING', 'STARTED')
    }

    void startBuilding() {
        // Git References
        currentCommitId = mainScmGit.getCommitId()
        steps.echo "currentCommitId: " + currentCommitId
        sendHipChat color: "GRAY", notify: true,
            message: "BUILDING: Job " + jobLinkHtml()
        updateGitHubCommitStatus('PENDING', 'BUILDING')
    }

    /* testResults: publish the current test results to GitHub and Hipchat
     *
     * testType: e.g. UNIT, WILDFLY, JBOSSEAP
     *
     * currentBuildResult: the current build state
     *   null | 'SUCCESS' | 'PENDING' :
     *     GitHub state 'PENDING', test passed, but still building
     *
     *   'UNSTABLE' | 'FAILURE' :
     *     GitHub state 'FAILURE', test failed.
     *
     *   'ERROR':
     *     GitHub state 'ERROR', build error.
     *
     * message: Additional message to be shown.
     */
    void testResults(def testType, def currentBuildResult, def message = '') {
        // if tests have failed currentBuild.result will be 'UNSTABLE'
        String summary
        String githubState
        String hipChatColor
        if (currentBuildResult == null || currentBuildResult == 'SUCCESS' ||
            currentBuildResult == 'PENDING') {
            summary = "TEST PASSED ($testType)"
            githubState = 'PENDING'
            hipChatColor = 'GREEN'
        } else if (currentBuildResult == 'ERROR') {
            summary = "TEST $currentBuildResult ($testType)"
            githubState = 'ERROR'
            hipChatColor = 'YELLOW'
        } else {
            summary = "TEST FAILED ($testType)"
            githubState = 'FAILURE'
            hipChatColor = 'YELLOW'
        }
        sendHipChat color: hipChatColor, notify: true,
            message: "$summary: Job " + jobLinkHtml()
        updateGitHubCommitStatus(githubState,
            summary + ((message == '') ? '' : ": $message"))
    }

    private String durationToString() {
        if (build == null) {
            steps.echo '[WARN] build is null, duration is null'
            return null
        }
        if (build.duration > 0) {
            int millisecond = build.duration % 1000
            int second = build.duration.intdiv(1000) % 60
            int minute = (build.duration.intdiv(1000 * 60)) % 60
            int hour = (build.duration.intdiv(1000 * 60 * 60)) % 60
            return ((hour > 0) ? hour + ' hr ' : '') +
                ((minute > 0) ? minute + ' min ' : '') +
                ((second > 0) ? second + ' sec ' : '') +
                ((millisecond > 0) ? millisecond + ' ms' : '')
        }
        return "0s"
    }

    // We have already obtain the job result
    // If currentBuild.result is null, it will set it as 'SUCCESS'
    void finish(def message = '') {
        durationStr = durationToString()
        if (build == null) {
            steps.echo '[WARN] build is null, skipping the finish() method'
            return
        }
        // No news is good news, so set it as 'SUCCESS'
        // Otherwise, sendEmail mistakenly sends 'FAILURE'
        if (!build.result) {
            build.result = 'SUCCESS'
        }
        if (build.result == 'SUCCESS') {
            successful(message);
        } else if (build.result == 'UNSTABLE') {
            failed(message);
        } else {
            error(message);
        }
    }

    // Revised from https://issues.jenkins-ci.org/browse/JENKINS-38674
    // GitHub commit supports following states: 'pending', 'success', 'error' or 'failure'.
    // See: https://developer.github.com/v3/repos/statuses/
    private void updateGitHubCommitStatus(String state, def message,
                                          String overrideContext = null) {
        def ctx = overrideContext ?: jobContext
        String outputStr = state + ': ' + message.toString() +
            (durationStr ? " Duration: " + durationStr : '')

        if (notifyPipelineLibraryScm) {
            // Set the status for zanata-pipeline-library
            steps.step([
                $class            : 'GitHubCommitStatusSetter',
                // Use properties GithubProjectProperty
                reposSource       : [$class: "ManuallyEnteredRepositorySource", url: pipelineLibraryScmGit.
                    getRepoUrl()],
                commitShaSource   : [$class: "ManuallyEnteredShaSource", sha: pipelineLibraryScmGit.
                    getCommitId()],
                contextSource     : [$class: 'ManuallyEnteredCommitContextSource', context: env.JOB_NAME],
                errorHandlers     : [[$class: 'ShallowAnyErrorHandler']],
                statusResultSource: [
                    $class : 'ConditionalStatusResultSource',
                    results: [
                        [$class: 'AnyBuildResult', state: state, message: outputStr],
                    ]
                ]
            ])
        }

        // mainScmGit is null before checkout scm
        if (mainScmGit) {
            steps.step([
                $class            : 'GitHubCommitStatusSetter',
                // Use properties GithubProjectProperty
                reposSource       : [$class: "ManuallyEnteredRepositorySource", url: mainScmGit.
                    getRepoUrl()],
                commitShaSource   : [$class: "ManuallyEnteredShaSource", sha: mainScmGit.
                    getCommitId()],
                contextSource     : [$class: 'ManuallyEnteredCommitContextSource', context: ctx],
                errorHandlers     : [[$class: 'ShallowAnyErrorHandler']],
                statusResultSource: [
                    $class : 'ConditionalStatusResultSource',
                    results: [
                        [$class: 'AnyBuildResult', state: state, message: outputStr],
                    ]
                ]
            ])
        }
    }

    // Build success without failed tests
    void successful(def message = '') {
        sendHipChat color: "GRAY", notify: true,
            message: "SUCCESSFUL: Job " + jobLinkHtml()
        updateGitHubCommitStatus('SUCCESS', message)
        sendEmail(message)
    }

    // Used when tests failure, but compile completed
    void failed(def message = '') {
        sendHipChat color: "RED", notify: true,
            message: "FAILED: Job " + jobLinkHtml()
        updateGitHubCommitStatus('FAILURE', message)
        sendEmail(message)
    }

    // Used when build failure. e.g. build system/script failed, or compile error
    void error(def message = '') {
        // Need durationStr here as notify.finish might not be invoked
        durationStr = durationToString()
        sendHipChat color: "RED", notify: true,
            message: "ERROR: Job " + jobLinkHtml()
        updateGitHubCommitStatus('ERROR', message)
        sendEmail(message)
    }

    private void sendEmail(def message = '') {
        assert build != null: 'Notifier.build is null'
        def changes = ""

        // build.changeSets might be null in TestJenkinsfile
        if (build.changeSets != null) {
            for (Iterator changeSetIter = build.changeSets.iterator();
                 changeSetIter.hasNext();) {
                def set = changeSetIter.next()
                for (
                    Iterator entryIter = set.iterator(); entryIter.hasNext();) {
                    def entry = entryIter.next()
                    changes +=
                        "Commit ${entry.commitId} by ${entry.author.id} (${entry.author.fullName})\n"
                }
            }
        }
        steps.emailext([
            subject           : "${env.JOB_NAME} - Build #${build.id} - ${build.result ?: 'FAILURE'}: ${message}",
            body              : "url: ${build.absoluteUrl}\n" +
                "      title: ${env.CHANGE_TITLE ?: ''}\n" +
                "     author: ${env.CHANGE_AUTHOR}\n" +
                "        job: ${env.JOB_NAME}\n" +
                "   build id: ${build.id}\n" +
                "     branch: ${env.BRANCH_NAME}\n" +
                "     target: ${env.CHANGE_TARGET ?: ''}\n" +
                "   duration: " + durationStr ?: '' + " \n" +
                "     result: ${build.result ?: 'FAILURE'}\n" +
                "description: ${build.description ?: ''}\n" +
                "    message: ${message}\n" +
                "    changes: ${changes}\n",
            recipientProviders: [
                [$class: 'CulpritsRecipientProvider'],
                [$class: 'RequesterRecipientProvider'],
            ],
        ])
    }

    private String jobLinkHtml() {
        "<a href=\"${env.BUILD_URL}\">${env.JOB_NAME} #${env.BUILD_NUMBER}</a>"
    }

    private void sendHipChat(Map p) {
        try {
            steps.hipchatSend(
                color: p.color,
                failOnError: p.failOnError ?: false,
                message: p.message +
                    (durationStr ? " Duration: " + durationStr : ''),
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
