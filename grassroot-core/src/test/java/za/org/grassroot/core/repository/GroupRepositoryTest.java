package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.GroupLogType;

import javax.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author Lesetse Kimwaga
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class GroupRepositoryTest {

    private static final Logger log = LoggerFactory.getLogger(GroupRepositoryTest.class);

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupLogRepository groupLogRepository;

    @Test
    public void shouldSaveAndRetrieveGroupData() throws Exception {

        assertThat(groupRepository.count(), is(0L));

        User userToDoTests = new User("56789");
        userRepository.save(userToDoTests);

        Group groupToCreate = new Group("TestGroup", userToDoTests);
        assertNull(groupToCreate.getId());
        assertNotNull(groupToCreate.getUid());
        groupRepository.save(groupToCreate);

        assertThat(groupRepository.count(), is(1l));
        Group groupFromDb = groupRepository.findAll().iterator().next();
        assertNotNull(groupFromDb.getId());
        assertNotNull(groupFromDb.getCreatedDateTime());
        assertThat(groupFromDb.getGroupName(), is("TestGroup"));
        assertThat(groupFromDb.getCreatedByUser().getPhoneNumber(), is("56789"));
    }

    @Test
    public void shouldSaveWithAddedMember() throws Exception {
        assertThat(groupRepository.count(), is(0L));
        User userForTest = userRepository.save(new User("0814441111"));
        Group groupToCreate = groupRepository.save(new Group("testGroup", userForTest));
        groupToCreate.addMember(userForTest);
        groupToCreate = groupRepository.save(groupToCreate);
        assertThat(groupRepository.count(), is(1L));
        assertNotNull(groupToCreate);
        assertThat(groupToCreate.getMemberships().size(), is(1));
        assertThat(groupToCreate.getMembers().contains(userForTest), is(true));
    }

    @Test
    public void shouldSaveAndFindByCreatedUser() throws Exception {

        User userToDoTests = new User("100001");
        userRepository.save(userToDoTests);

        Group groupToCreate = new Group("TestGroup", userToDoTests);
        assertNull(groupToCreate.getId());
        assertNotNull(groupToCreate.getUid());
        groupRepository.save(groupToCreate);
        // Group groupFromDb = groupRepository.findByCreatedByUser(userToDoTests).iterator().next();
        // log.info(groupFromDb.toString());
        // assertNotNull(groupFromDb);
        // assertEquals(userToDoTests.getId(), groupFromDb.getCreatedByUser().getId());
    }

    @Test
    public void shouldFindLastCreatedGroupForUser() throws Exception {

        User userToDoTests = new User("100002");
        userRepository.save(userToDoTests);

        Group group1 = new Group("TestGroup1", userToDoTests);
        groupRepository.save(group1);

        Group group2 = new Group("TestGroup2", userToDoTests);
        Group savedGroup2 = groupRepository.save(group2);
        Group groupFromDb = groupRepository.findFirstByCreatedByUserAndActiveTrueOrderByIdDesc(userToDoTests);
        log.debug("latest group........." + groupFromDb.toString());
        assertEquals(savedGroup2.getId(), groupFromDb.getId());
    }

    @Test
    public void shouldSaveParentRelationship() {
        User user = userRepository.save(new User("1111111111"));
        Group ga = groupRepository.save(new Group("ga", user));
        Group ga1 = groupRepository.save(new Group("ga1", user, ga));
        assertEquals(ga.getId(), ga1.getParent().getId());

    }

    @Test
    public void shouldReturnLevel1ChildrenByRepositoryQuery() {
        User user = userRepository.save(new User("2222222222"));
        Group gb = groupRepository.save(new Group("gb", user));
        Group gb1 = groupRepository.save(new Group("gb1", user, gb));
        Group gb2 = groupRepository.save(new Group("gb2", user, gb));
        List<Group> children = groupRepository.findByParentAndActiveTrue(gb);
        assertEquals(2,children.size());
    }

    @Test
    public void shouldReturnByEntityGet() {
        User user = userRepository.save(new User("3333333330"));
        Group gc = groupRepository.save(new Group("gc", user));
        Group gc1 = groupRepository.save(new Group("gc1", user, gc));
        Group gc2 = groupRepository.save(new Group("gc2", user, gc));
        Group gc1a = groupRepository.save(new Group("gc1a", user, gc1));
        Group gc1b = groupRepository.save(new Group("gc1b", user, gc1));

        Group gcUpdated = groupRepository.findOne(gc.getId());
        Group gc1Updated = groupRepository.findOne(gc1.getId());
        Group gc2Updated = groupRepository.findOne(gc2.getId());

        assertNotNull(gcUpdated);
        assertNotNull(gcUpdated.getDirectChildren());
        assertEquals(2, gcUpdated.getDirectChildren().size());
        assertTrue(gcUpdated.getDirectChildren().contains(gc1));
        assertTrue(gcUpdated.getDirectChildren().contains(gc2));
        assertFalse(gcUpdated.getDirectChildren().contains(gc1a));
        assertFalse(gcUpdated.getDirectChildren().contains(gc1b));
        assertEquals(2, gc1Updated.getDirectChildren().size());
        assertEquals(0, gc2Updated.getDirectChildren().size());
    }

    @Test
    public void shouldGetMaxToken() {
        log.info(String.valueOf(groupRepository.getMaxTokenValue()));
    }

    @Test
    public void shouldCreateAndUseToken() {
        User user1 = userRepository.save(new User("3331118888"));
        Group group = groupRepository.save(new Group("token", user1));
        Integer realToken = groupRepository.getMaxTokenValue();
        Integer fakeToken = realToken - 10;
        group.setGroupTokenCode(String.valueOf(realToken));
        groupRepository.save(group);
        Group groupFromDb1 = groupRepository.findByGroupTokenCode(String.valueOf(realToken));
        Group groupFromDb2 = groupRepository.findByGroupTokenCode(String.valueOf(fakeToken));
        assertNotNull(groupFromDb1);
        assertNull(groupFromDb2);
        assertEquals(groupFromDb1, group);
    }

    @Test
    public void shouldUseAndExtendToken() {
        User user = userRepository.save(new User("3335551111"));
        Group group = groupRepository.save(new Group("tg", user));
        String token = String.valueOf(groupRepository.getMaxTokenValue());
        Instant testDate1 = Instant.now().plus(12L, ChronoUnit.HOURS);
        Instant testDate2 = Instant.now().plus(24L, ChronoUnit.HOURS);
        Instant testDate3 = Instant.now().plus(36L, ChronoUnit.HOURS);

        group.setGroupTokenCode(token);
        group.setTokenExpiryDateTime(testDate2);
        groupRepository.save(group);
        Group group1 = groupRepository.findByGroupTokenCodeAndTokenExpiryDateTimeAfter(token, testDate1);
        Group group2 = groupRepository.findByGroupTokenCodeAndTokenExpiryDateTimeAfter(token, testDate3);
        assertNotNull(group1);
        assertEquals(group1, group);
        assertNull(group2);

        group.setTokenExpiryDateTime(testDate3);
        groupRepository.save(group);
        Group group3 = groupRepository.findByGroupTokenCodeAndTokenExpiryDateTimeAfter(token, testDate2);
        assertNotNull(group3);
        assertEquals(group3, group);
    }

    @Test
    public void shouldCloseToken() {
        User user = userRepository.save(new User("3335550000"));
        Group group = groupRepository.save(new Group("tg", user));
        String token = String.valueOf(groupRepository.getMaxTokenValue());
        Instant testDate1 = Instant.now().plus(12L, ChronoUnit.HOURS);
        Instant testDate2 = Instant.now().plus(24L, ChronoUnit.HOURS);

        group.setGroupTokenCode(token);
        group.setTokenExpiryDateTime(testDate2);
        groupRepository.save(group);
        Group group1 = groupRepository.findByGroupTokenCodeAndTokenExpiryDateTimeAfter(token, testDate1);
        assertNotNull(group1);
        assertEquals(group, group1);

        group.setTokenExpiryDateTime(Instant.now());
        Group group2 = groupRepository.findByGroupTokenCodeAndTokenExpiryDateTimeAfter(token, testDate1);
        assertNull(group2);

    }

    @Test
    public void shouldSetInactive() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("3331110000"));
        Group group = groupRepository.save(new Group("gc", user));
        group.setActive(false);
        groupRepository.save(group);
        Group groupFromDb = groupRepository.findAll().iterator().next();
        assertThat(groupFromDb.getId(), is(group.getId()));
        assertEquals(groupFromDb.getGroupName(), "gc");
        assertFalse(groupFromDb.isActive());
    }

    @Test
    public void shouldReturnOnlyActive() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("3331115555"));
        Group group1 = new Group("gc1", user);
        Group group2 = new Group("gc2", user);
        group1.addMember(user);
        group2.addMember(user);
        group1 = groupRepository.save(group1);
        group2 = groupRepository.save(group2);
        group2.setActive(false);
        group2 = groupRepository.save(group2);
        List<Group> list2 = groupRepository.findByMembershipsUserAndActiveTrue(user);
        assertThat(list2.size(), is(1));
        assertTrue(list2.contains(group1));
        assertFalse(list2.contains(group2));
    }

    @Test
    public void shouldReturnPagesCreatedBy() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("3331110000"));
        List<Group> testGroups = Arrays.asList(new Group("gc1", user), new Group("gc2", user), new Group("gc3", user), new Group("gc4", user));
        for (Group group : testGroups) group.addMember(user);
        testGroups = groupRepository.save(testGroups);
        assertThat(groupRepository.count(), is(4L));
        Page<Group> pageTest1 = groupRepository.findByCreatedByUserAndActiveTrueOrderByCreatedDateTimeDesc(user, new PageRequest(0, 3));
        assertThat(pageTest1.hasNext(), is(true));
        testGroups.get(0).setActive(false);
        groupRepository.save(testGroups.get(0));
        Page<Group> activeGroups = groupRepository.findByCreatedByUserAndActiveTrueOrderByCreatedDateTimeDesc(user, new PageRequest(0,3));
        assertFalse(activeGroups.hasNext());
    }

    @Test
    public void shouldSaveAndRetrievePaidFor() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("3331113333"));
        Group paidGroup = new Group("paidGroup", user);
        paidGroup.setPaidFor(true);
        Group group1 = groupRepository.save(paidGroup);
        Group group2 = groupRepository.save(new Group("unpaidGroup", user));
        assertTrue(group1.isPaidFor());
        assertFalse(group2.isPaidFor());
    }

    @Test
    public void shouldFindByPermission() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("0801113456"));
        Group group1 = new Group("group1", user);
        Group group2 = new Group("group2", user);

        group1.getRole(BaseRoles.ROLE_GROUP_ORGANIZER).addPermission(Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER);
        group1.getRole(BaseRoles.ROLE_GROUP_ORGANIZER).addPermission(Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);
        group2.getRole(BaseRoles.ROLE_ORDINARY_MEMBER).addPermission(Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);

        group1.addMember(user, BaseRoles.ROLE_GROUP_ORGANIZER);
        group2.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER);

        groupRepository.save(group1);
        groupRepository.save(group2);

        assertEquals(1, groupRepository.countActiveGroupsWhereUserHasPermission(user, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER));
        assertEquals(2, groupRepository.countActiveGroupsWhereUserHasPermission(user, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING));
    }

   /* @Test
    public void shouldFindByDiscoverable() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("0881110000"));
        Group testGroup1 = groupRepository.save(new Group("test group 1", user));
        Group testGroup2 = groupRepository.save(new Group("other test", user));
        testGroup1.setDiscoverable(true);
        testGroup1 = groupRepository.save(testGroup1);

        List<Group> firstList = groupRepository.findDiscoverableGroupsWithNameOrTaskTextWithoutMember("test", true);
        assertNotNull(firstList);
        assertThat(firstList.size(), is(1));
        assertTrue(firstList.contains(testGroup1));
        assertFalse(firstList.contains(testGroup2));

        List<Group> secondList = groupRepository.findDiscoverableGroupsWithNameOrTaskTextWithoutMember("1", true);
        assertNotNull(secondList);
        assertThat(secondList.size(), is(1));
        assertTrue(secondList.contains(testGroup1));
        assertFalse(secondList.contains(testGroup2));
    }*/

    @Test
    public void shouldFindWhereGroupJoinUsed() {
        assertThat(groupRepository.count(), is(0L));
        User user = userRepository.save(new User("0801110000"));
        User user2 = userRepository.save(new User("0801110001"));
        Group tg1 = groupRepository.save(new Group("tg1", user));
        Group tg2 = groupRepository.save(new Group("tg2", user));
        groupLogRepository.save(new GroupLog(tg1, user2, GroupLogType.GROUP_MEMBER_ADDED_VIA_JOIN_CODE,
                                             0L, "test"));
        List<Group> groups = groupRepository.findGroupsWhereJoinCodeUsedBetween(Instant.now().minus(1, ChronoUnit.MINUTES),
                                                                                Instant.now());
        assertNotNull(groups);
        assertTrue(groups.contains(tg1));
        assertFalse(groups.contains(tg2));
    }

}


