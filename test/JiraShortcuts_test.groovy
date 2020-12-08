import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.CustomFieldManager
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.link.IssueLink
import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import com.riadalabs.jira.plugins.insight.services.model.ObjectBean
import customRiadaLibraries.jiraShortcuts.JiraShortcuts
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.junit.runner.JUnitCore
import org.junit.runner.Request
import org.junit.runner.Result
import spock.lang.Shared
import spock.lang.Specification

@WithPlugin("com.riadalabs.jira.plugins.insight")

Logger log = Logger.getLogger(JiraShortcutsSpec)
log.setLevel(Level.ALL)

JUnitCore jUnitCore = new JUnitCore()
Result spockResult = jUnitCore.run(Request.method(JiraShortcutsSpec.class, "Verifying createRequest"))
//Result spockResult = jUnitCore.run(JiraShortcutsSpec)


spockResult.failures.each { log.error(it) }

log.debug("Test was successful:" + spockResult.wasSuccessful())

class JiraShortcutsSpec extends Specification {


    @Shared
    Logger log = Logger.getLogger(this.class)

    @Shared
    CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager()


    def setupSpec() {

        log.setLevel(Level.ALL)
        log.warn("Started")

    }

    def "Verify JQL"() {

        setup:
        log.info("*" * 20 + "Testing JQL" + "*")
        String jql = "project = \"JIP\""

        log.debug("\tUsing JQL:" + jql)
        JiraShortcuts jiraShortcuts = new JiraShortcuts()

        when:
        ArrayList<Issue> jqlResult = jiraShortcuts.jql(jql)
        log.debug("\tReturned ${jqlResult.size()} issues")
        log.trace("\t" * 2 + "Issues:" + jqlResult.join(","))

        then:
        !jqlResult.empty
        jqlResult.first() instanceof Issue

    }


    def "Verifying createRequest"(String requestName, Map issueParameters, Map setCustomFieldValues, Map<String, ArrayList<String>> expectedCustomFieldValues) {

        setup:
        JiraShortcuts jiraShortcuts = new JiraShortcuts()


        when:
        Issue issue = jiraShortcuts.createServiceDeskRequest(requestName, issueParameters, setCustomFieldValues)

        then:

        issue.projectObject.key == issueParameters.projectKey
        issue.issueType.name == issueParameters.issueTypeName
        issue.summary == issueParameters.summary
        issue.description == issueParameters?.description

        if (!expectedCustomFieldValues.isEmpty()) {
            expectedCustomFieldValues.each { customFieldId, expectedFieldValue ->

                CustomField customField = customFieldManager.getCustomFieldObject(customFieldId)

                def fieldValue = issue.getCustomFieldValue(customField)

                if (fieldValue instanceof ArrayList && fieldValue.first() == ObjectBean) {

                    fieldValue.objectKey == expectedFieldValue
                } else {

                    fieldValue == expectedFieldValue
                }


            }
        }


        where:
        requestName            | issueParameters                                                                                                | setCustomFieldValues                         | expectedCustomFieldValues
        "Computer support"     | [projectKey: "JIP", issueTypeName: "IT Help", summary: "Standard customfield notation"]                        | ["customfield_10303": "UTS-67, UTS-132"]     | ["customfield_10303": ["UTS-67", "UTS-132"]]
        "IT help"              | [projectKey: "JIP", issueTypeName: "IT Help", summary: "Numeric customfield notation"]                         | [10303: "UTS-67, UTS-132"]                   | ["customfield_10303": ["UTS-67", "UTS-132"]]
        "Purchase under \$100" | [projectKey: "JIP", issueTypeName: "IT Help", summary: "Array value"]                                          | ["customfield_10303": ["UTS-67", "UTS-132"]] | ["customfield_10303": ["UTS-67", "UTS-132"]]
        "Purchase under \$100" | [projectKey: "JIP", issueTypeName: "IT Help", summary: "Issue with description", description: "A description"] | [:]                                          | [:]
        "Purchase under \$100" | [projectKey: "JIP", issueTypeName: "IT Help", summary: "Issue with priority name", priority: "Low"]            | [:]                                          | [:]
        "Purchase under \$100" | [projectKey: "JIP", issueTypeName: "IT Help", summary: "Issue with priority id as string", priority: "4"]      | [:]                                          | [:]
        "Purchase under \$100" | [projectKey: "JIP", issueTypeName: "IT Help", summary: "Issue with priority id as int", priority: 4]           | [:]                                          | [:]


    }

    def "Verify createIssueLink"(String requestName, Map issueParameters, String linkName) {

        setup:
        JiraShortcuts jiraShortcuts = new JiraShortcuts()

        when: "Creating two test requests and linking them together"
        Issue sourceIssue = jiraShortcuts.createServiceDeskRequest(requestName, issueParameters, [:])
        Issue destinationIssue = jiraShortcuts.createServiceDeskRequest(requestName, issueParameters, [:])
        IssueLink issueLink = jiraShortcuts.createIssueLink(sourceIssue, destinationIssue, linkName)

        then: "If a valid linkName was given, the link should be created. If an invalid name then an exception should be thrown"
        issueLink.sourceObject.id == sourceIssue.id
        issueLink.destinationObject.id == destinationIssue.id
        issueLink.issueLinkType.name == linkName


        where:
        requestName        | issueParameters                                                                   | linkName

        "Computer support" | [projectKey: "JIP", issueTypeName: "IT Help", summary: "A Blocks link"]           | "Blocks"
        "IT help"          | [projectKey: "JIP", issueTypeName: "IT Help", summary: "A Problem/Incident link"] | "Problem/Incident"


    }

}
