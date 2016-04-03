package impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import api.CreditService;
import play.*;
import javax.inject.Inject;
import java.util.Date;
import java.io.*;

public class CreditPaymentModule extends AbstractModule implements ServiceGuiceSupport {
	@Override
	protected void configure() {
		bindServices(serviceBinding(CreditService.class, CreditServiceImpl.class));
	}
}