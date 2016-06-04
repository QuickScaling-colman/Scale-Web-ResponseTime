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
		Integer PeriodicTimeResponseTime = config().getJsonObject("TestingWebsiteConf").getInteger("PeriodicTimeResponseTime");
		Integer PeriodicTimeScale = config().getJsonObject("TestingWebsiteConf").getInteger("PeriodicTimeScale");
		Integer PeriodicTimeResponseTimeSaveMongo = config().getJsonObject("TestingWebsiteConf").getInteger("PeriodicTimeResponseTimeSaveMongo");
		
		Integer requestTimeout = config().getJsonObject("TestingWebsiteConf").getInteger("requestTimeout");
		JsonObject KubeSettings = config().getJsonObject("TestingWebsiteConf").getJsonObject("kubernetesConf");
		
		CheckWebSites(requestTimeout);
		
		vertx.setPeriodic(PeriodicTimeResponseTime, res -> {
			CheckWebSites(requestTimeout);			
		});
		
		vertx.setPeriodic(PeriodicTimeResponseTimeSaveMongo, res -> {
			
			SaveToMongo();			
		});
		
		vertx.setPeriodic(PeriodicTimeScale, res->{
			getScale(KubeSettings);
		});
		
		startFuture.complete();
	}
	
	private void getScale(JsonObject KubeSettings) {
		HttpClient client = vertx.createHttpClient();
		
		client.get(KubeSettings.getInteger("Port"),KubeSettings.getString("Host"),KubeSettings.getString("Path"),response->{
			if(response.statusCode() == 200) {
				response.bodyHandler(resHttpClient -> {
						JsonObject replicationcontrollers = new JsonObject(resHttpClient.toString());
						int replicas = replicationcontrollers.getJsonObject("status").getInteger("replicas");
					 
						this.SaveScale(new Date(), replicas);
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
	
	private void CheckWebSites(long requestTimeOut) {
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
									
									this.SaveResponseTime(StartingRequest, responseTime, currWebsite.HostName);
									
								});
							} else {
								this.SaveResponseTime(StartingRequest, 40000, currWebsite.HostName);
							}
						});
						
						request.exceptionHandler(response -> {
							this.SaveResponseTime(StartingRequest, 40000, currWebsite.HostName);
							
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
	
	SimpleDateFormat formatMongo = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	
	private void SaveResponseTime(Date saveDate, long responseTime,String HostName) {
		
		JsonObject MongoDate = new JsonObject();
		MongoDate.put("$date", formatMongo.format(saveDate));
		
		JsonObject jsonResponseTime = new JsonObject();
		jsonResponseTime.put("date",  MongoDate);
		jsonResponseTime.put("responseTime", responseTime);
		jsonResponseTime.put("website", HostName);
		
		allResponseTime.add(jsonResponseTime);
	}
	
	private void SaveScale(Date saveDate, int replicas) {
		
		JsonObject MongoDate = new JsonObject();
		MongoDate.put("$date", formatMongo.format(new Date()));
		
		JsonObject jsonResponseTime = new JsonObject();
		jsonResponseTime.put("date",  saveDate);
		jsonResponseTime.put("replicas", replicas);
		
		vertx.eventBus().send("SAVE_SCALE", jsonResponseTime);
	}
}
