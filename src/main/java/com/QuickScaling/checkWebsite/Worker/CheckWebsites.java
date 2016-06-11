package com.QuickScaling.checkWebsite.Worker;

import java.text.SimpleDateFormat;
import java.util.Date;

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
	private JsonObject LastConfigurations = new JsonObject();
	
	@Override
	public void start(Future<Void> startFuture) {
		CheckWebSites();
		
		vertx.setPeriodic(60000, res->{
			CheckWebSites();
		});
		
		startFuture.complete();
	}
	
	private void getScale(String Name, String Host,int port, String path) {
		HttpClient client = vertx.createHttpClient();
		
		client.get(port, Host, path, response->{
			if(response.statusCode() == 200) {
				response.bodyHandler(resHttpClient -> {
						JsonObject replicationcontrollers = new JsonObject(resHttpClient.toString());
						int replicas = replicationcontrollers.getJsonObject("status").getInteger("replicas");
						
						logger.debug(Name + ": " + replicas);
					 
						this.SaveScale(new Date(), replicas);
				});
			} else {
				logger.warn(Name + ": " + "Failed to read replicas(http://" + Host + ":" + port + path + ")");
			}
		}).end();
	}
	
	private void getResponseTime(String Name, String Host,int port, String path, int Timeout) {
		HttpClient client = vertx.createHttpClient();
		
		Date StartingRequest = new Date();
		
		HttpClientRequest request = client.get(port,Host,path, response -> {
			logger.debug(Name + ": " + response.statusCode());
			if(response.statusCode() == 200) {
				response.bodyHandler(resHttpClient -> {
					Date EndRequest = new Date();
					
					long responseTime = EndRequest.getTime() - StartingRequest.getTime();

					this.SaveResponseTime(StartingRequest, responseTime, Name);
					
				});
			} else {
				this.SaveResponseTime(StartingRequest, Timeout, Name);
			}
		});
		
		request.exceptionHandler(response -> {
			this.SaveResponseTime(StartingRequest, Timeout, Name);
			
		});
		
		request.setTimeout(Timeout);
		request.end();
		
		
	}
	
	private void SaveToMongo() {
		JsonArray arraycopy = allResponseTime.copy();
		allResponseTime.clear();
		
		for (int i = 0; i < arraycopy.size(); i++) {
			vertx.eventBus().send("SAVE_RESPONSE_TIME", arraycopy.getJsonObject(i));
		}
		
		logger.info("Finish save responseTime array To Mongo");
	}
	
	private void CheckWebSites() {
		vertx.eventBus().send("GET_ALL_WEBSITES","", res -> {
			try {
				JsonArray arrWebsites = new JsonArray(res.result().body().toString());
				
				if(!arrWebsites.isEmpty()) {
					for (Object currWebsite : arrWebsites) {
						
						JsonObject jocurrWebsite = (JsonObject) currWebsite;
						
					
						JsonObject ReponseTimeConf = jocurrWebsite.getJsonObject("ResponseTimeConf");
						JsonObject ScaleConf = jocurrWebsite.getJsonObject("ScaleConf");
						
						if(LastConfigurations.getJsonObject(jocurrWebsite.getString("name")) != null) {
							JsonObject lastResponseTimeConf = LastConfigurations.getJsonObject(jocurrWebsite.getString("name")).getJsonObject("ResponseTimeConf");
							if(!lastResponseTimeConf.equals(ReponseTimeConf)){
								logger.info("reload ReponseTime configuration");
								
								runPeriodicResponseTime(jocurrWebsite,ReponseTimeConf);
								LastConfigurations.put(jocurrWebsite.getString("name"), jocurrWebsite);
							}
							
							JsonObject lastScaleConf = LastConfigurations.getJsonObject(jocurrWebsite.getString("name")).getJsonObject("ScaleConf");
							
							if(!lastScaleConf.equals(ScaleConf)){
								logger.info("reload scale configuration");
								
								runPeriodicScale(jocurrWebsite,ScaleConf);
								LastConfigurations.put(jocurrWebsite.getString("name"), jocurrWebsite);
							}
						} else {
							runPeriodicResponseTime(jocurrWebsite,ReponseTimeConf);
							
							runPeriodicScale(jocurrWebsite,ScaleConf);
							
							LastConfigurations.put(jocurrWebsite.getString("name"), jocurrWebsite);
						}
					}
				} else {
					logger.warn("No websites exist in DB");
				}
			} catch (Exception e) {
				logger.error(e.getMessage(),e);
			}
		});
	}
	
	private void runPeriodicResponseTime(JsonObject jocurrWebsite,JsonObject ReponseTimeConf) {
		
		if(LastConfigurations.getJsonObject(jocurrWebsite.getString("name")) != null && 
		   LastConfigurations.getJsonObject(jocurrWebsite.getString("name")).getLong("idPeriodicTimeResponseTime") != null) {
			vertx.cancelTimer(LastConfigurations.getJsonObject(jocurrWebsite.getString("name")).getLong("idPeriodicTimeResponseTime"));
			logger.info("cancel Timer: " + LastConfigurations.getJsonObject(jocurrWebsite.getString("name")).getLong("idPeriodicTimeResponseTime"));
		}
		
		int PeriodicTimeResponseTime = ReponseTimeConf.getInteger("PeriodicTime");
		
		long idPeriodicTimeResponseTime = vertx.setPeriodic(PeriodicTimeResponseTime, resResponse -> {
			getResponseTime(jocurrWebsite.getString("name"),
							ReponseTimeConf.getString("HostName"),
							ReponseTimeConf.getInteger("Port"),
							ReponseTimeConf.getString("Path"),
							ReponseTimeConf.getInteger("requestTimeout"));			
		});
		
		logger.debug("start Timer: " + idPeriodicTimeResponseTime);
		
		jocurrWebsite.put("idPeriodicTimeResponseTime", idPeriodicTimeResponseTime);
		
		if(LastConfigurations.getJsonObject(jocurrWebsite.getString("name")) != null && 
		   LastConfigurations.getJsonObject(jocurrWebsite.getString("name")).getLong("idPeriodicTimeSaveToMongo") != null) {
			vertx.cancelTimer(LastConfigurations.getJsonObject(jocurrWebsite.getString("name")).getLong("idPeriodicTimeSaveToMongo"));
			logger.info("cancel Timer: " + LastConfigurations.getJsonObject(jocurrWebsite.getString("name")).getLong("idPeriodicTimeSaveToMongo"));
		}
		
		int PeriodicTimeSaveToMongo = ReponseTimeConf.getInteger("PeriodicTimeSaveToMongo");
		
		long idPeriodicTimeSaveToMongo = vertx.setPeriodic(PeriodicTimeSaveToMongo, resSaveToMongo -> {
			SaveToMongo();			
		});
		
		logger.debug("start Timer: " + idPeriodicTimeSaveToMongo);
		
		jocurrWebsite.put("idPeriodicTimeSaveToMongo", idPeriodicTimeSaveToMongo);
	}
	
	private void runPeriodicScale(JsonObject jocurrWebsite,JsonObject ScaleConf) {
		if(LastConfigurations.getJsonObject(jocurrWebsite.getString("name")) != null && 
		   LastConfigurations.getJsonObject(jocurrWebsite.getString("name")).getLong("idkubernetesConf") != null) {
			vertx.cancelTimer(LastConfigurations.getJsonObject(jocurrWebsite.getString("name")).getLong("idkubernetesConf"));
			logger.info("cancel Timer: " + LastConfigurations.getJsonObject(jocurrWebsite.getString("name")).getLong("idkubernetesConf"));
		}
			
		JsonObject kubernetesConf = ScaleConf.getJsonObject("kubernetesConf");
		
		long idkubernetesConf = vertx.setPeriodic(ScaleConf.getInteger("PeriodicTime"), resScale ->{
			
			getScale(jocurrWebsite.getString("name"),
					 kubernetesConf.getString("HostName"),
					 kubernetesConf.getInteger("Port"),
					 kubernetesConf.getString("Path"));
		});
		
		logger.debug("start Timer: " + idkubernetesConf);
		
		jocurrWebsite.put("idkubernetesConf", idkubernetesConf);
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
		jsonResponseTime.put("date", MongoDate);
		jsonResponseTime.put("replicas", replicas);
		
		vertx.eventBus().send("SAVE_SCALE", jsonResponseTime);
	}
}
