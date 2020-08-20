package jetbrains.buildServer.assignInfoCollector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.issueTracker.errors.NotFoundException;
import jetbrains.buildServer.serverSide.BuildStatistics;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.audit.*;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.serverSide.impl.audit.filters.TestId;
import jetbrains.buildServer.serverSide.stat.TestOutputCollector;
import jetbrains.buildServer.users.User;
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

import static jetbrains.buildServer.serverSide.BuildStatisticsOptions.ALL_TESTS_NO_DETAILS;

public class BuildInfoController extends BaseController {
    private final SBuildServer server;
    private final ProjectManager projectManager;
    private final AuditLogProvider auditLogProvider;
    private final SecurityContext mySecurityContext;

    private final Gson myGson = new GsonBuilder().setPrettyPrinting().create();

    public BuildInfoController(@NotNull final SBuildServer server,
                               @NotNull final WebControllerManager manager,
                               @NotNull final ProjectManager projectManager,
                               @NotNull final AuditLogProvider auditLogProvider,
                               @NotNull final SecurityContext securityContext) {
        super(server);
        this.server = server;
        this.projectManager = projectManager;
        this.auditLogProvider = auditLogProvider;
        mySecurityContext = securityContext;
        manager.registerController("/assignInfoCollector.html", this);
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

        Map<Long, List<Long>> buildToTestsMap = new HashMap<>();

        Arrays.stream(request.getParameter("ids").split(","))
                .map(pair -> pair.split("_"))
                .forEach(ids -> {
                    long buildId = Long.parseLong(ids[0]);
                    long testId = Long.parseLong(ids[1]);
                    buildToTestsMap.putIfAbsent(buildId, new ArrayList<>());
                    buildToTestsMap.get(buildId).add(testId);
                });

        List<BuildInfo> builds = new ArrayList<>();
        List<TestInfo> testRunsWithResponsibilities = new ArrayList<>();
        Set<String> projectIds = new HashSet<>();

        server.getHistory().findEntries(buildToTestsMap.keySet()).stream()
                .filter(build -> !build.isAgentLessBuild()) // filter composite builds
                .forEach(finishedBuild -> {
                    TestOutputCollector testOutputCollector = new TestOutputCollector(finishedBuild);

                    BuildInfo buildInfo = new BuildInfo(finishedBuild);
                    List<TestInfo> tests = new ArrayList<>();
                    BuildStatistics buildStat = finishedBuild.getBuildStatistics(ALL_TESTS_NO_DETAILS);

                    buildToTestsMap.get(finishedBuild.getBuildId()).stream()
                            .map(buildStat::findTestByTestNameId)
                            .filter(Objects::nonNull)
                            .filter(testRun -> !testRun.getTest().getAllResponsibilities().isEmpty())
                            .limit(Limits.TEST_LIMIT)
                            .forEach(testRun -> {
                                TestInfo testInfo = new TestInfo(testRun, testOutputCollector);
                                testRunsWithResponsibilities.add(testInfo);
                                tests.add(testInfo);
                            });

                    buildInfo.setTests(tests);
                    builds.add(buildInfo);
                    projectIds.add(finishedBuild.getProjectExternalId());
                });

        Map<Long, List<String>> auditResult = findInAudit(testRunsWithResponsibilities.stream()
                        .map(TestInfo::getTestNameId)
                        .collect(Collectors.toSet()),
                projectIds.stream()
                        .map(this::getProjectByExternalId)
                        .collect(Collectors.toSet()));

        testRunsWithResponsibilities.forEach(testRun -> {
            testRun.setPreviousResponsible(auditResult.get(testRun.getTestNameId()));
        });

        sendResponse(response, builds);
        return null;
    }

    @NotNull
    public Map<Long, List<String>> findInAudit(@NotNull final Set<Long> testNameIds, @NotNull Set<SProject> projects) {
        AuditLogBuilder builder = auditLogProvider.getBuilder();
        builder.setActionTypes(ActionType.TEST_MARK_AS_FIXED,
                ActionType.TEST_INVESTIGATION_ASSIGN,
                ActionType.TEST_INVESTIGATION_ASSIGN_STICKY);

        Set<String> objectIds = new HashSet<>();
        Set<String> projectIds = collectProjectsHierarchyIds(projects);
        for (Long testNameId : testNameIds) {
            for (String projectId : projectIds) {
                objectIds.add(TestId.createOn(testNameId, projectId).asString());
            }
        }

        builder.setObjectIds(objectIds);
        List<AuditLogAction> lastActions = builder.getLogActions(-1);
        Map<Long, List<String>> result = new HashMap<>();
        for (AuditLogAction action : lastActions) {
            for (ObjectWrapper obj : action.getObjects()) {
                Object user = obj.getObject();
                if (!(user instanceof User)) {
                    continue;
                }

                TestId testId = TestId.fromString(action.getObjectId());
                if (testId != null) {
                    result.putIfAbsent(testId.getTestNameId(), new ArrayList<>());
                    result.get(testId.getTestNameId()).add(((User) user).getExtendedName());
                }
            }
        }
        return result;
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

    private void sendResponse(@NotNull HttpServletResponse servletResponse,
                              @NotNull List<BuildInfo> responsibilities) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(servletResponse.getOutputStream(), StandardCharsets.UTF_8)) {
            servletResponse.setContentType("application/json");
            writer.write(myGson.toJson(responsibilities));
        }
    }

    @NotNull
    private Set<String> collectProjectHierarchyIds(@NotNull SProject project) {
        Set<String> result = new HashSet<>();
        do {
            result.add(project.getProjectId());
            project = project.getParentProject();
        } while (project != null);
        return result;
    }

    @NotNull
    private Set<String> collectProjectsHierarchyIds(@NotNull Set<SProject> projects) {
        Set<String> result = new HashSet<>();
        for (SProject project : projects) {
            result.addAll(collectProjectHierarchyIds(project));
        }
        return result;
    }
}
