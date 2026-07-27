package app.anikuta.domain.release.service

import app.anikuta.domain.release.interactor.GetApplicationRelease
import app.anikuta.domain.release.model.Release

interface ReleaseService {

    suspend fun latest(arguments: GetApplicationRelease.Arguments): Release?
}
