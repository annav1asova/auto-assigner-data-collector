package com.teamcity.autoAssignerDataCollector;

import jetbrains.buildServer.serverSide.STestRun;

class TestInfo {
    private String className;
    private String testMethod;
    private String testClass;
    private String testSuite;
    private int duration;

    TestInfo(STestRun testRun) {
        this.className = testRun.getTest().getClass().getCanonicalName();
        this.testMethod = testRun.getTest().getName().getTestMethodName();
        this.testClass = testRun.getTest().getName().getClassName();
        this.testSuite = testRun.getTest().getName().getSuite();
        this.duration = testRun.getDuration();
    }
}