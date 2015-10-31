package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Created by luke on 2015/10/30.
 */
@Controller
public class VoteController extends BaseController {

    Logger log = LoggerFactory.getLogger(VoteController.class);

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    GroupManagementService groupManagementService;

    @RequestMapping("/vote/create")
    public String createVote(Model model, @RequestParam(value="groupId", required = false) Long groupId) {

        boolean groupSpecified = (groupId != null);
        User user = getUserProfile();
        Event vote = eventManagementService.createVote(user);

        if (groupSpecified) {
            model.addAttribute("group", groupManagementService.loadGroup(groupId));
            vote = eventManagementService.setGroup(vote.getId(), groupId);
        } else {
            model.addAttribute("possibleGroups", groupManagementService.groupsOnWhichCanCallVote(user));
        }

        model.addAttribute("vote", vote);
        model.addAttribute("groupSpecified", groupSpecified);

        log.info("Vote that we are passing: " + vote);

        return "vote/create";
    }

    @RequestMapping(value = "/vote/create", method = RequestMethod.POST)
    public String createVoteDo(Model model, @ModelAttribute("vote") Event vote, BindingResult bindingResult,
                               @RequestParam(value = "selectedGroupId", required = false) Long selectedGroupId,
                               HttpServletRequest request, RedirectAttributes redirectAttributes) {

        log.info("Vote passed back to us: " + vote);

        vote = eventManagementService.updateEvent(vote);
        if (selectedGroupId != null) { eventManagementService.setGroup(vote, selectedGroupId); }

        log.info("Stored vote, at end of creation: " + vote.toString());

        addMessage(model, MessageType.SUCCESS, "vote.creation.success", request);
        model.addAttribute("eventId", vote.getId());
        return "vote/view";
    }

    @RequestMapping("/vote/view")
    public String viewVote(Model model, @RequestParam(value="eventId") Long eventId) {

        Event vote = eventManagementService.loadEvent(eventId);

        Map<String, Integer> voteResults = eventManagementService.getVoteResults(vote);

        model.addAttribute("vote", vote);
        model.addAttribute("yes", voteResults.get("yes"));
        model.addAttribute("no", voteResults.get("no"));
        model.addAttribute("abstained", voteResults.get("abstained"));
        model.addAttribute("possible", voteResults.get("possible"));
        return "vote/view";
    }

}