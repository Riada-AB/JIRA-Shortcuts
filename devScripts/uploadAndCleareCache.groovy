import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.apache.groovy.json.internal.LazyMap

/**
 * This is a script intended for developers, it's a bit of a dirty hack to upload new classes and clear Class cashes in JIRA by using private Scriptrunner REST APIs
 */

String hostURI  = "http://jira.domain.se"
String restUser = "user"
String restPw = "password"
String jiraHome = "/var/atlassian/application-data/jira/"
Map<String, String>sourceDestinationFile = [
        "src/customRiadaLibraries/jiraShortcuts/JiraShortcuts.groovy": "customRiadaLibraries/jiraShortcuts/JiraShortcuts.groovy",
]

sourceDestinationFile.each {sourceFile, destinationFile->
    uploadFiles(hostURI, restUser, restPw, sourceFile, jiraHome, destinationFile)
}

clearCodeCache(hostURI, restUser, restPw)



void uploadFiles(String hostURI, String restUser, String restPw, String sourceFilePath, String jiraHomePath, String destFileName) {

    println("Uploading file:" +sourceFilePath + " to:" + jiraHomePath)




    File sourceFile = new File(sourceFilePath)

    println("Path:" + System.getProperty("user.dir"))

    HttpURLConnection uploadConnection = new URL(hostURI + "/rest/scriptrunner/latest/idea/file?filePath=${URLEncoder.encode(destFileName, "UTF-8")}&rootPath=${ URLEncoder.encode(jiraHomePath + "scripts", "UTF-8")}").openConnection() as HttpURLConnection


    String auth = restUser + ":" + restPw
    auth = "Basic " + auth.bytes.encodeBase64().toString()
    uploadConnection.setRequestProperty("Authorization", auth)
    uploadConnection.setDoOutput(true)
    uploadConnection.setRequestMethod("PUT")
    uploadConnection.setRequestProperty("Content-Type", "application/octet-stream")
    uploadConnection.setRequestProperty("Accept", "*/*")
    OutputStreamWriter out = new OutputStreamWriter(
            uploadConnection.getOutputStream());
    out.write(sourceFile.text.bytes.encodeBase64());
    out.close();


    println("IM upload HTTP response code:" + uploadConnection.responseCode)



}


void clearCodeCache(String hostURI, String restUser, String restPw) {

    HttpURLConnection cacheClearConnection = new URL(hostURI + "/rest/scriptrunner/latest/canned/com.onresolve.scriptrunner.canned.jira.admin.JiraClearCaches").openConnection() as HttpURLConnection
    String auth = restUser + ":" + restPw
    auth = "Basic " + auth.bytes.encodeBase64().toString()
    cacheClearConnection.setRequestProperty("Authorization", auth)
    cacheClearConnection.setDoOutput(true)
    cacheClearConnection.setRequestMethod("POST")
    cacheClearConnection.setRequestProperty("Content-Type", "application/json")
    cacheClearConnection.setRequestProperty("Accept", "application/json")
    byte[] jsonByte = new JsonBuilder(["FIELD_WHICH_CACHE": "gcl", "canned-script": "com.onresolve.scriptrunner.canned.jira.admin.JiraClearCaches"]).toPrettyString().getBytes("UTF-8")
    cacheClearConnection.outputStream.write(jsonByte, 0, jsonByte.length)


    LazyMap rawReturn = new JsonSlurper().parse(cacheClearConnection.getInputStream())

    println("Cache clear output:" + rawReturn.output)

}



