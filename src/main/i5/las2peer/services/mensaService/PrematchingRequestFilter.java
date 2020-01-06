package i5.las2peer.services.mensaService;

import i5.las2peer.api.Context;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.ext.Provider;

/**
 * Refresh available dishes if necessary before actually processing the request.
 */
@Provider
@PreMatching
public class PrematchingRequestFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext ctx) {
        MensaService service = (MensaService) Context.getCurrent().getService();
        service.updateDishes();
    }
}