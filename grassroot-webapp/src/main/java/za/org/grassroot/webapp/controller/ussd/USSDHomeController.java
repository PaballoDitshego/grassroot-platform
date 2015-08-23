package za.org.grassroot.webapp.controller.ussd;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.UserManager;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;


import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * Controller for the USSD menu
 * todo: abstract out the messages, so can introduce a dictionary mechanism of some sort to deal with languages
 * todo: avoid hard-coding the URLs in the menus, so we can swap them around later
 * todo: create mini-routines of common menu flows (e.g., create a group) so they can be inserted in multiple flows
 * todo: Check if responses are less than 140 characters before sending
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDHomeController extends USSDController {

    private static final String keyRenameStart = "rename-start", keyGroupNameStart = "group-start";

    public USSDMenu welcomeMenu(String opening, User sessionUser) throws URISyntaxException {

        USSDMenu homeMenu = new USSDMenu(opening);
        Locale menuLang = new Locale(getLanguage(sessionUser));

        homeMenu.addMenuOption(MTG_MENUS + START_KEY, getMessage(HOME_KEY, START_KEY, OPTION + MTG_KEY, menuLang));
        homeMenu.addMenuOption(VOTE_MENUS, getMessage(HOME_KEY, START_KEY, OPTION + VOTE_KEY, menuLang));
        homeMenu.addMenuOption(LOG_MENUS, getMessage(HOME_KEY, START_KEY, OPTION + LOG_KEY, menuLang));
        homeMenu.addMenuOption(GROUP_MENUS + START_KEY, getMessage(HOME_KEY, START_KEY, OPTION + GROUP_KEY, menuLang));
        homeMenu.addMenuOption(USER_MENUS + START_KEY, getMessage(HOME_KEY, START_KEY, OPTION + USER_KEY, menuLang));

        System.out.println("Menu size: " + homeMenu.getMenuCharLength());

        return homeMenu;
    }

    @RequestMapping(value = USSD_BASE + START_KEY)
    @ResponseBody
    public Request startMenu(@RequestParam(value=PHONE_PARAM) String inputNumber) throws URISyntaxException {

        USSDMenu startMenu = new USSDMenu("");
        User sessionUser = userManager.loadOrSaveUser(inputNumber);

        if (sessionUser.needsToRenameSelf(10)) {
            startMenu.setPromptMessage(getMessage(HOME_KEY, START_KEY, PROMPT + "-rename", sessionUser));
            startMenu.setFreeText(true);
            startMenu.setNextURI(keyRenameStart);
        } else if (sessionUser.needsToRenameGroup() != null) {
            startMenu.setPromptMessage(getMessage(HOME_KEY, START_KEY, PROMPT + "-group-rename", sessionUser));
            startMenu.setFreeText(true);
            startMenu.setNextURI(keyGroupNameStart + GROUPID_URL + sessionUser.needsToRenameGroup().getId());
        } else {
            String welcomeMessage = sessionUser.hasName() ?
                    getMessage(HOME_KEY, START_KEY, PROMPT + "-named", sessionUser.getName(""), sessionUser) :
                    getMessage(HOME_KEY, START_KEY, PROMPT, sessionUser);
            startMenu = welcomeMenu(welcomeMessage, sessionUser);
        }

        return (checkMenuLength(startMenu, true)) ? menuBuilder(startMenu) : tooLongError;

    }

    @RequestMapping(value = USSD_BASE + keyRenameStart)
    @ResponseBody
    public Request renameAndStart(@RequestParam(value=PHONE_PARAM) String inputNumber,
                                  @RequestParam(value=TEXT_PARAM) String userName) throws URISyntaxException {

        User sessionUser = userManager.loadOrSaveUser(inputNumber);
        sessionUser.setDisplayName(userName);
        sessionUser = userManager.save(sessionUser);

        return menuBuilder(welcomeMenu(getMessage(HOME_KEY, START_KEY, PROMPT + "-rename-do", sessionUser.getName(""),
                                                  sessionUser), sessionUser));
    }

    @RequestMapping(value = USSD_BASE + keyGroupNameStart)
    @ResponseBody
    public Request groupNameAndStart(@RequestParam(value=PHONE_PARAM) String inputNumber,
                                     @RequestParam(value=GROUP_PARAM) Long groupId,
                                     @RequestParam(value=TEXT_PARAM) String groupName) throws URISyntaxException {

        // todo: use permission model to check if user can actually do this

        User sessionUser = userManager.loadOrSaveUser(inputNumber);
        Group groupToRename = groupManager.loadGroup(groupId);
        groupToRename.setGroupName(groupName);
        groupToRename = groupManager.saveGroup(groupToRename);

        // return menuBuilder(welcomeMenu("Thanks! Now what do you want to do?", sessionUser));
        return menuBuilder(welcomeMenu(getMessage(HOME_KEY, START_KEY, PROMPT + "-group-done", sessionUser.getName(""),
                                                  sessionUser), sessionUser));

    }

    @RequestMapping(value = { USSD_BASE + U404, USSD_BASE + VOTE_MENUS, USSD_BASE + LOG_MENUS, USSD_BASE + GROUP_MENUS + "menu2" })
    @ResponseBody
    public Request notBuilt() throws URISyntaxException {
        // String errorMessage = "Sorry! We haven't built that yet. We're working on it.";
        String errorMessage = messageSource.getMessage("ussd.error", null, new Locale("en"));
        return new Request(errorMessage, new ArrayList<Option>());
    }

    @RequestMapping(value = USSD_BASE + "exit")
    @ResponseBody
    public Request exitScreen(@RequestParam(value=PHONE_PARAM) String inputNumber) throws URISyntaxException {
        String exitMessage = getMessage("exit." + PROMPT, userManager.loadOrSaveUser(inputNumber));
        return menuBuilder(new USSDMenu(exitMessage)); // todo: check if methods can handle empty list of options
    }

    @RequestMapping(value = USSD_BASE + "test_question")
    @ResponseBody
    public Request question1() throws URISyntaxException {
        final Option option = new Option("Yes I can!", 1,1, new URI("http://yourdomain.tld/ussdxml.ashx?file=2"),true);
        return new Request("Can you answer the question?", Collections.singletonList(option));
    }

    @RequestMapping(value = USSD_BASE + "too_long")
    @ResponseBody
    public Request tooLong() throws URISyntaxException {
        return tooLongError;
    }


}
