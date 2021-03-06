package za.org.grassroot.webapp.controller.webapp;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.EventWrapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Created by paballo on 2016/01/22.
 */
public class VoteControllerTest extends WebAppAbstractUnitTest {

    private static final Logger logger = LoggerFactory.getLogger(MeetingControllerTest.class);

    @InjectMocks
    private VoteController voteController;

    @Before
    public void setUp() {
        setUp(voteController);


    }

    @Test
    public void createVoteWorksWhenGroupIdSpecified() throws Exception {

        Group testGroup = new Group("Dummy Group3", new User("234345345"));

        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(userManagementServiceMock.load(sessionTestUser.getUid())).thenReturn(sessionTestUser);

        mockMvc.perform(get("/vote/create").param("groupUid", testGroup.getUid()))
                .andExpect(status().isOk())
                .andExpect(view().name("vote/create"))
                .andExpect(model().attribute("group", hasProperty("uid", is(testGroup.getUid()))));

        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verify(permissionBrokerMock, times(1)).validateGroupPermission(sessionTestUser, testGroup, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyNoMoreInteractions(permissionBrokerMock);
    }

    @Test
    public void createVoteWorksWhengroupNotSpecified() throws Exception {

        Group testGroup = new Group("Dummy Group3", new User("234345345"));
        List<Group> testPossibleGroups = Collections.singletonList(testGroup);

        when(userManagementServiceMock.load(sessionTestUser.getUid())).thenReturn(sessionTestUser);
        when(permissionBrokerMock.getActiveGroupsSorted(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE)).thenReturn(testPossibleGroups);

        mockMvc.perform(get("/vote/create"))
                .andExpect(status().isOk())
                .andExpect(view().name("vote/create"))
                .andExpect(model().attribute("possibleGroups", hasItem(testGroup)));

        verify(permissionBrokerMock, times(1)).getActiveGroupsSorted(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);
        verifyNoMoreInteractions(permissionBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);
    }

    @Test
    public void voteCreateDoWorks() throws Exception {

        Group testGroup = new Group("Dummy Group3", new User("234345345"));
        LocalDateTime testTime = LocalDateTime.now().plusMinutes(7L);
        EventWrapper testVote = EventWrapper.makeEmpty(true);
        testVote.setTitle("test vote");
        testVote.setEventDateTime(testTime);
        testVote.setDescription("Abracadabra");

        mockMvc.perform(post("/vote/create").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("selectedGroupUid", testGroup.getUid())
                .sessionAttr("vote", testVote))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/group/view"))
                .andExpect(redirectedUrl("/group/view?groupUid=" + testGroup.getUid()));

        verify(eventBrokerMock, times(1)).createVote(sessionTestUser.getUid(), testGroup.getUid(), JpaEntityType.GROUP, "test vote",
                                                     testTime, false, false, "Abracadabra", Collections.emptySet());
        verifyNoMoreInteractions(groupBrokerMock);
        verifyNoMoreInteractions(eventBrokerMock);
    }

    @Test
    public void viewVoteWorks() throws Exception {

        Vote testVote = new Vote("test", Instant.now(), sessionTestUser, new Group("tg1", sessionTestUser));
        ResponseTotalsDTO testVoteResults = ResponseTotalsDTO.makeForTest(3, 7, 3, 0, 15);
        when(eventBrokerMock.load(testVote.getUid())).thenReturn(testVote);
        when(eventLogBrokerMock.getResponseCountForEvent(testVote)).thenReturn(testVoteResults);

        mockMvc.perform(get("/vote/view").param("eventUid", testVote.getUid()))
                .andExpect(status().isOk()).andExpect(view().name("vote/view"))
                .andExpect(model().attribute("yes", is(testVoteResults.getYes())))
                .andExpect(model().attribute("no", is(testVoteResults.getNo())))
                .andExpect(model().attribute("abstained", is(testVoteResults.getMaybe())))
                .andExpect(model().attribute("possible", is(testVoteResults.getNumberOfUsers())))
                .andExpect(model().attribute("vote", hasProperty("entityUid", is(testVote.getUid()))));

        verify(eventBrokerMock, times(1)).load(testVote.getUid());
        verifyNoMoreInteractions(eventBrokerMock);
        verify(eventLogBrokerMock, times(1)).getResponseCountForEvent(testVote);
        verifyNoMoreInteractions(eventLogBrokerMock);
    }

    @Test
    public void answerVoteWorks() throws Exception {

        Vote testVote = new Vote("test", Instant.now(), sessionTestUser, new Group("tg1", sessionTestUser));

        when(eventBrokerMock.load(testVote.getUid())).thenReturn(testVote);

        mockMvc.perform(get("/vote/answer").header("referer", "vote").param("eventUid", testVote.getUid()).param("answer", "yes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/vote/view"))
                .andExpect(flash().attributeExists(BaseController.MessageType.INFO.getMessageKey()))
                .andExpect(redirectedUrl("/vote/view?eventUid=" + testVote.getUid()));

        verify(eventBrokerMock, times(1)).load(testVote.getUid());
        verify(eventLogBrokerMock, times(1)).rsvpForEvent(testVote.getUid(), sessionTestUser.getUid(), EventRSVPResponse.fromString("yes"));
        verifyNoMoreInteractions(eventBrokerMock);
        verifyNoMoreInteractions(eventLogBrokerMock);
    }


}
