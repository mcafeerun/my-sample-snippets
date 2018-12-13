import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.CreateRequest;
import com.rallydev.rest.request.DeleteRequest;
import com.rallydev.rest.request.GetRequest;
import com.rallydev.rest.request.QueryRequest;
import com.rallydev.rest.request.UpdateRequest;
import com.rallydev.rest.response.CreateResponse;
import com.rallydev.rest.response.DeleteResponse;
import com.rallydev.rest.response.GetResponse;
import com.rallydev.rest.response.QueryResponse;
import com.rallydev.rest.response.UpdateResponse;
import com.rallydev.rest.util.Fetch;
import com.rallydev.rest.util.QueryFilter;
import com.rallydev.rest.util.Ref;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.codec.binary.Base64;

public class RestExample_CreateTestCaseResultAddAttachment {

    public static void main(String[] args) throws URISyntaxException, IOException {

        // Create and configure a new instance of RallyRestApi
        // Connection parameters
        String rallyURL = "https://rally1.rallydev.com";
        String wsapiVersion = "v2.0";
        String applicationName = "RestExample_CreateTestCaseResultAddAttachment";

        // Credentials
        String userName = "user@company.com";
        String userPassword = "topsecret";

        RallyRestApi restApi = new RallyRestApi(
                new URI(rallyURL),
                userName,
                userPassword);
        restApi.setWsapiVersion(wsapiVersion);
        restApi.setApplicationName(applicationName);   

        // Workspace and Project Settings
        String myWorkspace = "/workspace/12345678910";
        String myProject = "/project/12345678911";

        // Test Case to which we want to add a result
        String testCaseFormattedID = "TC40";

        // User name of tester
        String testerRallyID = "tester@testit.com";

        // Reference to created TestCaseResult
        String testCaseResultRef = "";

        // File handle for image to attach
        RandomAccessFile myImageFileHandle;
        String imageFilePath = "/home/username/Pictures/";
        String imageFileName = "image1.jpg";
        String fullImageFile = imageFilePath + imageFileName;
        String imageBase64String;
        long attachmentSize;

        // Open file
        myImageFileHandle = new RandomAccessFile(fullImageFile, "r");        

        //Read User
        QueryRequest userRequest = new QueryRequest("User");
        userRequest.setFetch(new Fetch("UserName", "Subscription", "DisplayName"));
        userRequest.setQueryFilter(new QueryFilter("UserName", "=", testerRallyID));
        QueryResponse userQueryResponse = restApi.query(userRequest);
        JsonArray userQueryResults = userQueryResponse.getResults();
        JsonObject userQueryObject = userQueryResults.get(0).getAsJsonObject();
        String userRef = userQueryObject.get("_ref").getAsString();

        // Query for Test Case to which we want to add results
        QueryRequest testCaseRequest = new QueryRequest("TestCase");
        testCaseRequest.setFetch(new Fetch("FormattedID","Name"));
        testCaseRequest.setQueryFilter(new QueryFilter("FormattedID", "=", testCaseFormattedID));
        QueryResponse testCaseQueryResponse = restApi.query(testCaseRequest);
        JsonObject testCaseJsonObject = testCaseQueryResponse.getResults().get(0).getAsJsonObject();
        String testCaseRef = testCaseQueryResponse.getResults().get(0).getAsJsonObject().get("_ref").getAsString();        

        // Query for Test Set to which we want to add Test Case
        QueryRequest testSetQuery = new QueryRequest("TestSet");
        testSetQuery.setFetch(new Fetch("FormattedID","Name","TestCases"));
        testSetQuery.setWorkspace(myWorkspace);
        testSetQuery.setProject(myProject);
        testSetQuery.setQueryFilter(new QueryFilter("FormattedID", "=", "TS5"));
        QueryResponse testSetQueryResponse = restApi.query(testSetQuery);
        JsonObject testSetJsonObject = testSetQueryResponse.getResults().get(0).getAsJsonObject();
        String testSetRef = testSetJsonObject.get("_ref").getAsString();
        System.out.println("Test Set Ref: " + testSetRef);

        try {           

            //Add a Test Case Result                
            System.out.println("Creating Test Case Result...");
            JsonObject newTestCaseResult = new JsonObject();
            newTestCaseResult.addProperty("Verdict", "Inconclusive");
            newTestCaseResult.addProperty("Date", "2013-09-04T18:00:00.000Z");
            newTestCaseResult.addProperty("Notes", "Automated Selenium Test Runs");
            newTestCaseResult.addProperty("Build", "2013.09.04.0020101");
            newTestCaseResult.addProperty("Tester", userRef);
            newTestCaseResult.addProperty("TestCase", testCaseRef);
            newTestCaseResult.addProperty("TestSet", testSetRef);

            CreateRequest createRequest = new CreateRequest("testcaseresult", newTestCaseResult);
            CreateResponse createResponse = restApi.create(createRequest);            

            if (createResponse.wasSuccessful()) {

                System.out.println(String.format("Created %s", createResponse.getObject().get("_ref").getAsString()));          

                //Read Test Case Result
                testCaseResultRef = Ref.getRelativeRef(createResponse.getObject().get("_ref").getAsString());
                System.out.println(String.format("\nReading Test Case Result %s...", testCaseResultRef));
                GetRequest getRequest = new GetRequest(testCaseResultRef);
                getRequest.setFetch(new Fetch("Date", "Verdict"));
                GetResponse getResponse = restApi.get(getRequest);
                JsonObject obj = getResponse.getObject();
                System.out.println(String.format("Read Test Case Result. Date = %s, Verdict = %s",
                        obj.get("Date").getAsString(), obj.get("Verdict").getAsString()));

                try {
                    // Get and check length
                    long longLength = myImageFileHandle.length();
                    long maxLength = 5000000;
                    if (longLength >= maxLength) throw new IOException("File size >= 5 MB Upper limit for Rally.");
                    int fileLength = (int) longLength;            

                    // Read file and return data
                    byte[] fileBytes = new byte[fileLength];
                    myImageFileHandle.readFully(fileBytes);
                    imageBase64String = Base64.encodeBase64String(fileBytes);
                    attachmentSize = fileLength;

                    // First create AttachmentContent from image string
                    JsonObject myAttachmentContent = new JsonObject();
                    myAttachmentContent.addProperty("Content", imageBase64String);
                    CreateRequest attachmentContentCreateRequest = new CreateRequest("AttachmentContent", myAttachmentContent);
                    CreateResponse attachmentContentResponse = restApi.create(attachmentContentCreateRequest);
                    String myAttachmentContentRef = attachmentContentResponse.getObject().get("_ref").getAsString();
                    System.out.println("Attachment Content created: " + myAttachmentContentRef);            

                    // Now create the Attachment itself
                    JsonObject myAttachment = new JsonObject();
                    myAttachment.addProperty("TestCaseResult", testCaseResultRef);
                    myAttachment.addProperty("Content", myAttachmentContentRef);
                    myAttachment.addProperty("Name", "AttachmentFromREST.jpg");
                    myAttachment.addProperty("Description", "Attachment From REST");
                    myAttachment.addProperty("ContentType","image/jpg");
                    myAttachment.addProperty("Size", attachmentSize);
                    myAttachment.addProperty("User", userRef);          

                    CreateRequest attachmentCreateRequest = new CreateRequest("Attachment", myAttachment);
                    CreateResponse attachmentResponse = restApi.create(attachmentCreateRequest);
                    String myAttachmentRef = attachmentResponse.getObject().get("_ref").getAsString();
                    System.out.println("Attachment  created: " + myAttachmentRef);  

                    if (attachmentResponse.wasSuccessful()) {
                        System.out.println("Successfully created Attachment");
                    } else {
                        String[] attachmentContentErrors;
                        attachmentContentErrors = attachmentResponse.getErrors();
                        System.out.println("Error occurred creating Attachment: ");
                        for (int i=0; i<attachmentContentErrors.length;i++) {
                            System.out.println(attachmentContentErrors[i]);
                        }                   
                    }
                } catch (Exception e) {
                    System.out.println("Exception occurred while attempting to create Content and/or Attachment: ");
                    e.printStackTrace();            
                }                           

            } else {
                String[] createErrors;
                createErrors = createResponse.getErrors();
                System.out.println("Error occurred creating Test Case Result: ");
                for (int j=0; j<createErrors.length;j++) {
                    System.out.println(createErrors[j]);
                }
            }                        

        } finally {
            //Release all resources
            restApi.close();
            myImageFileHandle.close();            
        }        
    }

}
