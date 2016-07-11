package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.LogBookLogType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.services.enums.TodoStatus;

import java.time.Instant;
import java.util.*;

/**
 * Created by luke on 2016/04/26.
 */
@Service
public class TaskBrokerImpl implements TaskBroker {

    private static final Logger log = LoggerFactory.getLogger(TaskBrokerImpl.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private EventBroker eventBroker;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private LogBookRepository logBookRepository;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Autowired
    private LogBookLogRepository logBookLogRepository;

    @Autowired
    private TodoBroker todoBroker;

    @Override
    @Transactional(readOnly = true)
    public TaskDTO load(String userUid, String taskUid, TaskType type) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(taskUid);
        Objects.requireNonNull(type);

        User user = userRepository.findOneByUid(userUid);
        TaskDTO taskToReturn;

        switch (type) {
            case MEETING:
            case VOTE:
                Event event = eventBroker.load(taskUid);
                taskToReturn = new TaskDTO(event, user, eventLogRepository);
                break;
            case TODO:
                LogBook logBook = todoBroker.load(taskUid);
                taskToReturn = new TaskDTO(logBook, user);
                break;
            default:
                taskToReturn = null;
        }

        log.info("Task created by user: {}", taskToReturn.isCreatedByUser());
        return taskToReturn;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskDTO> fetchGroupTasks(String userUid, String groupUid, boolean futureOnly, TodoStatus todoStatus) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupBroker.load(groupUid);

        Set<TaskDTO> taskSet = new HashSet<>();

        Instant start = futureOnly ? Instant.now() : null;
        for (Event event : groupBroker.retrieveGroupEvents(group, null, start, null)) {
            if (event.getEventStartDateTime() != null) {
                taskSet.add(new TaskDTO(event, user, eventLogRepository));
            }
        }

        for (LogBook logBook : todoBroker.loadGroupLogBooks(group.getUid(), futureOnly, todoStatus)) {
            if (logBook.getCreatedByUser().equals(user)) {
                taskSet.add(new TaskDTO(logBook, user));
            } else {
                taskSet.add(new TaskDTO(logBook, user));
            }
        }

        List<TaskDTO> tasks = new ArrayList<>(taskSet);
        Collections.sort(tasks);
        return tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskDTO> fetchUpcomingUserTasks(String userUid, Instant changedSince) {
        Objects.requireNonNull(userUid);

        User user = userRepository.findOneByUid(userUid);
        Set<TaskDTO> taskDtos = new HashSet<>();

        Instant now = Instant.now();

        List<Event> events = eventRepository.findByParentGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceledFalse(user, now);
        for (Event event : events) {
            if (changedSince == null || isEventChangedSince(event, changedSince)) {
                EventLog userResponseLog = eventLogRepository.findByEventAndUserAndEventLogType(event, user, EventLogType.RSVP);
                if (changedSince == null || userResponseLog == null || (userResponseLog != null && userResponseLog.getCreatedDateTime().isAfter(changedSince))) {
                    taskDtos.add(new TaskDTO(event, user, userResponseLog));
                }
            }
        }

        List<LogBook> logBooks = logBookRepository.findByParentGroupMembershipsUserAndActionByDateGreaterThan(user, now);
        for (LogBook logBook : logBooks) {
            if (changedSince == null || isLogBookChangedSince(logBook, changedSince)) {
                taskDtos.add(new TaskDTO(logBook, user));
            }
        }

        List<TaskDTO> tasks = new ArrayList<>(taskDtos);
        Collections.sort(tasks);
        log.info("Retrieved all the user's upcoming tasks, took {} msecs", System.currentTimeMillis() - now.toEpochMilli());
        return tasks;
    }

    private boolean isEventChangedSince(Event event, Instant changedSince) {
        if (event.getCreatedDateTime().isAfter(changedSince)) {
            return true;
        }
        EventLog lastChangeLog = eventLogRepository.findFirstByEventAndEventLogTypeOrderByCreatedDateTimeDesc(event, EventLogType.CHANGE);
        if (lastChangeLog != null && lastChangeLog.getCreatedDateTime().isAfter(changedSince)) {
            return true;
        }
        return false;
    }

    private boolean isLogBookChangedSince(LogBook logBook, Instant changedSince) {
        if (logBook.getCreatedDateTime().isAfter(changedSince)) {
            return true;
        }
        LogBookLog lastChangeLog = logBookLogRepository.findFirstByLogBookAndTypeOrderByCreatedDateTimeDesc(logBook, LogBookLogType.CHANGED);
        if (lastChangeLog != null && lastChangeLog.getCreatedDateTime().isAfter(changedSince)) {
            return true;
        }
        return false;
    }
}
