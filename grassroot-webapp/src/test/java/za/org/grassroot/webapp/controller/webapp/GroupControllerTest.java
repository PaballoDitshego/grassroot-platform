package za.org.grassroot.webapp.controller.webapp;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.GroupWrapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


/**
 * Created by paballo on 2016/01/19.
 */
public class GroupControllerTest extends WebAppAbstractUnitTest {

    private static final Logger logger = LoggerFactory.getLogger(MeetingControllerTest.class);

    @InjectMocks
    private GroupController groupController;

    @Before
    public void setUp() {
        setUp(groupController);
    }

    // todo: check for languages, check permissions, etc etc -- in effect, expand this to lots of testing, since
    // group view is the principal screen now for most group actions

    @Test
    public void viewGroupIndexWorks() throws Exception {
        Group dummyGroup = new Group("Dummy Group2", new User("234345345"));
        Group dummySubGroup = new Group("Dummy Group3", new User("234345345"));

        dummyGroup.addMember(sessionTestUser);
        Set<Group> subGroups = Collections.singleton(dummySubGroup);

        TaskDTO dummyTask = null;
        List<TaskDTO> dummyTasks = Collections.singletonList(dummyTask);

        when(userManagementServiceMock.load(sessionTestUser.getUid())).thenReturn(sessionTestUser);
        when(groupBrokerMock.load(dummyGroup.getUid())).thenReturn(dummyGroup);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, dummyGroup,
                                                        Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS)).thenReturn(true);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, dummyGroup,
                                                        Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS)).thenReturn(true);

        when(taskBrokerMock.fetchUpcomingIncompleteGroupTasks(sessionTestUser.getUid(), dummyGroup.getUid())).
                thenReturn(dummyTasks);

        when(groupQueryBrokerMock.subGroups(dummyGroup.getUid())).thenReturn(subGroups);
        when(groupBrokerMock.isDeactivationAvailable(sessionTestUser, dummyGroup, true)).thenReturn(true);
        when(groupQueryBrokerMock.getLastTimeGroupActiveOrModified(dummyGroup.getUid())).thenReturn(LocalDateTime.now());

        mockMvc.perform(get("/group/view").param("groupUid", dummyGroup.getUid())).
                andExpect(view().name("group/view")).
                andExpect(model().attribute("group", hasProperty("groupName", is("Dummy Group2")))).
                andExpect(model().attribute("groupTasks", hasItem(dummyTask))).
                andExpect(model().attribute("subGroups", hasItem(dummySubGroup))).
                andExpect(model().attribute("hasParent", is(false))).
                andExpect(model().attribute("openToken", is(false)));

        // todo: verify sequence of permission checking calls
        verify(groupBrokerMock, times(1)).load(dummyGroup.getUid());
        verify(groupQueryBrokerMock, times(1)).subGroups(dummyGroup.getUid());
        verify(taskBrokerMock, times(1)).fetchUpcomingIncompleteGroupTasks(sessionTestUser.getUid(), dummyGroup.getUid());
    }

    @Test
    public void modifyGroupWorks() throws Exception {
        Group dummyGroup = new Group("Dummy Group", new User("234345345"));
        dummyGroup.addMember(sessionTestUser);

        when(userManagementServiceMock.load(sessionTestUser.getUid())).thenReturn(sessionTestUser);
        when(groupBrokerMock.load(dummyGroup.getUid())).thenReturn(dummyGroup);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, dummyGroup, null)).thenReturn(true);

        mockMvc.perform(get("/group/change_multiple").param("groupUid", dummyGroup.getUid()))
                .andExpect(status().isOk())
                .andExpect(view().name("group/change_multiple"))
                .andExpect(model().attribute("groupModifier", instanceOf(GroupWrapper.class)));

        verify(groupBrokerMock, times(1)).load(dummyGroup.getUid());
        // verify(permissionBrokerMock, times(3)).isGroupPermissionAvailable(eq(sessionTestUser), eq(dummyGroup), Matchers.any(Permission.class));
        verify(permissionBrokerMock, times(1)).validateGroupPermission(sessionTestUser, dummyGroup, null);
        verify(permissionBrokerMock, times(1)).isGroupPermissionAvailable(sessionTestUser, dummyGroup, Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER);
        verify(permissionBrokerMock, times(1)).isGroupPermissionAvailable(sessionTestUser, dummyGroup, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER);
        verify(permissionBrokerMock, times(1)).isGroupPermissionAvailable(sessionTestUser, dummyGroup, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        verify(userManagementServiceMock, times(1)).load(sessionTestUser.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
    }

    @Test
    public void removeMemberModifyWorks() throws Exception {
        GroupWrapper groupCreator = new GroupWrapper();
        groupCreator.addMember(new MembershipInfo(sessionTestUser, BaseRoles.ROLE_GROUP_ORGANIZER));
        mockMvc.perform(post("/group/change_multiple").param("removeMember", String.valueOf(0)).param("removeMember", "")
                .sessionAttr("groupModifier", groupCreator))
                .andExpect(status().isOk()).andExpect(view().name("group/change_multiple"));
    }

    @Test
    public void modifyGroupDoWorks() throws Exception {

        Group testGroup = new Group("Dummy Group", new User("234345345"));
        GroupWrapper groupModifier = new GroupWrapper();
        groupModifier.populate(testGroup);
        groupModifier.setGroupName("Dummy Group");
        testGroup.addMember(sessionTestUser);

        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        mockMvc.perform(post("/group/change_multiple")
                .sessionAttr("groupModifier", groupModifier))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:view"))
                .andExpect(model().attribute("groupUid", is(testGroup.getUid())));

        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);

    }

    @Test
    public void newTokenWorks() throws Exception {
        Group testGroup = new Group("Dummy Group", new User("234345345"));
        testGroup.addMember(sessionTestUser);

        when(userManagementServiceMock.load(sessionTestUser.getUid())).thenReturn(sessionTestUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, testGroup, null)).thenReturn(true);

        mockMvc.perform(post("/group/token").param("groupUid", testGroup.getUid()))
                .andExpect(status().isOk())
                .andExpect(view().name("group/view"))
                .andExpect(model().attribute("group", hasProperty("groupName", is("Dummy Group"))));

        verify(groupBrokerMock, times(1)).openJoinToken(sessionTestUser.getUid(), testGroup.getUid(), null);
    }

    @Test
    public void closeTokenWorks() throws Exception {
        Group group = new Group("someGroupname", new User("234345345"));
        group.addMember(sessionTestUser);
        group.setGroupTokenCode("12345");
        group.setTokenExpiryDateTime(DateTimeUtil.convertToSystemTime(LocalDateTime.now().plusYears(1L), DateTimeUtil.getSAST()));

        when(groupBrokerMock.load(group.getUid())).thenReturn(group);
        when(userManagementServiceMock.load(sessionTestUser.getUid())).thenReturn(sessionTestUser);

        mockMvc.perform(post("/group/token").param("groupUid", group.getUid()))
                .andExpect(status().isOk()).andExpect(view().name("group/view"))
                .andExpect(model().attribute("group", hasProperty("uid", is(group.getUid()))));

        // note: since the model returns to group view, testing all verifications would be tedious and somewhat pointless
        verify(groupBrokerMock, times(1)).closeJoinToken(sessionTestUser.getUid(), group.getUid());

    }

    @Test
    public void listPossibleParentsWorks() throws Exception {

        Group testChildGroup = new Group("someGroup", new User("234345345"));
        Group testParentGroup = new Group("someParent", new User("234345345"));
        Group floatingGroup = new Group("some other group", sessionTestUser);
        testChildGroup.setParent(testParentGroup);
        Set<Group> possibleParent = Collections.singleton(testParentGroup);

        when(groupBrokerMock.load(testChildGroup.getUid())).thenReturn(testChildGroup);
        when(groupQueryBrokerMock.possibleParents(sessionTestUser.getUid(), testChildGroup.getUid())).thenReturn(Collections.emptySet());
        when(groupBrokerMock.load(floatingGroup.getUid())).thenReturn(floatingGroup);
        when(groupQueryBrokerMock.possibleParents(sessionTestUser.getUid(), floatingGroup.getUid())).thenReturn(possibleParent);

        mockMvc.perform(get("/group/parent").param("groupUid", testChildGroup.getUid()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:view")).andExpect(redirectedUrl("view?groupUid=" + testChildGroup.getUid()))
                .andExpect(model().attribute("groupUid", is(testChildGroup.getUid())));

        mockMvc.perform(get("/group/parent").param("groupUid", floatingGroup.getUid()))
                .andExpect(status().isOk())
                .andExpect(view().name("group/parent"))
                .andExpect(model().attribute("group", is(floatingGroup)))
                .andExpect(model().attribute("possibleParents", hasItem(testParentGroup)));

        verify(groupBrokerMock, times(2)).load(anyString());
        verify(groupQueryBrokerMock, times(1)).possibleParents(sessionTestUser.getUid(), testChildGroup.getUid());
        verify(groupQueryBrokerMock, times(1)).possibleParents(sessionTestUser.getUid(), floatingGroup.getUid());
        verifyNoMoreInteractions(groupBrokerMock);

    }

    @Test
    public void linkToParentWorks() throws Exception {
        Group testGroup = new Group("someGroupname", new User("234345345"));

        Group testParent = new Group("someParentGroup", new User("234345345"));
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(groupBrokerMock.load(testParent.getUid())).thenReturn(testParent);
        mockMvc.perform(post("/group/link").param("groupUid", testGroup.getUid()).param("parentUid", testParent.getUid()))
                .andExpect(status().is3xxRedirection()).andExpect(view().name("redirect:view"))
                .andExpect(redirectedUrl("view?groupUid=" + testGroup.getUid()))
                .andExpect(model().attribute("groupUid", testGroup.getUid()))
                .andExpect(flash().attributeExists(BaseController.MessageType.SUCCESS.getMessageKey()));
        verify(groupBrokerMock, times(1)).link(sessionTestUser.getUid(), testGroup.getUid(), testParent.getUid());
        verifyNoMoreInteractions(groupBrokerMock);

    }

    @Test
    public void selectConsolidateWorksWhenMergeCandidateHasEntries() throws Exception {

        Group testGroup = new Group("Dummy Group2", new User("234345345"));
        Group candidate = new Group("dummy testGroup", sessionTestUser);
        Set<Group> testCandidateGroups = Collections.singleton(candidate);

        when(groupQueryBrokerMock.mergeCandidates(sessionTestUser.getUid(), testGroup.getUid())).thenReturn(testCandidateGroups);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        mockMvc.perform(get("/group/consolidate/select").param("groupUid", testGroup.getUid()))
                .andExpect(status().isOk())
                .andExpect(view().name("group/consolidate_select"))
                .andExpect(model().attribute("group1", hasProperty("uid", is(testGroup.getUid()))))
                .andExpect(model().attribute("candidateGroups", hasItem(candidate)));

        verify(groupQueryBrokerMock, times(1)).mergeCandidates(sessionTestUser.getUid(), testGroup.getUid());
        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verifyNoMoreInteractions(groupBrokerMock);
        verifyNoMoreInteractions(groupQueryBrokerMock);
        verifyNoMoreInteractions(userManagementServiceMock);

    }

    @Test
    public void selectConsolidateWhenMergeCandidatesHasNoEntries() throws Exception {
        Set<Group> testCandidateGroups = new HashSet<>();
        Group testGroup = Group.makeEmpty();

        when(groupQueryBrokerMock.mergeCandidates(sessionTestUser.getUid(), testGroup.getUid())).thenReturn(testCandidateGroups);
        mockMvc.perform(get("/group/consolidate/select").param("groupUid", testGroup.getUid()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("view?groupUid=" + testGroup.getUid()))
                .andExpect(view().name("redirect:view"))
                .andExpect(model().attribute("groupUid", testGroup.getUid()))
                .andExpect(flash().attributeExists(BaseController.MessageType.ERROR.getMessageKey()));

        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verify(groupQueryBrokerMock, times(1)).mergeCandidates(sessionTestUser.getUid(), testGroup.getUid());
        verifyNoMoreInteractions(groupBrokerMock);
        verifyNoMoreInteractions(groupQueryBrokerMock);
        verifyNoMoreInteractions(userManagementServiceMock);
    }

    @Test
    public void consolidateGroupConfirmWorks() throws Exception {

        Group testGroupSmall = new Group("someGroupnameSmall", new User("234345345"));
        Group testGroupLarge = new Group("someGroupnameLarge", new User("234345345"));
        testGroupLarge.addMember(sessionTestUser);

        String[] orderedUids = {testGroupSmall.getUid(), testGroupLarge.getUid()};
        String[] orders = {"small_to_large", "1_into_2", "2_into_1"};
        when(groupBrokerMock.load(orderedUids[0])).thenReturn(testGroupSmall);
        when(groupBrokerMock.load(orderedUids[1])).thenReturn(testGroupLarge);

        for (int i = 0; i < orders.length; i++) {
            if (i < 2) {
                mockMvc.perform(post("/group/consolidate/confirm").param("groupUid1", testGroupSmall.getUid())
                        .param("groupUid2", testGroupLarge.getUid()).param("order", orders[i])
                        .param("leaveActive", String.valueOf(true)))
                        .andExpect(model().attribute("groupInto", hasProperty("uid", is(testGroupLarge.getUid()))))
                        .andExpect(model().attribute("groupFrom", hasProperty("uid", is(testGroupSmall.getUid()))))
                        .andExpect(model().attribute("numberFrom", is(testGroupSmall.getMemberships().size())))
                        .andExpect(model().attribute("leaveActive", is(true)));
            } else {
                mockMvc.perform(post("/group/consolidate/confirm").param("groupUid1", String.valueOf(testGroupSmall.getUid()))
                        .param("groupUid2", testGroupLarge.getUid()).param("order", orders[i]).param("leaveActive", "true")).
                        andExpect(model().attribute("groupInto", hasProperty("uid", is(testGroupSmall.getUid())))).
                        andExpect(model().attribute("groupFrom", hasProperty("uid", is(testGroupLarge.getUid())))).
                        andExpect(model().attribute("numberFrom", is(testGroupLarge.getMembers().size()))).
                        andExpect(model().attribute("leaveActive", is(true)));
            }
        }
        verify(groupBrokerMock, times(3)).load(orderedUids[0]);
        verify(groupBrokerMock, times(3)).load(orderedUids[1]);
    }

    @Test
    public void groupsConsolidateDoWorks() throws Exception {
        Group testGroupInto = new Group("someGroupname", new User("234345345"));
        testGroupInto.addMember(new User("100001"));

        Group testGroupFrom = new Group("someGroupname2", new User("234345345"));
        testGroupFrom.addMember(sessionTestUser);

        when(groupBrokerMock.load(testGroupFrom.getUid())).thenReturn(testGroupFrom);
        when(groupBrokerMock.merge(sessionTestUser.getUid(), testGroupInto.getUid(), testGroupFrom.getUid(), true, true, false, null))
                .thenReturn(testGroupInto);
        when(groupBrokerMock.load(testGroupInto.getUid())).thenReturn(testGroupInto);

        mockMvc.perform(post("/group/consolidate/do").param("groupInto", testGroupInto.getUid())
                .param("groupFrom", testGroupFrom.getUid()).param("leaveActive", "true").param("confirm_field", "merge"))
                .andExpect(model().attribute("groupUid", is(testGroupInto.getUid())))
                .andExpect(flash().attributeExists(BaseController.MessageType.SUCCESS.getMessageKey()))
                .andExpect(view().name("redirect:/group/view"));
        verify(groupBrokerMock, times(1)).load(testGroupFrom.getUid());
        verify(groupBrokerMock, times(1)).merge(sessionTestUser.getUid(), testGroupInto.getUid(), testGroupFrom.getUid(), true, true, false, null);
        verifyNoMoreInteractions(groupBrokerMock);


    }

    @Test
    public void deleteGroupWorksWithConfirmFieldValueValid() throws Exception {
        Group group = new Group("someGroupname", new User("234345345"));

        when(groupBrokerMock.load(group.getUid())).thenReturn(group);
        when(groupBrokerMock.isDeactivationAvailable(sessionTestUser, group, true)).thenReturn(true);
//        when(groupBrokerMock.deactivate(sessionTestUser.getUid(), testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(post("/group/inactive").param("groupUid", group.getUid()).param("confirm_field", "delete"))
                .andExpect(status().is3xxRedirection()).andExpect(view().name("redirect:/home"))
                .andExpect(redirectedUrl("/home")).andExpect(flash()
                .attributeExists(BaseController.MessageType.SUCCESS.getMessageKey()));
        verify(groupBrokerMock, times(1)).load(group.getUid());
        verify(groupBrokerMock, times(1)).deactivate(sessionTestUser.getUid(), group.getUid(), true);
        verifyNoMoreInteractions(groupBrokerMock);
    }


    // todo: add checks for security exceptions if user not a member of group
    @Test
    public void deleteGroupWorksWithConfirmFieldValueInvalid() throws Exception {

        Group group = new Group("someGroupname", sessionTestUser);
        group.addMember(sessionTestUser);

        when(groupBrokerMock.load(group.getUid())).thenReturn(group);
        when(groupBrokerMock.isDeactivationAvailable(sessionTestUser, group, true)).thenReturn(true);
        when(userManagementServiceMock.load(sessionTestUser.getUid())).thenReturn(sessionTestUser);

        mockMvc.perform(post("/group/inactive").param("groupUid", group.getUid()).param("confirm_field", "d"))
                .andExpect(status().isOk()).andExpect(view().name("group/view"))
                .andExpect(model().attributeExists(BaseController.MessageType.ERROR.getMessageKey()));

        verify(groupBrokerMock, times(2)).load(group.getUid());
        verify(groupBrokerMock, times(0)).deactivate(anyString(), anyString(), eq(true));

        // redirect to view causes all view method calls, no point repeating them here, but leaving verify written & commented

    }

    @Test
    public void unSubgroupWorks() throws Exception {
        Group testGroup = new Group("Dummy Group2", new User("234345345"));
        testGroup.addMember(sessionTestUser);

        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        mockMvc.perform(post("/group/unsubscribe").param("groupUid", testGroup.getUid())
                .param("confirm_field", "unsubscribe")).andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/home")).andExpect(redirectedUrl("/home"))
                .andExpect(flash().attributeExists(BaseController.MessageType.SUCCESS.getMessageKey()));

        verify(groupBrokerMock, times(1)).unsubscribeMember(sessionTestUser.getUid(), testGroup.getUid());
        verifyNoMoreInteractions(groupBrokerMock);

    }

    @Test
    public void groupHistoryThisMonthShouldWork() throws Exception {

        Group testGroup = new Group("someGroupname", new User("234345345"));
        testGroup.addMember(sessionTestUser);

        List<Event> dummyEvents = Arrays.asList(
                new Meeting("someMeeting", Instant.now(), sessionTestUser, testGroup, "someLoc"),
                new Vote("someMeeting", Instant.now(), sessionTestUser, testGroup));
        List<Todo> dummyLogbooks = Arrays.asList(new Todo(sessionTestUser, testGroup, "Do stuff", LocalDateTime.now().plusDays(2L).toInstant(ZoneOffset.ofHours(0))),
                                                  new Todo(sessionTestUser, testGroup, "Do more stuff", LocalDateTime.now().plusDays(5L).toInstant(ZoneOffset.ofHours(0))));
        List<GroupLog> dummyGroupLogs = Arrays.asList(new GroupLog(testGroup, sessionTestUser, GroupLogType.GROUP_MEMBER_ADDED, 0L, "guy joined"),
                                                      new GroupLog(testGroup, sessionTestUser, GroupLogType.GROUP_MEMBER_REMOVED, 0L, "other guy left"));
        List<LocalDate> dummyMonths = Arrays.asList(LocalDate.now(), LocalDate.now().minusMonths(1L));

        LocalDateTime start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(userManagementServiceMock.load(sessionTestUser.getUid())).thenReturn(sessionTestUser);
        when(eventBrokerMock.retrieveGroupEvents(testGroup, null, DateTimeUtil.convertToSystemTime(start, DateTimeUtil.getSAST()),
                DateTimeUtil.convertToSystemTime(end, DateTimeUtil.getSAST()))).thenReturn(dummyEvents);
        when(todoBrokerMock.fetchTodosForGroupCreatedDuring(testGroup.getUid(), start, end)).thenReturn(dummyLogbooks);
        when(groupQueryBrokerMock.getLogsForGroup(testGroup, start, end)).thenReturn(dummyGroupLogs);
        when(groupQueryBrokerMock.getMonthsGroupActive(testGroup.getUid())).thenReturn(dummyMonths);

        mockMvc.perform(get("/group/history").param("groupUid", testGroup.getUid())).
                andExpect(view().name("group/history")).
                andExpect(model().attribute("group", hasProperty("uid", is(testGroup.getUid())))).
                andExpect(model().attribute("eventsInPeriod", is(dummyEvents))).
                andExpect(model().attribute("todosInPeriod", is(dummyLogbooks))).
                andExpect(model().attribute("groupLogsInPeriod", is(dummyGroupLogs))).
                andExpect(model().attribute("monthsToView", is(dummyMonths)));

        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verify(groupQueryBrokerMock, times(1)).getMonthsGroupActive(testGroup.getUid());
        verify(groupQueryBrokerMock, times(1)).getLogsForGroup(testGroup, start, end);
        verify(permissionBrokerMock, times(1)).validateGroupPermission(sessionTestUser, testGroup, null);
        verifyNoMoreInteractions(groupBrokerMock);
        verify(userManagementServiceMock, times(1)).load(sessionTestUser.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventBrokerMock, times(1)).retrieveGroupEvents(testGroup, null, DateTimeUtil.convertToSystemTime(start, DateTimeUtil.getSAST()),
                DateTimeUtil.convertToSystemTime(end, DateTimeUtil.getSAST()));
        verifyNoMoreInteractions(eventBrokerMock);
        verify(todoBrokerMock, times(1)).fetchTodosForGroupCreatedDuring(testGroup.getUid(), start, end);
        verifyNoMoreInteractions(todoBrokerMock);

    }

    @Test
    public void groupHistoryLastMonthShouldWork() throws Exception {

        Group testGroup = new Group("someGroupname", new User("234345345"));
        testGroup.addMember(sessionTestUser);

        List<Event> dummyEvents = Collections.singletonList(new Meeting("someMeeting", Instant.now(), sessionTestUser, testGroup, "someLoc"));
        List<Todo> dummyTodos = Collections.singletonList(new Todo(sessionTestUser, testGroup, "do stuff", LocalDateTime.now().toInstant(ZoneOffset.ofHours(0))));
        List<GroupLog> dummyGroupLogs = Collections.singletonList(new GroupLog(testGroup, sessionTestUser, GroupLogType.GROUP_MEMBER_ADDED, 0L));
        List<LocalDate> dummyMonths = Arrays.asList(LocalDate.now(), LocalDate.now().minusMonths(1L));

        LocalDate lastMonth = LocalDate.now().minusMonths(1L);
        String monthToView = lastMonth.format(DateTimeFormatter.ofPattern("M-yyyy"));

        LocalDateTime start = LocalDate.parse("01-" + monthToView, DateTimeFormatter.ofPattern("dd-M-yyyy")).atStartOfDay();
        LocalDateTime end = start.plusMonths(1L);
        Instant startInst = DateTimeUtil.convertToSystemTime(start, DateTimeUtil.getSAST());
        Instant endInst = DateTimeUtil.convertToSystemTime(end, DateTimeUtil.getSAST());

        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(userManagementServiceMock.load(sessionTestUser.getUid())).thenReturn(sessionTestUser);
        when(eventBrokerMock.retrieveGroupEvents(testGroup, null, startInst, endInst)).thenReturn(dummyEvents);
        when(todoBrokerMock.fetchTodosForGroupCreatedDuring(testGroup.getUid(), start, end)).thenReturn(dummyTodos);
        when(groupQueryBrokerMock.getLogsForGroup(testGroup, start, end)).thenReturn(dummyGroupLogs);
        when(groupQueryBrokerMock.getMonthsGroupActive(testGroup.getUid())).thenReturn(dummyMonths);

        mockMvc.perform(get("/group/history").param("groupUid", testGroup.getUid()).param("monthToView", monthToView)).
                andExpect(view().name("group/history")).
                andExpect(model().attribute("group", hasProperty("uid", is(testGroup.getUid())))).
                andExpect(model().attribute("eventsInPeriod", is(dummyEvents))).
                andExpect(model().attribute("todosInPeriod", is(dummyTodos))).
                andExpect(model().attribute("groupLogsInPeriod", is(dummyGroupLogs))).
                andExpect(model().attribute("monthsToView", is(dummyMonths)));

        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verify(permissionBrokerMock, times(1)).validateGroupPermission(sessionTestUser, testGroup, null);
        verify(groupQueryBrokerMock, times(1)).getMonthsGroupActive(testGroup.getUid());
        verify(groupQueryBrokerMock, times(1)).getLogsForGroup(testGroup, start, end);
        verifyNoMoreInteractions(groupBrokerMock);
        verify(userManagementServiceMock, times(1)).load(sessionTestUser.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventBrokerMock, times(1)).retrieveGroupEvents(testGroup, null, startInst, endInst);
        verifyNoMoreInteractions(eventBrokerMock);
        verify(todoBrokerMock, times(1)).fetchTodosForGroupCreatedDuring(testGroup.getUid(), start, end);
        verifyNoMoreInteractions(todoBrokerMock);

    }

    @Test
    public void addBulkMembersDoShouldWork() throws Exception{
        String testNumbers = "0616780986,0833403013,01273,0799814669";
        List<String> numbers_to_be_added = new ArrayList<>();
        numbers_to_be_added.add("27616780986");
        numbers_to_be_added.add("27833403013");
        numbers_to_be_added.add("27799814669");

        Set<MembershipInfo> testMembers = Sets.newHashSet(
                new MembershipInfo("27616780986", BaseRoles.ROLE_ORDINARY_MEMBER, null),
                new MembershipInfo("27833403013", BaseRoles.ROLE_ORDINARY_MEMBER, null),
                new MembershipInfo("27799814669", BaseRoles.ROLE_ORDINARY_MEMBER, null));

        Group testGroup = new Group("someGroupName", new User("27616780989"));
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        mockMvc.perform(post("/group/add_bulk").param("groupUid", String.valueOf(testGroup.getUid())).param("list",testNumbers))
                .andExpect(status().isOk()).andExpect(view().name("group/add_bulk_error"));
        verify(groupBrokerMock,times(1)).load(testGroup.getUid());
        verify(groupBrokerMock, times(1)).addMembers(sessionTestUser.getUid(), testGroup.getUid(), testMembers, false);
      //  verifyNoMoreInteractions(userManagementServiceMock);

    }

}
