
package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.RSVPTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.EventBroker;
import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.EventDTO;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

/**
 * Created by paballo on 2016/03/21.
 */

@RestController
@RequestMapping("/api/meeting")
public class MeetingRestController {


    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    EventLogManagementService eventLogManagementService;

    @Autowired
    EventBroker eventBroker;


    @RequestMapping(value = "/meeting/create/{id}/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createMeeting(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
                                                         @PathVariable("id") String groupUid, @RequestParam("title") String title, @RequestParam("description") String description,
                                                         @RequestParam("startTime") String time, @RequestParam("notifyGroup") boolean relayable, @RequestParam("reminderMins") int reminderMinutes,
                                                         @RequestParam("location") String location, @RequestParam("includeSubGroups") boolean includeSubGroups, @RequestParam("rsvpRequired") boolean rsvp) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        eventBroker.createMeeting(user.getUid(), groupUid, title, Timestamp.valueOf(DateTimeUtil.parseDateTime(time)), location, includeSubGroups, rsvp, relayable,
                EventReminderType.CUSTOM, reminderMinutes, description);
        ResponseWrapper responseWrapper = new ResponseWrapperImpl(HttpStatus.CREATED, RestMessage.MEETING_CREATED, RestStatus.SUCCESS);
        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));

    }

    @RequestMapping(value = "/rsvp/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> rsvp(@PathVariable("phoneNumber") String phoneNumber,
                                                @PathVariable("code") String code, @PathVariable("id") String eventId,
                                                @RequestParam(value = "response", required = true) String response) {
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Meeting meeting = eventBroker.loadMeeting(eventId);
        String trimmedResponse = response.toLowerCase().trim();
        ResponseWrapper responseWrapper;
        if (meeting.isCanceled()) {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.NOT_FOUND, RestMessage.MEETING_CANCELLED, RestStatus.FAILURE);
        } else if (isOpen(meeting)) {
            eventLogManagementService.rsvpForEvent(meeting, user, EventRSVPResponse.fromString(trimmedResponse));
            responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.RSVP_SENT, RestStatus.SUCCESS);
        } else {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.PAST_DUE, RestStatus.FAILURE);
        }

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }


    @RequestMapping(value = "/view/{id}/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> rsvp(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code, @PathVariable("id") String id) {
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Meeting meeting = eventBroker.loadMeeting(id);
        EventLog eventLog = eventLogManagementService.getEventLogOfUser(meeting, user, EventLogType.EventRSVP);
        boolean hasResponded = eventLogManagementService.userRsvpForEvent(meeting, user);
        RSVPTotalsDTO totals = eventLogManagementService.getRSVPTotalsForEvent(meeting);
        EventDTO eventDTO = new EventDTO(meeting, eventLog, user, hasResponded, totals);
        ResponseWrapper responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.MEETING_DETAILS, RestStatus.SUCCESS,
                eventDTO);

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }

    private boolean isOpen(Event event) {
        return event.getEventStartDateTime().after(Timestamp.from(Instant.now()));
    }


}