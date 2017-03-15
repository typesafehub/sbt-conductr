package impl;

import api.CreditService;
import api.DebitService;
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class PaymentModule extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        bindServices(
                serviceBinding(DebitService.class, DebitServiceImpl.class),
                serviceBinding(CreditService.class, CreditServiceImpl.class)
        );
    }
}