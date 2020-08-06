package jetbrains.buildServer.assignInfoCollector;

import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.VcsChangeInfo;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

class ChangeInfo {
    private final List<String> changesNames;
    private final String description;
    private final Date vcsDate;
    private final String userName;

    public ChangeInfo (SVcsModification modification) {
        this.changesNames = modification.getChanges().stream().map(VcsChangeInfo::getFileName).collect(Collectors.toList());
        this.description = modification.getDescription();
        this.vcsDate = modification.getVcsDate();
        this.userName = modification.getUserName();

//        modification.getChanges().stream().forEach(change -> {
//                change.
//        });

//        this.vcsRoot = modification.getVcsRoot();
    }
}
