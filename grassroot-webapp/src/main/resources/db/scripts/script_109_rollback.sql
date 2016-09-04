UPDATE user_profile SET alert_preference = 'NOTIFY_ALL_EVENTS' WHERE alert_preference = 'NOTIFY_NEW_AND_REMINDERS';

ALTER TABLE action_todo_completion_confirmation RENAME COLUMN action_todo_id TO log_book_id;
ALTER TABLE event RENAME COLUMN parent_action_todo_id TO parent_log_book_id;
ALTER TABLE event_request RENAME COLUMN parent_action_todo_id TO parent_log_book_id;
ALTER TABLE notification RENAME COLUMN action_todo_id TO log_book_id;
ALTER TABLE notification RENAME COLUMN action_todo_log_id TO log_book_log_id;
ALTER TABLE action_todo_log RENAME COLUMN action_todo_id TO log_book_id;
ALTER TABLE action_todo_assigned_members RENAME COLUMN action_todo_id TO log_book_id ;
ALTER TABLE action_todo_request_assigned_members RENAME COLUMN action_todo_request_id TO log_book_request_id;

UPDATE notification SET type = 'LOG_BOOK_INFO' WHERE type = 'TODO_INFO';
UPDATE notification SET type = 'LOG_BOOK_REMINDER' WHERE type = 'TODO_REMINDER';

ALTER TABLE action_todo_completion_confirmation RENAME CONSTRAINT fk_action_todo_compl_confirm_action_todo TO fk_log_book_compl_confirm_log_book;
ALTER TABLE action_todo_completion_confirmation RENAME CONSTRAINT fk_action_todo_compl_confirm_member TO fk_log_book_compl_confirm_member;

ALTER TABLE action_todo_assigned_members RENAME CONSTRAINT fk_action_todo_assigned_todo TO fk_log_book_assigned_book;
ALTER TABLE action_todo_assigned_members RENAME CONSTRAINT fk_action_todo_assigned_user TO fk_log_book_assigned_user;

ALTER TABLE action_todo RENAME CONSTRAINT fk_action_todo_ancestor_group TO fk_log_book_ancestor_group;
ALTER TABLE action_todo RENAME CONSTRAINT fk_action_todo_parent_event TO fk_log_book_parent_event;

ALTER TABLE action_todo_request RENAME CONSTRAINT fk_action_todo_req_parent_event TO fk_log_book_req_parent_event;
ALTER TABLE action_todo_request RENAME CONSTRAINT fk_action_todo_request_created_by_user TO fk_log_book_request_created_by_user;
ALTER TABLE action_todo_request RENAME CONSTRAINT fk_action_todo_request_group TO fk_log_book_request_group;

ALTER TABLE action_todo_request_assigned_members RENAME CONSTRAINT fk_action_todo_request_assigned_request TO fk_log_book_request_assigned_request;
ALTER TABLE action_todo_request_assigned_members RENAME CONSTRAINT fk_action_todo_request_assigned_user TO fk_log_book_request_assigned_user;

ALTER TABLE notification RENAME CONSTRAINT fk_account_todo TO fk_log_book;
ALTER TABLE notification RENAME CONSTRAINT fk_action_todo__log_not144107 TO fk_log_book__log_not144107;

ALTER TABLE event RENAME CONSTRAINT fk_event_parent_action_todo TO fk_event_parent_log_book;
ALTER TABLE event_request RENAME CONSTRAINT fk_event_req_parent_action_todo TO fk_event_req_parent_log_book;

ALTER TABLE paid_account RENAME COLUMN action_todo_extra TO logbook_extra;