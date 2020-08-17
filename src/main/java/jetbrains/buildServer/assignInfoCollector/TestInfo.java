package jetbrains.buildServer.assignInfoCollector;

import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.stat.CombinedTestOutputProcessor;
import jetbrains.buildServer.serverSide.stat.LimitedStacktraceProcessor;
import jetbrains.buildServer.serverSide.stat.TestOutputCollector;

import java.util.List;

class TestInfo {
    private final int testRunId;
    private final long testNameId;
    private final String stacktrace;
    private String className;
    private String testMethod;
    private String testClass;
    private String testSuite;
    private int duration;
    private int orderId;
    private boolean isFixed;
    private List<String> previousResponsible;

    TestInfo(STestRun testRun, TestOutputCollector testOutputCollector) {
        this.testRunId = testRun.getTestRunId();
        this.testNameId = testRun.getTest().getTestNameId();
        this.className = testRun.getTest().getClass().getCanonicalName();
        this.testMethod = testRun.getTest().getName().getTestMethodName();
        this.testClass = testRun.getTest().getName().getClassName();
        this.testSuite = testRun.getTest().getName().getSuite();
        this.duration = testRun.getDuration();
        this.orderId = testRun.getOrderId();
        this.isFixed = testRun.isFixed();
        this.stacktrace = loadStackTrace(testOutputCollector);
    }

    private String loadStackTrace(TestOutputCollector testOutputCollector) {
        LimitedStacktraceProcessor outputProcessor = new LimitedStacktraceProcessor();
        testOutputCollector.processOutput(testRunId, outputProcessor);

        StringBuilder stackTrace = outputProcessor.getStacktrace();
        if (outputProcessor.isMaxLenExceeded()) {
            stackTrace.append("\r\n").append(CombinedTestOutputProcessor.TEXT_MAX_LENGTH_EXCEEDED_MSG).append("\r\n");
        }
        return stackTrace.toString();
    }

    public void setPreviousResponsible(List<String> previousResponsible) {
        this.previousResponsible = previousResponsible;
    }

    public List<String> getPreviousResponsible() {
        return this.previousResponsible;
    }

    public long getTestNameId() {
        return testNameId;
    }
}