package za.org.grassroot.core.domain.metamodel;

import za.org.grassroot.core.domain.AbstractTodoEntity;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.time.Instant;
import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(AbstractTodoEntity.class)
public abstract class AbstractTodoEntity_ {

	public static volatile SingularAttribute<AbstractTodoEntity, String> uid;
	public static volatile SingularAttribute<AbstractTodoEntity, User> createdByUser;
	public static volatile SingularAttribute<AbstractTodoEntity, Event> parentEvent;
	public static volatile SingularAttribute<AbstractTodoEntity, Integer> reminderMinutes;
	public static volatile SingularAttribute<AbstractTodoEntity, Instant> createdDateTime;
	public static volatile SingularAttribute<AbstractTodoEntity, Instant> actionByDate;
	public static volatile SingularAttribute<AbstractTodoEntity, Boolean> reminderActive;
	public static volatile SingularAttribute<AbstractTodoEntity, Group> parentGroup;
	public static volatile SingularAttribute<AbstractTodoEntity, Long> id;
	public static volatile SingularAttribute<AbstractTodoEntity, Instant> scheduledReminderTime;
	public static volatile SingularAttribute<AbstractTodoEntity, String> message;
	public static volatile SingularAttribute<AbstractTodoEntity, Integer> version;

}

