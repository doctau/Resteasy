package org.jboss.resteasy.star.messaging.test;

import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.spi.Link;
import org.jboss.resteasy.star.messaging.Constants;
import org.jboss.resteasy.star.messaging.queue.QueueDeployer;
import org.jboss.resteasy.star.messaging.queue.QueueDeployment;
import org.jboss.resteasy.star.messaging.queue.QueueServerDeployer;
import org.jboss.resteasy.test.BaseResourceTest;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import java.util.concurrent.CountDownLatch;

import static org.jboss.resteasy.test.TestPortProvider.*;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class AckQueueTest extends BaseResourceTest
{
   public static QueueDeployer server;

   @BeforeClass
   public static void setup() throws Exception
   {
      server = new QueueServerDeployer();
      server.getQueues().add(new QueueDeployment("testQueue", true, false));
      server.setRegistry(deployment.getRegistry());
      server.start();
   }

   @AfterClass
   public static void shutdown() throws Exception
   {
      server.stop();
   }

   @Test
   public void testAckTimeout() throws Exception
   {
      QueueDeployment deployment = new QueueDeployment();
      deployment.setAckTimeoutSeconds(1);
      deployment.setAutoAcknowledge(false);
      deployment.setDuplicatesAllowed(true);
      deployment.setDurableSend(false);
      deployment.setName("testAck");
      server.deploy(deployment);


      ClientRequest request = new ClientRequest(generateURL("/queues/testAck"));

      ClientResponse response = request.head();
      Assert.assertEquals(200, response.getStatus());
      Link sender = response.getLinkHeader().getLinkByTitle("create");
      System.out.println("create: " + sender);
      Link consumeNext = response.getLinkHeader().getLinkByTitle("consume-next");
      System.out.println("poller: " + consumeNext);

      {
         ClientResponse res = sender.request().body("text/plain", Integer.toString(1)).post();
         Assert.assertEquals(201, res.getStatus());


         res = consumeNext.request().post(String.class);
         Assert.assertEquals(200, res.getStatus());
         Link ack = res.getLinkHeader().getLinkByTitle("acknowledgement");
         System.out.println("ack: " + ack);
         Assert.assertNotNull(ack);
         Link session = res.getLinkHeader().getLinkByTitle("session");
         System.out.println("session: " + session);
         consumeNext = res.getLinkHeader().getLinkByTitle("consume-next");
         System.out.println("consumeNext: " + consumeNext);

         Thread.sleep(2000);

         ClientResponse ackRes = ack.request().formParameter("acknowledge", "true").post();
         Assert.assertEquals(412, ackRes.getStatus());
         System.out.println("**** Successfully failed ack");
         consumeNext = ackRes.getLinkHeader().getLinkByTitle("consume-next");
         System.out.println("consumeNext: " + consumeNext);
      }
      {
         ClientResponse res = consumeNext.request().post(String.class);
         Assert.assertEquals(200, res.getStatus());
         Link ack = res.getLinkHeader().getLinkByTitle("acknowledgement");
         System.out.println("ack: " + ack);
         Assert.assertNotNull(ack);
         Link session = res.getLinkHeader().getLinkByTitle("session");
         System.out.println("session: " + session);
         consumeNext = res.getLinkHeader().getLinkByTitle("consume-next");
         System.out.println("consumeNext: " + consumeNext);

         ClientResponse ackRes = ack.request().formParameter("acknowledge", "true").post();
         if (ackRes.getStatus() != 204)
         {
            System.out.println(ackRes.getEntity(String.class));
         }
         Assert.assertEquals(204, ackRes.getStatus());


         Assert.assertEquals(204, session.request().delete().getStatus());
      }


   }

   @Test
   public void testSuccessFirst() throws Exception
   {
      ClientRequest request = new ClientRequest(generateURL("/queues/testQueue"));

      ClientResponse response = request.head();
      Assert.assertEquals(200, response.getStatus());
      Link sender = response.getLinkHeader().getLinkByTitle("create");
      System.out.println("create: " + sender);
      Link consumeNext = response.getLinkHeader().getLinkByTitle("consume-next");
      System.out.println("poller: " + consumeNext);

      ClientResponse res = sender.request().body("text/plain", Integer.toString(1)).post();
      Assert.assertEquals(201, res.getStatus());

      res = consumeNext.request().post(String.class);
      Assert.assertEquals(200, res.getStatus());
      Link ack = res.getLinkHeader().getLinkByTitle("acknowledgement");
      System.out.println("ack: " + ack);
      Assert.assertNotNull(ack);
      Link session = res.getLinkHeader().getLinkByTitle("session");
      System.out.println("session: " + session);
      consumeNext = res.getLinkHeader().getLinkByTitle("consume-next");
      System.out.println("consumeNext: " + consumeNext);
      ClientResponse ackRes = ack.request().formParameter("acknowledge", "true").post();
      Assert.assertEquals(204, ackRes.getStatus());


      res = sender.request().body("text/plain", Integer.toString(2)).post();
      Assert.assertEquals(201, res.getStatus());

      System.out.println(consumeNext);
      res = consumeNext.request().header(Constants.WAIT_HEADER, "10").post(String.class);
      Assert.assertEquals(200, res.getStatus());
      ack = res.getLinkHeader().getLinkByTitle("acknowledgement");
      System.out.println("ack: " + ack);
      Assert.assertNotNull(ack);
      session = res.getLinkHeader().getLinkByTitle("session");
      System.out.println("session: " + session);
      res.getLinkHeader().getLinkByTitle("consume-next");
      System.out.println("consumeNext: " + consumeNext);
      ackRes = ack.request().formParameter("acknowledge", "true").post();
      Assert.assertEquals(204, ackRes.getStatus());

      Assert.assertEquals(204, session.request().delete().getStatus());
   }

   @Test
   public void testPull() throws Exception
   {
      ClientRequest request = new ClientRequest(generateURL("/queues/testQueue"));

      ClientResponse response = request.head();
      Assert.assertEquals(200, response.getStatus());
      Link sender = response.getLinkHeader().getLinkByTitle("create");
      System.out.println("create: " + sender);
      Link consumeNext = response.getLinkHeader().getLinkByTitle("consume-next");
      System.out.println("poller: " + consumeNext);

      ClientResponse<String> res = consumeNext.request().post(String.class);
      Assert.assertEquals(503, res.getStatus());
      consumeNext = res.getLinkHeader().getLinkByTitle("consume-next");
      System.out.println(consumeNext);
      Assert.assertEquals(201, sender.request().body("text/plain", Integer.toString(1)).post().getStatus());
      res = consumeNext.request().post(String.class);
      Assert.assertEquals(200, res.getStatus());
      Assert.assertEquals(Integer.toString(1), res.getEntity());
      Link ack = res.getLinkHeader().getLinkByTitle("acknowledgement");
      System.out.println("ack: " + ack);
      ClientResponse ackRes = ack.request().formParameter("acknowledge", "true").post();
      Assert.assertEquals(204, ackRes.getStatus());
      Assert.assertEquals(503, consumeNext.request().post().getStatus());
      Assert.assertEquals(201, sender.request().body("text/plain", Integer.toString(2)).post().getStatus());
      Assert.assertEquals(201, sender.request().body("text/plain", Integer.toString(3)).post().getStatus());


      res = consumeNext.request().post(String.class);
      Assert.assertEquals(200, res.getStatus());
      Assert.assertEquals(Integer.toString(2), res.getEntity());
      ack = res.getLinkHeader().getLinkByTitle("acknowledgement");
      System.out.println("ack: " + ack);
      ackRes = ack.request().formParameter("acknowledge", "true").post();
      Assert.assertEquals(204, ackRes.getStatus());

      res = consumeNext.request().post(String.class);
      Assert.assertEquals(200, res.getStatus());
      Assert.assertEquals(Integer.toString(3), res.getEntity());
      ack = res.getLinkHeader().getLinkByTitle("acknowledgement");
      System.out.println("ack: " + ack);
      ackRes = ack.request().formParameter("acknowledge", "true").post();
      Assert.assertEquals(204, ackRes.getStatus());
      Link session = ackRes.getLinkHeader().getLinkByTitle("session");

      Assert.assertEquals(503, consumeNext.request().post().getStatus());
      System.out.println(session);
      Assert.assertEquals(204, session.request().delete().getStatus());
   }

   private static CountDownLatch listenerLatch;

   @Path("/listener")
   public static class Listener
   {
      @POST
      @Consumes("text/plain")
      public void post(String message)
      {
         System.out.println(message);
         listenerLatch.countDown();

      }
   }

   /*
   @Test
   public void testPush() throws Exception
   {
      ClientRequest request = new ClientRequest(generateURL("/topics/test"));

      ClientResponse response = request.head();
      Assert.assertEquals(200, response.getStatus());
      Link sender = response.getLinkHeader().getLinkByTitle("sender");
      Link subscribers = response.getLinkHeader().getLinkByTitle("subscribers");


      listenerLatch = new CountDownLatch(1);
      response = subscribers.request().body("text/uri-list", "http://localhost:8085/listener").post();
      Assert.assertEquals(201, response.getStatus());
      String subscriber = (String) response.getHeaders().getFirst("Location");
      System.out.println("subscriber: " + subscriber);

      TJWSEmbeddedJaxrsServer server = new TJWSEmbeddedJaxrsServer();
      server.setPort(8085);
      server.start();
      server.getDeployment().getRegistry().addPerRequestResource(Listener.class);

      Assert.assertEquals(201, sender.request().body("text/plain", Integer.toString(1)).post().getStatus());

      Assert.assertTrue(listenerLatch.await(2, TimeUnit.SECONDS));


   }
   */
}