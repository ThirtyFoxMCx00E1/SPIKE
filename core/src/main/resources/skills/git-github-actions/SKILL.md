---
name: git-github-actions
description: Git workflow and GitHub Actions CI/CD
---
Commit small with clear messages; never force-push shared branches without asking. Pull with rebase before pushing. In workflows: pin action major versions, cache Gradle (`gradle/actions/setup-gradle`), upload build outputs with `actions/upload-artifact`, and publish releases on tags with `softprops/action-gh-release`. Give the job only the permissions it needs.
