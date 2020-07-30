package jetbrains.buildServer.assignInfoCollector;

import jetbrains.buildServer.BuildProblemData;

class FailureReasonInfo {
    private final String addData;
    private final String description;
    private final String type;
    private final String identity;

    public FailureReasonInfo (BuildProblemData reason) {
        this.addData = reason.getAdditionalData();
        this.description = reason.getDescription();
        this.type = reason.getType();
        this.identity = reason.getIdentity();
    }
}
