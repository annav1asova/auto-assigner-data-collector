package jetbrains.buildServer.assignInfoCollector;

import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.VcsUsernamePropertyKey;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class UserInfo {
    private String name;
    private String extendedName;
    private String email;
    private long id;
    private List<String> groups;
    private List<String> vcsProperties;
    private boolean isGuest;
    private final Date lastLogin;

    public UserInfo(SUser user) {
        this.name = user.getName();
        this.extendedName = user.getExtendedName();
        this.email = user.getEmail();
        this.id = user.getId();
        this.groups = user.getAllUserGroups().stream().map(UserGroup::getName).collect(Collectors.toList());
        this.vcsProperties = user.getVcsUsernameProperties().stream()
                .map(VcsUsernamePropertyKey::getVcsName)
                .collect(Collectors.toList());
        this.isGuest = user.isGuest();
        this.lastLogin = user.getLastLoginTimestamp();
    }
}
