package com.QuickScaling.checkWebsite.Worker;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.QuickScaling.checkWebsite.model.website;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class CheckWebsites extends AbstractVerticle { 
	public Logger logger = LoggerFactory.getLogger(CheckWebsites.class);
	@Override
	public void start(Future<Void> startFuture) {
		Integer PeriodicTime = config().getJsonObject("PeriodicConf").getInteger("PeriodicTime");
		
		CheckWebSites();
		
		vertx.setPeriodic(PeriodicTime, res -> {
			CheckWebSites();			
		});
		
		startFuture.complete();
	}
	
	private void CheckWebSites() {
		HttpClient client = vertx.createHttpClient();
		
		vertx.eventBus().send("GET_ALL_WEBSITES","", res -> {
			ObjectMapper mapper = new ObjectMapper();
			try {
				List<website> arrWebsites = mapper.readValue(res.result().body().toString(), new TypeReference<List<website>>() {});
				
				if(!arrWebsites.isEmpty()) {
					for (website currWebsite : arrWebsites) {
						Date StartingRequest = new Date();
						HttpClientRequest request = client.get(currWebsite.URL, response -> {
							response.endHandler(resHttpClient -> {
								Date EndRequest = new Date();
								
								long responseTime = EndRequest.getTime() - StartingRequest.getTime();
								
								currWebsite.LastCheckingTime = EndRequest;
								currWebsite.LastResponseTime = responseTime;
								
								SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
								
								JsonObject MongoDateNumber = new JsonObject();
								MongoDateNumber.put("$numberLong", String.valueOf(EndRequest.getTime()));
								
								JsonObject MongoDate = new JsonObject();
								MongoDate.put("$date", format.format(EndRequest));
								
								JsonObject jsonResponseTime = new JsonObject();
								jsonResponseTime.put("date",  MongoDate);
								jsonResponseTime.put("responseTime", responseTime);
								jsonResponseTime.put("website", currWebsite.URL);
								
								vertx.eventBus().send("SAVE_RESPONSE_TIME", jsonResponseTime.toString());
							});
						});
						
						request.end();
					}
				} else {
					logger.warn("No websites exist in DB");
				}
			} catch (Exception e) {
				logger.error(e.getMessage(),e);
			}
		});
	}
}
