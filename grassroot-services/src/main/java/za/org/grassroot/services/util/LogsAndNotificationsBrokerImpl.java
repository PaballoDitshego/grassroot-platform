package za.org.grassroot.services.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.repository.*;

import java.util.Objects;
import java.util.Set;

@Service
public class LogsAndNotificationsBrokerImpl implements LogsAndNotificationsBroker {
	private final Logger logger = LoggerFactory.getLogger(LogsAndNotificationsBrokerImpl.class);

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private GroupLogRepository groupLogRepository;

	@Autowired
	private UserLogRepository userLogRepository;

	@Autowired
	private EventLogRepository eventLogRepository;

	@Autowired
	private TodoLogRepository todoLogRepository;

	@Autowired
	private AccountLogRepository accountLogRepository;

	@Override
	@Transactional
	@Async
	public void asyncStoreBundle(LogsAndNotificationsBundle bundle) {
		storeBundle(bundle);
	}

	@Override
	@Transactional
	public void storeBundle(LogsAndNotificationsBundle bundle) {
		Objects.requireNonNull(bundle);

		Set<ActionLog> logs = bundle.getLogs();
		if (!logs.isEmpty()) {
			logger.info("Storing {} logs", logs.size());
		}
		for (ActionLog log : logs) {
			logger.info("Saving log {}", log);
			saveLog(log);
		}

		Set<Notification> notifications = bundle.getNotifications();
		if (!notifications.isEmpty()) {
			logger.info("Storing {} notifications", notifications.size());
		}

		for (Notification notification : notifications) {
			notificationRepository.save(notification);
		}
	}

	private void saveLog(ActionLog actionLog) {
		if (actionLog instanceof GroupLog) {
			groupLogRepository.save((GroupLog) actionLog);

		} else if (actionLog instanceof UserLog) {
			userLogRepository.save((UserLog) actionLog);

		} else if (actionLog instanceof EventLog) {
			eventLogRepository.save((EventLog) actionLog);

		} else if (actionLog instanceof TodoLog) {
			todoLogRepository.save((TodoLog) actionLog);

		} else if (actionLog instanceof AccountLog) {
			accountLogRepository.save((AccountLog) actionLog);

		} else {
			throw new UnsupportedOperationException("Unsupported log: " + actionLog);
		}
	}
}
