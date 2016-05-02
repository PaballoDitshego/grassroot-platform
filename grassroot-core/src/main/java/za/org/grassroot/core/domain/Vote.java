package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.EventType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.Instant;

@Entity
@DiscriminatorValue("VOTE")
public class Vote extends Event<VoteContainer> {

	@ManyToOne
	@JoinColumn(name = "meeting_id")
	protected Meeting meeting;

	private Vote() {
		// for JPA
	}

	public Vote(String name, Instant startDateTime, User user, VoteContainer parent) {
		this(name, startDateTime, user, parent, false);
	}

	public Vote(String name, Instant startDateTime, User user, VoteContainer parent, boolean includeSubGroups) {
		this(name, startDateTime, user, parent, includeSubGroups, false, null);
	}

	public Vote(String name, Instant startDateTime, User user, VoteContainer parent, boolean includeSubGroups, boolean relayable, String description) {
		super(startDateTime, user, parent, name, includeSubGroups, true, relayable, EventReminderType.DISABLED, 0, description);
	}

	@Override
	public EventType getEventType() {
		return EventType.VOTE;
	}

	@Override
	public JpaEntityType getJpaEntityType() {
		return JpaEntityType.VOTE;
	}

	public VoteContainer getParent() {
		if (parentGroup != null) {
			return parentGroup;
		} else if (parentLogBook != null) {
			return parentLogBook;
		} else if (meeting != null) {
			return meeting;
		} else {
			throw new IllegalStateException("There is no " + VoteContainer.class.getSimpleName() + " parent defined for " + this);
		}
	}

	public void setParent(VoteContainer parent) {
		if (parent instanceof Group) {
			this.parentGroup = (Group) parent;
		} else if (parent instanceof LogBook) {
			this.parentLogBook = (LogBook) parent;
		} else if (parent instanceof Meeting) {
			this.meeting = (Meeting) parent;
		} else {
			throw new UnsupportedOperationException("Unsupported parent: " + parent);
		}
	}
}
