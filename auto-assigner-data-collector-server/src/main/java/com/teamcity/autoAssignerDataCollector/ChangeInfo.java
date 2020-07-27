package com.teamcity.autoAssignerDataCollector;

import jetbrains.buildServer.vcs.Modification;

import java.util.Date;

class ChangeInfo {
    private final String description;
    private final Date vcsDate;
    private final String userName;

    public ChangeInfo (Modification modification) {
        this.description = modification.getDescription();
        this.vcsDate = modification.getVcsDate();
        this.userName = modification.getUserName();
    }
}
