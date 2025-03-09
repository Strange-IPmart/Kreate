package me.knighthat.utils

import it.fast4x.rimusic.BuildConfig

object Repository {

    const val GITHUB = "https://github.com"
    const val GITHUB_API = "https://api.github.com"

    const val OWNER = "knighthat"
    const val REPO = "$OWNER/${BuildConfig.APP_NAME}"
    const val REPO_URL = "$GITHUB/$REPO"

    const val LATEST_TAG_URL = "$REPO/releases/latest"
}