package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.*;
import za.org.grassroot.services.*;
import za.org.grassroot.services.enums.EventListTimeType;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDResponseTypes;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDEventUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.core.util.DateTimeUtil.convertToUserTimeZone;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;
import static za.org.grassroot.webapp.enums.USSDSection.*;

/**
 * Controller for the USSD menu
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDHomeController extends USSDController {

    @Autowired
    private EventLogBroker eventLogBroker;

    @Autowired
    private USSDEventUtil eventUtil;

    @Autowired
    private TodoBroker todoBroker;

    @Autowired
    private GroupQueryBroker groupQueryBroker;

    @Autowired
    private PermissionBroker permissionBroker;

    @Autowired
    private SafetyEventBroker safetyEventBroker;

    @Autowired
    private Environment environment;

    private static final Logger log = LoggerFactory.getLogger(USSDHomeController.class);

    private static final String path = homePath;
    private static final USSDSection thisSection = HOME;

    private static final String rsvpMenu = "rsvp",
            renameUserMenu = "rename-start",
            renameGroupAndStart = "group-start",
            promptGroupRename = "group-rename-prompt",
            promptConfirmGroupInactive = "group-inactive-confirm";

    private int hashPosition;

    @Value("${grassroot.ussd.safety.suffix:911}")
    private String safetyCode;

    @Value("${grassroot.ussd.sendlink.suffix:123}")
    private String sendMeLink;

    @Value("${grassroot.ussd.promotion.suffix:44}")
    private String promotionSuffix;

    private static final String openingMenuKey = String.join(".", Arrays.asList(homeKey, startMenu, optionsKey));

    private static final Map<USSDSection, String[]> openingMenuOptions = Collections.unmodifiableMap(Stream.of(
            new SimpleEntry<>(MEETINGS, new String[]{meetingMenus + startMenu, openingMenuKey + mtgKey}),
            new SimpleEntry<>(VOTES, new String[]{voteMenus + startMenu, openingMenuKey + voteKey}),
            new SimpleEntry<>(TODO, new String[]{todoMenus + startMenu, openingMenuKey + logKey}),
            new SimpleEntry<>(GROUP_MANAGER, new String[]{groupMenus + startMenu, openingMenuKey + groupKey}),
            new SimpleEntry<>(USER_PROFILE, new String[]{userMenus + startMenu, openingMenuKey + userKey}),
            new SimpleEntry<>(SAFETY_GROUP_MANAGER, new String[]{safetyMenus + startMenu, openingMenuKey + safetyKey})).
            collect(Collectors.toMap((e) -> e.getKey(), (e) -> e.getValue())));

    private static final List<USSDSection> openingSequenceWithGroups = Arrays.asList(
            MEETINGS, VOTES, TODO, GROUP_MANAGER, USER_PROFILE, SAFETY_GROUP_MANAGER);
    private static final List<USSDSection> openingSequenceWithoutGroups = Arrays.asList(
            USER_PROFILE, GROUP_MANAGER, MEETINGS, VOTES, TODO);

    @PostConstruct
    public void init() {
        hashPosition = environment.getRequiredProperty("grassroot.ussd.code.length", Integer.class);
    }

    public USSDMenu welcomeMenu(String opening, User user) {

        USSDMenu homeMenu = new USSDMenu(opening);
        List<USSDSection> menuSequence = permissionBroker.countActiveGroupsWithPermission(user, null) != 0 ?
                openingSequenceWithGroups : openingSequenceWithoutGroups;

        for (USSDSection section : menuSequence) {
            homeMenu.addMenuOption(openingMenuOptions.get(section)[0],
                    getMessage(openingMenuOptions.get(section)[1], user));
        }

        return homeMenu;
    }

    @RequestMapping(value = path + startMenu)
    @ResponseBody
    public Request startMenu(@RequestParam(value = phoneNumber) String inputNumber,
                             @RequestParam(value = userInputParam, required = false) String enteredUSSD) throws URISyntaxException {

        Long startTime = System.currentTimeMillis();

        log.info("testing ... hash position = {}, safetyPrefix = {}", hashPosition, safetyCode);

        USSDMenu openingMenu;
        final boolean trailingDigitsPresent = codeHasTrailingDigits(enteredUSSD);

        // first off, check if (a) the user has entered no special code after and (b) there is a cache entry for an
        // interrupted menu, and if so, just assemble without touching DB
        if (!trailingDigitsPresent && userInterrupted(inputNumber)) {
            return menuBuilder(interruptedPrompt(inputNumber));
        }

        User sessionUser = userManager.loadOrCreateUser(inputNumber);
        userLogger.recordUserSession(sessionUser.getUid(), UserInterfaceType.USSD);

        /*
        Adding some logic here to check for one of these things:
        (1) The user has appended a joining code, so we need to add them to a group, or has asked to trigger a panic call
        (2) The user has an outstanding RSVP request for a meeting
        (3) The user has not named themselves, or a prior group
         */

        if (trailingDigitsPresent) {
            String trailingDigits = enteredUSSD.substring(hashPosition + 1, enteredUSSD.length() - 1);
            openingMenu = processTrailingDigits(trailingDigits, sessionUser);
        } else {
            if (!sessionUser.isHasInitiatedSession())
                userManager.setHasInitiatedUssdSession(sessionUser.getUid());
            USSDResponseTypes neededResponse = neededResponse(sessionUser);
            openingMenu = neededResponse.equals(USSDResponseTypes.NONE) ? defaultStartMenu(sessionUser)
                    : requestUserResponse(sessionUser, neededResponse);
        }
        Long endTime = System.currentTimeMillis();
        log.info(String.format("Generating home menu, time taken: %d msecs", endTime - startTime));
        return menuBuilder(openingMenu, true);
    }

    /*
    Method to go straight to start menu, over-riding prior interruptions, and/or any responses, etc.
     */
    @RequestMapping(value = path + startMenu + "_force")
    @ResponseBody
    public Request forceStartMenu(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        return menuBuilder(defaultStartMenu(userManager.loadOrCreateUser(inputNumber)));
    }

    private USSDMenu interruptedPrompt(String inputNumber) {
        String returnUrl = cacheManager.fetchUssdMenuForUser(inputNumber);
        log.info("The user was interrupted somewhere ...Here's the URL: " + returnUrl);

        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu promptMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey + "-interrupted", user));
        promptMenu.addMenuOption(returnUrl, getMessage(thisSection, startMenu, "interrupted.resume", user));
        promptMenu.addMenuOption(startMenu + "_force", getMessage(thisSection, startMenu, "interrupted.start", user));

        // set the user's "last USSD menu" back to null, so avoids them always coming back here
        userLogger.recordUssdInterruption(user.getUid(), returnUrl);
        cacheManager.clearUssdMenuForUser(inputNumber);

        return promptMenu;

    }

    private boolean userInterrupted(String inputNumber) {
        return (cacheManager.fetchUssdMenuForUser(inputNumber) != null);
    }

    /* Note: the sequence in which these are checked and returned sets the order of priority of responses */
    /* Note: this involves around four DB pings on first menu -- somewhat expensive -- need to consolidate somehow */

    private USSDResponseTypes neededResponse(User user) {

        if (safetyEventBroker.needsToRespondToSafetyEvent(user)) return USSDResponseTypes.RESPOND_SAFETY;
        if (eventBroker.userHasResponsesOutstanding(user, EventType.VOTE)) return USSDResponseTypes.VOTE;
        if (eventBroker.userHasResponsesOutstanding(user, EventType.MEETING)) return USSDResponseTypes.MTG_RSVP;
        if (todoBroker.userHasTodosForResponse(user.getUid(), false)) return USSDResponseTypes.RESPOND_TODO;
        if (userManager.needsToRenameSelf(user)) return USSDResponseTypes.RENAME_SELF;
        if (userManager.fetchGroupUserMustRename(user) != null) return USSDResponseTypes.NAME_GROUP;

        return USSDResponseTypes.NONE;

    }

    private USSDMenu processTrailingDigits(String trailingDigits, User user) throws URISyntaxException {
        USSDMenu returnMenu;
        log.info("Processing trailing digits ..." + trailingDigits);
        if (safetyCode.equals(trailingDigits)) {
            returnMenu = assemblePanicButtonActivationMenu(user);
        } else if (sendMeLink.equals(trailingDigits)) {
            returnMenu = assembleSendMeAndroidLinkMenu(user);
        } else if (promotionSuffix.equals(trailingDigits)) {
            userLogger.recordUserLog(user.getUid(), UserLogType.USED_PROMOTIONAL_CODE, "used code: " + trailingDigits);
            if (!user.isHasInitiatedSession()) {
                userManager.setHasInitiatedUssdSession(user.getUid());
            }
            returnMenu = defaultStartMenu(user);
        } else {
            Optional<Group> searchResult = groupQueryBroker.findGroupFromJoinCode(trailingDigits.trim());
            if (searchResult.isPresent()) {
                Group group = searchResult.get();
                groupBroker.addMemberViaJoinCode(user.getUid(), group.getUid(), trailingDigits);
                String prompt = (group.hasName()) ?
                        getMessage(thisSection, startMenu, promptKey + ".group.token.named", group.getGroupName(), user) :
                        getMessage(thisSection, startMenu, promptKey + ".group.token.unnamed", user);
                returnMenu = welcomeMenu(prompt, user);
            } else {
                returnMenu = welcomeMenu(getMessage(thisSection, startMenu, promptKey + ".unknown.request", user), user);
            }
        }
        return returnMenu;

    }

    private boolean codeHasTrailingDigits(String enteredUSSD) {
        return (enteredUSSD != null && enteredUSSD.length() > hashPosition + 1);
    }

    private USSDMenu defaultStartMenu(User sessionUser) throws URISyntaxException {
        String welcomeMessage = sessionUser.hasName() ? getMessage(thisSection, startMenu, promptKey + "-named", sessionUser.getName(""), sessionUser) :
                getMessage(thisSection, startMenu, promptKey, sessionUser);
        return welcomeMenu(welcomeMessage, sessionUser);
    }

    private USSDMenu requestUserResponse(User user, USSDResponseTypes response) throws URISyntaxException {

        USSDMenu openingMenu = new USSDMenu();

        switch (response) {
            case RESPOND_SAFETY:
                SafetyEvent safetyEvent = safetyEventBroker.getOutstandingUserSafetyEventsResponse(user.getUid()).get(0);
                openingMenu = assemblePanicButtonActivationResponse(user, safetyEvent);
                break;
            case VOTE:
                openingMenu = assembleVoteMenu(user);
                break;
            case MTG_RSVP:
                openingMenu = assembleRsvpMenu(user);
                break;
            case RESPOND_TODO:
                openingMenu = assembleActionTodoMenu(user);
                break;
            case RENAME_SELF:
                openingMenu.setPromptMessage(getMessage(thisSection, USSDController.startMenu, promptKey + "-rename", user));
                openingMenu.setFreeText(true);
                openingMenu.setNextURI(renameUserMenu);
                break;
            case NAME_GROUP:
                Group group = userManager.fetchGroupUserMustRename(user);
                openingMenu = (groupBroker.isDeactivationAvailable(user, group, true)) ?
                        renameGroupAllowInactive(user, group.getUid(), dateFormat.format(convertToUserTimeZone(group.getCreatedDateTime(), getSAST()))) :
                        renameGroupNoInactiveOption(user, group.getUid(), dateFormat.format(convertToUserTimeZone(group.getCreatedDateTime(), getSAST())));
                break;
            case NONE:
                openingMenu = defaultStartMenu(user);
                break;
        }

        return openingMenu;

    }

    /*
    Section of helper methods for opening menu response handling
     */

    private USSDMenu assembleVoteMenu(User sessionUser) {
        Vote vote = (Vote) eventBroker.getOutstandingResponseForUser(sessionUser, EventType.VOTE).get(0);

        final String[] promptFields = new String[]{vote.getAncestorGroup().getName(""),
                vote.getCreatedByUser().nameToDisplay(),
                vote.getName()};

        final String voteUri = "vote" + entityUidUrlSuffix + vote.getUid() + "&response=";
        final String optionMsgKey = voteKey + "." + optionsKey;

        USSDMenu openingMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey + "-vote", promptFields, sessionUser));

        openingMenu.addMenuOption(voteUri + "yes", getMessage(optionMsgKey + "yes", sessionUser));
        openingMenu.addMenuOption(voteUri + "no", getMessage(optionMsgKey + "no", sessionUser));
        openingMenu.addMenuOption(voteUri + "maybe", getMessage(optionMsgKey + "abstain", sessionUser));

        return openingMenu;
    }

    private USSDMenu assembleRsvpMenu(User sessionUser) {
        Event meeting = eventBroker.getOutstandingResponseForUser(sessionUser, EventType.MEETING).get(0);

        String[] meetingDetails = new String[]{meeting.getAncestorGroup().getName(""),
                meeting.getCreatedByUser().nameToDisplay(),
                meeting.getName(),
                meeting.getEventDateTimeAtSAST().format(dateTimeFormat)};

        // if the composed message is longer than 120 characters, we are going to go over, so return a shortened message
        String defaultPrompt = getMessage(thisSection, startMenu, promptKey + "-" + rsvpMenu, meetingDetails, sessionUser);
        if (defaultPrompt.length() > 120)
            defaultPrompt = getMessage(thisSection, startMenu, promptKey + "-" + rsvpMenu + ".short", meetingDetails, sessionUser);

        String optionUri = rsvpMenu + entityUidUrlSuffix + meeting.getUid();
        USSDMenu openingMenu = new USSDMenu(defaultPrompt);
        openingMenu.setMenuOptions(new LinkedHashMap<>(optionsYesNo(sessionUser, optionUri, optionUri)));
        return openingMenu;
    }

    private USSDMenu assembleActionTodoMenu(User user) {
        Optional<Todo> optionalTodo = todoBroker.fetchTodoForUserResponse(user.getUid(), false);
        if (!optionalTodo.isPresent()) {
            log.info("For some reason, should have found an action to respond to, but didnt, user = {}", user);
            return welcomeMenu(getMessage(thisSection, startMenu, promptKey, user), user);
        } else {
            Todo todo = optionalTodo.get();
            String[] promptFields = new String[]{todo.getParent().getName(), todo.getMessage(), todo.getActionByDateAtSAST().format(dateFormat)};
            String prompt = getMessage(thisSection, startMenu, promptKey + ".todo", promptFields, user);
            String completeUri = "todo-complete" + entityUidUrlSuffix + todo.getUid();
            USSDMenu menu = new USSDMenu(prompt);
            menu.addMenuOptions(new LinkedHashMap<>(optionsYesNo(user, completeUri, completeUri)));
            menu.addMenuOption(completeUri + "&confirmed=unknown", getMessage(thisSection, startMenu, logKey + "." + optionsKey + "unknown", user));
            return menu;
        }
    }

    private USSDMenu renameGroupNoInactiveOption(User user, String groupUid, String dateCreated) {
        USSDMenu thisMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey + "-group-rename", dateCreated, user));
        thisMenu.setFreeText(true);
        thisMenu.setNextURI(renameGroupAndStart + groupUidUrlSuffix + groupUid);
        return thisMenu;
    }


    private USSDMenu renameGroupAllowInactive(User user, String groupUid, String dateCreated) {
        USSDMenu thisMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey + "-group-options", dateCreated, user));
        thisMenu.setFreeText(false);

        thisMenu.addMenuOption(promptGroupRename + groupUidUrlSuffix + groupUid,
                getMessage(thisSection, startMenu, "group.options.rename", user));
        thisMenu.addMenuOption(promptConfirmGroupInactive + groupUidUrlSuffix + groupUid,
                getMessage(thisSection, startMenu, "group.options.inactive", user));
        thisMenu.addMenuOption(groupMenus + "merge" + groupUidUrlSuffix + groupUid,
                getMessage(thisSection, startMenu, "group.options.merge", user));
        thisMenu.addMenuOption(startMenu + "_force", getMessage(thisSection, startMenu, "interrupted.start", user));

        return thisMenu;
    }

    private USSDMenu assemblePanicButtonActivationMenu(User user) {
        USSDMenu menu;
        if (user.hasSafetyGroup()) {
            boolean isBarred = safetyEventBroker.isUserBarred(user.getUid());
            String message = (!isBarred) ? getMessage(thisSection, "safety.activated", promptKey, user) : getMessage(thisSection, "safety.barred", promptKey, user);
            if (!isBarred) safetyEventBroker.create(user.getUid(), user.getSafetyGroup().getUid());
            menu = new USSDMenu(message);
        } else {
            menu = new USSDMenu(getMessage(thisSection, "safety.not-activated", promptKey, user));
            if (groupQueryBroker.fetchUserCreatedGroups(user, 0, 1).getTotalElements() != 0) {
                menu.addMenuOption(safetyMenus + "pick-group", getMessage(thisSection, "safety", optionsKey + "existing", user));
            }
            menu.addMenuOption(safetyMenus + "new-group", getMessage(thisSection, "safety", optionsKey + "new", user));
            menu.addMenuOption(startMenu, getMessage(optionsKey + "back.main", user));
        }
        return menu;
    }

    private USSDMenu assemblePanicButtonActivationResponse(User user, SafetyEvent safetyEvent) {
        String activateByDisplayName = safetyEvent.getActivatedBy().getDisplayName();
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "safety.responder", promptKey, activateByDisplayName, user));
        menu.addMenuOptions(optionsYesNo(user, USSDUrlUtil.safetyMenuWithId("record-response", safetyEvent.getUid())));
        return menu;
    }

    private USSDMenu assembleSendMeAndroidLinkMenu(User user) {
        userManager.sendAndroidLinkSms(user.getUid());
        String message = getMessage(thisSection, "link.android", promptKey, user);
        return new USSDMenu(message, optionsHomeExit(user));
    }

    /*
    Menus to process responses to votes and RSVPs,
     */

    @RequestMapping(value = path + rsvpMenu)
    @ResponseBody
    public Request rsvpAndWelcome(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(value = entityUidParam) String meetingUid,
                                  @RequestParam(value = yesOrNoParam) String attending) throws URISyntaxException {

        String welcomeKey;
        User user = userManager.loadOrCreateUser(inputNumber);
        Meeting meeting = eventBroker.loadMeeting(meetingUid);

        if ("yes".equals(attending)) {
            eventLogBroker.rsvpForEvent(meeting.getUid(), user.getUid(), EventRSVPResponse.YES);
            welcomeKey = String.join(".", Arrays.asList(homeKey, startMenu, promptKey, "rsvp-yes"));
        } else {
            eventLogBroker.rsvpForEvent(meeting.getUid(), user.getUid(), EventRSVPResponse.NO);
            welcomeKey = String.join(".", Arrays.asList(homeKey, startMenu, promptKey, "rsvp-no"));
        }

        return menuBuilder(new USSDMenu(getMessage(welcomeKey, user), optionsHomeExit(user)));

    }

    @RequestMapping(value = path + "vote")
    @ResponseBody
    public Request voteAndWelcome(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(value = entityUidParam) String voteUid,
                                  @RequestParam(value = "response") String response) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        eventLogBroker.rsvpForEvent(voteUid, user.getUid(), EventRSVPResponse.fromString(response));
        String prompt = getMessage(thisSection, startMenu, promptKey + ".vote-recorded", user);
        return menuBuilder(new USSDMenu(prompt, optionsHomeExit(user)));
    }

    @RequestMapping(value = path + "todo-complete")
    @ResponseBody
    public Request todoEntryMarkComplete(@RequestParam(value = phoneNumber) String inputNumber,
                                @RequestParam(value = entityUidParam) String todoUid,
                                @RequestParam(value = yesOrNoParam) String response) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        TodoCompletionConfirmType type = "yes".equals(response) ? TodoCompletionConfirmType.COMPLETED
                : "no".equals(response) ? TodoCompletionConfirmType.NOT_COMPLETED : TodoCompletionConfirmType.NOT_KNOWN;

        boolean stateChanged = todoBroker.confirmCompletion(user.getUid(), todoUid, type, LocalDateTime.now());
        final String prompt = (!"yes".equals(response)) ? getMessage(thisSection, startMenu, promptKey + ".todo-marked-no", user) :
                !stateChanged ?  getMessage(thisSection, startMenu, promptKey + ".todo-unchanged", user)
                        : getMessage(thisSection, startMenu, promptKey + ".todo-completed", user);

        return menuBuilder(welcomeMenu(prompt, user));
    }

    @RequestMapping(value = path + renameUserMenu)
    @ResponseBody
    public Request renameAndStart(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(value = userInputParam) String userName) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);
        String welcomeMessage;
        if ("0".equals(userName) || "".equals(userName.trim())) {
            welcomeMessage = getMessage(thisSection, startMenu, promptKey, sessionUser);
            userLogger.recordUserLog(sessionUser.getUid(), UserLogType.USER_SKIPPED_NAME, "");
        } else {
            userManager.updateDisplayName(sessionUser.getUid(), userName);
            welcomeMessage = getMessage(thisSection, startMenu, promptKey + "-rename-do", sessionUser.nameToDisplay(), sessionUser);
        }
        return menuBuilder(welcomeMenu(welcomeMessage, sessionUser));
    }

    @RequestMapping(value = path + renameGroupAndStart)
    @ResponseBody
    public Request groupNameAndStart(@RequestParam(value = phoneNumber) String inputNumber,
                                     @RequestParam(value = groupUidParam) String groupUid,
                                     @RequestParam(value = userInputParam) String groupName) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        String welcomeMessage;
        if ("0".equals(groupName) || "".equals(groupName.trim())) {
            welcomeMessage = getMessage(thisSection, startMenu, promptKey, user);
            userLogger.recordUserLog(user.getUid(), UserLogType.USER_SKIPPED_NAME, groupUid);
        } else {
            groupBroker.updateName(user.getUid(), groupUid, groupName);
            welcomeMessage = getMessage(thisSection, startMenu, promptKey + "-group-do", user.nameToDisplay(), user);
        }

        return menuBuilder(welcomeMenu(welcomeMessage, user));

    }

    @RequestMapping(value = path + promptGroupRename)
    @ResponseBody
    public Request askForGroupName(@RequestParam(value = phoneNumber) String inputNumber,
                                   @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {
        Group group = groupBroker.load(groupUid);
        return menuBuilder(renameGroupNoInactiveOption(userManager.findByInputNumber(inputNumber), groupUid,
                dateFormat.format(convertToUserTimeZone(group.getCreatedDateTime(), getSAST()))));
    }

    @RequestMapping(value = path + promptConfirmGroupInactive)
    @ResponseBody
    public Request confirmGroupInactive(@RequestParam(value = phoneNumber) String inputNumber,
                                        @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        Group group = groupBroker.load(groupUid);
        String sizeOfGroup = "" + (group.getMembers().size() - 1); // subtracting the group creator
        String optionsPrefix = thisSection.toKey() + "group.inactive." + optionsKey;

        USSDMenu thisMenu = new USSDMenu(getMessage(thisSection, "group", "inactive." + promptKey, sizeOfGroup, user));
        thisMenu.addMenuOption(promptConfirmGroupInactive + doSuffix + groupUidUrlSuffix + groupUid,
                getMessage(optionsPrefix + "confirm", user));
        thisMenu.addMenuOption(groupMenus + "merge" + groupUidUrlSuffix + groupUid,
                getMessage(optionsPrefix + "merge", user));
        thisMenu.addMenuOption(startMenu + "_force", getMessage(optionsPrefix + "cancel", user));

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = path + promptConfirmGroupInactive + doSuffix)
    @ResponseBody
    public Request setGroupInactiveAndStart(@RequestParam(value = phoneNumber) String inputNumber,
                                            @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {
        User sessionUser = userManager.findByInputNumber(inputNumber);
        log.info("At the request of user: " + sessionUser + ", we are setting inactive this group ... " + groupUid);
        groupBroker.deactivate(sessionUser.getUid(), groupUid, true);
        String welcomeMessage = getMessage(thisSection, "group", "inactive." + promptKey + ".done", sessionUser);
        return menuBuilder(welcomeMenu(welcomeMessage, sessionUser));
    }

    /*
    Helper methods, for group pagination, event pagination, etc.
     */

    @RequestMapping(value = path + "group_page")
    @ResponseBody
    public Request groupPaginationHelper(@RequestParam(value = phoneNumber) String inputNumber,
                                         @RequestParam(value = "prompt") String prompt,
                                         @RequestParam(value = "page") Integer pageNumber,
                                         @RequestParam(value = "existingUri") String existingUri,
                                         @RequestParam(value = "section", required = false) USSDSection section,
                                         @RequestParam(value = "newUri", required = false) String newUri) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        if (SAFETY_GROUP_MANAGER.equals(section)) {
            return menuBuilder(ussdGroupUtil.showUserCreatedGroupsForSafetyFeature(user, SAFETY_GROUP_MANAGER, existingUri, pageNumber));
        } else {
            return menuBuilder(ussdGroupUtil.userGroupMenuPaginated(user, prompt, existingUri, newUri, pageNumber, null, section));
        }
    }

    @RequestMapping(value = path + "event_page")
    @ResponseBody
    public Request eventPaginationHelper(@RequestParam(value = phoneNumber) String inputNumber,
                                         @RequestParam(value = "section") String section,
                                         @RequestParam(value = "prompt") String prompt,
                                         @RequestParam(value = "newMenu", required = false) String menuForNew,
                                         @RequestParam(value = "newOption", required = false) String optionForNew,
                                         @RequestParam(value = "page") Integer pageNumber,
                                         @RequestParam(value = "nextUrl") String nextUrl,
                                         @RequestParam(value = "pastPresentBoth") Integer pastPresentBoth,
                                         @RequestParam(value = "includeGroupName") boolean includeGroupName) throws URISyntaxException {
        // todo: error handling on the section and switch to time type on the integer
        EventListTimeType timeType = pastPresentBoth == 1 ? EventListTimeType.FUTURE : EventListTimeType.PAST;
        return menuBuilder(eventUtil.listPaginatedEvents(
                userManager.findByInputNumber(inputNumber), fromString(section),
                prompt, nextUrl, (menuForNew != null), menuForNew, optionForNew, includeGroupName, timeType, pageNumber));
    }

    @RequestMapping(value = path + U404)
    @ResponseBody
    public Request notBuilt(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        String errorMessage = messageSource.getMessage("ussd.error", null, new Locale("en"));
        return menuBuilder(new USSDMenu(errorMessage, optionsHomeExit(userManager.findByInputNumber(inputNumber))));
    }

    @RequestMapping(value = path + "exit")
    @ResponseBody
    public Request exitScreen(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, null);
        String exitMessage = getMessage("exit." + promptKey, user);
        return menuBuilder(new USSDMenu(exitMessage));
    }

    @RequestMapping(value = path + "test_question")
    @ResponseBody
    public Request question1() throws URISyntaxException {
        final Option option = new Option("Yes I can!", 1, 1, new URI("http://yourdomain.tld/ussdxml.ashx?file=2"), true);
        return new Request("Can you answer the question?", Collections.singletonList(option));
    }

    @RequestMapping(value = path + "too_long")
    @ResponseBody
    public Request tooLong() throws URISyntaxException {
        return tooLongError;
    }


}