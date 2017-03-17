package impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import api.FeedService;
import play.*;
import javax.inject.Inject;
import java.util.Date;
import java.io.*;

public class SocialModule extends AbstractModule implements ServiceGuiceSupport {
	@Override
	protected void configure() {
		bindServices(serviceBinding(FeedService.class, FeedServiceImpl.class));
	}
}