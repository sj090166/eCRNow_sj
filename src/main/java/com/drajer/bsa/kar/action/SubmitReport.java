package com.drajer.bsa.kar.action;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.drajer.bsa.ehr.service.EhrQueryService;
import com.drajer.bsa.kar.model.BsaAction;
import com.drajer.bsa.model.BsaTypes.BsaActionStatusType;
import com.drajer.bsa.model.KarProcessingData;
import com.drajer.bsa.utils.BsaServiceUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DataRequirement;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SubmitReport extends BsaAction {

  private final Logger logger = LoggerFactory.getLogger(SubmitReport.class);

  private String submissionEndpoint;

  private static final FhirContext context = FhirContext.forR4();

  @Autowired BsaServiceUtils bsaServiceUtils;

  @Override
  public BsaActionStatus process(KarProcessingData data, EhrQueryService ehrService) {

    logger.info(" Executing the submission of the Report to : {}", submissionEndpoint);

    BsaActionStatus actStatus = new SubmitReportStatus();
    actStatus.setActionId(this.getActionId());

    // Check Timing constraints and handle them before we evaluate conditions.
    BsaActionStatusType status = processTimingData(data);

    if (status != BsaActionStatusType.Scheduled || getIgnoreTimers()) {

      List<DataRequirement> input = getInputData();

      Set<Resource> resourcesToSubmit = new HashSet<Resource>();

      if (input != null) {

        for (DataRequirement dr : input) {
          Set<Resource> resources = data.getOutputDataById(dr.getId());
          if (resources != null && !resources.isEmpty()) {
            resourcesToSubmit.addAll(resources);
          }
        }
      }

      if (resourcesToSubmit.isEmpty()) {
        logger.info(" No resources to submit");
      } else {
        logger.info(" {} resource(s) to submit", resourcesToSubmit.size());
      }

      context.getRestfulClientFactory().setSocketTimeout(30 * 1000);

      for (Resource r : resourcesToSubmit) {
        IGenericClient client = context.newRestfulGenericClient(submissionEndpoint);

        // All submissions are expected to be bundles
        Bundle bundleToSubmit = (Bundle) r;
        if (logger.isInfoEnabled()) {
          logger.info(
              " Submit Bundle:::::{}",
              context.newJsonParser().encodeResourceToString(bundleToSubmit));
        }

        Bundle responseBundle =
            client
                .operation()
                .onServer()
                .named(this.getSubmissionOperation())
                .withParameter(Parameters.class, "content", bundleToSubmit)
                .returnResourceType(Bundle.class)
                .execute();

        if (responseBundle != null) {
          logger.debug(
              "Response Bundle:::::{}",
              context.newJsonParser().encodeResourceToString(responseBundle));

          data.addActionOutput(actionId, responseBundle);

          logger.info(" Adding Response Bundle to output using id {}", responseBundle.getId());

          data.addActionOutputById(responseBundle.getId(), responseBundle);
        }

        if (conditionsMet(data)) {

          // Execute sub Actions
          executeSubActions(data, ehrService);

          // Execute Related Actions.
          executeRelatedActions(data, ehrService);
        }

        bsaServiceUtils.saveEicrState(bundleToSubmit.getIdElement().getIdPart(), bundleToSubmit);
        actStatus.setActionStatus(BsaActionStatusType.Completed);
      } // for all resources to be submitted
    } else {

      logger.info(
          " Action may be executed in the future or Conditions have not been met, so cannot proceed any further. ");
      logger.info(" Setting Action Status : {}", status);
      actStatus.setActionStatus(status);
    }
    data.addActionStatus(getActionId(), actStatus);

    return actStatus;
  }

  protected String getSubmissionOperation() {
    String endpoint = this.getSubmissionEndpoint();
    // Default operation for message bundles in the case that
    // one isn't specified in the configuration
    if (endpoint == null || !endpoint.contains("$")) {
      return "process-message";
    }

    // Otherwise, parse the operation name
    return endpoint.substring(endpoint.indexOf("$") + 1);
  }

  public String getSubmissionEndpoint() {
    return submissionEndpoint;
  }

  public void setSubmissionEndpoint(String submissionEndpoint) {
    this.submissionEndpoint = submissionEndpoint;
  }
}