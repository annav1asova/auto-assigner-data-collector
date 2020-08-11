package jetbrains.buildServer.assignInfoCollector;

import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.vcs.SelectPrevBuildPolicy;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

class BuildInfo {
    private final long buildId;
    private final Date clientDate;
    private final List<UserInfo> committers;
    private final int changeCount;
    private final String comment;
    private final String triggeredBy;
    private final String description;
    private final boolean isDefaultBranch;
    private final String branchName;
    private final List<ChangeInfo> committersUsers;
    private final List<FailureReasonInfo> reasons;
    private List<TestInfo> tests;
    private final int testCount;


    BuildInfo(SBuild build, int changeLimit, int filesChangedLimit) {
        this.buildId = build.getBuildId();
        this.clientDate = build.getClientStartDate();
        this.committers = build.getCommitters(SelectPrevBuildPolicy.SINCE_LAST_BUILD).getUsers()
                .stream().map(UserInfo::new).collect(Collectors.toList());

        this.changeCount = build.getChanges(SelectPrevBuildPolicy.SINCE_LAST_BUILD, true).size();
        this.committersUsers = build.getChanges(SelectPrevBuildPolicy.SINCE_LAST_BUILD, true).stream()
                .limit(changeLimit)
                .map(modification -> new ChangeInfo(modification, filesChangedLimit))
                .collect(Collectors.toList());

        this.comment = build.getBuildComment() == null ? null : build.getBuildComment().getComment();

        this.triggeredBy = build.getTriggeredBy().getAsString();
        this.reasons = build.getFailureReasons().stream().map(FailureReasonInfo::new).collect(Collectors.toList());
        this.description = build.getBuildDescription();
        
        this.isDefaultBranch = build.getBranch() != null && build.getBranch().isDefaultBranch();
        this.branchName = build.getBranch() == null ? null : build.getBranch().getName();
        this.testCount = build.getFullStatistics().getAllTests().size();
    }

    public void setTests(List<TestInfo> tests) {
        this.tests = tests;
    }

    public void filterTests() {
        this.tests.removeIf(testInfo -> testInfo.getPreviousResponsible().isEmpty());
    }
}


