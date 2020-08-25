package jetbrains.buildServer.assignInfoCollector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.issueTracker.errors.NotFoundException;
import jetbrains.buildServer.responsibility.InvestigationTestRunsHolder;
import jetbrains.buildServer.responsibility.TestNameResponsibilityEntry;
import jetbrains.buildServer.responsibility.TestNameResponsibilityFacade;
import jetbrains.buildServer.responsibility.impl.InvestigationTestRunsHolderImpl;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.stat.FirstFailedInFixedInCalculator;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class BuildIdsController extends BaseController {
    private final ProjectManager projectManager;
    private final InvestigationTestRunsHolder testRunsHolderCache;
    private final TestNameResponsibilityFacade responsibilityFacade;
    private final FirstFailedInFixedInCalculator statisticsProvider;
    private final SecurityContext mySecurityContext;

    private final Gson myGson = new GsonBuilder().setPrettyPrinting().create();

    public BuildIdsController(@NotNull final SBuildServer server,
                              @NotNull final WebControllerManager manager,
                              @NotNull final ProjectManager projectManager,
                              @NotNull final SecurityContext securityContext,
                              @NotNull final TestNameResponsibilityFacade responsibilityFacade,
                              @NotNull final InvestigationTestRunsHolderImpl testRunsHolderCache,
                              @NotNull final FirstFailedInFixedInCalculator statisticsProvider) {
        super(server);
        this.projectManager = projectManager;
        this.responsibilityFacade = responsibilityFacade;
        this.testRunsHolderCache = testRunsHolderCache;
        this.statisticsProvider = statisticsProvider;
        mySecurityContext = securityContext;
        manager.registerController("/buildTestIdsCollector.html", this);
    }

    @Nullable
    @Override
    protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception {
        if (!isGet(request)) {
            throw new HttpRequestMethodNotSupportedException(request.getMethod());
        }

        @Nullable SProject project = getProjectByExternalId(request.getParameter("projectExternalId"));
        if (project == null) {
            throw new NotFoundException("Project with specified externalProjectId not found");
        } else if (!mySecurityContext.getAuthorityHolder().getPermissionsGrantedForProject(project.getProjectId()).contains(Permission.VIEW_PROJECT)) {
            throw new IllegalAccessException("User doesn't have enough permissions. " + Permission.VIEW_PROJECT.getName() + " permission required.");
        }

        Set<Long> testIds = new HashSet<>();
        List<TestNameResponsibilityEntry> responsibilities = responsibilityFacade.getUserTestNameResponsibilities(null,
                project.getProjectId());
        for (TestNameResponsibilityEntry responsibility : responsibilities) {
            testIds.add(responsibility.getTestNameId());
        }

        List<STestRun> lastTestRuns = testRunsHolderCache.getLastTestRunsInBulk(testIds, project.getProjectId(), false);

        Set<BuildTestId> buildsTests = lastTestRuns.stream()
                .filter(testRun -> testRun.getStatus().isFailed())
                .map(testRun -> {
                    final SBuild firstFailedBuild = findFirstFailedInBuild(testRun);
                    if (firstFailedBuild != null) {
                        return new BuildTestId(testRun, firstFailedBuild);
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        sendResponse(response, buildsTests);
        return null;
    }

    @Nullable
    private SProject getProjectByExternalId(@Nullable final String projectExternalId) throws AccessDeniedException {
        if (projectExternalId == null) {
            return null;
        }

        @Nullable SProject project = projectManager.findProjectByExternalId(projectExternalId); // throws AccessDeniedException if no rights
        if (project == null) {
            throw new NotFoundException("Project with specified externalProjectId not found");
        }

        return project;
    }

    @Nullable
    private SBuild findFirstFailedInBuild(@NotNull STestRun testRun) {
        final FirstFailedInFixedInCalculator.FFIData ffiData = statisticsProvider.calculateFFIData(testRun);
        @Nullable SBuild firstFailedBuild = ffiData.getFirstFailedIn();
        return firstFailedBuild;
    }

    private void sendResponse(@NotNull HttpServletResponse servletResponse,
                              @NotNull Set<BuildTestId> buildTestIds) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(servletResponse.getOutputStream(), StandardCharsets.UTF_8)) {
            servletResponse.setContentType("application/json");
            writer.write(myGson.toJson(buildTestIds));
        }
    }

    private static class BuildTestId {
        final long buildId;
        final long testId;

        BuildTestId(STestRun testRun, SBuild build) {
            buildId = build.getBuildId();
            testId = testRun.getTest().getTestNameId();
        }

        BuildTestId(long buildId, long testId) {
            this.buildId = buildId;
            this.testId = testId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BuildTestId that = (BuildTestId) o;
            return buildId == that.buildId &&
                    testId == that.testId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(buildId, testId);
        }
    }
}
