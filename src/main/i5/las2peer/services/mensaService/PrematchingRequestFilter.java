package i5.las2peer.services.mensaService;

import i5.las2peer.api.Context;
import i5.las2peer.api.logging.MonitoringEvent;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.ext.Provider;

@Provider
@PreMatching
public class PrematchingRequestFilter implements ContainerRequestFilter {

  /**
   * Gets called before the service starts processing the request
   */
  @Override
  public void filter(ContainerRequestContext ctx) {
    Context.get().monitorEvent(MonitoringEvent.MESSAGE_RECEIVED, "");
    ctx.setProperty("timestamp", System.currentTimeMillis());
    MensaService service = (MensaService) Context.getCurrent().getService();
    service.fetchMensas();
  }
}
