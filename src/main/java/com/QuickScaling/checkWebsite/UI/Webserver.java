package com.QuickScaling.checkWebsite.UI;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;

public class Webserver extends AbstractVerticle{
	@Override
	public void start(Future<Void> startFuture) { 
		startFuture.complete();
	}
}
