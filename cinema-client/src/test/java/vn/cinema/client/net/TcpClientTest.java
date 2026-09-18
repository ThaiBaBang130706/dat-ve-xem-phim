package vn.cinema.client.net;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TcpClientTest {
 @Test void malformedResponseNotifiesDisconnectExactlyOnce() throws Exception {
  try (ServerSocket server = new ServerSocket(0);
       TcpClient client = new TcpClient("127.0.0.1", server.getLocalPort());
       Socket peer = server.accept()) {
   AtomicInteger calls = new AtomicInteger();
   CountDownLatch first = new CountDownLatch(1), duplicate = new CountDownLatch(1);
   client.onDisconnect(message -> {
    if (calls.incrementAndGet() > 1) duplicate.countDown();
    first.countDown();
   });
   peer.getOutputStream().write("not-json\n".getBytes(StandardCharsets.UTF_8));
   peer.getOutputStream().flush();
   assertTrue(first.await(5, TimeUnit.SECONDS), "Reader must report the failed connection");
   client.close();
   assertFalse(duplicate.await(300, TimeUnit.MILLISECONDS), "Only one disconnect notification per connection");
   assertEquals(1, calls.get());
   assertTrue(client.request("PING", new com.google.gson.JsonObject()).isCompletedExceptionally());
  }
 }
}
