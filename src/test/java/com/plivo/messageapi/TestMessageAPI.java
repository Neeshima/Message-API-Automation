package com.plivo.messageapi;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import com.plivo.utilities.Constants;

public class TestMessageAPI {

	private static final String TEXT_MESSAGE = "Test Message";

	private Response getNumberListResponse;
	private Response sendMessageResponse;
	private Response messageDetailsResponse;
	private Response pricingResponse;

	double cashCreditBeforeSendMessage;
	String cashDeducted;

	private static final String AUTH_ID = "MAODUZYTQ0Y2FMYJBLOW";
	private static final String AUTH_TOKEN = "Mzk0MzU1Mzc3MTc1MTEyMGU2M2RlYTIwN2UyMzk1";

	private static final String COUNTRY_CODE = "US";

	private static final int OK = 200;
	private static final int ACCEPTED = 202;

	private static final String RESPONSE_ERROR_MESSAGE = "Unexpected response status code !!!";

	@BeforeClass
	public void setUp() {

		RestAssured.baseURI = Constants.BASE_URI + AUTH_ID;
	}

	@BeforeMethod
	public void before(Method method) {

		System.out.println("===================== " + method.getName() + " =====================");
	}

	@AfterMethod
	public void after() {

		System.out.println("---------------------------- End of Test ----------------------------\n\n");
	}

	@Test(priority = 0)
	public void getAccountDetails_BeforeSendMessage() {

		Response accountDetailsResponse = RestAssured.given().auth().basic(AUTH_ID, AUTH_TOKEN).when().get();
		Assert.assertEquals(accountDetailsResponse.statusCode(), OK, RESPONSE_ERROR_MESSAGE);
		printResponse(accountDetailsResponse);

		JSONObject responseObject = new JSONObject(accountDetailsResponse.getBody().asString());
		cashCreditBeforeSendMessage = Double.parseDouble(responseObject.getString(Constants.RESPONSE_CASH_CREDITS));
	}

	@Test(priority = 1)
	public void testGetListOfNumbers() {

		RestAssured.basePath = "/Number";
		getNumberListResponse = RestAssured.given().auth().basic(AUTH_ID, AUTH_TOKEN).when().get();
		printResponse(getNumberListResponse);

	}

	@Test(priority = 2, dependsOnMethods = { "testGetListOfNumbers" })
	public void testMessageApi_SendMessage() {

		// get 2 phoneNumbers from listNumbers Api
		List<String> numbers = getListOfNumbers(getNumberListResponse);

		if (numbers != null) {
			String srcNumber = numbers.get(0);
			String destNumber = numbers.get(1);

			RestAssured.basePath = "/Message";
			Map<String, String> queryParams = new HashMap<>();
			queryParams.put(Constants.QUERY_PARAM_SRC, srcNumber);
			queryParams.put(Constants.QUERY_PARAM_DST, destNumber);
			queryParams.put(Constants.QUERY_PARAM_TEXT, TEXT_MESSAGE);

			sendMessageResponse = RestAssured.given().auth().basic(AUTH_ID, AUTH_TOKEN)
					.header(Constants.CONTENT_TYPE, Constants.CONTENT_TYPE_MESSAGE_API).queryParameters(queryParams)
					.when().post();
			printResponse(sendMessageResponse);
			Assert.assertEquals(sendMessageResponse.statusCode(), ACCEPTED, RESPONSE_ERROR_MESSAGE);

		} else {
			System.out.println("Not enough numbers in list !!!");
		}

	}

	@Test(priority = 3, dependsOnMethods = { "testMessageApi_SendMessage" })
	public void testMessageDetailsApi() {

		String messageUuid = getMessageUUID(sendMessageResponse);
		RestAssured.basePath = "/Message/" + messageUuid;

		messageDetailsResponse = RestAssured.given().auth().basic(AUTH_ID, AUTH_TOKEN).when().get();
		printResponse(messageDetailsResponse);
		Assert.assertEquals(messageDetailsResponse.statusCode(), OK, RESPONSE_ERROR_MESSAGE);

	}

	@Test(priority = 4, dependsOnMethods = { "testMessageDetailsApi" })
	public void testPricingApi() {

		RestAssured.basePath = "/Pricing";
		Map<String, String> pathParams = new HashMap<>();
		pathParams.put(Constants.PATH_PARAM_COUNTRY_CODE, COUNTRY_CODE);

		pricingResponse = RestAssured.given().auth().basic(AUTH_ID, AUTH_TOKEN).params(pathParams).when().get();
		printResponse(pricingResponse);
		Assert.assertEquals(pricingResponse.statusCode(), OK, RESPONSE_ERROR_MESSAGE);
	}

	@Test(priority = 5, dependsOnMethods = { "testMessageDetailsApi", "testPricingApi" })
	public void testSentMessagePrice() {

		String actualPriceCharged = getPriceFromMessageDetails(messageDetailsResponse);
		String expectedPrice = getPriceFromPricingApi(pricingResponse);

		Assert.assertEquals(actualPriceCharged, expectedPrice, "Mismatch in the price charged for sent message !!!");
		cashDeducted = actualPriceCharged;
	}

	@Test(priority = 6, dependsOnMethods = { "testSentMessagePrice" })
	public void testAccountDetailsApi_AfterSendMessage() {

		RestAssured.basePath = "";
		Response accountDetailsResponse = RestAssured.given().auth().basic(AUTH_ID, AUTH_TOKEN).when().get();
		printResponse(accountDetailsResponse);
		Assert.assertEquals(accountDetailsResponse.statusCode(), OK, RESPONSE_ERROR_MESSAGE);

		JSONObject responseObject = new JSONObject(accountDetailsResponse.getBody().asString());
		double cashCreditAfterSendMessage = Double
				.parseDouble(responseObject.getString(Constants.RESPONSE_CASH_CREDITS));
		double priceCharged = Double.parseDouble(cashDeducted);

		Assert.assertEquals(cashCreditAfterSendMessage, cashCreditBeforeSendMessage - priceCharged,
				"Incorrect cash credit !!!");

	}

	private String getPriceFromMessageDetails(Response messageDetailsResponse) {
		JSONObject responseObject = new JSONObject(messageDetailsResponse.getBody().asString());
		return responseObject.getString("total_rate");
	}

	private String getPriceFromPricingApi(Response pricingResponse) {
		JSONObject responseObject = new JSONObject(pricingResponse.getBody().asString());
		return responseObject.getJSONObject("message").getJSONObject("outbound").getString("rate");
	}

	private String getMessageUUID(Response sendMessageResponse) {

		JSONObject responseObject = new JSONObject(sendMessageResponse.getBody().asString());

		return responseObject.getString("message_uuid");
	}

	private List<String> getListOfNumbers(Response getNumberListResponse) {
		List<String> numberList = new ArrayList<>();
		JSONObject responseObject = new JSONObject(getNumberListResponse.getBody().asString());
		JSONArray numbers = responseObject.getJSONArray("objects");
		if (numbers.length() < 2) {
			return null;
		}

		numberList.add(numbers.getJSONObject(0).getString("number"));
		numberList.add(numbers.getJSONObject(1).getString("number"));

		return numberList;

	}

	private void printResponse(Response response) {
		response.getBody().prettyPrint();
	}

}
