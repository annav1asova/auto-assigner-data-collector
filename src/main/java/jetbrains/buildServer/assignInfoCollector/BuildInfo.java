package jetbrains.buildServer.assignInfoCollector;

import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.vcs.SelectPrevBuildPolicy;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

class BuildInfo {
    private final long buildId;
    private final Date clientDate;
    private final List<UserInfo> committers;
    private final String comment;
    private final String triggeredBy;
    private final String description;
    private final boolean isDefaultBranch;
    private final String branchName;
    private final List<ChangeInfo> committersUsers;
    private final List<FailureReasonInfo> reasons;
    private List<TestInfo> tests;
    private HashMap<Long, String> previousResponsible;


    BuildInfo(SBuild build) {
        this.buildId = build.getBuildId();
        this.clientDate = build.getClientStartDate();
        this.committers = build.getCommitters(SelectPrevBuildPolicy.SINCE_LAST_BUILD).getUsers()
                .stream().map(UserInfo::new).collect(Collectors.toList());

        this.committersUsers = build.getChanges(SelectPrevBuildPolicy.SINCE_LAST_BUILD, true).stream()
                .map(ChangeInfo::new)
                .collect(Collectors.toList());

        this.comment = build.getBuildComment() == null ? null : build.getBuildComment().getComment();

        this.triggeredBy = build.getTriggeredBy().getAsString();
        this.reasons = build.getFailureReasons().stream().map(FailureReasonInfo::new).collect(Collectors.toList());
        this.description = build.getBuildDescription();
        
        this.isDefaultBranch = build.getBranch() != null && build.getBranch().isDefaultBranch();
        this.branchName = build.getBranch() == null ? null : build.getBranch().getName();
    }

    public void setTests(List<TestInfo> tests) {
        this.tests = tests;
    }
}


