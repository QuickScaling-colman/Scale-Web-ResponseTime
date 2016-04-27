package com.QuickScaling.checkWebsite.Main;

import com.QuickScaling.checkWebsite.DB.MongoWebsite;
import com.QuickScaling.checkWebsite.UI.Webserver;
import com.QuickScaling.checkWebsite.Worker.CheckWebsites;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Hello world!
 *
 */
public class MainVerticle extends AbstractVerticle
{
	private Logger logger = LoggerFactory.getLogger(MainVerticle.class);
	
	@Override
	public void start(Future<Void> startFuture) {		
		DeploymentOptions optionsMongo = new DeploymentOptions();
		optionsMongo.setWorker(true);
		
		String envMongo = System.getProperty("QuickScaling.MongoConfig");
		
		if(envMongo != null && envMongo != "") {
			JsonObject mongoConfig = new JsonObject();
			mongoConfig.put("DB", new JsonObject(envMongo));
			optionsMongo.setConfig(mongoConfig);
			logger.info("Load mongo config from environment variable");
		} else if (config().getJsonObject("DB") != null){
			JsonObject mongoConfig = new JsonObject();
			mongoConfig.put("DB", config().getJsonObject("DB"));
			optionsMongo.setConfig(mongoConfig);
			logger.info("Load mongo config from configuration file");
		} else {
			startFuture.fail("No mongoDB configuration");
		}
		
		vertx.deployVerticle(new MongoWebsite(), optionsMongo, res -> {
			if(res.succeeded()) {
				DeploymentOptions optionsCheckWebsites = new DeploymentOptions();
				optionsMongo.setWorker(true);
				
				String envCheckWebsites = System.getProperty("QuickScaling.PeriodicConf");
				
				if(envCheckWebsites != null && envCheckWebsites != "") {
					JsonObject mongoConfig = new JsonObject();
					mongoConfig.put("PeriodicConf", new JsonObject(envMongo));
					optionsCheckWebsites.setConfig(mongoConfig);
					logger.info("Load PeriodicConf config from environment variable");
				} else if (config().getJsonObject("PeriodicConf") != null){
					JsonObject mongoConfig = new JsonObject();
					mongoConfig.put("PeriodicConf", config().getJsonObject("PeriodicConf"));
					optionsCheckWebsites.setConfig(mongoConfig);
					logger.info("Load PeriodicConf config from configuration file");
				} else {
					startFuture.fail("No PeriodicConf configuration");
				}
				
				vertx.deployVerticle(new CheckWebsites(), optionsCheckWebsites);
			}
		});
		
		vertx.deployVerticle(new Webserver());
		
		startFuture.complete();
	}
}
