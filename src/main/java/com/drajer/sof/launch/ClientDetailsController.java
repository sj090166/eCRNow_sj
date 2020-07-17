package com.drajer.sof.launch;

import java.util.List;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.drajer.sof.model.ClientDetails;
import com.drajer.sof.service.ClientDetailsService;
import com.drajer.sof.service.LoadingQueryService;

@RestController
public class ClientDetailsController {

	@Autowired
	ClientDetailsService clientDetailsService;

	private final Logger logger = LoggerFactory.getLogger(ClientDetailsController.class);

	@CrossOrigin
	@RequestMapping("/api/clientDetails/{clientId}")
	public ClientDetails getClientDetailsById(@PathVariable("clientId") Integer clientId) {
		return clientDetailsService.getClientDetailsById(clientId);
	}

	// POST method to create a Client
	@CrossOrigin
	@RequestMapping(value = "/api/clientDetails", method = RequestMethod.POST)
	public ResponseEntity<?> createClientDetails(@RequestBody ClientDetails clientDetails) {
		ClientDetails checkClientDetails = null;
		if (clientDetails.getIsSystem()) {
			checkClientDetails = clientDetailsService.getClientDetailsByUrl(clientDetails.getFhirServerBaseURL(), clientDetails.getIsSystem());
		} else {
			checkClientDetails = clientDetailsService.getClientDetailsByUrl(clientDetails.getFhirServerBaseURL(), false);
		}
		if (checkClientDetails == null) {
			clientDetailsService.saveOrUpdate(clientDetails);
			return new ResponseEntity<>(clientDetails, HttpStatus.OK);
		} else {
			JSONObject responseObject = new JSONObject();
			responseObject.put("status", "error");
			responseObject.put("message", "URL is already registered");
			return new ResponseEntity<>(responseObject, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@CrossOrigin
	@RequestMapping(value = "/api/clientDetails", method = RequestMethod.PUT)
	public ResponseEntity<?> updateClientDetails(@RequestBody ClientDetails clientDetails) {
		ClientDetails checkClientDetails = null;
		if (clientDetails.getIsSystem()) {
			checkClientDetails = clientDetailsService.getClientDetailsByUrl(clientDetails.getFhirServerBaseURL(), clientDetails.getIsSystem());
		} else {
			checkClientDetails = clientDetailsService.getClientDetailsByUrl(clientDetails.getFhirServerBaseURL(), false);
		}
		if (checkClientDetails == null
				|| (checkClientDetails != null && checkClientDetails.getId().equals(clientDetails.getId()))) {
			clientDetailsService.saveOrUpdate(clientDetails);
			return new ResponseEntity<>(clientDetails, HttpStatus.OK);
		} else {
			JSONObject responseObject = new JSONObject();
			responseObject.put("status", "error");
			responseObject.put("message", "URL is already registered");
			return new ResponseEntity<>(responseObject, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/*
	 * @CrossOrigin
	 * 
	 * @RequestMapping("/api/clientDetails") public ClientDetails
	 * getClientDetailsByUrl(@RequestParam(value = "url") String url) { return
	 * clientDetailsService.getClientDetailsByUrl(url); }
	 */

	@CrossOrigin
	@RequestMapping("/api/clientDetails/")
	public List<ClientDetails> getAllClientDetails() {
		return clientDetailsService.getAllClientDetails();
	}
}
