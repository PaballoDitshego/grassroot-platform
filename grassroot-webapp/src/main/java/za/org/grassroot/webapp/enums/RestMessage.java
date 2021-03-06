package za.org.grassroot.webapp.enums;

/**
 * Created by paballo on 2016/03/08.
 */
public enum RestMessage {

    VERIFICATION_TOKEN_SENT,
    INVALID_OTP,
	INVALID_TOKEN,
    INVALID_MSISDN,
    TOKEN_EXPIRED,
    LOGIN_SUCCESS,
    USER_REGISTRATION_SUCCESSFUL,
    USER_ALREADY_EXISTS,
    USER_DOES_NOT_EXIST,
    USER_HAS_NO_GROUPS,
    USER_HAS_NO_TASKS,
    USER_PROFILE,
    USER_GROUPS,
    LOCATION_RECORDED,
    USER_OKAY,
	OTP_REQ_BEFORE_ADD,
    USER_LOGGED_OUT,

    GROUP_CREATED,
    GROUP_DUPLICATE_CREATE,
	GROUP_BAD_PHONE_NUMBER,
    GROUP_NOT_CREATED,
    GROUP_JOIN_REQUEST_SENT,
    GROUP_NOT_FOUND,
    POSSIBLE_GROUP_MATCHES,
    GROUP_FOUND,
    NO_GROUP_MATCHING_TERM_FOUND,
    GROUP_PROFILE,

    USER_ALREADY_PART_OF_GROUP,
    GROUP_JOIN_RESPONSE_PROCESSED,
    GROUP_JOIN_REQUEST_CANCELLED,
    GROUP_JOIN_REQUEST_REMIND,
    GROUP_JOIN_REQUEST_NOT_FOUND,

    UPDATED,
    APPROVER_PERMISSIONS_CHANGED,
    GROUP_RENAMED,
    GROUP_DESCRIPTION_CHANGED,
    GROUP_DISCOVERABLE_UPDATED,
    GROUP_JOIN_CODE_OPENED,
    GROUP_JOIN_CODE_CLOSED,
    PERMISSIONS_RETURNED,
    PERMISSIONS_UPDATED,
	MEMBER_ROLE_CHANGED,
	NO_MEMBERS_SENT,

    USER_ACTIVITIES,
    GROUP_ACTIVITIES,
    NO_GROUP_ACTIVITIES,
    TASK_DETAILS,
	TASK_NOT_FOUND,

    GROUP_MEMBERS, // assuming this is not an error, moving here
    PARENT_MEMBERS,
    MEMBERS_ADDED,
    MEMBERS_REMOVED,
    MEMBER_UNSUBSCRIBED,
    MEMBER_ALREADY_LEFT,

    USER_HAS_ALREADY_VOTED,
    VOTE_CLOSED,
    VOTE_CANCELLED,
    VOTE_SENT,
    VOTE_DETAILS,
    VOTE_CREATED,
    VOTE_DETAILS_UPDATED,

    MEETING_CANCELLED,
    MEETING_CREATED,
    MEETING_DETAILS,
    MEETING_DETAILS_UPDATED,
    MEETING_UPDATE_ERROR,
    RSVP_SENT,
    PAST_DUE,

    TODO_CREATED,
    TODO_SET_COMPLETED,
    TODO_ALREADY_COMPLETED,

    TASK_FOUND,

    INVALID_PAGE_NUMBER,
    EXCEEDS_TOTAL_PAGES,
    TIME_CANNOT_BE_IN_THE_PAST,
    CLIENT_ERROR,
    ERROR,
    REGISTERED_FOR_PUSH,

	NOTIFICATIONS,
    NOTIFICATIONS_FINISHED,
	INVALID_INPUT,
    INVALID_ENTITY_TYPE,
    PERMISSION_DENIED,

    PROFILE_SETTINGS_UPDATED,
	PROFILE_SETTINGS,
	PROFILE_SETTINGS_ERROR,
    INVALID_LANG_CODE,

    NOTIFICATION_UPDATED,
    ALREADY_UPDATED,
    DEREGISTERED_FOR_PUSH,
    USER_NOT_REGISTERED_FOR_PUSH,
    VOTE_ALREADY_CANCELLED,
    MEETING_ALREADY_CANCELLED,
    TODO_UPDATED,
    TODO_CANCELLED,

    UPLOADED,
    BAD_PICTURE_FORMAT,
    PICTURE_NOT_RECEIVED,
	PICTURE_REMOVED,
    PICTURE_NOT_FOUND,

    DATE_TIME_PARSED,
    DATE_TIME_FAILED,

    CHAT_SENT,
    CHAT_DEACTIVATED,
    CHAT_ACTIVATED,
    MESSAGE_SETTING_NOT_FOUND,
    MESSENGER_SETTINGS,
    PING,
    CHATS_MARKED_AS_READ, EMPTY_LIST

}
