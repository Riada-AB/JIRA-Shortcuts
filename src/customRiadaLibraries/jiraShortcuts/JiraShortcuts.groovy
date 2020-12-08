package customRiadaLibraries.jiraShortcuts

import com.atlassian.jira.bc.issue.IssueService
import com.atlassian.jira.bc.issue.link.IssueLinkService
import com.atlassian.jira.bc.issue.search.SearchService
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.config.IssueTypeManager
import com.atlassian.jira.config.PriorityManager
import com.atlassian.jira.event.type.EventDispatchOption
import com.atlassian.jira.issue.CustomFieldManager
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueInputParameters
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.link.IssueLink
import com.atlassian.jira.issue.link.IssueLinkManager
import com.atlassian.jira.issue.link.IssueLinkType
import com.atlassian.jira.issue.priority.Priority
import com.atlassian.jira.issue.search.SearchException
import com.atlassian.jira.issue.search.SearchResults
import com.atlassian.jira.project.Project
import com.atlassian.jira.project.ProjectManager
import com.atlassian.jira.security.JiraAuthenticationContext
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.web.bean.PagerFilter
import com.atlassian.servicedesk.api.NoSuchEntityException
import com.atlassian.servicedesk.api.ServiceDeskManager
import com.atlassian.servicedesk.api.requesttype.RequestType
import com.atlassian.servicedesk.api.requesttype.RequestTypeService
import com.onresolve.scriptrunner.runner.customisers.PluginModule
import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import org.apache.commons.lang3.math.NumberUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger


class JiraShortcuts {


    @WithPlugin("com.atlassian.servicedesk")
    @PluginModule
    ServiceDeskManager serviceDeskManager
    @PluginModule
    RequestTypeService requestTypeService

    ProjectManager projectManager = ComponentAccessor.getProjectManager()
    IssueManager issueManager = ComponentAccessor.getIssueManager()
    IssueService issueService = ComponentAccessor.getIssueService()
    PriorityManager priorityManager = ComponentAccessor.getComponentOfType(PriorityManager)
    JiraAuthenticationContext authenticationContext = ComponentAccessor.getJiraAuthenticationContext()
    CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager()
    IssueTypeManager issueTypeManager = ComponentAccessor.getComponentOfType(IssueTypeManager)
    IssueLinkManager issueLinkManager = ComponentAccessor.getIssueLinkManager()
    IssueLinkService issueLinkService = ComponentAccessor.getComponentOfType(IssueLinkService)
    SearchService searchService = ComponentAccessor.getComponentOfType(SearchService)


    Logger log = Logger.getLogger(JiraShortcuts)

    public ApplicationUser serviceUser


    JiraShortcuts() {

        this.log.setLevel(Level.ALL)
        log.info("JiraShortcuts has started")

        serviceUser = authenticationContext.getLoggedInUser()
    }


    ArrayList<Issue> jql(String jql) {

        log.info("Will run JQL query:" + jql)
        SearchService.ParseResult parseResult = searchService.parseQuery(serviceUser, jql)

        if (!parseResult.valid) {
            String errorMsg = "The supplied JQL is invalid:" + jql + ". Error:" + parseResult.errors.errorMessages.join(",")
            log.error(errorMsg)
            throw new SearchException(errorMsg)
        }

        SearchResults searchResults = searchService.search(serviceUser, parseResult.query, PagerFilter.getUnlimitedFilter())
        return searchResults.getResults().collect { issueManager.getIssueByCurrentKey(it.key) }

    }


    private IssueInputParameters prepareIssueInput(Map issueParameters, Map customFieldValues) {

        String parameterValidationResult = validateIssueParameterMap(issueParameters)

        if (parameterValidationResult != null) {
            throw new InputMismatchException(parameterValidationResult)
        }

        long projectId = projectManager.getProjectByCurrentKey(issueParameters.projectKey as String)?.id
        String issueTypeId = issueTypeManager.getIssueTypes().find { it.name == issueParameters.issueTypeName }?.id


        IssueInputParameters issueInputParameters = issueService.newIssueInputParameters()


        issueInputParameters.setProjectId(projectId)
        issueInputParameters.setIssueTypeId(issueTypeId)
        issueInputParameters.setSummary(issueParameters.summary as String)
        if (issueParameters.containsKey("description")) {
            issueInputParameters.setDescription(issueParameters.description as String)
        }

        if (issueParameters.containsKey("priority")) {
            Priority priority

            if (NumberUtils.isParsable(issueParameters.priority as String) ) {
                priority = priorityManager.getPriority(issueParameters.priority as String)
            }else {
                priority = priorityManager.getPriorities().find {it.name == issueParameters.priority}
            }

            if (priority == null) {
                throw new InputMismatchException("Could not find Priority:" + issueParameters.priority)
            }
            issueInputParameters.setPriorityId(priority.id)
        }

        try {
            customFieldValues.each {

                String value
                if ([ArrayList, List].contains(it.value.class)) {
                    value = it.value.join(",")
                } else {
                    value = it.value
                }
                issueInputParameters.addCustomFieldValue(it.key, value)
            }

        } catch (all) {
            log.error(all.message)
            throw all
        }


        return issueInputParameters

    }

    static String validateIssueParameterMap(Map parameters) {

        ArrayList<String> requiredKeys = ["projectKey", "issueTypeName", "summary"]
        ArrayList<String> missingKeys = requiredKeys.findAll { requiredKey -> !parameters.containsKey(requiredKey) }

        if (!missingKeys.empty) {
            return "Issue input parameter map is missing:" + missingKeys.join(",")
        }


        return null

    }

    /**
     * Get the requestType object needed for setting the "Customer Request Type" field value
     * @param projectKey The project where the request type is located
     * @param requestTypeName Name of the request type
     * @return an VpOrigin that can be written to the field.
     */
    def getRequestTypeFieldValue(String projectKey, String requestTypeName) {

        CustomField requestTypeField = customFieldManager.getCustomFieldObjectsByName("Customer Request Type").first()
        Project project = projectManager.getProjectByCurrentKey(projectKey)
        Integer portalId = serviceDeskManager.getServiceDeskForProject(projectManager.getProjectObj(project.id)).id


        RequestType requestType = requestTypeService.getRequestTypes(serviceUser, requestTypeService.newQueryBuilder().serviceDesk(portalId).build()).find { it.name == requestTypeName }
        def newRequestType = requestTypeField.getCustomFieldType().getSingularObjectFromString(projectKey.toLowerCase() + "/" + requestType.key)


        return newRequestType

    }
    /**
     * Get the RequestType object of an issue
     * @param issue The issue to get the request type from
     * @return The request type or null
     */
    RequestType getIssueRequestType(Issue issue) {

        ArrayList<RequestType> requestTypes = []


        try {
            requestTypes = requestTypeService.getRequestTypes(serviceUser, requestTypeService.newQueryBuilder().issue(issue.id).build()).results

        }catch(NoSuchEntityException ex) {

            if (ex.message == "The Service Desk you are trying to view does not exist.") {
                log.warn("Could not get request type for issue $issue as it does not belong to a ServiceDesk project")
            }else if (ex.message == "The request type you are trying to view does not exist."){
                log.warn("Could not get request type for issue $issue as it does not have one")
            }else {
                log.warn("MESS:" + ex.message + ":END")
                throw ex
            }

        }

        if (requestTypes.isEmpty()) {
            return null
        } else {
            return requestTypes.first()
        }

    }

    /**
     * A method for creating issue links between two issues
     * @param sourceIssue This issue will get the "Outward Link"
     * @param destinationIssue This issue will get the "Inward Link"
     * @param issueLinkTypeName The name of the issue link type, ex: Blocks, Cloners, Duplicate
     * @return the created IssueLink
     */
    IssueLink createIssueLink(Issue sourceIssue, Issue destinationIssue, String issueLinkTypeName) {

        log.info("Will create an issue link ($issueLinkTypeName) between the Source Issue: ${sourceIssue.key} and Destination Issue: ${destinationIssue.key}")

        IssueLinkType issueLinkType = issueLinkService.getIssueLinkTypes().find { it.name == issueLinkTypeName }

        if (issueLinkType == null) {
            log.error("Could not find an Issue link type with name:" + issueLinkTypeName)
            log.debug("The Available Issue link types are:" + issueLinkService.getIssueLinkTypes().name.join(","))
            throw new InputMismatchException("Could not find an Issue link type with name:" + issueLinkTypeName)
        }

        log.debug("\tDetermined issueLinkType ID to be:" + issueLinkType.id)
        log.debug("\tThe Source Issue (${sourceIssue.key}) will get the Outward Link Description:" + issueLinkType.outward)
        log.debug("\tThe Destination Issue (${destinationIssue.key}) will get the Inward Link Description:" + issueLinkType.inward)

        issueLinkManager.createIssueLink(sourceIssue.id, destinationIssue.id, issueLinkType.id, 1 as Long, this.serviceUser)

        IssueLink theNewIssueLink = issueLinkManager.getIssueLink(sourceIssue.id, destinationIssue.id, issueLinkType.id)

        if (theNewIssueLink == null) {
            throw new NullPointerException("There was an error creating an an issue link ($issueLinkTypeName) between the Source Issue: ${sourceIssue.key} and Destination Issue: ${destinationIssue.key}")
        }

        log.info("\tIssue link successfully created")
        return theNewIssueLink


    }

    /**
     *  A method for creating new Service Desk Requests<br>
     *  Example:<br>
     *  createServiceDeskRequest("IT Help", [projectKey: "JIP", issueTypeName: "IT Help", summary: "This is the summary"], ["customfield_10303": "UTS-67, UTS-132"] )<br>
     *
     *
     *
     * @param requestName Name of the Service Desk Request you want to create
     * @param issueParameters The basic parameters of an issue
     *  <ul>
     *      <li><b>Must contain:</b> projectKey, issueTypeName, summary</li>
     *      <li><b>May contain:</b> description, priority</li>
     *  </ul>
     * @param customfieldValues a map where the key is the ID of a field and the map value is the value that is to be set in that field. For example:
     * <ul>
     *     <li>["customfield_10303": "UTS-67, UTS-132"]</li>
     *     <li>[10303: ["UTS-67", "UTS-132"]</li>
     *  </ul>
     * @return the created Issue
     *
     *
     */
    Issue createServiceDeskRequest(String requestName, Map issueParameters, Map customfieldValues) {

        MutableIssue newIssue
        CustomField requestTypeField = customFieldManager.getCustomFieldObjectsByName("Customer Request Type").first()

        log.info("Will create new ServiceDesk Request with the following input issueParameters:")
        issueParameters.each { log.info(it.key + ":" + it.value) }

        IssueInputParameters issueInputParameters = prepareIssueInput(issueParameters, customfieldValues)
        IssueService.CreateValidationResult createValidationResult = issueService.validateCreate(serviceUser, issueInputParameters)

        if (createValidationResult.isValid()) {
            log.debug("\tThe issue issueParameters appears valid, will now create issue")
            IssueService.IssueResult issueResult = issueService.create(serviceUser, createValidationResult)

            if (issueResult.isValid()) {

                newIssue = issueResult.issue

                log.debug("\tSuccessfully created issue:" + newIssue.key)

                log.debug("\tSetting request type to:" + requestName)
                def requestType = getRequestTypeFieldValue(issueParameters.projectKey as String, requestName)
                newIssue.setCustomFieldValue(requestTypeField, requestType)
                newIssue = issueManager.updateIssue(serviceUser, newIssue, EventDispatchOption.ISSUE_UPDATED, false) as MutableIssue

                return newIssue

            } else {

                log.error("There was an error when creating the issue")
                return null
            }

        } else {

            log.error("There was an error when validating the input issueParameters")
            createValidationResult.errorCollection.each {
                log.debug(it.toString())
            }
            return null

        }

    }


}
