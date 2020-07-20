package com.teamcity.autoAssignerDataCollector;

import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.vcs.SelectPrevBuildPolicy;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

class BuildInfo {
    private final long buildId;
    private final Date clientDate;
    //    private final String owner;
    private final List<String> committers;
    private HashMap<Long, String> previousResponsible;
    private List<TestInfo> tests;

    BuildInfo(SBuild build) {
        this.buildId = build.getBuildId();
        this.clientDate = build.getClientStartDate();
//        this.owner = Objects.requireNonNullElse(Objects.requireNonNull(build.getOwner()).getExtendedName(), "DEFAULT_OWNER");
        this.committers = build.getCommitters(SelectPrevBuildPolicy.SINCE_LAST_BUILD).getUsers()
                .stream().map(User::getExtendedName).collect(Collectors.toList());
        this.tests = build.getFullStatistics().getAllTests().stream()
                .map(TestInfo::new)
                .collect(Collectors.toList());
    }

    void setPreviousResponsible(HashMap<Long, String> previousResponsible) {
        this.previousResponsible = previousResponsible;
    }
}
