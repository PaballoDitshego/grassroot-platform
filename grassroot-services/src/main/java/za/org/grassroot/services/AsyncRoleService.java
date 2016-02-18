package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Created by luke on 2016/02/17.
 */
public interface AsyncRoleService {

    Future<Role> fetchOrCreateGroupRole(String roleName, Group group);

    void addRoleToGroupAndUser(String roleName, Group group, User addingToUser, User callingUser);

    void removeUsersRoleInGroup(User user, Group group);

    void resetGroupToDefaultRolesPermissions(Long groupId);

    /*
    Methods to assign permissions to roles
     */

    void assignPermissionsToGroupRoles(Group group, GroupPermissionTemplate template);

    Role assignPermissionsToRole(Role role, List<Permission> permissions);

    Role addPermissionToRole(Role role, Permission permission);

    Role removePermissionFromRole(Role role, Permission permission);


}
