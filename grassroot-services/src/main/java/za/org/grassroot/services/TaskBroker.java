package za.org.grassroot.services;

import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.core.enums.TaskType;

import java.time.Instant;
import java.util.List;

/**
 * Created by luke on 2016/04/26.
 */
public interface TaskBroker {

    // note: this is a highly inefficient method that will scan the repos to find the entity with the corresponding UID
    // use it only when gains are significant and use relatively low. If the type is readily available, use the subsequent method.
    TaskDTO load(String userUid, String taskUid);

    TaskDTO load(String userUid, String taskUid, TaskType type);

    List<TaskDTO> fetchUpcomingIncompleteGroupTasks(String userUid, String groupUid);

    ChangedSinceData<TaskDTO> fetchGroupTasks(String userUid, String groupUid, Instant changedSince);

    List<TaskDTO> fetchUpcomingUserTasks(String userUid);

    ChangedSinceData<TaskDTO> fetchUpcomingTasksAndCancelled(String userUid, Instant changedSince);

    List<TaskDTO> searchForTasks(String userUid, String searchTerm);

}