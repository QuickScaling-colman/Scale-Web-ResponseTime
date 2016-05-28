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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class CheckWebsites extends AbstractVerticle { 
	public Logger logger = LoggerFactory.getLogger(CheckWebsites.class);
	private JsonArray allResponseTime = new JsonArray();
	
	@Override
	public void start(Future<Void> startFuture) {
		Integer PeriodicTime = config().getJsonObject("PeriodicConf").getInteger("PeriodicTime");
		
		CheckWebSites();
		
		vertx.setPeriodic(PeriodicTime, res -> {
			CheckWebSites();			
		});
		
		vertx.setPeriodic(40000, res -> {
			
			SaveToMongo();			
		});
		
		vertx.setPeriodic(5000, res->{
			getScale();
		});
		
		startFuture.complete();
	}
	
	private void getScale() {
		HttpClient client = vertx.createHttpClient();
		
		client.get(80,"kube.quickscaling.ml","/api/v1/namespaces/default/replicationcontrollers/geoserver-controller",response->{
			if(response.statusCode() == 200) {
				response.bodyHandler(resHttpClient -> {
						JsonObject replicationcontrollers = new JsonObject(resHttpClient.toString());
						int replicas = replicationcontrollers.getJsonObject("status").getInteger("replicas");
					 
					 	SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
						
						JsonObject MongoDate = new JsonObject();
						MongoDate.put("$date", format.format(new Date()));
						
						JsonObject jsonResponseTime = new JsonObject();
						jsonResponseTime.put("date",  MongoDate);
						jsonResponseTime.put("replicas", replicas);
						
						vertx.eventBus().send("SAVE_SCALE", jsonResponseTime);
				});
				
			}
		}).end();
		
		
	}
	
	private void SaveToMongo() {
		JsonArray arraycopy = allResponseTime.copy();
		allResponseTime.clear();
		
		for (int i = 0; i < arraycopy.size(); i++) {
			vertx.eventBus().send("SAVE_RESPONSE_TIME", arraycopy.getJsonObject(i));
		}	
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
						
						logger.info(currWebsite.HostName);
						HttpClientRequest request = client.get(currWebsite.port,currWebsite.HostName,currWebsite.path, response -> {
							logger.info(response.statusCode());
							if(response.statusCode() == 200) {
								response.bodyHandler(resHttpClient -> {
									Date EndRequest = new Date();
									
									long responseTime = EndRequest.getTime() - StartingRequest.getTime();
									
									currWebsite.LastCheckingTime = StartingRequest;
									currWebsite.LastResponseTime = responseTime;
									
									SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
									
									JsonObject MongoDateNumber = new JsonObject();
									MongoDateNumber.put("$numberLong", String.valueOf(StartingRequest.getTime()));
									
									JsonObject MongoDate = new JsonObject();
									MongoDate.put("$date", format.format(StartingRequest));
									
									JsonObject jsonResponseTime = new JsonObject();
									jsonResponseTime.put("date",  MongoDate);
									jsonResponseTime.put("responseTime", responseTime);
									jsonResponseTime.put("website", currWebsite.HostName);
									
									allResponseTime.add(jsonResponseTime);
									
								});
							} else {
								SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
								JsonObject MongoDate = new JsonObject();
								MongoDate.put("$date", format.format(StartingRequest));
								
								JsonObject jsonResponseTime = new JsonObject();
								jsonResponseTime.put("date",  MongoDate);
								jsonResponseTime.put("responseTime", 40000);
								jsonResponseTime.put("website", currWebsite.HostName);
								
								//allResponseTime.add(jsonResponseTime);
							}
						});
						
						request.exceptionHandler(response -> {
							SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
							JsonObject MongoDate = new JsonObject();
							MongoDate.put("$date", format.format(StartingRequest));
							
							JsonObject jsonResponseTime = new JsonObject();
							jsonResponseTime.put("date",  MongoDate);
							jsonResponseTime.put("responseTime", 40000);
							jsonResponseTime.put("website", currWebsite.HostName);
							
							//allResponseTime.add(jsonResponseTime);
						});
						
						request.setTimeout(40000);
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
