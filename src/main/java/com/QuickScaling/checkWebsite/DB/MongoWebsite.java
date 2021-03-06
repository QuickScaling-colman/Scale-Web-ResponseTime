package com.QuickScaling.checkWebsite.DB;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class MongoWebsite extends AbstractVerticle{
	private MongoClient _mongoClient;
	
	@Override
	public void start(Future<Void> startFuture) {
		EventBus eb = vertx.eventBus();
		
		_mongoClient = MongoClient.createNonShared(vertx, config().getJsonObject("DB"));

		eb.consumer("GET_ALL_WEBSITES", request -> {
			this.GetAllWebsites(resQuery -> {			
				try {
					request.reply(resQuery.toString());
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		});
		
		eb.consumer("SAVE_RESPONSE_TIME", request -> {
			JsonObject jsonResponseTime = new JsonObject(request.body().toString());
			
			this.SaveResponseTimeToMongo(jsonResponseTime, res -> {
				request.reply(res);
			});
			
		});
		
		eb.consumer("SAVE_SCALE", request -> {
			JsonObject jsonResponseTime = new JsonObject(request.body().toString());
			
			_mongoClient.insert("websitesScale", jsonResponseTime , res -> {
				request.reply(res.succeeded());
			});
			
		});
		
		startFuture.complete();
	}
	
	public void GetAllWebsites(Handler<JsonArray> handler) {
		_mongoClient.find("websites", new JsonObject(), res -> {
			JsonArray returnList = new JsonArray();
			//ObjectMapper om = new ObjectMapper();
			//om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			
			for (JsonObject websiteJson : res.result()) {
				try {
					returnList.add(websiteJson);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			handler.handle(returnList);
		});
	}
	
	public void SaveResponseTimeToMongo(JsonObject jsonResponseTime,Handler<Boolean> handler) {
		_mongoClient.insert("websitesResponseTime", jsonResponseTime , res -> {
			handler.handle(res.succeeded());
		});
	}
}
