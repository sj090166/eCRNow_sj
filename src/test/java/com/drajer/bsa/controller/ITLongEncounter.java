package com.drajer.bsa.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.drajer.bsa.model.BsaTypes.NotificationProcessingStatusType;
import com.drajer.bsa.model.NotificationContext;
import com.drajer.ecrapp.config.SpringConfiguration;
import com.drajer.test.BaseIntegrationTest;
import com.drajer.test.util.TestDataGenerator;
import com.drajer.test.util.TestUtils;
import com.drajer.test.util.WireMockHelper;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@RunWith(Parameterized.class)
@ContextConfiguration(classes = SpringConfiguration.class)
@ActiveProfiles("enc-test")
public class ITLongEncounter extends BaseIntegrationTest {

  String uri = "/api/launchPatient";

  private String testCaseId;
  private Map<String, String> testData;
  private Map<String, ?> allResourceMapping;
  private Map<String, ?> allOtherMapping;

  public ITLongEncounter(
      String testCaseId,
      Map<String, String> testData,
      Map<String, ?> resourceMapping,
      Map<String, ?> otherMapping) {
    this.testCaseId = testCaseId;
    this.testData = testData;
    this.allResourceMapping = resourceMapping;
    this.allOtherMapping = otherMapping;
  }

  private static final Logger logger = LoggerFactory.getLogger(ITLongEncounter.class);
  private String systemLaunch3PayLoad;
  WireMockHelper stubHelper;

  @Before
  public void launchTestSetUp() throws IOException {
    logger.info("Executing test: {}", testCaseId);
    saveHealtcareSetting(testData.get("HealcareSettingsFile"));
    systemLaunch3PayLoad = getSystemLaunch3Payload(testData.get("SystemLaunch3Payload"));
    stubHelper = new WireMockHelper(wireMockServer, wireMockHttpPort);
    logger.info("Creating WireMock stubs..");
    stubHelper.stubResources(allResourceMapping);
    stubHelper.stubAuthAndMetadata(allOtherMapping);
    mockRestApi();
  }

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    TestDataGenerator testDataGenerator = new TestDataGenerator("test-yaml/longEncounterTest.yaml");

    Set<String> testCaseSet = testDataGenerator.getAllTestCases();
    Object[][] data = new Object[testCaseSet.size()][4];

    int count = 0;
    for (String testCase : testCaseSet) {
      data[count][0] = testCase;
      data[count][1] = testDataGenerator.getTestCaseByID(testCase).getTestData();
      data[count][2] = testDataGenerator.getResourceMappings(testCase);
      data[count][3] = testDataGenerator.getOtherMappings(testCase);
      count++;
    }
    return Arrays.asList(data);
  }

  @Test
  public void testNotificationStatus() {
    // testing notification status whether it is suspended
    waitForSystemlaunch(2);
    assertEquals(
        NotificationProcessingStatusType.SUSPENDED.toString(),
        getNotificationContext().getNotificationProcessingStatus());
  }

  @Test
  public void testCallApi() {
    final TestRestTemplate restTemplate = new TestRestTemplate();
    HttpHeaders headers = new HttpHeaders();
    String requestId = "1234";
    String url = createURLWithPort(uri);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add("X-Request-ID", requestId);
    HttpEntity<String> entity = new HttpEntity<>(systemLaunch3PayLoad, headers);
    systemLaunch(restTemplate, url, entity);
  }

  private void systemLaunch(
      final TestRestTemplate restTemplate, String url, HttpEntity<String> entity) {
    restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    assertEquals(
        NotificationProcessingStatusType.IN_PROGRESS.toString(),
        getNotificationContext().getNotificationProcessingStatus());
  }

  private NotificationContext getNotificationContext() {
    Criteria criteria = session.createCriteria(NotificationContext.class);
    criteria.add(Restrictions.eq("notificationResourceId", "97953900"));
    NotificationContext nc = (NotificationContext) criteria.uniqueResult();
    return nc;
  }

  private void saveHealtcareSetting(String healthCareSettingsFile) throws IOException {
    String healthcareSettingFile = TestUtils.getFileContentAsString(healthCareSettingsFile);
    headers.setContentType(MediaType.APPLICATION_JSON);
    String requestId = "5678";
    headers.add("X-Request-ID", requestId);
    HttpEntity<String> entity = new HttpEntity<>(healthcareSettingFile, headers);
    restTemplate.exchange(
        createURLWithPort("/api/healthcareSettings"), HttpMethod.POST, entity, String.class);
  }

  private void waitForSystemlaunch(int interval) {
    try {
      TimeUnit.MINUTES.sleep(interval);
    } catch (InterruptedException e) {
      logger.warn("Issue with thread sleep", e);
      Thread.currentThread().interrupt();
    }
  }
}
