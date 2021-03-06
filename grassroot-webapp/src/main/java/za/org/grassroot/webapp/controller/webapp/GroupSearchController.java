package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.services.*;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.exception.RequestorAlreadyPartOfGroupException;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.PublicGroupWrapper;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by luke on 2016/09/06.
 */
@Controller
@RequestMapping("/group/")
public class GroupSearchController extends BaseController {

	private static final Logger log = LoggerFactory.getLogger(GroupSearchController.class);
	private static final Pattern tokenPattern = Pattern.compile("[^a-zA-Z\\s]+");

	@Value("${grassroot.ussd.dialcode:*134*1994*}")
	private String ussdDialCode;

	@Autowired
	private GroupBroker groupBroker;

	@Autowired
	private GroupQueryBroker groupQueryBroker;

	@Autowired
	private TaskBroker taskBroker;

	@Autowired
	private GroupJoinRequestService groupJoinRequestService;

	@Autowired
	private AsyncUserLogger userLogger;


	@RequestMapping(value = "/search")
	public String searchForGroup(Model model, @RequestParam String term, HttpServletRequest request) {

		boolean resultFound = false;
		if (term.isEmpty()) {
			addMessage(model, BaseController.MessageType.ERROR, "search.error.empty", request);
		} else {
			boolean onlyDigits = tokenPattern.matcher(term.trim()).find();
			if (onlyDigits && !userLogger.hasUsedJoinCodeRecently(getUserProfile().getUid())) {
				String tokenSearch = term.contains(ussdDialCode) ? term.substring(ussdDialCode.length(), term.length() - 1) : term;
				log.info("searching for group ... token to use ... " + tokenSearch);
				Optional<Group> groupByToken = groupQueryBroker.findGroupFromJoinCode(tokenSearch);
				if (groupByToken.isPresent()) {
					model.addAttribute("group", groupByToken.get());
					resultFound = true;
				}
			} else {
				// just for testing since no UI support yet exists...
				// GroupLocationFilter locationFilter = new GroupLocationFilter(new GeoLocation(45.567641, 18.701211), 30000, true);
				GroupLocationFilter locationFilter = null;
				List<PublicGroupWrapper> publicGroups = groupQueryBroker.findPublicGroups(getUserProfile().getUid(), term, locationFilter, false)
						.stream().map(g -> new PublicGroupWrapper(g, getMessage("search.group.desc"))).collect(Collectors.toList());

				model.addAttribute("groupCandidates", publicGroups);

				final String userUid = getUserProfile().getUid();
				List<Group> memberGroups = groupQueryBroker.searchUsersGroups(userUid, term);
				List<TaskDTO> memberTasks = taskBroker.searchForTasks(userUid, term);
				model.addAttribute("foundGroups", memberGroups);
				model.addAttribute("foundTasks", memberTasks);

				resultFound = !publicGroups.isEmpty() || !memberGroups.isEmpty() || !memberTasks.isEmpty();
			}
		}

		model.addAttribute("resultFound", resultFound);
		return "group/results";
	}

	@RequestMapping(value = "join/request", method = RequestMethod.POST)
	public String requestToJoinGroup(Model model, @RequestParam(value="uid") String groupToJoinUid,
	                                 @RequestParam(value="description", required = false) String description,
	                                 HttpServletRequest request, RedirectAttributes attributes) {
		// in case modal goes weird on old / new / etc browsers (because CSS/JS)
		if ("error".equals(groupToJoinUid)) {
			addMessage(attributes, MessageType.ERROR, "group.join.request.error", request);
			return "redirect:/home";
		} else {
			try {
				groupJoinRequestService.open(getUserProfile().getUid(), groupToJoinUid, description);
				addMessage(attributes, MessageType.INFO, "group.join.request.done", request);
				return "redirect:/home";
			} catch (RequestorAlreadyPartOfGroupException e) {
				addMessage(attributes, MessageType.INFO, "group.join.request.member", request);
				attributes.addAttribute("groupUid", groupToJoinUid);
				return "redirect:/group/view";
			}
		}
	}

	@RequestMapping(value = "join/approve")
	public String approveJoinRequest(RedirectAttributes attributes, @RequestParam String requestUid, HttpServletRequest request) {
		// note: join request service will do the permission checking etc and throw an error before proceeding
		groupJoinRequestService.approve(getUserProfile().getUid(), requestUid);
		addMessage(attributes, MessageType.INFO, "group.join.request.approved", request);
		attributes.addAttribute("groupUid", groupJoinRequestService.loadRequest(requestUid).getGroup().getUid());
		return "redirect:/group/view";
	}

	@RequestMapping(value = "join/decline")
	public String declineJoinRequest(@RequestParam String requestUid, HttpServletRequest request, RedirectAttributes attributes) {
		groupJoinRequestService.decline(getUserProfile().getUid(), requestUid);
		addMessage(attributes, MessageType.INFO, "group.join.request.declined", request);
		return "redirect:/home"; // no point showing group if decline request, want to get on with life
	}

	@RequestMapping(value = "join/token", method = RequestMethod.POST)
	public String joinGroup(RedirectAttributes attributes, @RequestParam String groupUid, @RequestParam String token, HttpServletRequest request) {
		groupBroker.addMemberViaJoinCode(getUserProfile().getUid(), groupUid, token);
		addMessage(attributes, MessageType.SUCCESS, "group.join.success", request);
		attributes.addAttribute("groupUid", groupUid);
		return "redirect:/group/view";
	}


}
