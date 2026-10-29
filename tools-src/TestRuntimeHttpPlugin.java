import java.net.http.*; import java.util.concurrent.*; import java.nio.*; import java.util.*;
public final class TestRuntimeHttpPlugin {
 public static void main(String[] args) throws Exception {
  HttpRequest.BodyPublisher body=HttpRequest.BodyPublishers.ofString("plugin body");
  StringBuilder result=new StringBuilder();
  body.subscribe(new Flow.Subscriber<ByteBuffer>() {
   public void onSubscribe(Flow.Subscription s) {s.request(Long.MAX_VALUE);}
   public void onNext(ByteBuffer b) {while(b.hasRemaining()) result.append((char)b.get());}
   public void onError(Throwable t) {throw new AssertionError(t);}
   public void onComplete() {}
  });
  if(!result.toString().equals("plugin body"))throw new AssertionError(result);
  Flow.Publisher<ByteBuffer> publisher=body;
  if(HttpRequest.BodyPublishers.fromPublisher(publisher,11).contentLength()!=11)throw new AssertionError();
  HttpResponse.BodySubscriber<String> subscriber=HttpResponse.BodySubscribers.ofString(java.nio.charset.StandardCharsets.UTF_8);
  subscriber.onSubscribe(new Flow.Subscription(){public void request(long n){}public void cancel(){}});
  subscriber.onNext(Collections.singletonList(ByteBuffer.wrap("response".getBytes("UTF-8"))));subscriber.onComplete();
  if(!subscriber.getBody().toCompletableFuture().get().equals("response"))throw new AssertionError();
  com.sun.net.httpserver.HttpServer server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),4);
  server.createContext("/", exchange -> {
   byte[] response="transport response".getBytes("UTF-8");
   exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
  });
  server.start();
  try {
   HttpRequest request=HttpRequest.newBuilder(new java.net.URI("http://127.0.0.1:"+server.getAddress().getPort()+"/"))
    .timeout(java.time.Duration.ofSeconds(5)).build();
   String response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString()).body();
   if(!response.equals("transport response"))throw new AssertionError(response);
  } finally {server.stop(0);}
  System.out.println("PASS: runtime plugin HTTP Flow publisher/subscriber signatures and real transport");
 }
}