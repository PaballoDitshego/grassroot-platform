package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.services.EventBroker;
import za.org.grassroot.services.GroupBroker;
import za.org.grassroot.services.TaskBroker;
import za.org.grassroot.services.TodoBroker;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.MemberListDTO;
import za.org.grassroot.webapp.model.rest.wrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapperImpl;
import za.org.grassroot.webapp.model.web.MemberPicker;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;

/**
 * Created by luke on 2016/04/28.
 */
@RestController
@RequestMapping(value = "/ajax")
public class AjaxController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(AjaxController.class);

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private EventBroker eventBroker;

    @Autowired
    private TodoBroker todoBroker;

    @Autowired
    private TaskBroker taskBroker;

    @Autowired
    private GeoLocationBroker geoLocationBroker;

    @RequestMapping(value = "/members/list", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> retrieveParentMembers(@RequestBody MemberListDTO listRequest) {

        MemberPicker memberPicker;
        final JpaEntityType type = listRequest.getParentEntityType();
        final String parentUid = listRequest.getParentUid();
        final boolean selected = listRequest.isSelectedByDefault();

        if (JpaEntityType.GROUP.equals(type)) {
            memberPicker = new MemberPicker(groupBroker.load(parentUid), selected);
        } else if (JpaEntityType.MEETING.equals(type) || JpaEntityType.VOTE.equals(type)) {
            memberPicker = new MemberPicker(eventBroker.load(parentUid), selected);
        } else if (JpaEntityType.TODO.equals(type)) {
            memberPicker = new MemberPicker(todoBroker.load(parentUid), selected);
        } else {
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.INVALID_ENTITY_TYPE);
        }

        ResponseWrapper body = new GenericResponseWrapper(HttpStatus.FOUND, RestMessage.PARENT_MEMBERS,
                                                                     RestStatus.SUCCESS, memberPicker);
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    @RequestMapping(value = "/tasks/fetch", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> fetchTaskDescription(@RequestParam String taskUid,
                                                                @RequestParam TaskType taskType) {
        log.info("Fetching task description for taskUid={}", taskUid);
        try {
            TaskDTO taskDTO = taskBroker.load(getUserProfile().getUid(), taskUid, taskType);
            ResponseWrapper body = new GenericResponseWrapper(HttpStatus.FOUND, RestMessage.TASK_FOUND, RestStatus.SUCCESS, taskDTO);
            return new ResponseEntity<>(body, HttpStatus.OK);
        } catch (Exception e) {
            log.info("Error fetching task! Error: {}", e.toString());
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.INVALID_INPUT);
        }
    }

    @RequestMapping(value = "/test", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> logIncomingRequest(@RequestBody MemberListDTO listRequest, HttpServletRequest request) {
        log.info("Received a request! It's this: {}", listRequest.toString());
        return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.FOUND, RestMessage.PARENT_MEMBERS, RestStatus.SUCCESS),
                                    HttpStatus.OK);
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/locations/list", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> retrieveLocationLogs(@RequestParam String userUid) {
        log.info("Retrieving raw location logs for = {}", userUid);
        List<double[]> listLatLongs = geoLocationBroker.fetchUserLatitudeLongitudeInAvgPeriod(userUid, LocalDate.now());
        ResponseWrapper body = new GenericResponseWrapper(HttpStatus.FOUND, RestMessage.TASK_FOUND, RestStatus.SUCCESS, listLatLongs);
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

}
